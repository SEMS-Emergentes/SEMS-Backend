package com.sems.alerts.domain.model.entities;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Regla que avisa cuando un dispositivo lleva demasiado tiempo sin reportar. */
@Getter
public class InactivityRule {

    private final UUID inactivityRuleId;
    private final UUID userId;
    private final UUID deviceId;
    private final String ruleName;
    private final int maxInactiveMinutes;
    private final boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Cuando se aviso por ultima vez de esta inactividad. Nulo mientras el
     * equipo reporta con normalidad.
     */
    private Instant lastAlertAt;

    /**
     * Cada cuanto se recuerda una inactividad que sigue ahi.
     *
     * <p>Mas larga que la media hora de la regla de demanda a proposito: un
     * pico de potencia se corrige en minutos, pero un equipo que dejo de
     * reportar suele seguir sin reportar durante dias, y recordarlo cada media
     * hora llenaria la bandeja de la misma noticia.
     */
    private static final Duration SILENCIO = Duration.ofHours(6);

    public InactivityRule(UUID inactivityRuleId, UUID userId, UUID deviceId, String ruleName,
                          int maxInactiveMinutes, boolean active, Instant createdAt,
                          Instant updatedAt) {
        this(inactivityRuleId, userId, deviceId, ruleName, maxInactiveMinutes, active, createdAt,
                updatedAt, null);
    }

    public InactivityRule(UUID inactivityRuleId, UUID userId, UUID deviceId, String ruleName,
                          int maxInactiveMinutes, boolean active, Instant createdAt,
                          Instant updatedAt, Instant lastAlertAt) {
        this.inactivityRuleId = inactivityRuleId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.ruleName = ruleName;
        this.maxInactiveMinutes = maxInactiveMinutes;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastAlertAt = lastAlertAt;
    }

    public static InactivityRule create(UUID userId, UUID deviceId, String ruleName,
                                        int maxInactiveMinutes, Boolean active) {
        Instant now = Instant.now();
        return new InactivityRule(UUID.randomUUID(), userId, deviceId, ruleName,
                maxInactiveMinutes, active == null || active, now, now);
    }

    /**
     * Un umbral de cero o negativo desactiva la regla en la practica: sin esa
     * guarda, cualquier dispositivo estaria siempre inactivo.
     */
    public boolean isInactive(Instant lastActive, Instant now) {
        if (maxInactiveMinutes <= 0 || lastActive == null) {
            return false;
        }
        return Duration.between(lastActive, now).toMinutes() >= maxInactiveMinutes;
    }

    /**
     * Si una revision debe levantar aviso.
     *
     * <p>La primera vez que se detecta la inactividad avisa; mientras siga sin
     * reportar, como mucho una vez por ventana de silencio. La ventana la
     * cuenta la regla y no quien la revisa, igual que en la de demanda: asi
     * cambiar cada cuanto se revisa no cambia cuantos avisos recibe nadie.
     */
    public boolean mereceAviso(boolean inactivo, Instant ahora) {
        if (!inactivo || !active) {
            return false;
        }
        return lastAlertAt == null || ahora.isAfter(lastAlertAt.plus(SILENCIO));
    }

    /** Anota el resultado de la revision para no repetir el mismo aviso. */
    public void anotarRevision(boolean inactivo, Instant ahora) {
        // Al volver a reportar se olvida el aviso anterior: si el equipo se
        // vuelve a caer, hay que enterarse enseguida y no dentro de seis horas.
        this.lastAlertAt = inactivo ? ahora : null;
        this.updatedAt = ahora;
    }
}
