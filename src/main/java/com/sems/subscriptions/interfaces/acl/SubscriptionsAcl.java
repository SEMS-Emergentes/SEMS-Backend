package com.sems.subscriptions.interfaces.acl;

import java.util.Optional;
import java.util.UUID;

/**
 * Capa anticorrupcion del bounded context de suscripciones.
 *
 * <p>Existe para que organizaciones pueda respetar el cupo de locales del plan
 * sin conocer que es una suscripcion, un plan ni una caracteristica. Lo unico
 * que devuelve es cuantos locales incluye el plan vigente y como se llama, que
 * es lo justo para decidir y para explicar la negativa.
 */
public interface SubscriptionsAcl {

    /** Cuantos locales incluye el plan vigente y como se llama. */
    record SiteAllowance(int sitesIncluded, String planName) {
    }

    /**
     * Cupo de locales del plan vigente del usuario.
     *
     * <p>Devuelve vacio cuando no hay nada que aplicar: el usuario no tiene
     * suscripcion activa, su plan declara el cupo como ilimitado, o el plan no
     * declara la caracteristica. Un cupo que no se conoce no se inventa, porque
     * bloquear por desconocimiento seria peor que no bloquear.
     */
    Optional<SiteAllowance> siteAllowanceFor(UUID userId);
}
