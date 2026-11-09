package com.sems.energy.infrastructure.persistence.jpa.repositories;

import com.sems.energy.infrastructure.persistence.jpa.entities.EnergyMeterJpaEntity;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnergyMeterJpaRepository extends JpaRepository<EnergyMeterJpaEntity, UUID> {
    Optional<EnergyMeterJpaEntity> findByMeterSerial(String meterSerial);
    /** Solo los que siguen vinculados: un medidor desvinculado no es del usuario. */
    List<EnergyMeterJpaEntity> findByUserIdAndUnlinkedAtIsNullOrderByRegisteredAtDesc(String userId);

    List<EnergyMeterJpaEntity> findBySiteIdAndUnlinkedAtIsNullOrderByRegisteredAtDesc(String siteId);

    List<EnergyMeterJpaEntity> findByZoneIdAndUnlinkedAtIsNullOrderByRegisteredAtDesc(String zoneId);

    Optional<EnergyMeterJpaEntity> findByCredentialHashAndUnlinkedAtIsNull(String credentialHash);

    /** Toma la fila del medidor en exclusiva hasta que termine la transaccion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from EnergyMeterJpaEntity m where m.id = :id")
    Optional<EnergyMeterJpaEntity> lockById(@Param("id") UUID id);
}
