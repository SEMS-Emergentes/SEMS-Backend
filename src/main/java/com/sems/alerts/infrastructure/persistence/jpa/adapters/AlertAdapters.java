package com.sems.alerts.infrastructure.persistence.jpa.adapters;

import com.sems.alerts.domain.model.entities.*;
import com.sems.alerts.domain.repositories.AlertRepositories.*;
import com.sems.alerts.infrastructure.persistence.jpa.entities.AlertJpaEntities.*;
import com.sems.alerts.infrastructure.persistence.jpa.repositories.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Implementaciones JPA de los puertos del modulo de alertas. */
public final class AlertAdapters {

    private AlertAdapters() {
    }

    @Repository
    @RequiredArgsConstructor
    public static class AlertAdapter implements AlertRepository {
        private final AlertJpa jpa;

        @Override
        public Alert save(Alert a) {
            return toDomain(jpa.save(new AlertRow(a.getAlertId(), a.getUserId(), a.getDeviceId(),
                    a.getThresholdId(), a.getInactivityRuleId(), a.getAlertType(), a.getTitle(),
                    a.getMessage(), a.getSeverity(), a.getStatus(), a.getTriggeredAt(),
                    a.getResolvedAt())));
        }

        @Override
        public Optional<Alert> findById(UUID alertId) {
            return jpa.findById(alertId).map(AlertAdapter::toDomain);
        }

        @Override
        public List<Alert> findAll() {
            return jpa.findAllByOrderByTriggeredAtDesc().stream().map(AlertAdapter::toDomain).toList();
        }

        @Override
        public List<Alert> findByUserId(UUID userId) {
            return jpa.findByUserIdOrderByTriggeredAtDesc(userId).stream()
                    .map(AlertAdapter::toDomain).toList();
        }

        private static Alert toDomain(AlertRow r) {
            return new Alert(r.getAlertId(), r.getUserId(), r.getDeviceId(), r.getThresholdId(),
                    r.getInactivityRuleId(), r.getAlertType(), r.getTitle(), r.getMessage(),
                    r.getSeverity(), r.getStatus(), r.getTriggeredAt(), r.getResolvedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class ThresholdAdapter implements ThresholdRepository {
        private final ThresholdJpa jpa;

        @Override
        public AlertThreshold save(AlertThreshold t) {
            return toDomain(jpa.save(new ThresholdRow(t.getThresholdId(), t.getUserId(),
                    t.getDeviceId(), t.getThresholdName(), t.getMetric(), t.getOperator(),
                    t.getThresholdValue(), t.isActive(), t.getCreatedAt(), t.getUpdatedAt())));
        }

        @Override
        public Optional<AlertThreshold> findById(UUID thresholdId) {
            return jpa.findById(thresholdId).map(ThresholdAdapter::toDomain);
        }

        @Override
        public List<AlertThreshold> findByUserId(UUID userId) {
            return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(ThresholdAdapter::toDomain).toList();
        }

        @Override
        public List<AlertThreshold> findActiveByDeviceId(UUID deviceId) {
            if (deviceId == null) {
                return List.of();
            }
            return jpa.findByDeviceIdAndActiveTrue(deviceId).stream()
                    .map(ThresholdAdapter::toDomain).toList();
        }

        @Override
        public long countByUserId(UUID userId) {
            return jpa.countByUserId(userId);
        }

        private static AlertThreshold toDomain(ThresholdRow r) {
            return new AlertThreshold(r.getThresholdId(), r.getUserId(), r.getDeviceId(),
                    r.getThresholdName(), r.getMetric(), r.getOperator(), r.getThresholdValue(),
                    r.isActive(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class InactivityRuleAdapter implements InactivityRuleRepository {
        private final InactivityRuleJpa jpa;

        @Override
        public InactivityRule save(InactivityRule r) {
            return toDomain(jpa.save(new InactivityRuleRow(r.getInactivityRuleId(), r.getUserId(),
                    r.getDeviceId(), r.getRuleName(), r.getMaxInactiveMinutes(), r.isActive(),
                    r.getCreatedAt(), r.getUpdatedAt())));
        }

        @Override
        public List<InactivityRule> findByUserId(UUID userId) {
            return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(InactivityRuleAdapter::toDomain).toList();
        }

        @Override
        public List<InactivityRule> findAllActive() {
            return jpa.findByActiveTrue().stream().map(InactivityRuleAdapter::toDomain).toList();
        }

        private static InactivityRule toDomain(InactivityRuleRow r) {
            return new InactivityRule(r.getInactivityRuleId(), r.getUserId(), r.getDeviceId(),
                    r.getRuleName(), r.getMaxInactiveMinutes(), r.isActive(), r.getCreatedAt(),
                    r.getUpdatedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class PreferenceAdapter implements NotificationPreferenceRepository {
        private final PreferenceJpa jpa;

        @Override
        public NotificationPreference save(NotificationPreference p) {
            return toDomain(jpa.save(new PreferenceRow(p.getPreferenceId(), p.getUserId(),
                    p.getChannel(), p.isEnabled(), p.getMinSeverity(), p.getQuietHoursStart(),
                    p.getQuietHoursEnd(), p.getCreatedAt(), p.getUpdatedAt())));
        }

        @Override
        public List<NotificationPreference> findByUserId(UUID userId) {
            return jpa.findByUserId(userId).stream().map(PreferenceAdapter::toDomain).toList();
        }

        @Override
        public Optional<NotificationPreference> findByUserIdAndChannel(UUID userId, String channel) {
            return jpa.findByUserIdAndChannel(userId, channel).map(PreferenceAdapter::toDomain);
        }

        private static NotificationPreference toDomain(PreferenceRow r) {
            return new NotificationPreference(r.getPreferenceId(), r.getUserId(), r.getChannel(),
                    r.isEnabled(), r.getMinSeverity(), r.getQuietHoursStart(), r.getQuietHoursEnd(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class NotificationLogAdapter implements NotificationLogRepository {
        private final NotificationLogJpa jpa;

        @Override
        public NotificationLog save(NotificationLog l) {
            return toDomain(jpa.save(new NotificationLogRow(l.getNotificationId(), l.getAlertId(),
                    l.getChannel(), l.getRecipient(), l.getStatus(), l.getSentAt(),
                    l.getErrorMessage(), l.getCreatedAt())));
        }

        @Override
        public List<NotificationLog> findByAlertId(UUID alertId) {
            return jpa.findByAlertIdOrderByCreatedAtDesc(alertId).stream()
                    .map(NotificationLogAdapter::toDomain).toList();
        }

        private static NotificationLog toDomain(NotificationLogRow r) {
            return new NotificationLog(r.getNotificationId(), r.getAlertId(), r.getChannel(),
                    r.getRecipient(), r.getStatus(), r.getSentAt(), r.getErrorMessage(),
                    r.getCreatedAt());
        }
    }
}
