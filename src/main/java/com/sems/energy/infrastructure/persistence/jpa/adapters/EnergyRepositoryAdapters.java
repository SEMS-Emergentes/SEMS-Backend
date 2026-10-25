package com.sems.energy.infrastructure.persistence.jpa.adapters;

import com.sems.energy.domain.model.entities.*;
import com.sems.energy.domain.repositories.*;
import com.sems.energy.infrastructure.persistence.jpa.repositories.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Implementaciones JPA de los puertos del modulo de energia.
 *
 * <p>Agrupadas en un mismo archivo por ser adaptadores finos: cada una traduce
 * la firma del puerto a una consulta de Spring Data y mapea el resultado.
 */
public final class EnergyRepositoryAdapters {

    private EnergyRepositoryAdapters() {
    }

    @Repository
    @RequiredArgsConstructor
    public static class MeterAdapter implements EnergyMeterRepository {
        private final EnergyMeterJpaRepository jpa;

        @Override
        public EnergyMeter save(EnergyMeter meter) {
            return EnergyMapper.toDomain(jpa.save(EnergyMapper.toEntity(meter)));
        }

        @Override
        public Optional<EnergyMeter> findById(UUID id) {
            return jpa.findById(id).map(EnergyMapper::toDomain);
        }

        @Override
        public Optional<EnergyMeter> findBySerial(String meterSerial) {
            return jpa.findByMeterSerial(meterSerial).map(EnergyMapper::toDomain);
        }

        @Override
        public List<EnergyMeter> findByUserId(String userId) {
            return jpa.findByUserIdAndUnlinkedAtIsNullOrderByRegisteredAtDesc(userId).stream()
                    .map(EnergyMapper::toDomain).toList();
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class ReadingAdapter implements EnergyReadingRepository {
        private final EnergyReadingJpaRepository jpa;

        @Override
        public EnergyReading save(EnergyReading reading) {
            return EnergyMapper.toDomain(jpa.save(EnergyMapper.toEntity(reading)));
        }

        @Override
        public Optional<EnergyReading> findById(UUID id) {
            return jpa.findById(id).map(EnergyMapper::toDomain);
        }

        @Override
        public List<EnergyReading> findByUserId(String userId, int limit) {
            return jpa.findByUserIdOrderByTimestampDesc(userId, PageRequest.of(0, limit))
                    .stream().map(EnergyMapper::toDomain).toList();
        }

        @Override
        public List<EnergyReading> findByDeviceId(String deviceId, int limit, int skip) {
            int page = limit > 0 ? skip / limit : 0;
            return jpa.findByDeviceIdOrderByTimestampDesc(deviceId, PageRequest.of(page, limit))
                    .stream().map(EnergyMapper::toDomain).toList();
        }

        @Override
        public List<EnergyReading> findByRange(String userId, Instant from, Instant to) {
            return jpa.findByUserIdAndTimestampBetweenOrderByTimestampAsc(userId, from, to)
                    .stream().map(EnergyMapper::toDomain).toList();
        }

        @Override
        public Optional<EnergyReading> findLatestByMeter(String meterId) {
            return jpa.findFirstByMeterIdOrderByTimestampDesc(meterId).map(EnergyMapper::toDomain);
        }

        @Override
        public Optional<EnergyReading> findLatestByDevice(String deviceId) {
            return jpa.findFirstByDeviceIdOrderByTimestampDesc(deviceId).map(EnergyMapper::toDomain);
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class ConsumptionAdapter implements DeviceConsumptionRepository {
        private final DeviceConsumptionJpaRepository jpa;

        @Override
        public DeviceConsumption save(DeviceConsumption consumption) {
            return EnergyMapper.toDomain(jpa.save(EnergyMapper.toEntity(consumption)));
        }

        @Override
        public Optional<DeviceConsumption> findById(UUID id) {
            return jpa.findById(id).map(EnergyMapper::toDomain);
        }

        @Override
        public List<DeviceConsumption> findByUserId(String userId) {
            return jpa.findByUserIdOrderByPeriodEndDesc(userId).stream()
                    .map(EnergyMapper::toDomain).toList();
        }

        @Override
        public List<DeviceConsumption> findByDeviceId(String deviceId) {
            return jpa.findByDeviceIdOrderByPeriodEndDesc(deviceId).stream()
                    .map(EnergyMapper::toDomain).toList();
        }

        @Override
        public List<DeviceConsumption> findTopByUserId(String userId, int limit) {
            return jpa.findByUserIdOrderByTotalKwhDesc(userId, PageRequest.of(0, limit))
                    .stream().map(EnergyMapper::toDomain).toList();
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class AlertAdapter implements ConsumptionAlertRepository {
        private final ConsumptionAlertJpaRepository jpa;

        @Override
        public ConsumptionAlert save(ConsumptionAlert alert) {
            return EnergyMapper.toDomain(jpa.save(EnergyMapper.toEntity(alert)));
        }

        @Override
        public Optional<ConsumptionAlert> findById(UUID id) {
            return jpa.findById(id).map(EnergyMapper::toDomain);
        }

        @Override
        public List<ConsumptionAlert> findByUserId(String userId) {
            return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(EnergyMapper::toDomain).toList();
        }

        @Override
        public List<ConsumptionAlert> findUnreadByUserId(String userId) {
            return jpa.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                    .map(EnergyMapper::toDomain).toList();
        }
    }
}
