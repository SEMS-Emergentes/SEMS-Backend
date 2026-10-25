package com.sems.energy.infrastructure.persistence.jpa.adapters;

import com.sems.energy.domain.model.entities.*;
import com.sems.energy.domain.model.valueobjects.PowerReading;
import com.sems.energy.infrastructure.persistence.jpa.entities.*;

/** Traduce entre el modelo de dominio y las filas de Postgres. */
public final class EnergyMapper {

    private EnergyMapper() {
    }

    public static EnergyMeterJpaEntity toEntity(EnergyMeter m) {
        return new EnergyMeterJpaEntity(m.getId(), m.getUserId(), m.getSiteId(), m.getZoneId(),
                m.getMeterSerial(), m.getModel(), m.getBrand(), m.getLocation(), m.getStatus(),
                m.getFirmwareVersion(), m.getMaxPowerWatts(), m.getRegisteredAt(),
                m.getLastSeenAt(), m.getUpdatedAt(), m.getUnlinkedAt());
    }

    public static EnergyMeter toDomain(EnergyMeterJpaEntity r) {
        return EnergyMeter.rehydrate(r.getId(), r.getUserId(), r.getSiteId(), r.getZoneId(),
                r.getMeterSerial(), r.getModel(), r.getBrand(), r.getLocation(), r.getStatus(),
                r.getFirmwareVersion(), r.getMaxPowerWatts(), r.getRegisteredAt(),
                r.getLastSeenAt(), r.getUpdatedAt(), r.getUnlinkedAt());
    }

    public static EnergyReadingJpaEntity toEntity(EnergyReading e) {
        PowerReading p = e.getMeasurement();
        return new EnergyReadingJpaEntity(e.getId(), e.getUserId(), e.getMeterId(), e.getDeviceId(),
                p.powerWatts(), p.voltage(), p.current(), p.frequency(), p.energyKwh(),
                e.getTimestamp(), e.getReadingType(), e.getPhase(), e.getCreatedAt());
    }

    public static EnergyReading toDomain(EnergyReadingJpaEntity r) {
        PowerReading measurement = new PowerReading(r.getPowerWatts(), r.getVoltage(),
                r.getCurrent(), r.getFrequency(), r.getEnergyKwh());
        return EnergyReading.rehydrate(r.getId(), r.getUserId(), r.getMeterId(), r.getDeviceId(),
                measurement, r.getTimestamp(), r.getReadingType(), r.getPhase(), r.getCreatedAt());
    }

    public static DeviceConsumptionJpaEntity toEntity(DeviceConsumption c) {
        return new DeviceConsumptionJpaEntity(c.getId(), c.getUserId(), c.getDeviceId(),
                c.getDeviceName(), c.getMeterId(), c.getTotalKwh(), c.getCostEstimateSoles(),
                c.getPeriodStart(), c.getPeriodEnd(), c.getPeakPowerWatts(),
                c.getAveragePowerWatts(), c.getReadingCount(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public static DeviceConsumption toDomain(DeviceConsumptionJpaEntity r) {
        return DeviceConsumption.rehydrate(r.getId(), r.getUserId(), r.getDeviceId(),
                r.getDeviceName(), r.getMeterId(), r.getTotalKwh(), r.getCostEstimateSoles(),
                r.getPeriodStart(), r.getPeriodEnd(), r.getPeakPowerWatts(),
                r.getAveragePowerWatts(), r.getReadingCount(), r.getCreatedAt(), r.getUpdatedAt());
    }

    public static ConsumptionAlertJpaEntity toEntity(ConsumptionAlert a) {
        return new ConsumptionAlertJpaEntity(a.getId(), a.getUserId(), a.getDeviceId(), a.getMeterId(),
                a.getAlertType(), a.getSeverity(), a.getThresholdValue(), a.getActualValue(),
                a.getMessage(), a.isRead(), a.isResolved(), a.getCreatedAt(), a.getResolvedAt());
    }

    public static ConsumptionAlert toDomain(ConsumptionAlertJpaEntity r) {
        return ConsumptionAlert.rehydrate(r.getId(), r.getUserId(), r.getDeviceId(), r.getMeterId(),
                r.getAlertType(), r.getSeverity(), r.getThresholdValue(), r.getActualValue(),
                r.getMessage(), r.isRead(), r.isResolved(), r.getCreatedAt(), r.getResolvedAt());
    }
}
