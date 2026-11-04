package com.sems.analytics.application;

import com.sems.shared.events.DomainEvents;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recalcula los hallazgos de analitica cuando entra consumo nuevo.
 *
 * <p>Se engancha a la lectura, como ya hacian los umbrales y la demanda, en vez
 * de a un trabajo programado: el backend duerme cuando nadie lo usa, de modo
 * que un cron nocturno no es fiable aqui, y en cambio una lectura es justo la
 * senal de que hay algo nuevo que mirar.
 *
 * <p>No recalcula en cada lectura, que seria recorrer un mes de consumo cada
 * pocos minutos; tampoco en la primera y luego esperar, que es peor: al
 * importar historia de golpe, la primera lectura que entra es la mas antigua y
 * los hallazgos saldrian calculados con un solo dia. Lo que hace es esperar a
 * que el flujo se calme —si no llega nada nuevo en unos segundos, el lote ha
 * terminado— y entonces calcular una vez con todo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightEventHandler {

    /** Tiempo sin lecturas nuevas que se considera "ya no viene mas". */
    private static final Duration ESPERA = Duration.ofSeconds(5);

    private final InsightEngine engine;
    private final ScheduledExecutorService reloj =
            Executors.newSingleThreadScheduledExecutor(hilo -> {
                Thread t = new Thread(hilo, "insights");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, ScheduledFuture<?>> pendientes = new ConcurrentHashMap<>();

    @TransactionalEventListener
    public void onReadingProcessed(DomainEvents.ReadingProcessed event) {
        if (event.userId() == null) {
            return;
        }
        String usuario = event.userId().toString();

        // Cada lectura nueva aplaza el calculo: solo corre el ultimo aviso.
        ScheduledFuture<?> anterior = pendientes.put(usuario,
                reloj.schedule(() -> calcular(usuario), ESPERA.toSeconds(), TimeUnit.SECONDS));
        if (anterior != null) {
            anterior.cancel(false);
        }
    }

    private void calcular(String usuario) {
        pendientes.remove(usuario);
        try {
            var hallazgos = engine.recalcular(usuario);
            if (!hallazgos.anomalias().isEmpty() || !hallazgos.recomendaciones().isEmpty()) {
                log.info("Analitica de {}: {} anomalia(s), {} recomendacion(es)", usuario,
                        hallazgos.anomalias().size(), hallazgos.recomendaciones().size());
            }
        } catch (RuntimeException fallo) {
            // Un fallo calculando hallazgos no puede afectar a la ingesta de
            // lecturas: lo que el cliente paga es que la medicion no se pierda.
            log.warn("No se pudieron recalcular los hallazgos de {}: {}", usuario,
                    fallo.getMessage());
        }
    }

    @PreDestroy
    void cerrar() {
        reloj.shutdownNow();
    }
}
