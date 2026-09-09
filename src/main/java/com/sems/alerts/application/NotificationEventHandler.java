package com.sems.alerts.application;

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

    @Value("${service.public-base-url:}")
    private String appBaseUrl;

    @Async
    @TransactionalEventListener
    public void onUserRegistered(DomainEvents.UserRegistered event) {
        notifications.sendEmail(null, event.emailAddress(),
                "Welcome to SEMS",
                """
                Hello,

                Your SEMS account is ready. You can now link your meters and start
                seeing where your electricity bill actually goes.

                Sign in here: %s

                The SEMS team
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
                "Verify your SEMS account",
                """
                Hello,

                Use this code to activate your account:

                    %s

                Or open this link directly: %s/verify?token=%s

                If this wasn't you, please ignore this message.
                """.formatted(event.token(), appBaseUrl, event.token()));
    }

    @Async
    @TransactionalEventListener
    public void onPasswordResetRequested(DomainEvents.PasswordResetRequested event) {
        notifications.sendEmail(null, event.emailAddress(),
                "Reset your SEMS password",
                """
                Hello,

                We received a request to change your password. Use this link:

                    %s/reset-password?token=%s

                The link expires in one hour. If this wasn't you, ignore this
                message and your password will stay the same.
                """.formatted(appBaseUrl, event.token()));
    }

    /** Comprobante de pago. El correo se resuelve preguntando a IAM. */
    @Async
    @TransactionalEventListener
    public void onPaymentProcessed(DomainEvents.PaymentProcessed event) {
        resolveEmail(event.userId()).ifPresent(email ->
                notifications.sendEmail(null, email,
                        "SEMS payment receipt",
                        """
                        Hello,

                        Your payment was recorded successfully.

                            Amount:       %s %s
                            Reference:    %s
                            Status:       %s

                        You can see the details at %s/subscription

                        Thank you for using SEMS.
                        """.formatted(event.amount(), event.currency().toUpperCase(),
                                event.paymentId(), event.status(), appBaseUrl)));
    }

    /** Alerta de consumo disparada por un umbral o una regla de inactividad. */
    @Async
    @TransactionalEventListener
    public void onAlertTriggered(DomainEvents.AlertTriggered event) {
        resolveEmail(event.userId()).ifPresent(email ->
                notifications.sendEmail(event.alertId(), email,
                        "Consumption alert at your site",
                        """
                        Hello,

                        %s

                        Severity: %s

                        See the details at %s/alerts
                        """.formatted(event.message(), event.severity(), appBaseUrl)));
    }

    private java.util.Optional<String> resolveEmail(UUID userId) {
        var email = iam.emailOf(userId);
        if (email.isEmpty()) {
            log.warn("No se encontro correo para el usuario {}; no se envia notificacion", userId);
        }
        return email;
    }
}
