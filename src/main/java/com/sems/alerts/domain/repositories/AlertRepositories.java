package com.sems.alerts.domain.repositories;

import com.sems.alerts.domain.model.entities.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puertos de salida del modulo de alertas. */
public final class AlertRepositories {

    private AlertRepositories() {
    }

    public interface AlertRepository {
        Alert save(Alert alert);
        Optional<Alert> findById(UUID alertId);
        List<Alert> findAll();
        List<Alert> findByUserId(UUID userId);
        /** Si el umbral ya levanto algun aviso entre dos instantes, [desde, hasta). */
        boolean existsForThresholdBetween(UUID thresholdId, java.time.Instant from, java.time.Instant to);
    }

    public interface ThresholdRepository {
        AlertThreshold save(AlertThreshold threshold);
        Optional<AlertThreshold> findById(UUID thresholdId);
        List<AlertThreshold> findByUserId(UUID userId);
        /** Umbrales activos de un dispositivo; los usa la evaluacion automatica. */
        List<AlertThreshold> findActiveByDeviceId(UUID deviceId);
        long countByUserId(UUID userId);
    }

    public interface InactivityRuleRepository {
        InactivityRule save(InactivityRule rule);
        List<InactivityRule> findByUserId(UUID userId);
        List<InactivityRule> findAllActive();
    }

    public interface NotificationPreferenceRepository {
        NotificationPreference save(NotificationPreference preference);
        List<NotificationPreference> findByUserId(UUID userId);
        Optional<NotificationPreference> findByUserIdAndChannel(UUID userId, String channel);
    }

    public interface NotificationLogRepository {
        NotificationLog save(NotificationLog log);
        List<NotificationLog> findByAlertId(UUID alertId);
    }
}
