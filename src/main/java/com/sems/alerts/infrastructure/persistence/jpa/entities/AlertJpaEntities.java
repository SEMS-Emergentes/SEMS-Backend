package com.sems.alerts.infrastructure.persistence.jpa.entities;

import com.sems.alerts.domain.model.valueobjects.Operator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Filas de las tablas del modulo de alertas. */
public final class AlertJpaEntities {

    private AlertJpaEntities() {
    }

    @Entity
    @Table(name = "al_alerts",
            indexes = {
                    @Index(name = "idx_al_alert_user", columnList = "user_id"),
                    @Index(name = "idx_al_alert_device", columnList = "device_id")
            })
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AlertRow {
        @Id @Column(name = "alert_id", nullable = false, updatable = false) private UUID alertId;
        @Column(name = "user_id", nullable = false) private UUID userId;
        @Column(name = "device_id") private UUID deviceId;
        @Column(name = "threshold_id") private UUID thresholdId;
        @Column(name = "inactivity_rule_id") private UUID inactivityRuleId;
        @Column(name = "alert_type", length = 80) private String alertType;
        @Column(name = "title", length = 200) private String title;
        @Column(name = "message", columnDefinition = "text") private String message;
        @Column(name = "severity", length = 20) private String severity;
        @Column(name = "status", length = 20) private String status;
        @Column(name = "triggered_at", nullable = false) private Instant triggeredAt;
        @Column(name = "resolved_at") private Instant resolvedAt;
    }

    @Entity
    @Table(name = "al_thresholds",
            indexes = {
                    @Index(name = "idx_al_thr_user", columnList = "user_id"),
                    @Index(name = "idx_al_thr_device", columnList = "device_id")
            })
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ThresholdRow {
        @Id @Column(name = "threshold_id", nullable = false, updatable = false) private UUID thresholdId;
        @Column(name = "user_id", nullable = false) private UUID userId;
        @Column(name = "device_id") private UUID deviceId;
        @Column(name = "threshold_name", length = 160) private String thresholdName;
        @Column(name = "metric", length = 80) private String metric;
        @Enumerated(EnumType.STRING)
        @Column(name = "operator", length = 30) private Operator operator;
        @Column(name = "threshold_value", nullable = false) private double thresholdValue;
        @Column(name = "active", nullable = false) private boolean active;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
        @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    }

    @Entity
    @Table(name = "al_inactivity_rules",
            indexes = @Index(name = "idx_al_rule_user", columnList = "user_id"))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class InactivityRuleRow {
        @Id @Column(name = "inactivity_rule_id", nullable = false, updatable = false) private UUID inactivityRuleId;
        @Column(name = "user_id", nullable = false) private UUID userId;
        @Column(name = "device_id") private UUID deviceId;
        @Column(name = "rule_name", length = 160) private String ruleName;
        @Column(name = "max_inactive_minutes", nullable = false) private int maxInactiveMinutes;
        @Column(name = "active", nullable = false) private boolean active;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
        @Column(name = "updated_at", nullable = false) private Instant updatedAt;
        /** Ultimo aviso de inactividad de esta regla. Columna nueva y opcional. */
        @Column(name = "last_alert_at") private Instant lastAlertAt;
    }

    @Entity
    @Table(name = "al_notification_preferences",
            uniqueConstraints = @UniqueConstraint(name = "uk_al_pref_user_channel",
                    columnNames = {"user_id", "channel"}))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PreferenceRow {
        @Id @Column(name = "preference_id", nullable = false, updatable = false) private UUID preferenceId;
        @Column(name = "user_id", nullable = false) private UUID userId;
        @Column(name = "channel", nullable = false, length = 40) private String channel;
        @Column(name = "enabled", nullable = false) private boolean enabled;
        @Column(name = "min_severity", length = 20) private String minSeverity;
        @Column(name = "quiet_hours_start") private Instant quietHoursStart;
        @Column(name = "quiet_hours_end") private Instant quietHoursEnd;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
        @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    }

    @Entity
    @Table(name = "al_notification_logs",
            indexes = @Index(name = "idx_al_log_alert", columnList = "alert_id"))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class NotificationLogRow {
        @Id @Column(name = "notification_id", nullable = false, updatable = false) private UUID notificationId;
        @Column(name = "alert_id") private UUID alertId;
        @Column(name = "channel", length = 40) private String channel;
        @Column(name = "recipient", length = 200) private String recipient;
        @Column(name = "status", length = 20) private String status;
        @Column(name = "sent_at") private Instant sentAt;
        @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
    }
}
