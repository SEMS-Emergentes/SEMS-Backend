package com.sems.alerts.infrastructure.persistence.jpa.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Fila de la tabla de reglas de demanda. */
@Entity
@Table(name = "al_demand_rules",
        indexes = {
                @Index(name = "idx_al_demand_site", columnList = "site_id"),
                @Index(name = "idx_al_demand_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DemandRuleJpaEntity {

    @Id
    @Column(name = "demand_rule_id", nullable = false, updatable = false)
    private UUID demandRuleId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rule_name", length = 160)
    private String ruleName;

    @Column(name = "contracted_power_kw", nullable = false)
    private double contractedPowerKw;

    @Column(name = "warning_percent", nullable = false)
    private double warningPercent;

    @Column(name = "active", nullable = false)
    private boolean active;

    // Ultimo aviso levantado por esta regla, para no repetirlo en cada lectura.
    @Column(name = "last_alert_level", length = 20)
    private String lastAlertLevel;

    @Column(name = "last_alert_at")
    private Instant lastAlertAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
