package com.sems.organizations.domain.repositories;

import com.sems.organizations.domain.model.aggregates.Site;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para locales. */
public interface SiteRepository {

    Site save(Site site);

    Optional<Site> findById(UUID siteId);

    /** Locales vigentes de la organizacion. Los archivados no salen. */
    List<Site> findByOrganizationId(UUID organizationId);

    boolean existsBySiteCode(UUID organizationId, String siteCode);
}
