package com.sems.organizations.infrastructure.persistence.jpa.entities;

import com.sems.organizations.domain.model.valueobjects.MembershipRole;
import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Fila de la tabla de vinculos de acceso. */
@Entity
@Table(name = "og_memberships",
        indexes = {
                @Index(name = "idx_og_mem_user", columnList = "user_id"),
                @Index(name = "idx_og_mem_org", columnList = "organization_id"),
                // Una persona tiene un solo papel en cada organizacion. Dos
                // vinculos obligarian a resolver cual gana, y eso siempre acaba
                // dando mas acceso del previsto.
                @Index(name = "idx_og_mem_unique", columnList = "organization_id, user_id",
                        unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MembershipJpaEntity {

    @Id
    @Column(name = "membership_id", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MembershipRole role;

    @Column(name = "site_id")
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrgStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
