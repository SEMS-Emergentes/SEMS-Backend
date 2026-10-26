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

    /**
     * Locales de la organizacion que esa persona alcanza.
     *
     * <p>Un SUPERVISOR esta asignado a un local concreto y no deberia ver el
     * resto ni en lista. Podia: abrir otro local devolvia 403, pero el listado
     * de la organizacion se los mostraba todos con su codigo, direccion y
     * potencia contratada. {@link Membership#alcanzaAlLocal} existe justo para
     * marcar ese limite y el listado no lo consultaba.
     *
     * <p>Sin membresia vigente no se devuelve nada. Llegar aqui ya exige
     * pertenecer a la organizacion, asi que el caso solo se da si la membresia
     * se revoco entre la comprobacion y la consulta.
     */
    public List<Site> listSites(UUID organizationId, UUID userId) {
        List<Site> todos = sites.findByOrganizationId(organizationId);
        return memberships.findByOrganizationAndUser(organizationId, userId)
                .filter(Membership::isActive)
                .map(vinculo -> todos.stream()
                        .filter(s -> vinculo.alcanzaAlLocal(s.getSiteId()))
                        .toList())
                .orElseGet(List::of);
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
