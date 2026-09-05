package com.sems.analytics.infrastructure.persistence.jpa.adapters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sems.analytics.domain.model.entities.*;
import com.sems.analytics.domain.model.valueobjects.RankingItem;
import com.sems.analytics.domain.repositories.AnalyticsRepositories.*;
import com.sems.analytics.infrastructure.persistence.jpa.entities.AnalyticsJpaEntities.*;
import com.sems.analytics.infrastructure.persistence.jpa.repositories.*;
import com.sems.shared.errors.AppException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Implementaciones JPA de los puertos del modulo de analitica. */
public final class AnalyticsAdapters {

    private AnalyticsAdapters() {
    }

    @Repository
    @RequiredArgsConstructor
    public static class BillPredictionAdapter implements BillPredictionRepository {
        private final BillPredictionJpa jpa;

        @Override
        public BillPrediction save(BillPrediction p) {
            BillPredictionRow row = new BillPredictionRow(p.getId(), p.getUserId(),
                    p.getPredictionYear(), p.getPredictionMonth(), p.getPeriodStart(),
                    p.getPeriodEnd(), p.getEstimatedKwh(), p.getEstimatedAmount(), p.getCurrency(),
                    p.getTariffUsed(), p.getErrorMarginPercentage(), p.getGeneratedAt(), p.getCreatedAt());
            return toDomain(jpa.save(row));
        }

        @Override
        public List<BillPrediction> findByUserId(String userId) {
            return jpa.findByUserIdOrderByGeneratedAtDesc(userId).stream()
                    .map(BillPredictionAdapter::toDomain).toList();
        }

        private static BillPrediction toDomain(BillPredictionRow r) {
            return new BillPrediction(r.getId(), r.getUserId(), r.getPredictionYear(),
                    r.getPredictionMonth(), r.getPeriodStart(), r.getPeriodEnd(),
                    r.getEstimatedKwh(), r.getEstimatedAmount(), r.getCurrency(),
                    r.getTariffUsed(), r.getErrorMarginPercentage(), r.getGeneratedAt(),
                    r.getCreatedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class RecommendationAdapter implements RecommendationRepository {
        private final RecommendationJpa jpa;

        @Override
        public Recommendation save(Recommendation x) {
            RecommendationRow row = new RecommendationRow(x.getId(), x.getUserId(), x.getDeviceId(),
                    x.getRecommendationType(), x.getTitle(), x.getDescription(),
                    x.getEstimatedSavingKwh(), x.getEstimatedSavingAmount(), x.getCurrency(),
                    x.getStatus(), x.getGeneratedAt(), x.getAppliedAt(), x.getCreatedAt());
            return toDomain(jpa.save(row));
        }

        @Override
        public Optional<Recommendation> findById(UUID id) {
            return jpa.findById(id).map(RecommendationAdapter::toDomain);
        }

        @Override
        public List<Recommendation> findByUserId(String userId) {
            return jpa.findByUserIdOrderByGeneratedAtDesc(userId).stream()
                    .map(RecommendationAdapter::toDomain).toList();
        }

        private static Recommendation toDomain(RecommendationRow r) {
            return new Recommendation(r.getId(), r.getUserId(), r.getDeviceId(),
                    r.getRecommendationType(), r.getTitle(), r.getDescription(),
                    r.getEstimatedSavingKwh(), r.getEstimatedSavingAmount(), r.getCurrency(),
                    r.getStatus(), r.getGeneratedAt(), r.getAppliedAt(), r.getCreatedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class AnomalyAdapter implements AnomalyRepository {
        private final AnomalyJpa jpa;

        @Override
        public Anomaly save(Anomaly a) {
            AnomalyRow row = new AnomalyRow(a.getId(), a.getUserId(), a.getDeviceId(),
                    a.getAnomalyType(), a.getDescription(), a.getSeverity(), a.getStatus(),
                    a.getActualKwh(), a.getExpectedKwh(), a.getDeviationPercentage(),
                    a.getDetectedAt(), a.getResolvedAt(), a.getCreatedAt());
            return toDomain(jpa.save(row));
        }

        @Override
        public Optional<Anomaly> findById(UUID id) {
            return jpa.findById(id).map(AnomalyAdapter::toDomain);
        }

        @Override
        public List<Anomaly> findByUserId(String userId) {
            return jpa.findByUserIdOrderByDetectedAtDesc(userId).stream()
                    .map(AnomalyAdapter::toDomain).toList();
        }

        private static Anomaly toDomain(AnomalyRow r) {
            return new Anomaly(r.getId(), r.getUserId(), r.getDeviceId(), r.getAnomalyType(),
                    r.getDescription(), r.getSeverity(), r.getStatus(), r.getActualKwh(),
                    r.getExpectedKwh(), r.getDeviationPercentage(), r.getDetectedAt(),
                    r.getResolvedAt(), r.getCreatedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class DeviceIdentificationAdapter implements DeviceIdentificationRepository {
        private final DeviceIdentificationJpa jpa;

        @Override
        public DeviceIdentificationResult save(DeviceIdentificationResult d) {
            DeviceIdentificationRow row = new DeviceIdentificationRow(d.getId(), d.getUserId(),
                    d.getDeviceId(), d.getPredictedDeviceType(), d.getConfidenceScore(),
                    d.getStatus(), d.getAnalyzedAt(), d.getCreatedAt());
            return toDomain(jpa.save(row));
        }

        @Override
        public List<DeviceIdentificationResult> findByUserId(String userId) {
            return jpa.findByUserIdOrderByAnalyzedAtDesc(userId).stream()
                    .map(DeviceIdentificationAdapter::toDomain).toList();
        }

        private static DeviceIdentificationResult toDomain(DeviceIdentificationRow r) {
            return new DeviceIdentificationResult(r.getId(), r.getUserId(), r.getDeviceId(),
                    r.getPredictedDeviceType(), r.getConfidenceScore(), r.getStatus(),
                    r.getAnalyzedAt(), r.getCreatedAt());
        }
    }

    /**
     * El ranking guarda sus posiciones como JSON en una columna, igual que el
     * documento anidado que usaba MongoDB.
     */
    @Repository
    @RequiredArgsConstructor
    public static class ConsumptionRankingAdapter implements ConsumptionRankingRepository {

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final TypeReference<List<RankingItem>> ITEMS = new TypeReference<>() {
        };

        private final ConsumptionRankingJpa jpa;

        @Override
        public ConsumptionRanking save(ConsumptionRanking c) {
            ConsumptionRankingRow row = new ConsumptionRankingRow(c.getId(), c.getUserId(),
                    c.getPeriodType(), c.getPeriodStart(), c.getPeriodEnd(),
                    writeItems(c.getRankings()), c.getGeneratedAt(), c.getCreatedAt());
            return toDomain(jpa.save(row));
        }

        @Override
        public List<ConsumptionRanking> findByUserId(String userId) {
            return jpa.findByUserIdOrderByGeneratedAtDesc(userId).stream()
                    .map(ConsumptionRankingAdapter::toDomain).toList();
        }

        private static ConsumptionRanking toDomain(ConsumptionRankingRow r) {
            return new ConsumptionRanking(r.getId(), r.getUserId(), r.getPeriodType(),
                    r.getPeriodStart(), r.getPeriodEnd(), readItems(r.getRankingsJson()),
                    r.getGeneratedAt(), r.getCreatedAt());
        }

        private static String writeItems(List<RankingItem> items) {
            try {
                return MAPPER.writeValueAsString(items);
            } catch (Exception e) {
                throw AppException.internal("no se pudo serializar el ranking");
            }
        }

        /** Una fila con JSON corrupto devuelve un ranking vacio, no una excepcion. */
        private static List<RankingItem> readItems(String json) {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            try {
                return MAPPER.readValue(json, ITEMS);
            } catch (Exception e) {
                return List.of();
            }
        }
    }
}
