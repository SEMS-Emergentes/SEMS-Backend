package com.sems.energy.domain.repositories;

import com.sems.energy.domain.model.entities.EnergyReading;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnergyReadingRepository {
    EnergyReading save(EnergyReading reading);
    Optional<EnergyReading> findById(UUID id);
    List<EnergyReading> findByUserId(String userId, int limit);
    List<EnergyReading> findByDeviceId(String deviceId, int limit, int skip);
    List<EnergyReading> findByRange(String userId, Instant from, Instant to);

    /**
     * Lecturas de un conjunto de medidores desde una fecha.
     *
     * <p>Va por medidor y no por usuario porque el reparto por zona y la
     * comparacion entre locales se hacen sobre la instalacion, no sobre quien
     * dio de alta el equipo: en una cadena, los medidores de un local pueden
     * haberlos registrado varias personas.
     */
    List<EnergyReading> findByMeters(Collection<String> meterIds, Instant from);
    Optional<EnergyReading> findLatestByMeter(String meterId);
    Optional<EnergyReading> findLatestByDevice(String deviceId);
}
