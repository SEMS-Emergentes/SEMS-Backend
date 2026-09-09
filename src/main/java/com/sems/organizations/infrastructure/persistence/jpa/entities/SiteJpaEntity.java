package com.sems.organizations.infrastructure.persistence.jpa.entities;

import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.domain.model.valueobjects.TariffCategory;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Fila de la tabla de locales. */
@Entity
@Table(name = "og_sites",
        indexes = {
                @Index(name = "idx_og_sites_org", columnList = "organization_id"),
                // El codigo de local se repite entre cadenas distintas (dos
                // empresas pueden llamar "T-001" a su primera tienda), pero no
                // dentro de la misma.
                @Index(name = "idx_og_sites_code", columnList = "organization_id, site_code",
                        unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SiteJpaEntity {

    @Id
    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "district", length = 120)
    private String district;

    @Column(name = "floor_area_m2", precision = 12, scale = 2)
    private BigDecimal floorAreaM2;

    @Column(name = "contracted_power_kw", nullable = false, precision = 12, scale = 2)
    private BigDecimal contractedPowerKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "tariff_category", nullable = false, length = 10)
    private TariffCategory tariffCategory;

    // El valor por defecto va en la definicion de la columna, no solo en Java.
    //
    // Hibernate no puede anadir una columna NOT NULL sin default a una tabla que ya
    // tiene filas: la sentencia falla y el arranque queda a medias. Con el default,
    // las filas existentes se rellenan solas con el caso general del pliego, que es
    // no tener la exclusion concedida.
    @Column(name = "excludes_sundays_from_peak", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean excludesSundaysFromPeak;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrgStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
