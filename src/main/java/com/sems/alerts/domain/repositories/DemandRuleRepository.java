package com.sems.alerts.domain.repositories;

import com.sems.alerts.domain.model.entities.DemandRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para las reglas de vigilancia de demanda. */
public interface DemandRuleRepository {

    DemandRule save(DemandRule rule);

    Optional<DemandRule> findById(UUID demandRuleId);

    List<DemandRule> findActiveBySiteId(UUID siteId);

    List<DemandRule> findByUserId(UUID userId);
}
