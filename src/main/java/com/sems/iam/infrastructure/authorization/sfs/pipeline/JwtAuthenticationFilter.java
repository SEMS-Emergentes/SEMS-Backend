package com.sems.iam.infrastructure.authorization.sfs.pipeline;

import com.sems.iam.application.internal.commandservices.AccountRecoveryService;
import com.sems.iam.infrastructure.authorization.sfs.services.SecurityUserFactory;
import com.sems.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.sems.iam.infrastructure.tokens.jwt.services.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JwtAuthenticationFilter runs on incoming HTTP requests and, if it finds a
 * valid JWT, tells Spring Security "this request belongs to this authenticated
 * user". It extends OncePerRequestFilter, which guarantees it runs exactly once
 * per request. This is the bridge between our JWT tokens and Spring Security's
 * authorization machinery.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final SecurityUserFactory securityUserFactory;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, SecurityUserFactory securityUserFactory,
                                   UserRepository users) {
        this.jwtService = jwtService;
        this.securityUserFactory = securityUserFactory;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.startsWith("/api/v1/auth/")
                || path.equals("/health")
                || path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars")
                || path.equals("/swagger-ui.html");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Tokens are sent in the "Authorization: Bearer <token>" header. If it
        // is missing or has the wrong prefix, we do NOT block the request here;
        // we just continue the chain without authentication. Whether the
        // endpoint actually requires a login is decided later by the security
        // configuration. (filterChain.doFilter passes control to the next filter.)
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Remove the "Bearer " prefix (7 characters) to get the raw token.
        String token = header.substring(7);
        // If the token is invalid/expired, again we simply continue
        // unauthenticated rather than throwing.
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // The token is valid, so read who the user is from its claims...
        Claims claims = jwtService.extractAllClaims(token);
        UUID userId = UUID.fromString(claims.get("userId", String.class));
        String email = claims.get("email", String.class);
        List<String> roles = claims.get("roles", List.class)
                .stream()
                .map(String::valueOf)
                .toList();
        // ...build a Spring Security "principal" (the logged-in user) and an
        // Authentication object. The null is the credentials (password), which
        // we do not need because the token already proved the identity.
        // Una cuenta dada de baja deja de entrar en el acto.
        //
        // El principal se arma con los claims del token, que no saben nada de
        // lo ocurrido desde que se emitio. Sin esta consulta, quien cerrara su
        // cuenta seguiria operando hasta que el token caducara, dos horas
        // despues. Es una lectura por clave primaria, del mismo orden que las
        // que ya hace cada comprobacion de propiedad.
        if (cuentaDadaDeBaja(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        var principal = securityUserFactory.create(userId, email, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        // Storing it in the SecurityContext is what marks the request as
        // authenticated for the rest of the processing.
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    /** Si la cuenta ya no existe, tampoco se deja pasar. */
    private boolean cuentaDadaDeBaja(UUID userId) {
        return users.findById(userId)
                .map(u -> AccountRecoveryService.STATUS_CLOSED
                        .equalsIgnoreCase(String.valueOf(u.getStatus())))
                .orElse(true);
    }
}
