package com.sems.energy.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sems.energy.domain.model.entities.*;
import com.sems.energy.domain.model.valueobjects.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contrato JSON del modulo de energia.
 *
 * <p><b>Importante:</b> el servicio original estaba escrito en FastAPI y
 * serializaba en snake_case ({@code user_id}, {@code power_watts},
 * {@code meter_serial}...). Java usa camelCase por convencion, asi que cada
 * recurso lleva {@code @JsonNaming} con la estrategia snake_case. Sin eso el
 * frontend dejaria de encontrar los campos y las pantallas saldrian vacias.
 */
public final class EnergyResources {

    private EnergyResources() {
    }

    // ------------------------------------------------------------- peticiones

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterMeterRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String meterSerial,
            String model,
            String brand,
            String location,
            String firmwareVersion,
            Double maxPowerWatts) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateReadingRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String meterId,
            String deviceId,
            double powerWatts,
            double voltage,
            double current,
            double frequency,
            double energyKwh,
            Instant timestamp,
            String readingType,
            String phase) {
    }

    // -------------------------------------------------------------- respuestas

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MeterResponse(
            String id, String userId, String meterSerial, String model, String brand,
            String location, MeterStatus status, String firmwareVersion, double maxPowerWatts,
            Instant registeredAt, Instant lastSeenAt, Instant updatedAt) {

        public static MeterResponse from(EnergyMeter m) {
            return new MeterResponse(m.getId().toString(), m.getUserId(), m.getMeterSerial(),
                    m.getModel(), m.getBrand(), m.getLocation(), m.getStatus(),
                    m.getFirmwareVersion(), m.getMaxPowerWatts(), m.getRegisteredAt(),
                    m.getLastSeenAt(), m.getUpdatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReadingResponse(
            String id, String userId, String meterId, String deviceId, double powerWatts,
            double voltage, double current, double frequency, double energyKwh,
            Instant timestamp, String readingType, String phase, Instant createdAt) {

        public static ReadingResponse from(EnergyReading e) {
            PowerReading p = e.getMeasurement();
            return new ReadingResponse(e.getId().toString(), e.getUserId(), e.getMeterId(),
                    e.getDeviceId(), p.powerWatts(), p.voltage(), p.current(), p.frequency(),
                    p.energyKwh(), e.getTimestamp(), e.getReadingType(), e.getPhase(),
                    e.getCreatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ConsumptionResponse(
            String id, String userId, String deviceId, String deviceName, String meterId,
            double totalKwh, double costEstimateSoles, Instant periodStart, Instant periodEnd,
            double peakPowerWatts, double averagePowerWatts, int readingCount,
            Instant createdAt, Instant updatedAt) {

        public static ConsumptionResponse from(DeviceConsumption c) {
            return new ConsumptionResponse(c.getId().toString(), c.getUserId(), c.getDeviceId(),
                    c.getDeviceName(), c.getMeterId(), c.getTotalKwh(), c.getCostEstimateSoles(),
                    c.getPeriodStart(), c.getPeriodEnd(), c.getPeakPowerWatts(),
                    c.getAveragePowerWatts(), c.getReadingCount(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AlertResponse(
            String id, String userId, String deviceId, String meterId, AlertType alertType,
            AlertSeverity severity, double thresholdValue, double actualValue, String message,
            boolean isRead, boolean isResolved, Instant createdAt, Instant resolvedAt) {

        public static AlertResponse from(ConsumptionAlert a) {
            return new AlertResponse(a.getId().toString(), a.getUserId(), a.getDeviceId(),
                    a.getMeterId(), a.getAlertType(), a.getSeverity(), a.getThresholdValue(),
                    a.getActualValue(), a.getMessage(), a.isRead(), a.isResolved(),
                    a.getCreatedAt(), a.getResolvedAt());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PricingResponse(String provider, double pricePerKwh, String currency, Instant timestamp) {

        public static PricingResponse from(EnergyPrice price) {
            return new PricingResponse(price.provider(), price.pricePerKwh(),
                    price.currency(), price.timestamp());
        }
    }

    /** Tarifa comercial: precios por franja y cargos por potencia. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TariffResponse(String provider, String tariffCategory, String currency,
                                 BigDecimal energyPeakPerKwh, BigDecimal energyOffPeakPerKwh,
                                 BigDecimal powerPerKwMonth, BigDecimal excessPowerPerKwMonth,
                                 BigDecimal fixedCharge, BigDecimal igv, String peakHours,
                                 boolean inPeakNow, Instant timestamp) {

        public static TariffResponse from(CommercialTariff t) {
            return new TariffResponse(t.provider(), t.tariffCategory(), t.currency(),
                    t.energiaPuntaPorKwh(), t.energiaFueraDePuntaPorKwh(), t.potenciaPorKwMes(),
                    t.excesoDePotenciaPorKwMes(), t.cargoFijoMensual(), t.igv(),
                    String.format("%02d:00-%02d:00 lun-sab", HorarioPunta.HORA_INICIO,
                            HorarioPunta.HORA_FIN),
                    HorarioPunta.esHoraPunta(Instant.now()), t.timestamp());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EstimateBillRequest(
            @NotBlank(message = "is required") String tariffCategory,
            @NotNull(message = "is required") BigDecimal contractedPowerKw,
            BigDecimal kwhPeak,
            BigDecimal kwhOffPeak,
            BigDecimal maxDemandKw) {
    }

    /** Desglose de la factura estimada de un local. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BillEstimateResponse(BigDecimal kwhPeak, BigDecimal kwhOffPeak,
                                       BigDecimal maxDemandKw, BigDecimal contractedPowerKw,
                                       BigDecimal excessPowerKw, boolean hasPowerExcess,
                                       BigDecimal energyCost, BigDecimal powerCost,
                                       BigDecimal powerSharePct, BigDecimal fixedCharge,
                                       BigDecimal subtotal, BigDecimal igv, BigDecimal total,
                                       String currency) {

        public static BillEstimateResponse from(BillBreakdown b) {
            return new BillEstimateResponse(b.kwhPunta(), b.kwhFueraDePunta(), b.demandaMaximaKw(),
                    b.potenciaContratadaKw(), b.excesoDePotenciaKw(), b.hayExcesoDePotencia(),
                    b.costoEnergia(), b.costoPotencia(), b.pesoDeLaPotencia(), b.cargoFijo(),
                    b.subtotal(), b.igv(), b.total(), b.currency());
        }
    }
}
