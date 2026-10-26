package com.sems.organizations.application;

import com.sems.organizations.domain.model.aggregates.Organization;
import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.model.entities.Zone;
import com.sems.organizations.domain.model.valueobjects.*;
import com.sems.organizations.domain.repositories.*;
import com.sems.shared.errors.AppException;
import com.sems.subscriptions.interfaces.acl.SubscriptionsAcl;
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
    private final SubscriptionsAcl suscripciones;

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

        comprobarCupoDeLocales(organizationId);

        return sites.save(Site.register(organizationId, siteCode, name, address, district,
                floorAreaM2, contractedPowerKw, TariffCategory.of(tariffCategory),
                Boolean.TRUE.equals(excludesSundaysFromPeak)));
    }

    /**
     * Impide pasarse de los locales que incluye el plan contratado.
     *
     * <p>El cupo existia solo como texto en la ficha del plan: nadie lo leia, de
     * modo que un cliente de Starter podia abrir locales sin limite. Es un hueco
     * de ingreso, no solo funcional.
     *
     * <p>Se mide contra los administradores de la organizacion, que son quienes
     * pagan. Si varios administran y tienen planes distintos, manda el mas
     * amplio. Si ninguno tiene suscripcion activa no se aplica cupo: un limite
     * que no se conoce no se inventa, y bloquear por desconocimiento dejaria sin
     * poder operar a quien aun no ha contratado.
     *
     * <p>Solo cuenta locales vigentes. Un local archivado no ocupa cupo, que es
     * justo lo que el usuario espera al archivarlo.
     */
    private void comprobarCupoDeLocales(UUID organizationId) {
        Optional<SubscriptionsAcl.SiteAllowance> cupo = memberships.findByOrganizationId(organizationId)
                .stream()
                .filter(m -> m.isActive() && m.getRole() == MembershipRole.ORG_ADMIN)
                .map(m -> suscripciones.siteAllowanceFor(m.getUserId()))
                .flatMap(Optional::stream)
                .max((a, b) -> Integer.compare(a.sitesIncluded(), b.sitesIncluded()));

        if (cupo.isEmpty()) {
            return;
        }

        long vigentes = sites.findByOrganizationId(organizationId).stream()
                .filter(Site::isActive)
                .count();

        if (vigentes >= cupo.get().sitesIncluded()) {
            throw AppException.conflict("the " + cupo.get().planName() + " plan includes "
                    + cupo.get().sitesIncluded() + " site(s) and this organization already has "
                    + vigentes + "; upgrade the plan to add more");
        }
    }

    /**
     * Da de baja la organizacion entera.
     *
     * <p>Archivar, no borrar, igual que un local: las lecturas, los recibos y
     * los pagos que la referencian tienen que seguir existiendo, y la operacion
     * es reversible porque {@code Organization} ya sabia reactivarse.
     *
     * <p>Arrastra locales y zonas. Dejar locales vigentes colgando de una
     * organizacion archivada seria peor que no archivar nada: seguirian
     * contando para el cupo del plan y apareciendo en los listados por local,
     * sin una organizacion viva de la que dependan.
     *
     * <p>Las membresias se conservan. Quien administraba sigue pudiendo ver lo
     * que archivo y volver a activarlo; revocarlas dejaria la organizacion
     * fuera del alcance de todos, que es justo el estado que esto viene a
     * evitar.
     */
    @Transactional
    public void archiveOrganization(UUID organizationId) {
        Organization organizacion = requireOrganization(organizationId);
        if (!organizacion.isActive()) {
            return;
        }

        for (Site local : sites.findByOrganizationId(organizationId)) {
            if (!local.isActive()) {
                continue;
            }
            for (Zone zona : zones.findBySiteId(local.getSiteId())) {
                if (zona.isActive()) {
                    zona.archive();
                    zones.save(zona);
                }
            }
            local.archive();
            sites.save(local);
        }

        organizacion.archive();
        organizations.save(organizacion);
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
