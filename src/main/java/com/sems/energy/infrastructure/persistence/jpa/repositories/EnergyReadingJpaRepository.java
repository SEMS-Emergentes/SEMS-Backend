package com.sems.energy.infrastructure.persistence.jpa.repositories;

import com.sems.energy.infrastructure.persistence.jpa.entities.EnergyReadingJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnergyReadingJpaRepository extends JpaRepository<EnergyReadingJpaEntity, UUID> {

    List<EnergyReadingJpaEntity> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    List<EnergyReadingJpaEntity> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);

    List<EnergyReadingJpaEntity> findByUserIdAndTimestampBetweenOrderByTimestampAsc(
            String userId, Instant from, Instant to);

    List<EnergyReadingJpaEntity> findByMeterIdInAndTimestampGreaterThanEqualOrderByTimestampAsc(
            Collection<String> meterIds, Instant from);

    Optional<EnergyReadingJpaEntity> findFirstByMeterIdOrderByTimestampDesc(String meterId);

    Optional<EnergyReadingJpaEntity> findFirstByDeviceIdOrderByTimestampDesc(String deviceId);
}
