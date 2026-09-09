package com.sems.organizations.infrastructure.persistence.jpa.entities;

import com.sems.organizations.domain.model.valueobjects.BusinessType;
import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Fila de la tabla de organizaciones. Prefijo og_ para aislar el modulo. */
@Entity
@Table(name = "og_organizations",
        indexes = @Index(name = "idx_og_org_tax", columnList = "tax_id", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationJpaEntity {

    @Id
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "tax_id", nullable = false, length = 11)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 30)
    private BusinessType businessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrgStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
