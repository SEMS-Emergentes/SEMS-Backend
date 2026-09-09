package com.sems.organizations.application;

import com.sems.organizations.domain.model.aggregates.Organization;
import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.model.entities.Zone;
import com.sems.organizations.domain.repositories.*;
import com.sems.shared.errors.AppException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas del modulo de organizaciones. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationQueryService {

    private final OrganizationRepository organizations;
    private final SiteRepository sites;
    private final ZoneRepository zones;
    private final MembershipRepository memberships;

    public Organization get(UUID organizationId) {
        return organizations.findById(organizationId)
                .orElseThrow(() -> AppException.notFound("organization not found"));
    }

    public List<Site> listSites(UUID organizationId) {
        return sites.findByOrganizationId(organizationId);
    }

    public Site getSite(UUID siteId) {
        return sites.findById(siteId).orElseThrow(() -> AppException.notFound("site not found"));
    }

    public List<Zone> listZones(UUID siteId) {
        return zones.findBySiteId(siteId);
    }

    public List<Membership> listMemberships(UUID organizationId) {
        return memberships.findByOrganizationId(organizationId);
    }

    /** Organizaciones a las que pertenece una persona, con el papel que tiene en cada una. */
    public List<Map.Entry<Organization, Membership>> listMine(UUID userId) {
        List<Map.Entry<Organization, Membership>> resultado = new ArrayList<>();
        for (Membership vinculo : memberships.findByUserId(userId)) {
            organizations.findById(vinculo.getOrganizationId())
                    .ifPresent(o -> resultado.add(Map.entry(o, vinculo)));
        }
        return resultado;
    }
}
