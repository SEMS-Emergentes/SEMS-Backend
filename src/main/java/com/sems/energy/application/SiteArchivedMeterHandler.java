package com.sems.energy.application;

import com.sems.energy.domain.model.entities.EnergyMeter;
import com.sems.energy.domain.repositories.EnergyMeterRepository;
import com.sems.shared.events.DomainEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Desactiva los medidores de un local que se dio de baja.
 *
 * <p>Sin esto los medidores quedaban activos colgando de un local archivado y
 * seguian saliendo en los listados: el usuario cerraba una tienda y sus
 * medidores seguian figurando como si midieran algo.
 *
 * <p>Se desactivan, no se desvinculan. El equipo fisico sigue existiendo y su
 * historico de lecturas tambien; lo que termino es el suministro. Si el local
 * se reactiva, los medidores estan donde estaban.
 *
 * <p>Va por evento y no por llamada directa para que organizaciones no tenga
 * que conocer el modulo de energia. {@code @TransactionalEventListener} hace
 * que solo corra si la baja del local llego a confirmarse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteArchivedMeterHandler {

    private final EnergyMeterRepository meters;

    // El listener corre despues del commit de la baja, fuera de su transaccion,
    // asi que necesita una propia.
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSiteArchived(DomainEvents.SiteArchived event) {
        String siteId = event.siteId().toString();
        int desactivados = 0;
        for (EnergyMeter medidor : meters.findBySiteId(siteId)) {
            if (medidor.isActive()) {
                medidor.deactivate();
                meters.save(medidor);
                desactivados++;
            }
        }
        if (desactivados > 0) {
            log.info("Local {} dado de baja: {} medidores desactivados", siteId, desactivados);
        }
    }
}
