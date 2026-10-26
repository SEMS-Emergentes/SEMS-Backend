package com.sems.organizations.interfaces.acl;

import java.math.BigDecimal;
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

    /** Si el local existe, este vigente o no. */
    boolean siteExists(UUID siteId);

    /** Si el local existe y esta vigente. */
    boolean siteIsActive(UUID siteId);

    /** Si la zona existe, esta vigente y pertenece a ese local. */
    boolean zoneBelongsToSite(UUID zoneId, UUID siteId);

    /**
     * Potencia contratada del local, o vacio si no existe o no esta vigente.
     *
     * <p>La pide alertas para que el aviso de demanda no guarde su propia copia
     * del dato. El contrato con la distribuidora es del local; tener dos
     * numeros para la misma potencia significa que uno de los dos esta
     * desactualizado y nadie se entera.
     */
    Optional<BigDecimal> contractedPowerOf(UUID siteId);

    /** El local al que pertenece la zona, o vacio si la zona no existe. */
    Optional<UUID> siteOfZone(UUID zoneId);

    /** Numero de locales vigentes de la organizacion. */
    long activeSiteCount(UUID organizationId);

    /**
     * Si la persona es la unica administradora de alguna organizacion vigente.
     *
     * <p>La usa IAM antes de dar de baja una cuenta. Cerrar la cuenta del unico
     * administrador dejaria la organizacion sin nadie que pueda gestionarla: ni
     * dar de alta un local, ni invitar a un companero, ni darla de baja. Quedaria
     * viva y fuera de alcance para siempre.
     */
    boolean esUnicoAdministradorDeAlgunaOrganizacion(UUID userId);
}
