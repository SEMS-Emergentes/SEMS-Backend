package com.sems.energy.application;

import com.sems.energy.domain.model.entities.*;
import com.sems.energy.domain.model.valueobjects.*;
import com.sems.energy.domain.repositories.*;
import com.sems.energy.domain.services.EnergyPricingProvider;
import com.sems.shared.errors.AppException;
import com.sems.shared.events.DomainEventBus;
import com.sems.shared.events.DomainEvents;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso que modifican el estado del modulo de energia. */
@Service
@RequiredArgsConstructor
public class EnergyCommandService {

    private final EnergyMeterRepository meters;
    private final EnergyReadingRepository readings;
    private final ConsumptionAlertRepository alerts;
    private final EnergyPricingProvider pricing;
    private final DomainEventBus bus;

    /**
     * Vincular dos veces el mismo numero de serie es un conflicto, salvo que el
     * medidor estuviera desvinculado: en ese caso se vuelve a vincular en lugar
     * de crear una fila nueva, porque el numero de serie es unico en la tabla y
     * el medidor fisico es el mismo.
     */
    @Transactional
    public EnergyMeter registerMeter(String userId, String meterSerial, String model, String brand,
                                     String location, String firmwareVersion, Double maxPowerWatts) {
        if (meterSerial != null) {
            EnergyMeter existing = meters.findBySerial(meterSerial.trim()).orElse(null);
            if (existing != null && !existing.isUnlinked()) {
                throw AppException.conflict("Meter '" + meterSerial + "' is already registered");
            }
            if (existing != null) {
                existing.relink(userId);
                return meters.save(existing);
            }
        }
        return meters.save(EnergyMeter.register(userId, meterSerial, model, brand,
                location, firmwareVersion, maxPowerWatts));
    }

    /**
     * Desvincula el medidor de la cuenta. A diferencia de desactivar, deja de
     * aparecer en la lista del usuario; el historico de lecturas se conserva.
     */
    @Transactional
    public EnergyMeter unlinkMeter(UUID meterId) {
        EnergyMeter meter = meters.findById(meterId)
                .orElseThrow(() -> AppException.notFound("Meter '" + meterId + "' not found"));
        meter.unlink();
        return meters.save(meter);
    }

    @Transactional
    public EnergyMeter deactivateMeter(UUID meterId) {
        EnergyMeter meter = meters.findById(meterId)
                .orElseThrow(() -> AppException.notFound("Meter '" + meterId + "' not found"));
        meter.deactivate();
        return meters.save(meter);
    }

    /**
     * Registra una lectura y avisa al resto del sistema.
     *
     * <p>El evento {@code ReadingProcessed} es el que consumen analytics para
     * rankings y proyecciones, y alerts para evaluar umbrales. Antes viajaba por
     * el topic {@code energy.events}.
     */
    @Transactional
    public EnergyReading recordReading(String userId, String meterId, String deviceId,
                                       double powerWatts, double voltage, double current,
                                       double frequency, double energyKwh, Instant timestamp,
                                       String readingType, String phase) {
        // El medidor tiene que existir y ser de quien manda la lectura.
        //
        // Antes la lectura se guardaba primero y el medidor se buscaba despues
        // "por si acaso": si no aparecia, la lectura quedaba igualmente grabada
        // con un medidor que no existe. Eso devolvia 201 a quien enviaba datos
        // mal referenciados y dejaba filas que ninguna consulta por medidor
        // vuelve a encontrar, el mismo tipo de fuga silenciosa que las
        // notificaciones huerfanas.
        EnergyMeter medidor = parseUuid(meterId).flatMap(meters::findById)
                .orElseThrow(() -> AppException.notFound("Meter '" + meterId + "' not found"));
        if (medidor.getUserId() == null || !medidor.getUserId().equalsIgnoreCase(
                userId == null ? "" : userId.trim())) {
            throw AppException.validation("the meter is not registered to that user");
        }

        PowerReading measurement = new PowerReading(powerWatts, voltage, current, frequency, energyKwh);
        EnergyReading saved = readings.save(EnergyReading.record(userId, meterId, deviceId,
                measurement, timestamp, readingType, phase));

        // El medidor deja constancia de que sigue vivo.
        medidor.updateLastSeen();
        meters.save(medidor);

        parseUuid(userId).ifPresent(uid -> bus.publish(new DomainEvents.ReadingProcessed(
                uid, parseUuid(deviceId).orElse(null), parseUuid(meterId).orElse(null),
                BigDecimal.valueOf(energyKwh), saved.getTimestamp())));

        return saved;
    }

    @Transactional
    public ConsumptionAlert raiseAlert(String userId, String deviceId, String meterId,
                                       AlertType type, AlertSeverity severity,
                                       double thresholdValue, double actualValue, String message) {
        ConsumptionAlert saved = alerts.save(ConsumptionAlert.raise(userId, deviceId, meterId,
                type, severity, thresholdValue, actualValue, message));
        parseUuid(userId).ifPresent(uid -> bus.publish(new DomainEvents.AlertTriggered(
                uid, saved.getId(), type.wire(), severity.wire(), message)));
        return saved;
    }

    @Transactional
    public ConsumptionAlert markAlertRead(UUID alertId) {
        ConsumptionAlert alert = requireAlert(alertId);
        alert.markAsRead();
        return alerts.save(alert);
    }

    @Transactional
    public ConsumptionAlert resolveAlert(UUID alertId) {
        ConsumptionAlert alert = requireAlert(alertId);
        alert.resolve();
        return alerts.save(alert);
    }

    /** Tarifa comercial vigente para una categoria del pliego. */
    public CommercialTariff currentTariff(String tariffCategory) {
        return pricing.currentTariff(TariffCategory.normalizar(tariffCategory));
    }

    /**
     * Estima la factura del mes de un local a partir de su consumo por franja y
     * de la demanda maxima registrada.
     *
     * <p>El local aporta la categoria tarifaria y la potencia contratada;
     * energia no los guarda ni los consulta, para no depender del modulo de
     * organizaciones. Es quien llama el que los trae.
     */
    public BillBreakdown estimateBill(String tariffCategory, BigDecimal kwhPunta,
                                      BigDecimal kwhFueraDePunta, BigDecimal demandaMaximaKw,
                                      BigDecimal potenciaContratadaKw) {
        BigDecimal punta = kwhPunta == null ? BigDecimal.ZERO : kwhPunta;
        BigDecimal fuera = kwhFueraDePunta == null ? BigDecimal.ZERO : kwhFueraDePunta;
        BigDecimal demanda = demandaMaximaKw == null ? BigDecimal.ZERO : demandaMaximaKw;

        if (punta.signum() < 0 || fuera.signum() < 0 || demanda.signum() < 0) {
            throw AppException.validation("consumption and demand cannot be negative");
        }
        if (potenciaContratadaKw == null || potenciaContratadaKw.signum() <= 0) {
            throw AppException.validation("contracted_power_kw must be greater than zero");
        }

        return pricing.currentTariff(TariffCategory.normalizar(tariffCategory))
                .calcular(punta, fuera, demanda, potenciaContratadaKw);
    }

    public EnergyPrice currentPrice() {
        return pricing.currentPrice();
    }

    private ConsumptionAlert requireAlert(UUID alertId) {
        return alerts.findById(alertId)
                .orElseThrow(() -> AppException.notFound("Alert '" + alertId + "' not found"));
    }

    /** Los identificadores viajan como texto; uno invalido no debe romper el flujo. */
    private java.util.Optional<UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }
}
