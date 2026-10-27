package com.sems.organizations.interfaces.acl;

import java.math.BigDecimal;
import java.util.List;
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
 * <p>Energia y dispositivos entran los dos por aqui. Tambien devuelve el nombre
 * del local y de la zona: el desglose de consumo se lee por nombre, y pedirselo
 * a quien es dueno del dato sale mas barato que copiarlo en cada modulo.
 */
public interface OrganizationsAcl {

    /**
     * Lo que otro modulo necesita saber de un local para repartir consumo:
     * como se llama, cuanta potencia tiene contratada y con que tarifa se
     * factura.
     *
     * <p>La categoria tarifaria viaja como texto porque energia no conoce el
     * enum de organizaciones; es quien la recibe el que la traduce.
     */
    record SiteSummary(UUID siteId, String siteCode, String name,
                       BigDecimal contractedPowerKw, String tariffCategory,
                       boolean excludesSundaysFromPeak) {
    }

    /** Lo que otro modulo necesita saber de una zona: como se llama y de que tipo es. */
    record ZoneSummary(UUID zoneId, String name, String zoneType) {
    }

    /** Datos del local, o vacio si no existe o esta archivado. */
    Optional<SiteSummary> siteSummary(UUID siteId);

    /** Locales vigentes de la organizacion, en el orden en que se registraron. */
    List<SiteSummary> activeSitesOf(UUID organizationId);

    /** Zonas vigentes del local. */
    List<ZoneSummary> activeZonesOf(UUID siteId);

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
