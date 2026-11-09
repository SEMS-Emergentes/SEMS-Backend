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

    boolean existsByMeterIdAndDeviceIdAndTimestamp(String meterId, String deviceId, Instant timestamp);

    /**
     * Para la lectura del medidor que no se atribuye a ningun equipo.
     *
     * <p>Hace falta aparte porque {@code deviceId = null} en SQL no iguala a
     * nada, ni a otro nulo: la consulta derivada con el parametro a nulo no
     * encontraria nunca la fila que si existe.
     */
    boolean existsByMeterIdAndDeviceIdIsNullAndTimestamp(String meterId, Instant timestamp);
}
