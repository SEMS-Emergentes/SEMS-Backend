package com.sems.alerts.application;

import com.sems.shared.events.DomainEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Vigila la demanda del local cada vez que llega una lectura.
 *
 * <p>Hasta ahora la evaluacion solo ocurria cuando alguien abria la pantalla de
 * alertas y escribia una demanda a mano. Eso convierte en manual lo unico que
 * el producto promete automatico: el cargo por potencia lo fija el pico mas
 * alto del mes y dura quince minutos, de modo que un aviso que depende de que
 * el cliente se acuerde de comprobarlo llega siempre tarde.
 *
 * <p>Los umbrales por equipo ya se evaluaban asi desde
 * {@link ConsumptionEventHandler}; esto le da a la demanda del local el mismo
 * tratamiento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemandEventHandler {

    private final AlertCommandService alertas;

    @Async
    @TransactionalEventListener
    public void onReadingProcessed(DomainEvents.ReadingProcessed event) {
        if (event.siteId() == null || event.powerKw() == null) {
            return;
        }
        // La potencia instantanea de la lectura es la demanda del momento. No es
        // la demanda facturable —esa es la maxima del mes en intervalos de
        // quince minutos— pero es la unica senal disponible en tiempo real, y
        // para avisar a tiempo es la que sirve.
        var levantadas = alertas.evaluarDemandaAutomatica(event.siteId(),
                event.powerKw().doubleValue());
        if (!levantadas.isEmpty()) {
            log.info("Demanda del local {}: {} aviso(s) levantado(s)", event.siteId(),
                    levantadas.size());
        }
    }
}
