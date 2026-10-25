package com.sems.organizations.interfaces.acl;

import java.util.Optional;
import java.util.UUID;

/**
 * Capa anticorrupcion del bounded context de organizaciones.
 *
 * <p>Es la puerta por la que otro modulo pregunta lo minimo sobre un local o una
 * zona sin conocer el agregado. Sigue el mismo patron que
 * {@code IamAcl}: devuelve datos concretos, no entidades, para que un cambio
 * interno aqui no se propague a quien pregunta.
 *
 * <p>Nace porque los medidores pasan a pertenecer a un local y a una zona, y
 * energia necesita comprobar dos cosas antes de aceptar el alta: que el local
 * este vigente y que la zona sea de ese local. Sin esa segunda comprobacion se
 * podria colgar un medidor de la zona de otro establecimiento, y el consumo
 * quedaria atribuido al area equivocada.
 *
 * <p>El modulo de dispositivos resuelve la misma necesidad con un puerto propio
 * ({@code SiteDirectory}) que declara el consumidor. Ambos patrones conviven en
 * el proyecto; unificarlos es una limpieza pendiente, no un cambio de esta
 * entrega.
 */
public interface OrganizationsAcl {

    /** Si el local existe y esta vigente. */
    boolean siteIsActive(UUID siteId);

    /** Si la zona existe, esta vigente y pertenece a ese local. */
    boolean zoneBelongsToSite(UUID zoneId, UUID siteId);

    /** El local al que pertenece la zona, o vacio si la zona no existe. */
    Optional<UUID> siteOfZone(UUID zoneId);

    /** Numero de locales vigentes de la organizacion. */
    long activeSiteCount(UUID organizationId);
}
