package com.sems.analytics.domain.model.entities;

import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Proyeccion del recibo de un periodo, calculada a partir del historial. */
@Getter
public class BillPrediction {

    private final UUID id;
    private final String userId;
    private final int predictionYear;
    private final int predictionMonth;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final double estimatedKwh;
    private final double estimatedAmount;
    private final String currency;
    private final double tariffUsed;
    private final double errorMarginPercentage;

    // --- desglose comercial ---
    //
    // La prediccion del segmento anterior era un numero: kWh por precio. Para un
    // local eso se queda corto, porque una parte grande de la factura no depende
    // de la energia sino del pico de demanda. Guardar el desglose es lo que
    // permite ensenar de donde sale el importe y sobre que se puede actuar.

    /** Local al que corresponde la prediccion. Vacio en las heredadas. */
    private final String siteId;

    private final double estimatedKwhPeak;
    private final double estimatedKwhOffPeak;

    /** Demanda maxima prevista, en kW. Es la que fija el cargo por potencia. */
    private final double estimatedMaxDemandKw;

    private final double energyCost;
    private final double powerCost;

    private final Instant generatedAt;
    private final Instant createdAt;

    public BillPrediction(UUID id, String userId, int predictionYear, int predictionMonth,
                          Instant periodStart, Instant periodEnd, double estimatedKwh,
                          double estimatedAmount, String currency, double tariffUsed,
                          double errorMarginPercentage, String siteId, double estimatedKwhPeak,
                          double estimatedKwhOffPeak, double estimatedMaxDemandKw,
                          double energyCost, double powerCost, Instant generatedAt,
                          Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.predictionYear = predictionYear;
        this.predictionMonth = predictionMonth;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.estimatedKwh = estimatedKwh;
        this.estimatedAmount = estimatedAmount;
        this.currency = currency;
        this.tariffUsed = tariffUsed;
        this.errorMarginPercentage = errorMarginPercentage;
        this.siteId = siteId;
        this.estimatedKwhPeak = estimatedKwhPeak;
        this.estimatedKwhOffPeak = estimatedKwhOffPeak;
        this.estimatedMaxDemandKw = estimatedMaxDemandKw;
        this.energyCost = energyCost;
        this.powerCost = powerCost;
        this.generatedAt = generatedAt;
        this.createdAt = createdAt;
    }

    public static BillPrediction create(String userId, int year, int month, Instant periodStart,
                                        Instant periodEnd, double estimatedKwh, double estimatedAmount,
                                        String currency, double tariffUsed, double errorMargin) {
        return create(userId, year, month, periodStart, periodEnd, estimatedKwh, estimatedAmount,
                currency, tariffUsed, errorMargin, null, 0, 0, 0, 0, 0);
    }

    /** Version con el desglose comercial completo. */
    public static BillPrediction create(String userId, int year, int month, Instant periodStart,
                                        Instant periodEnd, double estimatedKwh,
                                        double estimatedAmount, String currency, double tariffUsed,
                                        double errorMargin, String siteId, double kwhPeak,
                                        double kwhOffPeak, double maxDemandKw, double energyCost,
                                        double powerCost) {
        // Un periodo que termina antes de empezar no describe ningun mes, y
        // cualquier agregado que lo cruce con fechas lo contara mal o no lo
        // contara. Se rechaza aqui, donde los dos extremos estan a la vista.
        if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
            throw AppException.validation("period_end cannot be before period_start");
        }
        Instant now = Instant.now();
        return new BillPrediction(UUID.randomUUID(), userId, year, month, periodStart, periodEnd,
                estimatedKwh, estimatedAmount, currency == null ? "PEN" : currency,
                tariffUsed, errorMargin, siteId, kwhPeak, kwhOffPeak, maxDemandKw, energyCost,
                powerCost, now, now);
    }
}
