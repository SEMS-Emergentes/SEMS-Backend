package com.sems.organizations.domain.model.aggregates;

import com.sems.organizations.domain.model.valueobjects.BusinessType;
import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Raiz del agregado Organization: la empresa que contrata el servicio.
 *
 * <p>Es el nivel mas alto del segmento: una cadena de supermercados es una
 * organizacion con varios locales. Una tienda independiente tambien es una
 * organizacion, solo que con un local. Modelarlo asi evita tener dos caminos
 * distintos segun el tamano del cliente.
 */
@Getter
public class Organization {

    private final UUID organizationId;
    private String legalName;
    private String tradeName;
    private final String taxId;
    private BusinessType businessType;
    private OrgStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Organization(UUID organizationId, String legalName, String tradeName, String taxId,
                         BusinessType businessType, OrgStatus status, Instant createdAt,
                         Instant updatedAt) {
        this.organizationId = organizationId;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.taxId = taxId;
        this.businessType = businessType;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Unica forma correcta de dar de alta una organizacion. */
    public static Organization register(String legalName, String tradeName, String taxId,
                                        BusinessType businessType) {
        if (legalName == null || legalName.isBlank()) {
            throw AppException.validation("legal_name is required");
        }

        String ruc = taxId == null ? "" : taxId.trim();
        if (ruc.isEmpty()) {
            throw AppException.validation("tax_id is required");
        }

        // El RUC peruano son 11 digitos. Se comprueba la forma, no la existencia:
        // validar contra SUNAT es una llamada a un tercero y no puede decidir si
        // un alta se acepta o no.
        if (ruc.length() != 11 || !ruc.chars().allMatch(Character::isDigit)) {
            throw AppException.validation("tax_id must be 11 digits");
        }

        Instant ahora = Instant.now();
        return new Organization(UUID.randomUUID(), legalName.trim(),
                tradeName == null || tradeName.isBlank() ? null : tradeName.trim(),
                ruc, businessType, OrgStatus.ACTIVE, ahora, ahora);
    }

    /** Reconstruye desde persistencia. */
    public static Organization rehydrate(UUID organizationId, String legalName, String tradeName,
                                         String taxId, BusinessType businessType, OrgStatus status,
                                         Instant createdAt, Instant updatedAt) {
        return new Organization(organizationId, legalName, tradeName, taxId, businessType, status,
                createdAt, updatedAt);
    }

    public void updateDetails(String legalName, String tradeName, BusinessType businessType) {
        if (legalName == null || legalName.isBlank()) {
            throw AppException.validation("legal_name is required");
        }
        this.legalName = legalName.trim();
        this.tradeName = tradeName == null || tradeName.isBlank() ? null : tradeName.trim();
        this.businessType = businessType;
        this.updatedAt = Instant.now();
    }

    public void suspend() {
        this.status = OrgStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status == OrgStatus.ARCHIVED) {
            throw AppException.validation("an archived organization cannot be reactivated");
        }
        this.status = OrgStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = OrgStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == OrgStatus.ACTIVE;
    }
}
