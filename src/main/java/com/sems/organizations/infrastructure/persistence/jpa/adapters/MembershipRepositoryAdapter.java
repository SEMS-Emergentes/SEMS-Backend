package com.sems.organizations.infrastructure.persistence.jpa.adapters;

import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.domain.repositories.MembershipRepository;
import com.sems.organizations.infrastructure.persistence.jpa.repositories.MembershipJpa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MembershipRepositoryAdapter implements MembershipRepository {

    private final MembershipJpa jpa;

    @Override
    public Membership save(Membership membership) {
        return OrganizationMapper.toDomain(jpa.save(OrganizationMapper.toEntity(membership)));
    }

    @Override
    public Optional<Membership> findById(UUID membershipId) {
        return jpa.findById(membershipId).map(OrganizationMapper::toDomain);
    }

    @Override
    public List<Membership> findByOrganizationId(UUID organizationId) {
        return jpa.findByOrganizationIdAndStatusNot(organizationId, OrgStatus.ARCHIVED)
                .stream().map(OrganizationMapper::toDomain).toList();
    }

    @Override
    public List<Membership> findByUserId(UUID userId) {
        return jpa.findByUserIdAndStatusNot(userId, OrgStatus.ARCHIVED)
                .stream().map(OrganizationMapper::toDomain).toList();
    }

    @Override
    public Optional<Membership> findByOrganizationAndUser(UUID organizationId, UUID userId) {
        return jpa.findByOrganizationIdAndUserIdAndStatusNot(organizationId, userId,
                OrgStatus.ARCHIVED).map(OrganizationMapper::toDomain);
    }
}
