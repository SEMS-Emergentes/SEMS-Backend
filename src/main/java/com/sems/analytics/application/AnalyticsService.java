package com.sems.analytics.application;

import com.sems.analytics.domain.model.entities.*;
import com.sems.analytics.domain.model.valueobjects.RankingItem;
import com.sems.analytics.domain.services.BillCalculator;
import com.sems.analytics.domain.repositories.AnalyticsRepositories.*;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del modulo de analitica.
 *
 * <p>Comandos y consultas conviven aqui porque el contexto es pequenio: cinco
 * agregados con operaciones de crear, listar y cerrar. Separarlos en dos
 * servicios anadiria ceremonia sin aportar claridad.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final BillPredictionRepository predictions;
    private final RecommendationRepository recommendations;
    private final AnomalyRepository anomalies;
    private final DeviceIdentificationRepository identifications;
    private final ConsumptionRankingRepository rankings;
    private final BillCalculator calculator;

    // ------------------------------------------------------ proyeccion de recibo

    @Transactional
    public BillPrediction createPrediction(String userId, int year, int month, Instant periodStart,
                                           Instant periodEnd, double estimatedKwh,
                                           double estimatedAmount, String currency,
                                           double tariffUsed, double errorMargin) {
        return predictions.save(BillPrediction.create(userId, year, month, periodStart, periodEnd,
                estimatedKwh, estimatedAmount, currency, tariffUsed, errorMargin));
    }

    /**
     * Calcula y guarda la proyeccion de recibo de un local con la tarifa
     * comercial vigente.
     *
     * <p>A diferencia de {@code createPrediction}, aqui el importe no lo trae
     * quien llama: se calcula. Es lo que permite que la proyeccion refleje el
     * cargo por potencia, que en un local puede ser la mitad de la factura y con
     * la tarifa plana anterior no aparecia por ningun lado.
     */
    public BillPrediction forecastSiteBill(String userId, String siteId, String tariffCategory,
                                           double contractedPowerKw, int year, int month,
                                           Instant periodStart, Instant periodEnd, double kwhPeak,
                                           double kwhOffPeak, double maxDemandKw,
                                           double errorMargin) {
        BillCalculator.EstimatedBill estimado = calculator.estimate(tariffCategory, kwhPeak,
                kwhOffPeak, maxDemandKw, contractedPowerKw);

        return predictions.save(BillPrediction.create(userId, year, month, periodStart, periodEnd,
                kwhPeak + kwhOffPeak, estimado.total(), estimado.currency(), estimado.tariffUsed(),
                errorMargin, siteId, kwhPeak, kwhOffPeak, maxDemandKw, estimado.energyCost(),
                estimado.powerCost()));
    }

    @Transactional(readOnly = true)
    public List<BillPrediction> predictionsByUser(String userId) {
        return predictions.findByUserId(userId);
    }

    // ------------------------------------------------------------ recomendaciones

    @Transactional
    public Recommendation createRecommendation(String userId, String deviceId, String type,
                                               String title, String description, double savingKwh,
                                               double savingAmount, String currency) {
        return recommendations.save(Recommendation.create(userId, deviceId, type, title,
                description, savingKwh, savingAmount, currency));
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommendationsByUser(String userId) {
        return recommendations.findByUserId(userId);
    }

    @Transactional
    public Recommendation applyRecommendation(UUID id) {
        Recommendation recommendation = recommendations.findById(id)
                .orElseThrow(() -> AppException.notFound("Recommendation '" + id + "' not found"));
        recommendation.apply();
        return recommendations.save(recommendation);
    }

    // ------------------------------------------------------------------ anomalias

    @Transactional
    public Anomaly createAnomaly(String userId, String deviceId, String type, String description,
                                 String severity, double actualKwh, double expectedKwh) {
        return anomalies.save(Anomaly.detect(userId, deviceId, type, description, severity,
                actualKwh, expectedKwh));
    }

    @Transactional(readOnly = true)
    public List<Anomaly> anomaliesByUser(String userId) {
        return anomalies.findByUserId(userId);
    }

    @Transactional
    public Anomaly resolveAnomaly(UUID id) {
        Anomaly anomaly = anomalies.findById(id)
                .orElseThrow(() -> AppException.notFound("Anomaly '" + id + "' not found"));
        anomaly.resolve();
        return anomalies.save(anomaly);
    }

    // ------------------------------------------------- identificacion de aparatos

    @Transactional
    public DeviceIdentificationResult createIdentification(String userId, String deviceId,
                                                           String predictedType, double confidence,
                                                           String status) {
        return identifications.save(DeviceIdentificationResult.create(userId, deviceId,
                predictedType, confidence, status));
    }

    @Transactional(readOnly = true)
    public List<DeviceIdentificationResult> identificationsByUser(String userId) {
        return identifications.findByUserId(userId);
    }

    // -------------------------------------------------------------------- ranking

    @Transactional
    public ConsumptionRanking createRanking(String userId, String periodType, Instant periodStart,
                                            Instant periodEnd, List<RankingItem> items) {
        return rankings.save(ConsumptionRanking.create(userId, periodType, periodStart,
                periodEnd, items));
    }

    @Transactional(readOnly = true)
    public List<ConsumptionRanking> rankingsByUser(String userId) {
        return rankings.findByUserId(userId);
    }
}
