package com.sems.devices.application;

import com.sems.devices.domain.model.aggregates.Device;
import com.sems.devices.domain.model.valueobjects.DeviceStatus;
import com.sems.devices.domain.repositories.DeviceRepository;
import com.sems.shared.events.DomainEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Desactiva los equipos de un local que se dio de baja.
 *
 * <p>Mismo motivo que en energia: un equipo activo colgando de un local
 * archivado sigue apareciendo en los listados del local y contando como
 * "dispositivo activo" en el panel.
 *
 * <p>Se pasan a INACTIVE y no a REMOVED: el equipo existe y puede reinstalarse
 * si el local vuelve a abrir. Dar de baja definitivamente es decision de quien
 * lo gestiona, no efecto colateral de cerrar el local.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteArchivedDeviceHandler {

    private final DeviceRepository devices;

    // El listener corre despues del commit de la baja, fuera de su transaccion,
    // asi que necesita una propia.
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSiteArchived(DomainEvents.SiteArchived event) {
        int desactivados = 0;
        for (Device equipo : devices.findBySiteId(event.siteId())) {
            if (equipo.getStatus() == DeviceStatus.ACTIVE) {
                equipo.changeStatus(DeviceStatus.INACTIVE);
                devices.save(equipo);
                desactivados++;
            }
        }
        if (desactivados > 0) {
            log.info("Local {} dado de baja: {} equipos desactivados", event.siteId(), desactivados);
        }
    }
}
