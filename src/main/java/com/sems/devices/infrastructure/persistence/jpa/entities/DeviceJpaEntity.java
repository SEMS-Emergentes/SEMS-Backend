package com.sems.devices.infrastructure.persistence.jpa.entities;

import com.sems.devices.domain.model.valueobjects.ConnectionProtocol;
import com.sems.devices.domain.model.valueobjects.DeviceStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Fila de la tabla de dispositivos.
 *
 * <p>Equivale al modelo de GORM del servicio original. Vive en infraestructura
 * porque es un detalle de persistencia: el dominio no debe depender de JPA.
 */
@Entity
@Table(name = "dm_devices",
        indexes = {
                @Index(name = "idx_dm_devices_user", columnList = "user_id"),
                @Index(name = "idx_dm_devices_site", columnList = "site_id"),
                @Index(name = "idx_dm_devices_zone", columnList = "zone_id"),
                @Index(name = "idx_dm_devices_external", columnList = "external_device_code", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceJpaEntity {

    @Id
    @Column(name = "device_id", nullable = false, updatable = false)
    private UUID deviceId;

    @Column(name = "external_device_code", nullable = false, length = 120)
    private String externalDeviceCode;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "device_name", nullable = false, length = 160)
    private String deviceName;

    @Column(name = "device_type", nullable = false, length = 80)
    private String deviceType;

    @Column(name = "brand", length = 120)
    private String brand;

    @Column(name = "model", length = 120)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_protocol", nullable = false, length = 20)
    private ConnectionProtocol connectionProtocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeviceStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
