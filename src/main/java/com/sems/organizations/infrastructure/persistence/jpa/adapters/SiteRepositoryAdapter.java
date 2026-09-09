package com.sems.organizations.infrastructure.persistence.jpa.adapters;

import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.domain.repositories.SiteRepository;
import com.sems.organizations.infrastructure.persistence.jpa.repositories.SiteJpa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SiteRepositoryAdapter implements SiteRepository {

    private final SiteJpa jpa;

    @Override
    public Site save(Site site) {
        return OrganizationMapper.toDomain(jpa.save(OrganizationMapper.toEntity(site)));
    }

    @Override
    public Optional<Site> findById(UUID siteId) {
        return jpa.findById(siteId).map(OrganizationMapper::toDomain);
    }

    @Override
    public List<Site> findByOrganizationId(UUID organizationId) {
        return jpa.findByOrganizationIdAndStatusNotOrderBySiteCodeAsc(organizationId,
                        OrgStatus.ARCHIVED)
                .stream().map(OrganizationMapper::toDomain).toList();
    }

    @Override
    public boolean existsBySiteCode(UUID organizationId, String siteCode) {
        return jpa.existsByOrganizationIdAndSiteCode(organizationId, siteCode);
    }
}
