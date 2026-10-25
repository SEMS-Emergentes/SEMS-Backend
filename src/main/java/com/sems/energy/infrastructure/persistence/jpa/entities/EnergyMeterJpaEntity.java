package com.sems.energy.infrastructure.persistence.jpa.entities;

import com.sems.energy.domain.model.valueobjects.MeterStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "em_energy_meters",
        indexes = {
                @Index(name = "idx_em_meters_user", columnList = "user_id"),
                @Index(name = "idx_em_meters_serial", columnList = "meter_serial", unique = true)
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EnergyMeterJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 80)
    private String userId;

    @Column(name = "meter_serial", nullable = false, length = 120)
    private String meterSerial;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "brand", length = 120)
    private String brand;

    @Column(name = "location", length = 160)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeterStatus status;

    @Column(name = "firmware_version", length = 40)
    private String firmwareVersion;

    @Column(name = "max_power_watts", nullable = false)
    private double maxPowerWatts;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Instante en que el medidor dejo de estar vinculado a la cuenta. Nulo
     * mientras sigue vinculado. Es columna nueva y acepta nulos, asi que
     * ddl-auto=update la agrega sin tocar las filas existentes.
     */
    @Column(name = "unlinked_at")
    private Instant unlinkedAt;
}
