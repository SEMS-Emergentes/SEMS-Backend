package com.sems.alerts.application;

import com.sems.alerts.domain.model.entities.Alert;
import com.sems.alerts.domain.model.entities.InactivityRule;
import com.sems.alerts.domain.repositories.AlertRepositories.InactivityRuleRepository;
import com.sems.devices.interfaces.acl.DevicesAcl;
import com.sems.energy.interfaces.acl.EnergyAcl;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revisa las reglas de inactividad y avisa de los equipos que dejaron de
 * reportar.
 *
 * <p>Las reglas se podian crear y consultar desde el primer dia, pero nadie las
 * miraba: {@code findAllActive()} existia en el repositorio sin un solo
 * llamador, asi que una regla de inactividad era un formulario que no hacia
 * nada. Esta es la pieza que faltaba.
 *
 * <p>A diferencia de la demanda, que se evalua en la propia lectura porque la
 * lectura trae el dato, la inactividad solo se puede detectar por lo que
 * <em>no</em> llega. Eso obliga a mirar cada cierto tiempo, y de ahi que esta
 * sea la primera tarea periodica del sistema.
 */
@Service
@RequiredArgsConstructor
public class InactivityEvaluationService {

    private final InactivityRuleRepository rules;
    private final AlertCommandService alertas;
    private final EnergyAcl energia;
    private final DevicesAcl equipos;

    /**
     * Revisa todas las reglas activas y levanta los avisos que toquen.
     *
     * @return los avisos levantados en esta pasada
     */
    @Transactional
    public List<Alert> evaluateAll() {
        Instant ahora = Instant.now();
        List<Alert> levantadas = new ArrayList<>();
        Map<UUID, String> nombres = new HashMap<>();

        for (InactivityRule regla : rules.findAllActive()) {
            if (regla.getDeviceId() == null) {
                continue;
            }
            Instant ultima = energia.lastReadingOf(regla.getDeviceId()).orElse(null);

            // Un equipo que nunca reporto no cuenta como inactivo: es la
            // decision del dominio (isInactive devuelve false sin ultima
            // actividad) y la razon es que no se puede distinguir el que se
            // averio del que acaban de dar de alta y aun no ha medido.
            boolean inactivo = regla.isInactive(ultima, ahora);

            boolean avisar = regla.mereceAviso(inactivo, ahora);
            regla.anotarRevision(inactivo, ahora);
            rules.save(regla);
            if (!avisar) {
                continue;
            }

            long minutos = Duration.between(ultima, ahora).toMinutes();
            String nombre = nombreDe(nombres, regla);
            String mensaje = String.format(Locale.ROOT,
                    "%s no reporta desde hace %d minutos, más de los %d configurados. "
                            + "Su consumo no se está midiendo.",
                    nombre, minutos, regla.getMaxInactiveMinutes());

            levantadas.add(alertas.createAlert(regla.getUserId(), regla.getDeviceId(), null,
                    regla.getInactivityRuleId(), "INACTIVITY", "Equipo sin reportar",
                    mensaje, "WARNING", null, ahora));
        }
        return levantadas;
    }

    /**
     * Nombre del equipo para el mensaje, cacheado por usuario.
     *
     * <p>Un aviso que dice "el equipo 7f3a-..." no se puede leer, y el nombre
     * es dato del modulo de dispositivos. Se piden todos los del usuario de una
     * vez porque lo habitual es que varias reglas suyas caigan en la misma
     * pasada.
     */
    private String nombreDe(Map<UUID, String> cache, InactivityRule regla) {
        if (!cache.containsKey(regla.getDeviceId())) {
            for (DevicesAcl.DeviceSummary equipo : equipos.devicesOf(regla.getUserId())) {
                cache.put(equipo.deviceId(), equipo.name());
            }
        }
        String nombre = cache.get(regla.getDeviceId());
        return nombre == null ? "The device" : nombre;
    }
}
