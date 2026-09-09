package com.sems.organizations.infrastructure.persistence.jpa.adapters;

import com.sems.organizations.domain.model.aggregates.Organization;
import com.sems.organizations.domain.repositories.OrganizationRepository;
import com.sems.organizations.infrastructure.persistence.jpa.repositories.OrganizationJpa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrganizationRepositoryAdapter implements OrganizationRepository {

    private final OrganizationJpa jpa;

    @Override
    public Organization save(Organization organization) {
        return OrganizationMapper.toDomain(jpa.save(OrganizationMapper.toEntity(organization)));
    }

    @Override
    public Optional<Organization> findById(UUID organizationId) {
        return jpa.findById(organizationId).map(OrganizationMapper::toDomain);
    }

    @Override
    public Optional<Organization> findByTaxId(String taxId) {
        return jpa.findByTaxId(taxId).map(OrganizationMapper::toDomain);
    }

    @Override
    public boolean existsByTaxId(String taxId) {
        return jpa.existsByTaxId(taxId);
    }

    @Override
    public List<Organization> findAll() {
        return jpa.findAll().stream().map(OrganizationMapper::toDomain).toList();
    }
}
