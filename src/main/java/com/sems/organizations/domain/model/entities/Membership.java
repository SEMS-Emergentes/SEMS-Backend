package com.sems.organizations.domain.model.entities;

import com.sems.organizations.domain.model.valueobjects.MembershipRole;
import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Vinculo entre una persona y una organizacion, con su papel y su alcance.
 *
 * <p>Es lo que sustituye al usuario unico del segmento residencial. En una casa,
 * quien entra es el dueno y ve todo. En una cadena, el jefe de tienda de
 * Miraflores no tiene por que ver el consumo de San Isidro, y el operario no
 * tiene por que poder dar de baja un local.
 *
 * <p>El alcance se decide con {@code siteId}: si es nulo, el vinculo cubre la
 * organizacion entera; si trae un local, solo ese.
 */
@Getter
public class Membership {

    private final UUID membershipId;
    private final UUID organizationId;
    private final UUID userId;
    private MembershipRole role;
    private UUID siteId;
    private OrgStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Membership(UUID membershipId, UUID organizationId, UUID userId, MembershipRole role,
                       UUID siteId, OrgStatus status, Instant createdAt, Instant updatedAt) {
        this.membershipId = membershipId;
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
        this.siteId = siteId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Membership grant(UUID organizationId, UUID userId, MembershipRole role,
                                   UUID siteId) {
        if (organizationId == null) {
            throw AppException.validation("organization_id is required");
        }
        if (userId == null) {
            throw AppException.validation("user_id is required");
        }
        validarAlcance(role, siteId);

        Instant ahora = Instant.now();
        return new Membership(UUID.randomUUID(), organizationId, userId, role, siteId,
                OrgStatus.ACTIVE, ahora, ahora);
    }

    public static Membership rehydrate(UUID membershipId, UUID organizationId, UUID userId,
                                       MembershipRole role, UUID siteId, OrgStatus status,
                                       Instant createdAt, Instant updatedAt) {
        return new Membership(membershipId, organizationId, userId, role, siteId, status, createdAt,
                updatedAt);
    }

    private static void validarAlcance(MembershipRole role, UUID siteId) {
        // El administrador manda en toda la cadena por definicion. Atarlo a un
        // local seria contradictorio, y ademas dejaria la organizacion sin nadie
        // que pueda dar de alta el siguiente local.
        if (role == MembershipRole.ORG_ADMIN && siteId != null) {
            throw AppException.validation("an ORG_ADMIN cannot be limited to a single site");
        }
        // Al reves tambien: un supervisor sin local asignado no supervisa nada,
        // y dejarlo pasar acabaria dandole acceso a toda la cadena por descuido.
        if (role == MembershipRole.SUPERVISOR && siteId == null) {
            throw AppException.validation("a SUPERVISOR must be assigned to a site");
        }
    }

    public void changeRole(MembershipRole role, UUID siteId) {
        validarAlcance(role, siteId);
        this.role = role;
        this.siteId = siteId;
        this.updatedAt = Instant.now();
    }

    public void revoke() {
        this.status = OrgStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == OrgStatus.ACTIVE;
    }

    /** Si este vinculo permite ver o tocar el local indicado. */
    public boolean alcanzaAlLocal(UUID siteId) {
        return isActive() && (this.siteId == null || this.siteId.equals(siteId));
    }

    /** Si este vinculo permite modificar, no solo consultar. */
    public boolean puedeModificar() {
        return isActive()
                && (role == MembershipRole.ORG_ADMIN || role == MembershipRole.SUPERVISOR);
    }

    /** Si este vinculo permite gestionar locales y personas. */
    public boolean puedeAdministrarLaOrganizacion() {
        return isActive() && role == MembershipRole.ORG_ADMIN;
    }
}
