package com.sems.alerts.application;

import com.sems.alerts.domain.model.entities.AlertThreshold;
import com.sems.alerts.domain.repositories.AlertRepositories.ThresholdRepository;
import com.sems.shared.events.DomainEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evalua los umbrales cada vez que llega una lectura.
 *
 * <p>Sustituye al consumidor del topic {@code energy.events}. Escucha
 * {@link DomainEvents.ReadingProcessed} y, si alguna regla activa del
 * dispositivo se rompe, levanta la alerta correspondiente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumptionEventHandler {

    private final ThresholdRepository thresholds;
    private final AlertCommandService alertCommands;

    @Async
    @TransactionalEventListener
    public void onReadingProcessed(DomainEvents.ReadingProcessed event) {
        if (event.deviceId() == null || event.consumptionKwh() == null) {
            return;
        }
        double value = event.consumptionKwh().doubleValue();

        for (AlertThreshold threshold : thresholds.findActiveByDeviceId(event.deviceId())) {
            if (!threshold.isBreachedBy(value)) {
                continue;
            }
            String message = "Device exceeded threshold '%s': %s %s %s %s"
                    .formatted(threshold.getThresholdName(), value, threshold.getOperator().symbol(),
                            threshold.getThresholdValue(), threshold.getMetric());

            alertCommands.createAlert(event.userId(), event.deviceId(), threshold.getThresholdId(),
                    null, "threshold_exceeded", "Consumption above threshold", message,
                    "high", null, event.recordedAt());

            log.info("Umbral {} roto por el dispositivo {}", threshold.getThresholdId(),
                    event.deviceId());
        }
    }
}
