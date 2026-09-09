package com.sems.analytics.interfaces.rest;

import com.sems.analytics.application.AnalyticsService;
import com.sems.analytics.interfaces.rest.resources.AnalyticsResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API REST del bounded context de analitica.
 *
 * <p>Rutas identicas a las del microservicio en FastAPI, bajo el prefijo
 * {@code /api/v1/analytics}. El frontend consume sobre todo
 * {@code /bill-predictions/user/{id}} y {@code /consumption-rankings/user/{id}}.
 */
@Tag(name = "Analytics", description = "Forecasts, recommendations, anomalies and rankings")
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService service;

    // -------------------------------------------------------- bill-predictions

    @Operation(summary = "A user's bill forecasts")
    @GetMapping("/bill-predictions/user/{userId}")
    public List<BillPredictionResponse> predictions(@PathVariable String userId) {
        return service.predictionsByUser(userId).stream().map(BillPredictionResponse::from).toList();
    }

    @Operation(summary = "Records a bill forecast")
    @PostMapping("/bill-predictions")
    public ResponseEntity<BillPredictionResponse> createPrediction(
            @Valid @RequestBody CreatePredictionRequest r) {
        var created = service.createPrediction(r.userId(), r.predictionYear(), r.predictionMonth(),
                r.periodStart(), r.periodEnd(), r.estimatedKwh(), r.estimatedAmount(),
                r.currency(), r.tariffUsed(), r.errorMarginPercentage());
        return ResponseEntity.status(HttpStatus.CREATED).body(BillPredictionResponse.from(created));
    }

    @Operation(summary = "Forecasts a site's bill using the commercial tariff")
    @PostMapping("/bill-predictions/forecast")
    public ResponseEntity<BillPredictionResponse> forecastSiteBill(
            @Valid @RequestBody ForecastSiteBillRequest r) {
        var created = service.forecastSiteBill(r.userId(), r.siteId(), r.tariffCategory(),
                r.contractedPowerKw(), r.predictionYear(), r.predictionMonth(), r.periodStart(),
                r.periodEnd(), r.kwhPeak(), r.kwhOffPeak(), r.maxDemandKw(),
                r.errorMarginPercentage());
        return ResponseEntity.status(HttpStatus.CREATED).body(BillPredictionResponse.from(created));
    }

    // ---------------------------------------------------------- recommendations

    @Operation(summary = "A user's saving recommendations")
    @GetMapping("/recommendations/user/{userId}")
    public List<RecommendationResponse> recommendations(@PathVariable String userId) {
        return service.recommendationsByUser(userId).stream()
                .map(RecommendationResponse::from).toList();
    }

    @Operation(summary = "Records a recommendation")
    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationResponse> createRecommendation(
            @Valid @RequestBody CreateRecommendationRequest r) {
        var created = service.createRecommendation(r.userId(), r.deviceId(), r.recommendationType(),
                r.title(), r.description(), r.estimatedSavingKwh(), r.estimatedSavingAmount(),
                r.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(RecommendationResponse.from(created));
    }

    @Operation(summary = "Marks a recommendation as applied")
    @PatchMapping("/recommendations/{recommendationId}/apply")
    public RecommendationResponse applyRecommendation(@PathVariable UUID recommendationId) {
        return RecommendationResponse.from(service.applyRecommendation(recommendationId));
    }

    // ---------------------------------------------------------------- anomalies

    @Operation(summary = "Anomalies detected for a user")
    @GetMapping("/anomalies/user/{userId}")
    public List<AnomalyResponse> anomalies(@PathVariable String userId) {
        return service.anomaliesByUser(userId).stream().map(AnomalyResponse::from).toList();
    }

    @Operation(summary = "Records an anomaly")
    @PostMapping("/anomalies")
    public ResponseEntity<AnomalyResponse> createAnomaly(@Valid @RequestBody CreateAnomalyRequest r) {
        var created = service.createAnomaly(r.userId(), r.deviceId(), r.anomalyType(),
                r.description(), r.severity(), r.actualKwh(), r.expectedKwh());
        return ResponseEntity.status(HttpStatus.CREATED).body(AnomalyResponse.from(created));
    }

    @Operation(summary = "Marks an anomaly as resolved")
    @PatchMapping("/anomalies/{anomalyId}/resolve")
    public AnomalyResponse resolveAnomaly(@PathVariable UUID anomalyId) {
        return AnomalyResponse.from(service.resolveAnomaly(anomalyId));
    }

    // ------------------------------------------------------ device-identifications

    @Operation(summary = "Appliance identifications for a user")
    @GetMapping("/device-identifications/user/{userId}")
    public List<DeviceIdentificationResponse> identifications(@PathVariable String userId) {
        return service.identificationsByUser(userId).stream()
                .map(DeviceIdentificationResponse::from).toList();
    }

    @Operation(summary = "Records an appliance identification")
    @PostMapping("/device-identifications")
    public ResponseEntity<DeviceIdentificationResponse> createIdentification(
            @Valid @RequestBody CreateIdentificationRequest r) {
        var created = service.createIdentification(r.userId(), r.deviceId(),
                r.predictedDeviceType(), r.confidenceScore(), r.status());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DeviceIdentificationResponse.from(created));
    }

    // ------------------------------------------------------- consumption-rankings

    @Operation(summary = "A user's consumption rankings")
    @GetMapping("/consumption-rankings/user/{userId}")
    public List<ConsumptionRankingResponse> rankings(@PathVariable String userId) {
        return service.rankingsByUser(userId).stream().map(ConsumptionRankingResponse::from).toList();
    }

    @Operation(summary = "Records a consumption ranking")
    @PostMapping("/consumption-rankings")
    public ResponseEntity<ConsumptionRankingResponse> createRanking(
            @Valid @RequestBody CreateRankingRequest r) {
        var items = r.rankings() == null ? List.<com.sems.analytics.domain.model.valueobjects.RankingItem>of()
                : r.rankings().stream().map(RankingItemResource::toDomain).toList();
        var created = service.createRanking(r.userId(), r.periodType(), r.periodStart(),
                r.periodEnd(), items);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConsumptionRankingResponse.from(created));
    }
}
