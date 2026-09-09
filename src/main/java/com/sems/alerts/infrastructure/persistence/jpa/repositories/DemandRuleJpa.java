package com.sems.alerts.infrastructure.persistence.jpa.repositories;

import com.sems.alerts.infrastructure.persistence.jpa.entities.DemandRuleJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemandRuleJpa extends JpaRepository<DemandRuleJpaEntity, UUID> {

    /** Reglas activas de un local; las usa la evaluacion de demanda. */
    List<DemandRuleJpaEntity> findBySiteIdAndActiveTrue(UUID siteId);

    List<DemandRuleJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
