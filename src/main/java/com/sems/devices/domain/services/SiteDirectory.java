package com.sems.devices.domain.services;

import java.util.UUID;

/**
 * Lo unico que el modulo de dispositivos necesita saber sobre locales y zonas.
 *
 * <p>Es un puerto y no una llamada directa al modulo de organizaciones a
 * proposito. Dispositivos no tiene por que conocer que es una organizacion, ni
 * una categoria tarifaria, ni un vinculo de acceso: solo necesita responder a
 * dos preguntas antes de dar de alta un equipo.
 *
 * <p>Declarar aqui esa frontera es lo que evita que un monolito modular se
 * convierta en una maranha donde cualquier modulo llama a cualquier otro.
 */
public interface SiteDirectory {

    /** Si el local existe y esta vigente. */
    boolean siteIsActive(UUID siteId);

    /** Si la zona existe, esta vigente y pertenece a ese local. */
    boolean zoneBelongsToSite(UUID zoneId, UUID siteId);
}
