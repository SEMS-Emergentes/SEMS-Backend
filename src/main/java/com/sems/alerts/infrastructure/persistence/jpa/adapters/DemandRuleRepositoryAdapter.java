package com.sems.alerts.infrastructure.persistence.jpa.adapters;

import com.sems.alerts.domain.model.entities.DemandRule;
import com.sems.alerts.domain.model.valueobjects.DemandLevel;
import com.sems.alerts.domain.repositories.DemandRuleRepository;
import com.sems.alerts.infrastructure.persistence.jpa.entities.DemandRuleJpaEntity;
import com.sems.alerts.infrastructure.persistence.jpa.repositories.DemandRuleJpa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DemandRuleRepositoryAdapter implements DemandRuleRepository {

    private final DemandRuleJpa jpa;

    @Override
    public DemandRule save(DemandRule rule) {
        return toDomain(jpa.save(toEntity(rule)));
    }

    @Override
    public Optional<DemandRule> findById(UUID demandRuleId) {
        return jpa.findById(demandRuleId).map(DemandRuleRepositoryAdapter::toDomain);
    }

    @Override
    public List<DemandRule> findActiveBySiteId(UUID siteId) {
        return jpa.findBySiteIdAndActiveTrue(siteId).stream()
                .map(DemandRuleRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<DemandRule> findByUserId(UUID userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DemandRuleRepositoryAdapter::toDomain).toList();
    }

    private static DemandRuleJpaEntity toEntity(DemandRule r) {
        return new DemandRuleJpaEntity(r.getDemandRuleId(), r.getSiteId(), r.getUserId(),
                r.getRuleName(), r.getContractedPowerKw(), r.getWarningPercent(), r.isActive(),
                r.getLastAlertLevel() == null ? null : r.getLastAlertLevel().name(),
                r.getLastAlertAt(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private static DemandRule toDomain(DemandRuleJpaEntity e) {
        return DemandRule.rehydrate(e.getDemandRuleId(), e.getSiteId(), e.getUserId(),
                e.getRuleName(), e.getContractedPowerKw(), e.getWarningPercent(), e.isActive(),
                nivel(e.getLastAlertLevel()), e.getLastAlertAt(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    /** Un nivel guardado que ya no exista en el enum cuenta como "sin aviso previo". */
    private static DemandLevel nivel(String guardado) {
        if (guardado == null || guardado.isBlank()) {
            return null;
        }
        try {
            return DemandLevel.valueOf(guardado);
        } catch (IllegalArgumentException desconocido) {
            return null;
        }
    }
}
