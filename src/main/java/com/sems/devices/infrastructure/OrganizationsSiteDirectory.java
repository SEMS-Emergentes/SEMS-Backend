package com.sems.devices.infrastructure;

import com.sems.devices.domain.services.SiteDirectory;
import com.sems.organizations.domain.repositories.SiteRepository;
import com.sems.organizations.domain.repositories.ZoneRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resuelve {@link SiteDirectory} contra el modulo de organizaciones.
 *
 * <p>La dependencia entre modulos vive aqui, en infraestructura, que es la capa
 * a la que le corresponde conocer el mundo exterior. El dominio y la capa de
 * aplicacion de dispositivos siguen sin saber que existe una organizacion.
 */
@Component
@RequiredArgsConstructor
public class OrganizationsSiteDirectory implements SiteDirectory {

    private final SiteRepository sites;
    private final ZoneRepository zones;

    @Override
    public boolean siteIsActive(UUID siteId) {
        return sites.findById(siteId).map(s -> s.isActive()).orElse(false);
    }

    @Override
    public boolean zoneBelongsToSite(UUID zoneId, UUID siteId) {
        return zones.findById(zoneId)
                .map(z -> z.isActive() && z.getSiteId().equals(siteId))
                .orElse(false);
    }
}
