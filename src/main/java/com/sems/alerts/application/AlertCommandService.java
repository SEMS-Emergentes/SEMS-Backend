package com.sems.alerts.application;

import com.sems.alerts.domain.model.entities.*;
import com.sems.alerts.domain.model.valueobjects.Operator;
import com.sems.alerts.domain.repositories.AlertRepositories.*;
import com.sems.alerts.domain.repositories.DemandRuleRepository;
import com.sems.alerts.domain.model.valueobjects.DemandLevel;
import com.sems.shared.errors.AppException;
import com.sems.shared.events.DomainEventBus;
import com.sems.shared.events.DomainEvents;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso que modifican el estado del modulo de alertas. */
@Service
@RequiredArgsConstructor
public class AlertCommandService {

    private final AlertRepository alerts;
    private final ThresholdRepository thresholds;
    private final InactivityRuleRepository rules;
    private final DemandRuleRepository demandRules;
    private final NotificationPreferenceRepository preferences;
    private final DomainEventBus bus;

    // ------------------------------------------------------ reglas de demanda

    @Transactional
    public DemandRule createDemandRule(UUID siteId, UUID userId, String ruleName,
                                       double contractedPowerKw, Double warningPercent,
                                       Boolean active) {
        return demandRules.save(DemandRule.create(siteId, userId, ruleName, contractedPowerKw,
                warningPercent, active));
    }

    public List<DemandRule> demandRulesBySite(UUID siteId) {
        return demandRules.findActiveBySiteId(siteId);
    }

    /**
     * Evalua una demanda medida contra las reglas del local y levanta la alerta
     * que corresponda.
     *
     * <p>Se levanta una alerta por regla incumplida, no una por local: un local
     * puede tener una regla estricta para el turno de noche y otra mas laxa para
     * el dia.
     *
     * <p>La severidad distingue los dos casos porque la accion del cliente es
     * distinta: con WARNING todavia puede apagar algo y evitar el recargo; con
     * EXCEEDED el cargo del mes ya esta hecho y lo unico util es que no se
     * repita.
     */
    @Transactional
    public List<Alert> evaluateDemand(UUID siteId, double demandaKw) {
        List<Alert> levantadas = new ArrayList<>();

        for (DemandRule regla : demandRules.findActiveBySiteId(siteId)) {
            DemandLevel nivel = regla.evaluar(demandaKw);
            if (nivel == DemandLevel.OK) {
                continue;
            }

            double margen = regla.margenKw(demandaKw);
            String titulo;
            String mensaje;
            String severidad;

            if (nivel == DemandLevel.WARNING) {
                titulo = "Demand close to the contracted limit";
                mensaje = String.format(
                        "The site is at %.2f kW of %.2f kW contracted. %.2f kW of headroom left.",
                        demandaKw, regla.getContractedPowerKw(), margen);
                severidad = "WARNING";
            } else {
                titulo = "Demand above the contracted limit";
                mensaje = String.format(
                        "The site reached %.2f kW, %.2f kW above the %.2f kW contracted. "
                                + "The excess power charge applies to the whole month.",
                        demandaKw, Math.abs(margen), regla.getContractedPowerKw());
                severidad = "CRITICAL";
            }

            levantadas.add(createAlert(regla.getUserId(), null, null, null, "DEMAND", titulo,
                    mensaje, severidad, null, Instant.now()));
        }

        return levantadas;
    }

    @Transactional
    public Alert createAlert(UUID userId, UUID deviceId, UUID thresholdId, UUID inactivityRuleId,
                             String alertType, String title, String message, String severity,
                             String status, Instant triggeredAt) {
        Alert saved = alerts.save(Alert.raise(userId, deviceId, thresholdId, inactivityRuleId,
                alertType, title, message, severity, status, triggeredAt));
        // El envio del correo lo dispara este evento, no una llamada directa:
        // asi la alerta se guarda aunque el servidor de correo este caido.
        bus.publish(new DomainEvents.AlertTriggered(userId, saved.getAlertId(),
                alertType, severity, message));
        return saved;
    }

    @Transactional
    public Alert updateStatus(UUID alertId, String status, Instant resolvedAt) {
        Alert alert = alerts.findById(alertId)
                .orElseThrow(() -> AppException.notFound("alert not found"));
        alert.updateStatus(status, resolvedAt);
        return alerts.save(alert);
    }

    @Transactional
    public AlertThreshold createThreshold(UUID userId, UUID deviceId, String name, String metric,
                                          String operator, double value, Boolean active) {
        return thresholds.save(AlertThreshold.create(userId, deviceId, name, metric,
                Operator.of(operator), value, active));
    }

    @Transactional
    public InactivityRule createInactivityRule(UUID userId, UUID deviceId, String ruleName,
                                               int maxInactiveMinutes, boolean active) {
        if (maxInactiveMinutes <= 0) {
            throw AppException.validation("max_inactive_minutes must be greater than zero");
        }
        return rules.save(InactivityRule.create(userId, deviceId, ruleName, maxInactiveMinutes, active));
    }

    /** Repetir el canal actualiza la preferencia en vez de duplicarla. */
    @Transactional
    public NotificationPreference createPreference(UUID userId, String channel, boolean enabled,
                                                   String minSeverity, Instant quietStart,
                                                   Instant quietEnd) {
        preferences.findByUserIdAndChannel(userId, channel).ifPresent(existing ->
                preferences.save(new NotificationPreference(existing.getPreferenceId(), userId,
                        channel, enabled, minSeverity, quietStart, quietEnd,
                        existing.getCreatedAt(), Instant.now())));

        return preferences.findByUserIdAndChannel(userId, channel)
                .orElseGet(() -> preferences.save(NotificationPreference.create(userId, channel,
                        enabled, minSeverity, quietStart, quietEnd)));
    }
}
