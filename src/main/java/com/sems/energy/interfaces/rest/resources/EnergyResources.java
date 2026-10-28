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
            // El local es obligatorio: la tarifa, la potencia contratada y el
            // recibo son del suministro, no de quien da de alta el equipo.
            @NotBlank(message = "is required") String siteId,
            // La zona no: al abrir un local puede que aun no esten definidas.
            String zoneId,
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

    /** Peticion de traslado de un medidor a otra zona del mismo local. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReassignMeterZoneRequest(String zoneId) {
    }

    // -------------------------------------------------------------- respuestas

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MeterResponse(
            String id, String userId, String siteId, String zoneId,
            String meterSerial, String model, String brand,
            String location, MeterStatus status, String firmwareVersion, double maxPowerWatts,
            Instant registeredAt, Instant lastSeenAt, Instant updatedAt) {

        public static MeterResponse from(EnergyMeter m) {
            return new MeterResponse(m.getId().toString(), m.getUserId(), m.getSiteId(),
                    m.getZoneId(), m.getMeterSerial(),
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
            // Coste de esta lectura con la tarifa del local y la franja horaria
            // en que se midio. Viaja con la lectura para que ninguna pantalla
            // tenga que multiplicar por un precio propio.
            BigDecimal estimatedCost,
            Instant timestamp, String readingType, String phase, Instant createdAt) {

        public static ReadingResponse from(EnergyReading e) {
            return from(e, null);
        }

        public static ReadingResponse from(EnergyReading e, BigDecimal estimatedCost) {
            PowerReading p = e.getMeasurement();
            return new ReadingResponse(e.getId().toString(), e.getUserId(), e.getMeterId(),
                    e.getDeviceId(), p.powerWatts(), p.voltage(), p.current(), p.frequency(),
                    p.energyKwh(), estimatedCost, e.getTimestamp(), e.getReadingType(),
                    e.getPhase(), e.getCreatedAt());
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
                                 boolean peakEveryDay, boolean inPeakNow, Instant timestamp) {

        public static TariffResponse from(CommercialTariff t) {
            return new TariffResponse(t.provider(), t.tariffCategory(), t.currency(),
                    t.energiaPuntaPorKwh(), t.energiaFueraDePuntaPorKwh(), t.potenciaPorKwMes(),
                    t.excesoDePotenciaPorKwMes(), t.cargoFijoMensual(), t.igv(),
                    // Solo las horas. Antes decia "lun-sab", que es falso: la
                    // hora punta rige todos los dias y excluir domingos es una
                    // opcion que el cliente pide a su distribuidora, no una
                    // regla general. Que dias cuentan depende del suministro,
                    // asi que esta respuesta —que es de la tarifa, no de un
                    // local— se limita a la franja horaria y deja el matiz a
                    // peakEveryDay.
                    String.format("%02d:00-%02d:00", HorarioPunta.HORA_INICIO,
                            HorarioPunta.HORA_FIN),
                    true,
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
