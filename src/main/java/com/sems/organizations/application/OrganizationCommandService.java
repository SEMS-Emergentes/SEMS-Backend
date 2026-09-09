package com.sems.organizations.application;

import com.sems.organizations.domain.model.aggregates.Organization;
import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.model.entities.Zone;
import com.sems.organizations.domain.model.valueobjects.*;
import com.sems.organizations.domain.repositories.*;
import com.sems.shared.errors.AppException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso que modifican organizaciones, locales, zonas y vinculos. */
@Service
@RequiredArgsConstructor
public class OrganizationCommandService {

    private final OrganizationRepository organizations;
    private final SiteRepository sites;
    private final ZoneRepository zones;
    private final MembershipRepository memberships;

    /**
     * Da de alta una organizacion y deja a quien la crea como administrador.
     *
     * <p>Las dos cosas van juntas a proposito, y en una sola transaccion. Una
     * organizacion sin ningun administrador no la puede gestionar nadie: no
     * habria quien diera de alta el primer local ni quien invitara al primer
     * supervisor.
     */
    @Transactional
    public Organization register(String legalName, String tradeName, String taxId,
                                 String businessType, UUID ownerUserId) {
        String ruc = taxId == null ? "" : taxId.trim();
        if (!ruc.isEmpty() && organizations.existsByTaxId(ruc)) {
            throw AppException.conflict("an organization with that tax_id already exists");
        }

        Organization organizacion = organizations.save(
                Organization.register(legalName, tradeName, taxId, BusinessType.of(businessType)));

        memberships.save(Membership.grant(organizacion.getOrganizationId(), ownerUserId,
                MembershipRole.ORG_ADMIN, null));

        return organizacion;
    }

    @Transactional
    public Organization update(UUID organizationId, String legalName, String tradeName,
                               String businessType) {
        Organization organizacion = requireOrganization(organizationId);
        organizacion.updateDetails(legalName, tradeName, BusinessType.of(businessType));
        return organizations.save(organizacion);
    }

    @Transactional
    public Site registerSite(UUID organizationId, String siteCode, String name, String address,
                             String district, BigDecimal floorAreaM2, BigDecimal contractedPowerKw,
                             String tariffCategory, Boolean excludesSundaysFromPeak) {
        Organization organizacion = requireOrganization(organizationId);
        if (!organizacion.isActive()) {
            throw AppException.conflict("the organization is not active");
        }

        String codigo = siteCode == null ? "" : siteCode.trim().toUpperCase();
        if (!codigo.isEmpty() && sites.existsBySiteCode(organizationId, codigo)) {
            throw AppException.conflict(
                    "a site with that site_code already exists in this organization");
        }

        return sites.save(Site.register(organizationId, siteCode, name, address, district,
                floorAreaM2, contractedPowerKw, TariffCategory.of(tariffCategory),
                Boolean.TRUE.equals(excludesSundaysFromPeak)));
    }

    @Transactional
    public Site updateSite(UUID siteId, String name, String address, String district,
                           BigDecimal floorAreaM2, BigDecimal contractedPowerKw,
                           String tariffCategory, Boolean excludesSundaysFromPeak) {
        Site local = requireSite(siteId);
        local.updateDetails(name, address, district, floorAreaM2, contractedPowerKw,
                TariffCategory.of(tariffCategory),
                Boolean.TRUE.equals(excludesSundaysFromPeak));
        return sites.save(local);
    }

    @Transactional
    public void archiveSite(UUID siteId) {
        Site local = requireSite(siteId);
        local.archive();
        sites.save(local);
    }

    @Transactional
    public Zone registerZone(UUID siteId, String name, String zoneType, Boolean operatesOffHours) {
        Site local = requireSite(siteId);
        if (!local.isActive()) {
            throw AppException.conflict("the site is not active");
        }
        return zones.save(Zone.register(siteId, name, ZoneType.of(zoneType), operatesOffHours));
    }

    @Transactional
    public Zone updateZone(UUID zoneId, String name, String zoneType, boolean operatesOffHours) {
        Zone zona = zones.findById(zoneId)
                .orElseThrow(() -> AppException.notFound("zone not found"));
        zona.updateDetails(name, ZoneType.of(zoneType), operatesOffHours);
        return zones.save(zona);
    }

    @Transactional
    public void archiveZone(UUID zoneId) {
        Zone zona = zones.findById(zoneId)
                .orElseThrow(() -> AppException.notFound("zone not found"));
        zona.archive();
        zones.save(zona);
    }

    /** Da acceso a una persona, o cambia el que ya tenia. */
    @Transactional
    public Membership grantMembership(UUID organizationId, UUID userId, String role, UUID siteId) {
        requireOrganization(organizationId);

        if (siteId != null) {
            Site local = requireSite(siteId);
            // Sin esta comprobacion se podria dar a alguien acceso de supervisor
            // sobre el local de otra empresa pasando el identificador a mano.
            if (!local.getOrganizationId().equals(organizationId)) {
                throw AppException.validation("the site does not belong to this organization");
            }
        }

        MembershipRole papel = MembershipRole.of(role);
        Optional<Membership> existente =
                memberships.findByOrganizationAndUser(organizationId, userId);

        if (existente.isPresent()) {
            Membership vinculo = existente.get();
            vinculo.changeRole(papel, siteId);
            return memberships.save(vinculo);
        }

        return memberships.save(Membership.grant(organizationId, userId, papel, siteId));
    }

    @Transactional
    public void revokeMembership(UUID membershipId) {
        Membership vinculo = memberships.findById(membershipId)
                .orElseThrow(() -> AppException.notFound("membership not found"));

        // Dejar una organizacion sin administradores la vuelve ingobernable: no
        // quedaria nadie que pueda dar de alta locales ni devolver el acceso.
        if (vinculo.getRole() == MembershipRole.ORG_ADMIN) {
            List<Membership> todos = memberships.findByOrganizationId(vinculo.getOrganizationId());
            long administradores = todos.stream()
                    .filter(m -> m.getRole() == MembershipRole.ORG_ADMIN).count();
            if (administradores <= 1) {
                throw AppException.conflict(
                        "the organization would be left without an administrator");
            }
        }

        vinculo.revoke();
        memberships.save(vinculo);
    }

    private Organization requireOrganization(UUID organizationId) {
        return organizations.findById(organizationId)
                .orElseThrow(() -> AppException.notFound("organization not found"));
    }

    private Site requireSite(UUID siteId) {
        return sites.findById(siteId).orElseThrow(() -> AppException.notFound("site not found"));
    }
}
