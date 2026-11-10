package com.sems.alerts.application;

import com.sems.alerts.domain.model.entities.NotificationPreference;
import com.sems.alerts.domain.repositories.AlertRepositories.NotificationPreferenceRepository;
import com.sems.iam.interfaces.acl.IamAcl;
import com.sems.shared.events.DomainEvents;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Convierte eventos de dominio en correos al usuario.
 *
 * <p>Sustituye al consumidor de Kafka del servicio de alertas. Cada metodo
 * escucha un tipo de evento en lugar de un topic.
 *
 * <p>Dos anotaciones hacen el trabajo pesado:
 * <ul>
 *   <li>{@code @TransactionalEventListener} con fase AFTER_COMMIT: el correo
 *       sale solo si la operacion que lo origino se confirmo. Sin esto, un
 *       registro que luego se revierte generaria un correo de bienvenida a un
 *       usuario que no existe.</li>
 *   <li>{@code @Async}: el envio ocurre fuera del hilo de la peticion, asi el
 *       usuario no espera al servidor de correo.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationService notifications;
    private final IamAcl iam;
    private final NotificationPreferenceRepository preferencias;

    @Value("${service.public-base-url:}")
    private String appBaseUrl;

    @Async
    @TransactionalEventListener
    public void onUserRegistered(DomainEvents.UserRegistered event) {
        notifications.sendEmail(null, event.emailAddress(),
                "Bienvenido a SEMS",
                """
                Hola,

                Tu cuenta de SEMS está lista. Ya puedes vincular tus medidores y ver
                en qué se va tu recibo de luz.

                Ingresa aquí: %s

                El equipo de SEMS
                """.formatted(appBaseUrl));
    }

    /**
     * Codigo de verificacion de cuenta.
     *
     * <p>El token llega en el evento y no se vuelve a consultar: quien lo emitio
     * es el unico que lo conoce en claro, ya que en base de datos se guarda su
     * resumen.
     */
    @Async
    @TransactionalEventListener
    public void onVerificationRequested(DomainEvents.VerificationRequested event) {
        notifications.sendEmail(null, event.emailAddress(),
                "Verifica tu cuenta de SEMS",
                """
                Hola,

                Usa este código para activar tu cuenta:

                    %s

                O abre directamente este enlace: %s/verify?token=%s

                Si no fuiste tú, ignora este mensaje.
                """.formatted(event.token(), appBaseUrl, event.token()));
    }

    @Async
    @TransactionalEventListener
    public void onPasswordResetRequested(DomainEvents.PasswordResetRequested event) {
        notifications.sendEmail(null, event.emailAddress(),
                "Restablece tu contraseña de SEMS",
                """
                Hola,

                Recibimos un pedido para cambiar tu contraseña. Usa este enlace:

                    %s/reset-password?token=%s

                El enlace vence en una hora. Si no fuiste tú, ignora este mensaje
                y tu contraseña seguirá igual.
                """.formatted(appBaseUrl, event.token()));
    }

    /** Comprobante de pago. El correo se resuelve preguntando a IAM. */
    @Async
    @TransactionalEventListener
    public void onPaymentProcessed(DomainEvents.PaymentProcessed event) {
        resolveEmail(event.userId()).ifPresent(email ->
                notifications.sendEmail(null, email,
                        "Comprobante de pago de SEMS",
                        """
                        Hola,

                        Registramos tu pago.

                            Importe:      %s %s
                            Referencia:   %s
                            Estado:       %s

                        Puedes ver el detalle en %s/subscription

                        Gracias por usar SEMS.
                        """.formatted(event.amount(), event.currency().toUpperCase(),
                                event.paymentId(), event.status(), appBaseUrl)));
    }

    /** Alerta de consumo disparada por un umbral o una regla de inactividad. */
    @Async
    @TransactionalEventListener
    public void onAlertTriggered(DomainEvents.AlertTriggered event) {
        // La preferencia de correo se guardaba pero nadie la consultaba: apagarla
        // en la pantalla de Alertas no cambiaba nada y los avisos seguian
        // llegando. Sin preferencia guardada se envia, como hasta ahora.
        if (!correoActivado(event.userId())) {
            return;
        }
        resolveEmail(event.userId()).ifPresent(email ->
                notifications.sendEmail(event.alertId(), email,
                        "Alerta de consumo en tu local",
                        """
                        Hola,

                        %s

                        Severidad: %s

                        Mira el detalle en %s/alerts
                        """.formatted(event.message(), severidad(event.severity()), appBaseUrl)));
    }

    // Las severidades llegan como codigos ("high", "CRITICAL", "WARNING"); el
    // correo es para una persona, no para otro sistema.
    private static String severidad(String codigo) {
        return switch (String.valueOf(codigo).toLowerCase()) {
            case "critical", "high" -> "Alta";
            case "warning", "medium" -> "Media";
            case "low", "info" -> "Baja";
            default -> String.valueOf(codigo);
        };
    }

    private boolean correoActivado(UUID userId) {
        return preferencias.findByUserId(userId).stream()
                .filter(p -> NotificationPreference.CHANNEL_EMAIL.equalsIgnoreCase(p.getChannel()))
                .findFirst()
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }

    private java.util.Optional<String> resolveEmail(UUID userId) {
        var email = iam.emailOf(userId);
        if (email.isEmpty()) {
            log.warn("No se encontro correo para el usuario {}; no se envia notificacion", userId);
        }
        return email;
    }
}
