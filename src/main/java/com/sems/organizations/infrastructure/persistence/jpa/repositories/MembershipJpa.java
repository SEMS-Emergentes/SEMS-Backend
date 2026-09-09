package com.sems.organizations.infrastructure.persistence.jpa.repositories;

import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.infrastructure.persistence.jpa.entities.MembershipJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipJpa extends JpaRepository<MembershipJpaEntity, UUID> {

    List<MembershipJpaEntity> findByOrganizationIdAndStatusNot(UUID organizationId,
                                                              OrgStatus status);

    List<MembershipJpaEntity> findByUserIdAndStatusNot(UUID userId, OrgStatus status);

    Optional<MembershipJpaEntity> findByOrganizationIdAndUserIdAndStatusNot(UUID organizationId,
                                                                           UUID userId,
                                                                           OrgStatus status);
}
