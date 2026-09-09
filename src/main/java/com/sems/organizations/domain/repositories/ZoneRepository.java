package com.sems.organizations.domain.repositories;

import com.sems.organizations.domain.model.entities.Zone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para zonas. */
public interface ZoneRepository {

    Zone save(Zone zone);

    Optional<Zone> findById(UUID zoneId);

    /** Zonas vigentes del local. Las archivadas no salen. */
    List<Zone> findBySiteId(UUID siteId);
}
