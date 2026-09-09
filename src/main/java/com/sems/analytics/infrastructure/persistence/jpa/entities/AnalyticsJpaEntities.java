package com.sems.analytics.infrastructure.persistence.jpa.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Filas de las tablas del modulo de analitica. */
public final class AnalyticsJpaEntities {

    private AnalyticsJpaEntities() {
    }

    @Entity
    @Table(name = "an_bill_predictions",
            indexes = @Index(name = "idx_an_pred_user", columnList = "user_id"))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class BillPredictionRow {
        @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
        @Column(name = "user_id", nullable = false, length = 80) private String userId;
        @Column(name = "prediction_year", nullable = false) private int predictionYear;
        @Column(name = "prediction_month", nullable = false) private int predictionMonth;
        @Column(name = "period_start", nullable = false) private Instant periodStart;
        @Column(name = "period_end", nullable = false) private Instant periodEnd;
        @Column(name = "estimated_kwh", nullable = false) private double estimatedKwh;
        @Column(name = "estimated_amount", nullable = false) private double estimatedAmount;
        @Column(name = "currency", length = 10) private String currency;
        @Column(name = "tariff_used", nullable = false) private double tariffUsed;
        @Column(name = "error_margin_percentage", nullable = false) private double errorMarginPercentage;
        // Desglose comercial: en un local, buena parte de la factura no viene
        // del consumo sino del pico de demanda, y eso hay que poder ensenarlo.
        @Column(name = "site_id", length = 80) private String siteId;
        @Column(name = "estimated_kwh_peak") private double estimatedKwhPeak;
        @Column(name = "estimated_kwh_off_peak") private double estimatedKwhOffPeak;
        @Column(name = "estimated_max_demand_kw") private double estimatedMaxDemandKw;
        @Column(name = "energy_cost") private double energyCost;
        @Column(name = "power_cost") private double powerCost;
        @Column(name = "generated_at", nullable = false) private Instant generatedAt;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
    }

    @Entity
    @Table(name = "an_recommendations",
            indexes = @Index(name = "idx_an_reco_user", columnList = "user_id"))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RecommendationRow {
        @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
        @Column(name = "user_id", nullable = false, length = 80) private String userId;
        @Column(name = "device_id", length = 80) private String deviceId;
        @Column(name = "recommendation_type", length = 80) private String recommendationType;
        @Column(name = "title", length = 200) private String title;
        @Column(name = "description", columnDefinition = "text") private String description;
        @Column(name = "estimated_saving_kwh", nullable = false) private double estimatedSavingKwh;
        @Column(name = "estimated_saving_amount", nullable = false) private double estimatedSavingAmount;
        @Column(name = "currency", length = 10) private String currency;
        @Column(name = "status", length = 20) private String status;
        @Column(name = "generated_at", nullable = false) private Instant generatedAt;
        @Column(name = "applied_at") private Instant appliedAt;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
    }

    @Entity
    @Table(name = "an_anomalies",
            indexes = @Index(name = "idx_an_anom_user", columnList = "user_id"))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AnomalyRow {
        @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
        @Column(name = "user_id", nullable = false, length = 80) private String userId;
        @Column(name = "device_id", length = 80) private String deviceId;
        @Column(name = "anomaly_type", length = 80) private String anomalyType;
        @Column(name = "description", columnDefinition = "text") private String description;
        @Column(name = "severity", length = 20) private String severity;
        @Column(name = "status", length = 20) private String status;
        @Column(name = "actual_kwh", nullable = false) private double actualKwh;
        @Column(name = "expected_kwh", nullable = false) private double expectedKwh;
        @Column(name = "deviation_percentage", nullable = false) private double deviationPercentage;
        @Column(name = "detected_at", nullable = false) private Instant detectedAt;
        @Column(name = "resolved_at") private Instant resolvedAt;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
    }

    @Entity
    @Table(name = "an_device_identifications",
            indexes = @Index(name = "idx_an_ident_user", columnList = "user_id"))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DeviceIdentificationRow {
        @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
        @Column(name = "user_id", nullable = false, length = 80) private String userId;
        @Column(name = "device_id", length = 80) private String deviceId;
        @Column(name = "predicted_device_type", length = 120) private String predictedDeviceType;
        @Column(name = "confidence_score", nullable = false) private double confidenceScore;
        @Column(name = "status", length = 20) private String status;
        @Column(name = "analyzed_at", nullable = false) private Instant analyzedAt;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
    }

    /**
     * Ranking de consumo.
     *
     * <p>Las posiciones se guardan como JSON en una sola columna, replicando el
     * documento anidado de MongoDB. Se consultan siempre completas, nunca por
     * posicion suelta, asi que normalizarlas en otra tabla no aportaria nada.
     */
    @Entity
    @Table(name = "an_consumption_rankings",
            indexes = @Index(name = "idx_an_rank_user", columnList = "user_id"))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ConsumptionRankingRow {
        @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
        @Column(name = "user_id", nullable = false, length = 80) private String userId;
        @Column(name = "period_type", length = 40) private String periodType;
        @Column(name = "period_start", nullable = false) private Instant periodStart;
        @Column(name = "period_end", nullable = false) private Instant periodEnd;
        @Column(name = "rankings_json", columnDefinition = "text") private String rankingsJson;
        @Column(name = "generated_at", nullable = false) private Instant generatedAt;
        @Column(name = "created_at", nullable = false) private Instant createdAt;
    }
}
