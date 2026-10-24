package com.sems.energy.infrastructure.persistence.jpa.repositories;

import com.sems.energy.infrastructure.persistence.jpa.entities.EnergyMeterJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnergyMeterJpaRepository extends JpaRepository<EnergyMeterJpaEntity, UUID> {
    Optional<EnergyMeterJpaEntity> findByMeterSerial(String meterSerial);
    /** Solo los que siguen vinculados: un medidor desvinculado no es del usuario. */
    List<EnergyMeterJpaEntity> findByUserIdAndUnlinkedAtIsNullOrderByRegisteredAtDesc(String userId);
}
