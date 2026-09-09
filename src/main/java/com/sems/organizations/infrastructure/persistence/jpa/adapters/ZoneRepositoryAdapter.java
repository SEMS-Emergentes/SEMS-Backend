package com.sems.organizations.infrastructure.persistence.jpa.adapters;

import com.sems.organizations.domain.model.entities.Zone;
import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.domain.repositories.ZoneRepository;
import com.sems.organizations.infrastructure.persistence.jpa.repositories.ZoneJpa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ZoneRepositoryAdapter implements ZoneRepository {

    private final ZoneJpa jpa;

    @Override
    public Zone save(Zone zone) {
        return OrganizationMapper.toDomain(jpa.save(OrganizationMapper.toEntity(zone)));
    }

    @Override
    public Optional<Zone> findById(UUID zoneId) {
        return jpa.findById(zoneId).map(OrganizationMapper::toDomain);
    }

    @Override
    public List<Zone> findBySiteId(UUID siteId) {
        return jpa.findBySiteIdAndStatusNotOrderByNameAsc(siteId, OrgStatus.ARCHIVED)
                .stream().map(OrganizationMapper::toDomain).toList();
    }
}
