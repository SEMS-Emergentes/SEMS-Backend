package com.sems.energy.application;

import com.sems.energy.domain.model.entities.EnergyMeter;
import com.sems.energy.domain.repositories.EnergyMeterRepository;
import com.sems.shared.errors.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credenciales con las que un medidor se identifica ante la API.
 *
 * <p>Hasta ahora la unica forma de registrar consumo era el JWT de una persona:
 * un aparato que quisiera enviar lo suyo tendria que llevar incrustado el token
 * de su dueno, que caduca y que da acceso a toda la cuenta. Esto le da al
 * medidor una identidad propia, limitada a enviar sus propias lecturas.
 *
 * <p>La clave se genera con {@link SecureRandom} y se devuelve una sola vez; de
 * ella solo se guarda el hash SHA-256. No hace falta un algoritmo lento tipo
 * bcrypt porque no es una contrasena elegida por una persona: son 256 bits
 * aleatorios, y contra eso no hay diccionario que valga.
 */
@Service
@RequiredArgsConstructor
public class MeterCredentialService {

    /** Prefijo visible para reconocer la clave de un vistazo en un registro o un fichero. */
    private static final String PREFIJO = "sems_mk_";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final EnergyMeterRepository meters;

    /** Clave recien emitida. El texto en claro viaja una vez y no se guarda. */
    public record IssuedCredential(UUID meterId, String apiKey, java.time.Instant issuedAt) {
    }

    /**
     * Emite (o rota) la credencial de un medidor.
     *
     * <p>Emitir una nueva invalida la anterior, que es lo que se quiere tanto
     * al sustituir un aparato como al sospechar que la clave se filtro.
     */
    @Transactional
    public IssuedCredential issue(UUID meterId) {
        EnergyMeter medidor = meters.findById(meterId)
                .orElseThrow(() -> AppException.notFound("Meter '" + meterId + "' not found"));
        if (medidor.isUnlinked()) {
            throw AppException.conflict("the meter is not linked to any account");
        }
        String clave = generarClave();
        medidor.issueCredential(hash(clave));
        EnergyMeter guardado = meters.save(medidor);
        return new IssuedCredential(guardado.getId(), clave, guardado.getCredentialIssuedAt());
    }

    /**
     * El medidor que presenta esa clave.
     *
     * <p>Una clave desconocida y una ausente dan la misma respuesta, 401 sin
     * detalle: decir cual de las dos es ayudaria a adivinar claves validas.
     */
    public EnergyMeter resolve(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AppException.unauthorized("a meter credential is required");
        }
        return meters.findByCredentialHash(hash(apiKey.trim()))
                .orElseThrow(() -> AppException.unauthorized("invalid meter credential"));
    }

    private static String generarClave() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String clave) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(clave.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda implementacion de Java; si faltara,
            // el fallo es del entorno y no hay forma sensata de continuar.
            throw AppException.internal("SHA-256 is not available");
        }
    }
}
