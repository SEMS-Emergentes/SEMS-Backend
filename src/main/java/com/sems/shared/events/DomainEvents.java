package com.sems.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Eventos de dominio que cruzan bounded contexts.
 *
 * <p>Sustituyen a los topics de Kafka del disenio anterior. Cada topic paso a ser
 * un tipo de evento; la entrega la hace {@link org.springframework.context.ApplicationEventPublisher}
 * dentro del mismo proceso, despues de que la transaccion del emisor confirme.
 *
 * <p>Equivalencia con el disenio de microservicios:
 * <pre>
 *   iam.events            -> UserRegistered, UserLoggedIn, VerificationRequested, PasswordResetRequested
 *   device.events         -> DeviceRegistered, DeviceLinked, DeviceUnlinked, DeviceStatusUpdated
 *   energy.events         -> ReadingProcessed
 *   alerts.events         -> AlertTriggered
 *   payments.events       -> PaymentProcessed
 *   subscriptions.events  -> SubscriptionChanged
 * </pre>
 *
 * <p>Los consumidores no conocen al emisor: escuchan el tipo. Si en el futuro se
 * vuelve a un broker, basta con anadir un adaptador que reenvie estos mismos
 * eventos, sin tocar ni emisores ni consumidores.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    /** Contrato comun: todo evento sabe cuando ocurrio y a que usuario pertenece. */
    public interface DomainEvent {
        UUID userId();

        Instant occurredAt();
    }

    // ------------------------------------------------------- organizaciones

    /**
     * Un local se dio de baja.
     *
     * <p>Lo escuchan energia y dispositivos para desactivar lo que tenian
     * instalado alli. Va por evento y no por llamada directa porque
     * organizaciones no sabe que es un medidor ni un equipo, y no deberia tener
     * que saberlo para cerrar un local.
     *
     * <p>{@code userId} es quien pidio la baja, no el dueno del equipo: sirve
     * para rastrear quien cerro que, no para decidir sobre que actuar.
     */
    public record SiteArchived(UUID userId, UUID siteId, Instant occurredAt)
            implements DomainEvent {
        public SiteArchived(UUID userId, UUID siteId) {
            this(userId, siteId, Instant.now());
        }
    }

    // ---------------------------------------------------------------- iam

    public record UserRegistered(UUID userId, String emailAddress, String role, Instant occurredAt)
            implements DomainEvent {
        public UserRegistered(UUID userId, String emailAddress, String role) {
            this(userId, emailAddress, role, Instant.now());
        }
    }

    public record UserLoggedIn(UUID userId, String emailAddress, Instant occurredAt)
            implements DomainEvent {
        public UserLoggedIn(UUID userId, String emailAddress) {
            this(userId, emailAddress, Instant.now());
        }
    }

    public record RoleAssigned(UUID userId, String role, Instant occurredAt)
            implements DomainEvent {
        public RoleAssigned(UUID userId, String role) {
            this(userId, role, Instant.now());
        }
    }

    /** Pide al modulo de notificaciones que envie el codigo de verificacion de cuenta. */
    public record VerificationRequested(UUID userId, String emailAddress, String token, Instant occurredAt)
            implements DomainEvent {
        public VerificationRequested(UUID userId, String emailAddress, String token) {
            this(userId, emailAddress, token, Instant.now());
        }
    }

    /** Pide al modulo de notificaciones que envie el enlace de recuperacion de contrasena. */
    public record PasswordResetRequested(UUID userId, String emailAddress, String token, Instant occurredAt)
            implements DomainEvent {
        public PasswordResetRequested(UUID userId, String emailAddress, String token) {
            this(userId, emailAddress, token, Instant.now());
        }
    }

    // ------------------------------------------------------------ devices

    public record DeviceRegistered(UUID userId, UUID deviceId, String name, String type, Instant occurredAt)
            implements DomainEvent {
        public DeviceRegistered(UUID userId, UUID deviceId, String name, String type) {
            this(userId, deviceId, name, type, Instant.now());
        }
    }

    public record DeviceLinked(UUID userId, UUID deviceId, UUID bindingId, Instant occurredAt)
            implements DomainEvent {
        public DeviceLinked(UUID userId, UUID deviceId, UUID bindingId) {
            this(userId, deviceId, bindingId, Instant.now());
        }
    }

    public record DeviceUnlinked(UUID userId, UUID deviceId, UUID bindingId, Instant occurredAt)
            implements DomainEvent {
        public DeviceUnlinked(UUID userId, UUID deviceId, UUID bindingId) {
            this(userId, deviceId, bindingId, Instant.now());
        }
    }

    public record DeviceStatusUpdated(UUID userId, UUID deviceId, String status, Instant occurredAt)
            implements DomainEvent {
        public DeviceStatusUpdated(UUID userId, UUID deviceId, String status) {
            this(userId, deviceId, status, Instant.now());
        }
    }

    // ------------------------------------------------------------- energy

    /**
     * Una lectura del medidor ya validada y almacenada.
     * La escuchan analytics (para rankings y proyecciones) y alerts (para umbrales).
     */
    public record ReadingProcessed(UUID userId, UUID deviceId, UUID meterId,
                                   BigDecimal consumptionKwh, Instant recordedAt, Instant occurredAt)
            implements DomainEvent {
        public ReadingProcessed(UUID userId, UUID deviceId, UUID meterId,
                                BigDecimal consumptionKwh, Instant recordedAt) {
            this(userId, deviceId, meterId, consumptionKwh, recordedAt, Instant.now());
        }
    }

    // ------------------------------------------------------------- alerts

    public record AlertTriggered(UUID userId, UUID alertId, String alertType,
                                 String severity, String message, Instant occurredAt)
            implements DomainEvent {
        public AlertTriggered(UUID userId, UUID alertId, String alertType, String severity, String message) {
            this(userId, alertId, alertType, severity, message, Instant.now());
        }
    }

    // ----------------------------------------------------------- payments

    public record PaymentProcessed(UUID userId, UUID paymentId, BigDecimal amount,
                                   String currency, String status, Instant occurredAt)
            implements DomainEvent {
        public PaymentProcessed(UUID userId, UUID paymentId, BigDecimal amount, String currency, String status) {
            this(userId, paymentId, amount, currency, status, Instant.now());
        }
    }

    // ------------------------------------------------------ subscriptions

    public record SubscriptionChanged(UUID userId, UUID subscriptionId, String planName,
                                      String status, Instant occurredAt)
            implements DomainEvent {
        public SubscriptionChanged(UUID userId, UUID subscriptionId, String planName, String status) {
            this(userId, subscriptionId, planName, status, Instant.now());
        }
    }
}
