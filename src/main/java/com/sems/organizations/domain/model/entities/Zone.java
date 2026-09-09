package com.sems.organizations.domain.model.entities;

import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.domain.model.valueobjects.ZoneType;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Zona dentro de un local: sala de ventas, camaras, almacen, cocina, oficinas.
 *
 * <p>Es una entidad del agregado Site, no una raiz: una zona no existe fuera de
 * su local y siempre se llega a ella a traves de el.
 */
@Getter
public class Zone {

    private final UUID zoneId;
    private final UUID siteId;
    private String name;
    private ZoneType zoneType;
    private boolean operatesOffHours;
    private OrgStatus status;
    private final Instant createdAt;

    private Zone(UUID zoneId, UUID siteId, String name, ZoneType zoneType, boolean operatesOffHours,
                 OrgStatus status, Instant createdAt) {
        this.zoneId = zoneId;
        this.siteId = siteId;
        this.name = name;
        this.zoneType = zoneType;
        this.operatesOffHours = operatesOffHours;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Zone register(UUID siteId, String name, ZoneType zoneType,
                                Boolean operatesOffHours) {
        if (siteId == null) {
            throw AppException.validation("site_id is required");
        }
        if (name == null || name.isBlank()) {
            throw AppException.validation("name is required");
        }

        // Si no lo dicen, se deduce del tipo: las camaras y el aire
        // acondicionado no se apagan; el resto si.
        boolean fueraDeHorario = operatesOffHours != null
                ? operatesOffHours
                : zoneType.funcionaFueraDeHorario();

        return new Zone(UUID.randomUUID(), siteId, name.trim(), zoneType, fueraDeHorario,
                OrgStatus.ACTIVE, Instant.now());
    }

    public static Zone rehydrate(UUID zoneId, UUID siteId, String name, ZoneType zoneType,
                                 boolean operatesOffHours, OrgStatus status, Instant createdAt) {
        return new Zone(zoneId, siteId, name, zoneType, operatesOffHours, status, createdAt);
    }

    public void updateDetails(String name, ZoneType zoneType, boolean operatesOffHours) {
        if (name == null || name.isBlank()) {
            throw AppException.validation("name is required");
        }
        this.name = name.trim();
        this.zoneType = zoneType;
        this.operatesOffHours = operatesOffHours;
    }

    public void archive() {
        this.status = OrgStatus.ARCHIVED;
    }

    public boolean isActive() {
        return this.status == OrgStatus.ACTIVE;
    }
}
