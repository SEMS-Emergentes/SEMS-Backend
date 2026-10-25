package com.sems.organizations.application;

import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.model.entities.Zone;
import com.sems.organizations.domain.repositories.MembershipRepository;
import com.sems.organizations.domain.repositories.SiteRepository;
import com.sems.organizations.domain.repositories.ZoneRepository;
import com.sems.shared.security.AccesoUsuario;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decide si el usuario autenticado alcanza una organizacion, un local o una
 * zona.
 *
 * <p>Las reglas no se inventan aqui: {@link Membership} ya sabia desde el
 * principio distinguir una membresia vigente, el alcance de un SUPERVISOR
 * limitado a un local y quien puede modificar o administrar. Lo que faltaba era
 * que alguien se lo preguntara antes de servir la respuesta. Este bean es ese
 * alguien.
 *
 * <p>Criterio comun a todos los metodos: si el recurso no existe la
 * comprobacion pasa, para que el servicio responda su 404 habitual en vez de
 * convertirse en 403. Si existe y es de otra persona, se deniega. La diferencia
 * entre 403 y 404 revela que un identificador existe, pero al ser UUID
 * aleatorios no son enumerables, y es una fuga mucho menor que devolver el
 * recurso entero.
 */
@Component("accesoOrganizaciones")
@RequiredArgsConstructor
public class AccesoOrganizaciones {

    private final MembershipRepository memberships;
    private final SiteRepository sites;
    private final ZoneRepository zones;
    private final AccesoUsuario accesoUsuario;

    /** Lectura de la organizacion: basta una membresia vigente. */
    @Transactional(readOnly = true)
    public boolean organizacion(String organizationId) {
        return conOrganizacion(organizationId, Membership::isActive);
    }

    /** Alta y baja de locales, zonas y miembros: solo ORG_ADMIN. */
    @Transactional(readOnly = true)
    public boolean administraOrganizacion(String organizationId) {
        return conOrganizacion(organizationId, Membership::puedeAdministrarLaOrganizacion);
    }

    /** Lectura de un local: la membresia tiene que alcanzarlo. */
    @Transactional(readOnly = true)
    public boolean local(String siteId) {
        return conLocal(siteId, (m, id) -> m.alcanzaAlLocal(id));
    }

    /** Escritura sobre un local: lo alcanza y tiene rol para modificar. */
    @Transactional(readOnly = true)
    public boolean modificaLocal(String siteId) {
        return conLocal(siteId, (m, id) -> m.alcanzaAlLocal(id) && m.puedeModificar());
    }

    /** Lectura de una zona, resuelta por el local al que pertenece. */
    @Transactional(readOnly = true)
    public boolean zona(String zoneId) {
        return zonaComo(zoneId, false);
    }

    /** Escritura sobre una zona. */
    @Transactional(readOnly = true)
    public boolean modificaZona(String zoneId) {
        return zonaComo(zoneId, true);
    }

    /** Variantes por UUID, para los controladores que ya reciben el tipo. */
    public boolean local(UUID siteId) {
        return siteId == null || local(siteId.toString());
    }

    public boolean organizacion(UUID organizationId) {
        return organizationId == null || organizacion(organizationId.toString());
    }

    public boolean zona(UUID zoneId) {
        return zoneId == null || zona(zoneId.toString());
    }

    private boolean zonaComo(String zoneId, boolean paraModificar) {
        UUID id = AccesoUsuario.aUuid(zoneId);
        if (id == null) {
            return true;
        }
        Optional<Zone> zona = zones.findById(id);
        if (zona.isEmpty()) {
            return true;
        }
        UUID siteId = zona.get().getSiteId();
        return paraModificar ? modificaLocal(siteId.toString()) : local(siteId.toString());
    }

    private boolean conLocal(String siteId, ReglaDeLocal regla) {
        UUID id = AccesoUsuario.aUuid(siteId);
        if (id == null) {
            return true;
        }
        Optional<Site> local = sites.findById(id);
        if (local.isEmpty()) {
            return true;
        }
        return membresia(local.get().getOrganizationId())
                .map(m -> regla.cumple(m, id))
                .orElseGet(accesoUsuario::esAdministrador);
    }

    private boolean conOrganizacion(String organizationId, ReglaDeOrganizacion regla) {
        UUID id = AccesoUsuario.aUuid(organizationId);
        if (id == null) {
            return true;
        }
        return membresia(id).map(regla::cumple).orElseGet(accesoUsuario::esAdministrador);
    }

    private Optional<Membership> membresia(UUID organizationId) {
        if (accesoUsuario.esAdministrador()) {
            return Optional.empty();
        }
        return accesoUsuario.actual()
                .flatMap(userId -> memberships.findByOrganizationAndUser(organizationId, userId));
    }

    @FunctionalInterface
    private interface ReglaDeLocal {
        boolean cumple(Membership membresia, UUID siteId);
    }

    @FunctionalInterface
    private interface ReglaDeOrganizacion {
        boolean cumple(Membership membresia);
    }
}
