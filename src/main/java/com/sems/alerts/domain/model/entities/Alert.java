package com.sems.alerts.domain.model.entities;

import com.sems.alerts.domain.model.valueobjects.AlertStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Aviso generado para un usuario sobre uno de sus dispositivos. */
@Getter
public class Alert {

    public static final String STATUS_ACTIVE = AlertStatus.ACTIVE.wire();
    public static final String STATUS_RESOLVED = AlertStatus.RESOLVED.wire();

    private final UUID alertId;
    private final UUID userId;
    private final UUID deviceId;
    private final UUID thresholdId;
    private final UUID inactivityRuleId;
    private final String alertType;
    private final String title;
    private final String message;
    private final String severity;
    private String status;
    private final Instant triggeredAt;
    private Instant resolvedAt;

    public Alert(UUID alertId, UUID userId, UUID deviceId, UUID thresholdId, UUID inactivityRuleId,
                 String alertType, String title, String message, String severity, String status,
                 Instant triggeredAt, Instant resolvedAt) {
        this.alertId = alertId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.thresholdId = thresholdId;
        this.inactivityRuleId = inactivityRuleId;
        this.alertType = alertType;
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.status = status;
        this.triggeredAt = triggeredAt;
        this.resolvedAt = resolvedAt;
    }

    public static Alert raise(UUID userId, UUID deviceId, UUID thresholdId, UUID inactivityRuleId,
                              String alertType, String title, String message, String severity,
                              String status, Instant triggeredAt) {
        return new Alert(UUID.randomUUID(), userId, deviceId, thresholdId, inactivityRuleId,
                alertType, title, message, severity, AlertStatus.of(status).wire(),
                triggeredAt == null ? Instant.now() : triggeredAt, null);
    }

    /**
     * Cambia el estado del aviso. Al pasar a resuelta se sella la fecha si el
     * cliente no la envio.
     *
     * <p>El estado se valida y se normaliza: antes se guardaba lo que llegara,
     * y una palabra inventada dejaba el aviso fuera de todos los filtros, es
     * decir invisible, sin que nada diera error.
     */
    public void updateStatus(String newStatus, Instant resolvedAt) {
        AlertStatus estado = AlertStatus.of(newStatus);
        this.status = estado.wire();
        if (estado.esResuelta()) {
            this.resolvedAt = resolvedAt == null ? Instant.now() : resolvedAt;
        } else {
            this.resolvedAt = resolvedAt;
        }
    }
}
