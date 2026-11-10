package com.sems.iam.infrastructure.tokens.jwt.services;

import com.sems.iam.domain.model.aggregates.UserAggregate;
import com.sems.iam.domain.services.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * JwtService is the infrastructure "adapter" that implements the TokenService
 * port using JSON Web Tokens (JWT). A JWT is a signed, self-contained token: it
 * carries data ("claims") and a signature, so anyone with the secret key can
 * verify it was issued by us and was not modified. Because the domain talks to
 * the TokenService interface, the rest of the app does not know (or care) that
 * JWT is the technology behind it.
 */
@Service
public class JwtService implements TokenService {
    private final Key key;               // the secret signing key
    private final long expirationMinutes; // how long a token stays valid

    // The constructor receives configuration values via @Value. The secret
    // string is turned into a cryptographic HMAC key. Keeping the secret in
    // configuration (not in code) is important so it can differ per environment
    // and never be committed to source control.
    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-minutes}") long expirationMinutes) {
        rechazarSecretoDeEjemplo(secret);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Valores de ejemplo publicados en el repositorio: en application.properties
     * como valor por defecto y en .env.example como plantilla.
     */
    static final Set<String> SECRETOS_DE_EJEMPLO = Set.of(
            "replace_with_minimum_32_chars_secret_key_for_hs256",
            "cambia_esto_por_un_secreto_de_al_menos_32_caracteres");

    /**
     * Se niega a arrancar con un secreto que cualquiera puede leer en GitHub.
     *
     * <p>Si JWT_SECRET no esta definida, Spring toma el valor por defecto de
     * application.properties, y el servicio arrancaba igual: firmaba los tokens
     * con una clave publica, con la que cualquiera puede fabricarse uno con rol
     * ADMIN. Fallar al arrancar es la unica forma de que ese olvido no pase
     * desapercibido en un despliegue.
     */
    static void rechazarSecretoDeEjemplo(String secret) {
        if (secret == null || SECRETOS_DE_EJEMPLO.contains(secret.trim())) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set or still has the example value; "
                    + "set it to a private random string of at least 32 characters");
        }
    }

    /**
     * Builds and signs a token for the user. We put the userId, email and roles
     * inside as claims, set when it was issued and when it expires, and finally
     * sign it. compact() serialises everything into the final token string.
     */
    @Override
    public String generateToken(UserAggregate userAggregate) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("userId", userAggregate.userId().toString())
                .claim("email", userAggregate.emailAddress().value())
                .claim("roles", userAggregate.roles().stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** Convenience method to read just the userId claim out of a token. */
    @Override
    public String extractUserId(String token) {
        return extractAllClaims(token).get("userId", String.class);
    }

    /**
     * Checks a token by trying to parse it. If the signature is wrong or the
     * token is expired, the parser throws and we return false. Catching a broad
     * Exception here is acceptable because ANY parsing problem means "invalid".
     */
    @Override
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Parses the token, verifying the signature with our key, and returns all
     * its claims. This will throw if the token is not authentic, which is why
     * the methods above rely on it.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build().parseSignedClaims(token).getPayload();
    }
}
