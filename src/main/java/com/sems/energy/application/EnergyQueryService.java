package com.sems.energy.application;

import com.sems.energy.domain.model.entities.*;
import com.sems.energy.domain.repositories.*;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de solo lectura del modulo de energia. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnergyQueryService {

    private final EnergyMeterRepository meters;
    private final EnergyReadingRepository readings;
    private final DeviceConsumptionRepository consumptions;
    private final ConsumptionAlertRepository alerts;

    public EnergyMeter meterById(UUID meterId) {
        return meters.findById(meterId)
                .orElseThrow(() -> AppException.notFound("Meter '" + meterId + "' not found"));
    }

    public List<EnergyMeter> metersByUser(String userId) {
        return meters.findByUserId(userId);
    }

    /** Medidores instalados en un local: la cobertura de medicion del local. */
    public List<EnergyMeter> metersBySite(String siteId) {
        return meters.findBySiteId(siteId);
    }

    /** Medidores de una zona concreta del local. */
    public List<EnergyMeter> metersByZone(String zoneId) {
        return meters.findByZoneId(zoneId);
    }

    public EnergyReading readingById(UUID readingId) {
        return readings.findById(readingId)
                .orElseThrow(() -> AppException.notFound("Reading '" + readingId + "' not found"));
    }

    public List<EnergyReading> readingsByUser(String userId, int limit) {
        return readings.findByUserId(userId, limit);
    }

    public List<EnergyReading> readingsByDevice(String deviceId, int limit, int skip) {
        return readings.findByDeviceId(deviceId, limit, skip);
    }

    public List<EnergyReading> readingsByRange(String userId, Instant from, Instant to) {
        return readings.findByRange(userId, from, to);
    }

    public EnergyReading latestByMeter(String meterId) {
        return readings.findLatestByMeter(meterId)
                .orElseThrow(() -> AppException.notFound("No readings for meter '" + meterId + "'"));
    }

    public EnergyReading latestByDevice(String deviceId) {
        return readings.findLatestByDevice(deviceId)
                .orElseThrow(() -> AppException.notFound("No readings found for device '" + deviceId + "'"));
    }

    public DeviceConsumption consumptionById(UUID id) {
        return consumptions.findById(id)
                .orElseThrow(() -> AppException.notFound("Consumption '" + id + "' not found"));
    }

    public List<DeviceConsumption> consumptionsByUser(String userId) {
        return consumptions.findByUserId(userId);
    }

    public List<DeviceConsumption> topConsumersByUser(String userId, int limit) {
        return consumptions.findTopByUserId(userId, limit);
    }

    public ConsumptionAlert alertById(UUID alertId) {
        return alerts.findById(alertId)
                .orElseThrow(() -> AppException.notFound("Alert '" + alertId + "' not found"));
    }

    public List<ConsumptionAlert> alertsByUser(String userId) {
        return alerts.findByUserId(userId);
    }

    public List<ConsumptionAlert> unreadAlertsByUser(String userId) {
        return alerts.findUnreadByUserId(userId);
    }
}
