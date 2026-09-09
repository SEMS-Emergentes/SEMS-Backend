package com.sems.devices.infrastructure.persistence.jpa.entities;

import com.sems.devices.domain.model.valueobjects.BindingStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "dm_device_bindings",
        indexes = {
                @Index(name = "idx_dm_bindings_device", columnList = "device_id"),
                @Index(name = "idx_dm_bindings_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceBindingJpaEntity {

    @Id
    @Column(name = "binding_id", nullable = false, updatable = false)
    private UUID bindingId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "site_id")
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_status", nullable = false, length = 20)
    private BindingStatus bindingStatus;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "unlinked_at")
    private Instant unlinkedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
