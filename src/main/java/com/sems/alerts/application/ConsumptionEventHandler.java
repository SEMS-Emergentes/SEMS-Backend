package com.sems.alerts.application;

import com.sems.alerts.domain.model.entities.AlertThreshold;
import com.sems.alerts.domain.repositories.AlertRepositories.AlertRepository;
import com.sems.alerts.domain.repositories.AlertRepositories.ThresholdRepository;
import com.sems.energy.interfaces.acl.EnergyAcl;
import com.sems.shared.events.DomainEvents;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
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
 *
 * <p>El umbral es un limite de kWh por dia, que es como se define en la
 * pantalla. Antes se comparaba con la energia de la lectura suelta: con un
 * medidor que reporta cada hora ninguna lectura llega al limite de un dia, y el
 * aviso no salia nunca aunque el equipo lo pasara de largo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumptionEventHandler {

    /** Peru esta en UTC-5 todo el ano: el dia del limite se corta ahi. */
    private static final ZoneOffset PERU = ZoneOffset.ofHours(-5);

    private final ThresholdRepository thresholds;
    private final AlertRepository alerts;
    private final AlertCommandService alertCommands;
    private final EnergyAcl energia;

    @Async
    @TransactionalEventListener
    public void onReadingProcessed(DomainEvents.ReadingProcessed event) {
        if (event.deviceId() == null || event.consumptionKwh() == null) {
            return;
        }
        List<AlertThreshold> activos = thresholds.findActiveByDeviceId(event.deviceId());
        if (activos.isEmpty()) {
            return;
        }
        // Las lecturas de un lote se evaluan en hilos paralelos. Sin el cerrojo,
        // dos lecturas del mismo dia veian a la vez que aun no habia aviso y
        // creaban dos. Solo llegan aqui equipos con umbral, que son pocos.
        synchronized (this) {
            evaluar(event, activos);
        }
    }

    private void evaluar(DomainEvents.ReadingProcessed event, List<AlertThreshold> activos) {
        Instant cuando = event.recordedAt() != null ? event.recordedAt() : Instant.now();
        double delDia = energia.deviceKwhOnDayOf(event.deviceId(), cuando);
        Instant inicioDelDia = cuando.atOffset(PERU).toLocalDate().atStartOfDay().toInstant(PERU);
        Instant finDelDia = inicioDelDia.plus(Duration.ofDays(1));

        for (AlertThreshold threshold : activos) {
            if (!threshold.isBreachedBy(delDia)) {
                continue;
            }
            // Una vez pasado el limite, cada lectura nueva del dia lo seguiria
            // pasando: sin esto la bandeja recibiria un aviso por lectura.
            if (alerts.existsForThresholdBetween(threshold.getThresholdId(), inicioDelDia, finDelDia)) {
                continue;
            }
            String message = String.format(Locale.ROOT,
                    "El equipo superó el umbral '%s': %.2f kWh hoy %s %.2f kWh por día.",
                    threshold.getThresholdName(), delDia, threshold.getOperator().symbol(),
                    threshold.getThresholdValue());

            alertCommands.createAlert(event.userId(), event.deviceId(), threshold.getThresholdId(),
                    null, "threshold_exceeded", "Consumo por encima del umbral", message,
                    "high", null, cuando);

            log.info("Umbral {} roto por el dispositivo {}: {} kWh en el dia",
                    threshold.getThresholdId(), event.deviceId(), delDia);
        }
    }
}
