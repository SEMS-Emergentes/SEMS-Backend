package com.sems.alerts.application;

import com.sems.alerts.domain.model.entities.NotificationLog;
import com.sems.alerts.domain.repositories.AlertRepositories.NotificationLogRepository;
import com.sems.alerts.domain.services.EmailSender;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Envio de correo con reintentos y registro.
 *
 * <p>Reemplaza al {@code sendWithRetry} escrito a mano en Go: aqui los tres
 * intentos con espera creciente los aporta {@code @Retryable}, y solo cuando se
 * agotan entra {@code @Recover}, que deja el fallo asentado en la bitacora.
 *
 * <p>Un correo que no sale <b>nunca</b> debe tumbar la operacion de negocio que
 * lo origino: el cobro ya ocurrio aunque el comprobante no llegue. Por eso el
 * metodo de recuperacion registra y calla.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailSender emailSender;
    private final NotificationLogRepository logs;

    @Retryable(retryFor = Exception.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendEmail(UUID alertId, String recipient, String subject, String body) {
        emailSender.send(recipient, subject, body);
        // El identificador de alerta viaja tal cual, nulo incluido: la bienvenida,
        // la verificacion y la recuperacion de contrasena no nacen de ninguna
        // alerta. Antes se guardaba un UUID de ceros en su lugar, que apuntaba a
        // una alerta inexistente y convertia cada registro de usuario en una fila
        // huerfana. La columna acepta nulos, que es lo que significa de verdad
        // "este aviso no viene de una alerta".
        logs.save(NotificationLog.sent(alertId, "email", recipient));
    }

    @Recover
    public void recoverFromFailedEmail(Exception failure, UUID alertId, String recipient,
                                       String subject, String body) {
        log.error("No se pudo enviar el correo a {} tras 3 intentos: {}",
                recipient, failure.getMessage());
        logs.save(NotificationLog.failed(alertId, "email", recipient, failure.getMessage()));
    }
}
