package com.sems.devices.application;

import com.sems.devices.domain.repositories.DeviceBindingRepository;
import com.sems.devices.domain.repositories.DeviceConfigurationRepository;
import com.sems.devices.domain.repositories.DeviceRepository;
import com.sems.shared.security.AccesoUsuario;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprueba que el equipo, su configuracion o su vinculo son del usuario.
 *
 * <p>Un {@code Device} ya guarda a quien pertenece. La configuracion y el
 * vinculo no se preguntan por su propio dueno sino por el del equipo: son
 * detalles de un equipo, y si el equipo es ajeno tambien lo son ellos.
 */
@Component("accesoDispositivos")
@RequiredArgsConstructor
public class AccesoDispositivos {

    private final DeviceRepository devices;
    private final DeviceConfigurationRepository configurations;
    private final DeviceBindingRepository bindings;
    private final AccesoUsuario accesoUsuario;

    @Transactional(readOnly = true)
    public boolean dispositivo(UUID deviceId) {
        if (deviceId == null) {
            return true;
        }
        return devices.findById(deviceId)
                .map(d -> accesoUsuario.esMio(d.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean dispositivo(String deviceId) {
        UUID id = AccesoUsuario.aUuid(deviceId);
        return id == null || dispositivo(id);
    }

    @Transactional(readOnly = true)
    public boolean configuracion(UUID configurationId) {
        if (configurationId == null) {
            return true;
        }
        return configurations.findById(configurationId)
                .map(c -> dispositivo(c.getDeviceId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean enlace(UUID bindingId) {
        if (bindingId == null) {
            return true;
        }
        return bindings.findById(bindingId)
                .map(b -> accesoUsuario.esMio(b.getUserId()))
                .orElse(true);
    }
}
