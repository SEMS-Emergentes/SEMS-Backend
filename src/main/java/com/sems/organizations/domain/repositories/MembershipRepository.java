package com.sems.organizations.domain.repositories;

import com.sems.organizations.domain.model.entities.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para los vinculos de acceso. */
public interface MembershipRepository {

    Membership save(Membership membership);

    Optional<Membership> findById(UUID membershipId);

    List<Membership> findByOrganizationId(UUID organizationId);

    /** Vinculos vigentes de una persona. Puede pertenecer a varias organizaciones. */
    List<Membership> findByUserId(UUID userId);

    Optional<Membership> findByOrganizationAndUser(UUID organizationId, UUID userId);
}
