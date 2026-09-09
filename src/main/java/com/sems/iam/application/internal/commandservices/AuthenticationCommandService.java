package com.sems.iam.application.internal.commandservices;

import com.sems.iam.application.internal.outboundservices.IamEventPublisher;
import com.sems.iam.domain.model.aggregates.UserAggregate;
import com.sems.iam.domain.model.commands.*;
import com.sems.iam.domain.model.exceptions.*;
import com.sems.iam.domain.model.valueobjects.*;
import com.sems.iam.domain.services.*;
import com.sems.iam.infrastructure.oauth.google.GoogleTokenVerifier;
import com.sems.iam.infrastructure.oauth.google.GoogleOAuthClient;
import com.sems.iam.infrastructure.persistence.jpa.repositories.*;
import com.sems.iam.interfaces.rest.resources.LoginResponse;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthenticationCommandService holds the "command" use cases (the operations
 * that CHANGE data) related to authentication: register, login and the two
 * Google sign-in flows. This is the application layer, which ORCHESTRATES the
 * work: it calls repositories, domain services and the event publisher, but it
 * does not contain business rules itself (those live in the domain).
 *
 * Spring annotations:
 *  - @Service marks it as a Spring-managed bean so it can be injected elsewhere.
 *  - Constructor injection wires the 'final' dependencies below.
 */
@Service
public class AuthenticationCommandService {
    // All collaborators are 'final' and injected through the constructor. Note
    // that several are interfaces (PasswordHashingService, TokenService,
    // IamEventPublisher): the service depends on abstractions, not concrete
    // implementations, which keeps it flexible and testable.
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordHashingService passwordHashingService;
    private final TokenService tokenService;
    private final IamEventPublisher eventPublisher;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleOAuthClient googleOAuthClient;
    private final AuthTokenService authTokens;

    public AuthenticationCommandService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordHashingService passwordHashingService,
            TokenService tokenService,
            IamEventPublisher eventPublisher,
            GoogleTokenVerifier googleTokenVerifier,
            GoogleOAuthClient googleOAuthClient,
            AuthTokenService authTokens) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordHashingService = passwordHashingService;
        this.tokenService = tokenService;
        this.eventPublisher = eventPublisher;
        this.googleTokenVerifier = googleTokenVerifier;
        this.googleOAuthClient = googleOAuthClient;
        this.authTokens = authTokens;
    }

    // @Value injects a setting from configuration. The ":" with nothing after it
    // means the default is an empty string when the property is not set.
    @Value("${security.oauth2.google.client-id:}")
    private String googleClientId;

    // Cuando esta activo, una cuenta recien creada no puede iniciar sesion
    // hasta verificar su correo.
    @Value("${app.security.require-verification:false}")
    private boolean requireVerification;

    /**
     * Registers a brand new user. The @Transactional annotation wraps the whole
     * method in a single database transaction: if anything throws, every change
     * (the user row AND the user-role row) is rolled back together, so we never
     * end up with half-saved data.
     */
    @Transactional
    public LoginResponse register(RegisterUserCommand command) {
        // Build the email value object first; its constructor validates it.
        EmailAddress email = new EmailAddress(command.emailAddress());
        // Enforce that emails are unique before creating anything.
        if (userRepository.existsByEmailAddress(email.value())) throw new ConflictException("Email already exists");
        RoleName roleName = RoleName.from(command.role());
        // Look up the requested role; orElseThrow turns an empty Optional into a
        // clear NotFoundException instead of a confusing null later on.
        var role = roleRepository.findByName(roleName.name()).orElseThrow(() -> new NotFoundException("Role not found"));
        // Create the persistence entity. CRITICAL: we never store the raw
        // password; we store the result of passwordHashingService.hash(...).
        UserJpaEntity user = new UserJpaEntity();
        user.setUserId(UUID.randomUUID()); user.setEmailAddress(email.value()); user.setPasswordHash(passwordHashingService.hash(command.password())); user.setCreatedAt(Instant.now()); user.setUpdatedAt(Instant.now());
        // La cuenta nace pendiente solo si la verificacion esta activa; si no,
        // queda activa y el alta funciona como antes.
        user.setStatus(requireVerification ? "PENDING" : "ACTIVE");
        userRepository.save(user);
        // Link the user to the role through a join entity (many-to-many table).
        UserRoleJpaEntity userRole = new UserRoleJpaEntity(); userRole.setId(new UserRoleId(user.getUserId(), role.getRoleId())); userRole.setUser(user); userRole.setRole(role); userRoleRepository.save(userRole);
        // Build the domain aggregate and ask the token service to issue a token.
        UserAggregate agg = new UserAggregate(user.getUserId(), email, user.getPasswordHash(), Set.of(roleName), user.getCreatedAt(), user.getUpdatedAt());
        String token = tokenService.generateToken(agg);
        // Tell the rest of the system a user was registered (e.g. so other
        // microservices can create their own related records).
        eventPublisher.publishUserRegistered(user.getUserId().toString(), email.value(), roleName.name());

        // El codigo de verificacion se emite y se pide por evento: el envio del
        // correo ocurre en el modulo de notificaciones, tras confirmar la
        // transaccion.
        if (requireVerification) {
            String verificationToken = authTokens.issueVerificationToken(user.getUserId());
            eventPublisher.publishVerificationRequested(user.getUserId().toString(),
                    email.value(), verificationToken);
        }

        String refreshToken = authTokens.issueRefreshToken(user.getUserId());
        return new LoginResponse(token, refreshToken, user.getUserId(), email.value(), List.of(roleName.name()));
    }

    /**
     * Logs an existing user in with email + password. It is marked
     * readOnly = true because it only reads data; this is a hint that lets the
     * database/ORM optimise the transaction since nothing will be written.
     */
    @Transactional
    public LoginResponse login(LoginCommand command) {
        EmailAddress email = new EmailAddress(command.emailAddress());
        // Security note: when the email is unknown OR the password is wrong we
        // throw the SAME "Invalid credentials" error on purpose. Giving a
        // different message ("email not found") would leak which emails exist.
        UserJpaEntity user = userRepository.findByEmailAddress(email.value()).orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        // matches() re-hashes the given password and compares it to the stored
        // hash; we never decrypt the stored value (hashes are one-way).
        if (!passwordHashingService.matches(command.password(), user.getPasswordHash())) throw new UnauthorizedException("Invalid credentials");
        // Una cuenta sin verificar no entra. El mensaje si es explicito aqui
        // porque las credenciales ya se comprobaron: no filtra nada.
        if (requireVerification && "PENDING".equalsIgnoreCase(String.valueOf(user.getStatus()))) {
            throw new UnauthorizedException("Account is not verified yet");
        }
        // Collect the user's role names. This is a Java Stream pipeline:
        // findByUser_UserId -> map each join row to its role name -> toList().
        List<String> roles = userRoleRepository.findByUser_UserId(user.getUserId()).stream().map(ur -> ur.getRole().getName()).toList();
        UserAggregate agg = new UserAggregate(user.getUserId(), email, user.getPasswordHash(), roles.stream().map(RoleName::from).collect(java.util.stream.Collectors.toSet()), user.getCreatedAt(), user.getUpdatedAt());
        String token = tokenService.generateToken(agg);
        eventPublisher.publishUserLoggedIn(user.getUserId().toString(), email.value());
        String refreshToken = authTokens.issueRefreshToken(user.getUserId());
        return new LoginResponse(token, refreshToken, user.getUserId(), email.value(), roles);
    }

    /**
     * Logs in (or signs up) a user using a Google ID token. This implements the
     * "social login" pattern: instead of a password, we trust a token that
     * Google issued, after carefully validating it. If the email is new we
     * create the account on the fly ("just-in-time provisioning").
     */
    @Transactional
    public LoginResponse loginWithGoogle(String idToken) {
        // The verifier checks Google's signature and returns the token "claims"
        // (the key/value data inside it).
        Map<String, Object> claims = googleTokenVerifier.verify(idToken);
        String audience = String.valueOf(claims.getOrDefault("aud", ""));
        String email = String.valueOf(claims.getOrDefault("email", ""));
        String emailVerified = String.valueOf(claims.getOrDefault("email_verified", "false"));

        // These four checks are the security gate. Each one closes a hole:
        //  - config must exist; the token must have been issued FOR OUR app
        //    (audience); it must contain an email; and Google must have verified
        //    that email. Only then do we trust it.
        if (googleClientId.isBlank()) throw new IllegalArgumentException("Google OAuth is not configured");
        if (!googleClientId.equals(audience)) throw new UnauthorizedException("Google token audience is invalid");
        if (email.isBlank()) throw new UnauthorizedException("Google token does not include email");
        if (!"true".equalsIgnoreCase(emailVerified)) throw new UnauthorizedException("Google email is not verified");

        var existing = userRepository.findByEmailAddress(email);
        // Case 1: the user already exists -> just log them in.
        if (existing.isPresent()) {
            UserJpaEntity user = existing.get();
            List<String> roles = userRoleRepository.findByUser_UserId(user.getUserId()).stream().map(ur -> ur.getRole().getName()).toList();
            UserAggregate agg = new UserAggregate(
                    user.getUserId(),
                    new EmailAddress(email),
                    user.getPasswordHash(),
                    roles.stream().map(RoleName::from).collect(java.util.stream.Collectors.toSet()),
                    user.getCreatedAt(),
                    user.getUpdatedAt());
            String token = tokenService.generateToken(agg);
            eventPublisher.publishUserLoggedIn(user.getUserId().toString(), email);
            String refreshToken = authTokens.issueRefreshToken(user.getUserId());
            return new LoginResponse(token, refreshToken, user.getUserId(), email, roles);
        }

        // Case 2: first time we see this Google email -> create the account now.
        // New social users get a sensible default role (STAFF).
        RoleName defaultRole = RoleName.STAFF;
        var role = roleRepository.findByName(defaultRole.name()).orElseThrow(() -> new NotFoundException("Role not found"));
        UserJpaEntity user = new UserJpaEntity();
        user.setUserId(UUID.randomUUID());
        user.setEmailAddress(email);
        // The user logs in via Google, so there is no real password. We still
        // store a hash of a random value so the column is never empty and the
        // account cannot be used with password login.
        user.setPasswordHash(passwordHashingService.hash(UUID.randomUUID().toString()));
        user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        UserRoleJpaEntity userRole = new UserRoleJpaEntity();
        userRole.setId(new UserRoleId(user.getUserId(), role.getRoleId()));
        userRole.setUser(user);
        userRole.setRole(role);
        userRoleRepository.save(userRole);

        UserAggregate agg = new UserAggregate(
                user.getUserId(),
                new EmailAddress(email),
                user.getPasswordHash(),
                Set.of(defaultRole),
                user.getCreatedAt(),
                user.getUpdatedAt());
        String token = tokenService.generateToken(agg);
        eventPublisher.publishUserRegistered(user.getUserId().toString(), email, defaultRole.name());
        eventPublisher.publishUserLoggedIn(user.getUserId().toString(), email);
        // Google ya verifico el correo, asi que la cuenta nace activa.
        String refreshToken = authTokens.issueRefreshToken(user.getUserId());
        return new LoginResponse(token, refreshToken, user.getUserId(), email, List.of(defaultRole.name()));
    }

    /**
     * Handles the OAuth2 "authorization code" flow. The browser sends us a
     * short-lived 'code'; we exchange it with Google for an ID token, then reuse
     * loginWithGoogle to do the actual sign-in. Splitting it this way avoids
     * duplicating the login logic.
     */
    @Transactional
    public LoginResponse loginWithGoogleAuthorizationCode(String code) {
        String idToken = googleOAuthClient.exchangeCodeForIdToken(code);
        if (idToken.isBlank()) throw new UnauthorizedException("Google OAuth token exchange failed");
        return loginWithGoogle(idToken);
    }
}
