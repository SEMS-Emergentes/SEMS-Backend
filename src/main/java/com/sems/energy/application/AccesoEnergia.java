package com.sems.energy.application;

import com.sems.energy.domain.repositories.ConsumptionAlertRepository;
import com.sems.energy.domain.repositories.DeviceConsumptionRepository;
import com.sems.energy.domain.repositories.EnergyMeterRepository;
import com.sems.energy.domain.repositories.EnergyReadingRepository;
import com.sems.shared.security.AccesoUsuario;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprueba que el medidor, la lectura, el consumo o la alerta son del usuario.
 *
 * <p>Los endpoints que van por {@code deviceId} se resuelven con los datos del
 * propio modulo y no preguntando al modulo de dispositivos: la pregunta real no
 * es "de quien es el equipo" sino "de quien son las lecturas de ese equipo",
 * que es lo unico que estos endpoints devuelven. Si el equipo no tiene ninguna
 * lectura no hay nada que filtrar y se deja pasar, porque la respuesta sera una
 * lista vacia o un 404.
 */
@Component("accesoEnergia")
@RequiredArgsConstructor
public class AccesoEnergia {

    private final EnergyMeterRepository meters;
    private final EnergyReadingRepository readings;
    private final DeviceConsumptionRepository consumptions;
    private final ConsumptionAlertRepository alerts;
    private final AccesoUsuario accesoUsuario;

    @Transactional(readOnly = true)
    public boolean medidor(UUID meterId) {
        if (meterId == null) {
            return true;
        }
        return meters.findById(meterId)
                .map(m -> accesoUsuario.esMio(m.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean medidor(String meterId) {
        UUID id = AccesoUsuario.aUuid(meterId);
        if (id == null) {
            return true;
        }
        return medidor(id);
    }

    /** Las lecturas de un medidor, para el endpoint que va por codigo de medidor. */
    @Transactional(readOnly = true)
    public boolean lecturasDelMedidor(String meterId) {
        if (meterId == null || meterId.isBlank()) {
            return true;
        }
        UUID id = AccesoUsuario.aUuid(meterId);
        if (id != null && meters.findById(id).isPresent()) {
            return medidor(id);
        }
        return readings.findLatestByMeter(meterId)
                .map(r -> accesoUsuario.esMio(r.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean lectura(UUID readingId) {
        if (readingId == null) {
            return true;
        }
        return readings.findById(readingId)
                .map(r -> accesoUsuario.esMio(r.getUserId()))
                .orElse(true);
    }

    /** Lecturas y consumo de un equipo, resueltos por la ultima lectura. */
    @Transactional(readOnly = true)
    public boolean lecturasDelDispositivo(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return true;
        }
        return readings.findLatestByDevice(deviceId)
                .map(r -> accesoUsuario.esMio(r.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean consumo(UUID consumptionId) {
        if (consumptionId == null) {
            return true;
        }
        return consumptions.findById(consumptionId)
                .map(c -> accesoUsuario.esMio(c.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean alertaDeConsumo(UUID alertId) {
        if (alertId == null) {
            return true;
        }
        return alerts.findById(alertId)
                .map(a -> accesoUsuario.esMio(a.getUserId()))
                .orElse(true);
    }
}
