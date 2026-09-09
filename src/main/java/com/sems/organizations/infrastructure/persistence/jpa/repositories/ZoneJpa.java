package com.sems.organizations.infrastructure.persistence.jpa.repositories;

import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.infrastructure.persistence.jpa.entities.ZoneJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneJpa extends JpaRepository<ZoneJpaEntity, UUID> {

    List<ZoneJpaEntity> findBySiteIdAndStatusNotOrderByNameAsc(UUID siteId, OrgStatus status);
}
