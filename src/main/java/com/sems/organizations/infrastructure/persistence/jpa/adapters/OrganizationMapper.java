package com.sems.organizations.infrastructure.persistence.jpa.adapters;

import com.sems.organizations.domain.model.aggregates.Organization;
import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.model.entities.Zone;
import com.sems.organizations.infrastructure.persistence.jpa.entities.*;

/**
 * Traduccion entre el dominio y las filas de la base.
 *
 * <p>Existe para que el dominio no lleve anotaciones de JPA: el agregado se
 * escribe pensando en las reglas de negocio y la entidad JPA en las columnas,
 * sin que ninguno de los dos condicione al otro.
 */
public final class OrganizationMapper {

    private OrganizationMapper() {
    }

    public static Organization toDomain(OrganizationJpaEntity e) {
        return Organization.rehydrate(e.getOrganizationId(), e.getLegalName(), e.getTradeName(),
                e.getTaxId(), e.getBusinessType(), e.getStatus(), e.getCreatedAt(),
                e.getUpdatedAt());
    }

    public static OrganizationJpaEntity toEntity(Organization o) {
        return new OrganizationJpaEntity(o.getOrganizationId(), o.getLegalName(), o.getTradeName(),
                o.getTaxId(), o.getBusinessType(), o.getStatus(), o.getCreatedAt(),
                o.getUpdatedAt());
    }

    public static Site toDomain(SiteJpaEntity e) {
        return Site.rehydrate(e.getSiteId(), e.getOrganizationId(), e.getSiteCode(), e.getName(),
                e.getAddress(), e.getDistrict(), e.getFloorAreaM2(), e.getContractedPowerKw(),
                e.getTariffCategory(), e.isExcludesSundaysFromPeak(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public static SiteJpaEntity toEntity(Site s) {
        return new SiteJpaEntity(s.getSiteId(), s.getOrganizationId(), s.getSiteCode(), s.getName(),
                s.getAddress(), s.getDistrict(), s.getFloorAreaM2(), s.getContractedPowerKw(),
                s.getTariffCategory(), s.isExcludesSundaysFromPeak(), s.getStatus(),
                s.getCreatedAt(), s.getUpdatedAt());
    }

    public static Zone toDomain(ZoneJpaEntity e) {
        return Zone.rehydrate(e.getZoneId(), e.getSiteId(), e.getName(), e.getZoneType(),
                e.isOperatesOffHours(), e.getStatus(), e.getCreatedAt());
    }

    public static ZoneJpaEntity toEntity(Zone z) {
        return new ZoneJpaEntity(z.getZoneId(), z.getSiteId(), z.getName(), z.getZoneType(),
                z.isOperatesOffHours(), z.getStatus(), z.getCreatedAt());
    }

    public static Membership toDomain(MembershipJpaEntity e) {
        return Membership.rehydrate(e.getMembershipId(), e.getOrganizationId(), e.getUserId(),
                e.getRole(), e.getSiteId(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public static MembershipJpaEntity toEntity(Membership m) {
        return new MembershipJpaEntity(m.getMembershipId(), m.getOrganizationId(), m.getUserId(),
                m.getRole(), m.getSiteId(), m.getStatus(), m.getCreatedAt(), m.getUpdatedAt());
    }
}
