package com.sems.organizations.interfaces.acl;

import com.sems.organizations.domain.model.aggregates.Organization;
import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.repositories.MembershipRepository;
import com.sems.organizations.domain.repositories.OrganizationRepository;
import com.sems.organizations.domain.repositories.SiteRepository;
import com.sems.organizations.domain.repositories.ZoneRepository;
import com.sems.organizations.domain.model.entities.Zone;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementacion de la capa anticorrupcion de organizaciones. */
@Service
@RequiredArgsConstructor
public class OrganizationsAclImpl implements OrganizationsAcl {

    private final SiteRepository sites;
    private final ZoneRepository zones;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;

    @Override
    @Transactional(readOnly = true)
    public Optional<SiteSummary> siteSummary(UUID siteId) {
        if (siteId == null) {
            return Optional.empty();
        }
        return sites.findById(siteId).filter(Site::isActive).map(OrganizationsAclImpl::resumen);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteSummary> activeSitesOf(UUID organizationId) {
        if (organizationId == null) {
            return List.of();
        }
        return sites.findByOrganizationId(organizationId).stream()
                .filter(Site::isActive)
                .sorted(Comparator.comparing(Site::getCreatedAt))
                .map(OrganizationsAclImpl::resumen)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneSummary> activeZonesOf(UUID siteId) {
        if (siteId == null) {
            return List.of();
        }
        return zones.findBySiteId(siteId).stream()
                .filter(Zone::isActive)
                .sorted(Comparator.comparing(Zone::getName, String.CASE_INSENSITIVE_ORDER))
                .map(z -> new ZoneSummary(z.getZoneId(), z.getName(), z.getZoneType().name()))
                .toList();
    }

    private static SiteSummary resumen(Site local) {
        return new SiteSummary(local.getSiteId(), local.getSiteCode(), local.getName(),
                local.getContractedPowerKw(),
                local.getTariffCategory() == null ? null : local.getTariffCategory().name(),
                local.isExcludesSundaysFromPeak());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean siteExists(UUID siteId) {
        return siteId != null && sites.findById(siteId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean siteIsActive(UUID siteId) {
        return siteId != null && sites.findById(siteId).map(Site::isActive).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean zoneBelongsToSite(UUID zoneId, UUID siteId) {
        if (zoneId == null || siteId == null) {
            return false;
        }
        return zones.findById(zoneId)
                .map(z -> z.isActive() && siteId.equals(z.getSiteId()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> contractedPowerOf(UUID siteId) {
        if (siteId == null) {
            return Optional.empty();
        }
        return sites.findById(siteId)
                .filter(Site::isActive)
                .map(Site::getContractedPowerKw);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> siteOfZone(UUID zoneId) {
        if (zoneId == null) {
            return Optional.empty();
        }
        return zones.findById(zoneId).map(z -> z.getSiteId());
    }

    @Override
    @Transactional(readOnly = true)
    public long activeSiteCount(UUID organizationId) {
        if (organizationId == null) {
            return 0L;
        }
        return sites.findByOrganizationId(organizationId).stream().filter(Site::isActive).count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean esUnicoAdministradorDeAlgunaOrganizacion(UUID userId) {
        if (userId == null) {
            return false;
        }
        for (Membership vinculo : memberships.findByUserId(userId)) {
            if (!vinculo.isActive() || !vinculo.puedeAdministrarLaOrganizacion()) {
                continue;
            }
            boolean organizacionVigente = organizations.findById(vinculo.getOrganizationId())
                    .map(Organization::isActive)
                    .orElse(false);
            if (!organizacionVigente) {
                continue;
            }
            long otrosAdministradores = memberships.findByOrganizationId(vinculo.getOrganizationId())
                    .stream()
                    .filter(m -> m.isActive() && m.puedeAdministrarLaOrganizacion())
                    .filter(m -> !m.getUserId().equals(userId))
                    .count();
            if (otrosAdministradores == 0) {
                return true;
            }
        }
        return false;
    }
}
