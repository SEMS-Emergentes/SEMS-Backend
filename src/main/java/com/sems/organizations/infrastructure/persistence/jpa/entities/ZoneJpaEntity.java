package com.sems.organizations.infrastructure.persistence.jpa.entities;

import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.domain.model.valueobjects.ZoneType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Fila de la tabla de zonas. */
@Entity
@Table(name = "og_zones",
        indexes = @Index(name = "idx_og_zones_site", columnList = "site_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ZoneJpaEntity {

    @Id
    @Column(name = "zone_id", nullable = false, updatable = false)
    private UUID zoneId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 30)
    private ZoneType zoneType;

    @Column(name = "operates_off_hours", nullable = false)
    private boolean operatesOffHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrgStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
