package com.sems.alerts.infrastructure.scheduling;

import com.sems.alerts.application.InactivityEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara cada cierto tiempo la revision de los equipos que dejaron de
 * reportar.
 *
 * <p>Es la unica tarea periodica del sistema, y existe porque la inactividad no
 * se puede detectar con un evento: no hay nada que publicar cuando una lectura
 * no llega. Todo lo demas se evalua en el momento en que entra el dato.
 *
 * <p>El reloj solo marca el ritmo. Cada cuanto se revisa no cambia cuantos
 * avisos recibe nadie, porque la ventana de silencio la cuenta la propia regla:
 * revisar mas a menudo solo adelanta el momento en que se detecta la caida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InactivityWatchdog {

    private final InactivityEvaluationService evaluacion;

    @Scheduled(
            fixedDelayString = "${app.alerts.inactivity-scan-ms:300000}",
            initialDelayString = "${app.alerts.inactivity-scan-initial-ms:60000}")
    public void revisarEquiposSinReportar() {
        try {
            int avisos = evaluacion.evaluateAll().size();
            if (avisos > 0) {
                log.info("Revision de inactividad: {} avisos levantados", avisos);
            }
        } catch (RuntimeException e) {
            // Una pasada que falla no puede llevarse por delante al
            // planificador: con fixedDelay, una excepcion que sale de aqui
            // cancela las siguientes y la vigilancia se apaga en silencio.
            log.error("La revision de inactividad fallo; se reintentara en la siguiente pasada", e);
        }
    }
}
