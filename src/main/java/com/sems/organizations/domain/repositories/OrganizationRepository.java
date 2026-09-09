package com.sems.organizations.domain.repositories;

import com.sems.organizations.domain.model.aggregates.Organization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para organizaciones. */
public interface OrganizationRepository {

    Organization save(Organization organization);

    Optional<Organization> findById(UUID organizationId);

    Optional<Organization> findByTaxId(String taxId);

    boolean existsByTaxId(String taxId);

    List<Organization> findAll();
}
