package com.sems.organizations.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sems.organizations.domain.model.aggregates.Organization;
import com.sems.organizations.domain.model.aggregates.Site;
import com.sems.organizations.domain.model.entities.Membership;
import com.sems.organizations.domain.model.entities.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contrato JSON del modulo de organizaciones, en snake_case.
 *
 * <p>Se elige snake_case por ser el que usan la mayoria de modulos (energia,
 * analitica, alertas y pagos). Modulo nuevo, criterio unico: aqui no hay
 * historia previa que respetar.
 */
public final class OrganizationResources {

    private OrganizationResources() {
    }

    // ------------------------------------------------------------- peticiones

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateOrganizationRequest(
            @NotBlank(message = "is required") String legalName,
            String tradeName,
            @NotBlank(message = "is required") String taxId,
            @NotBlank(message = "is required") String businessType,
            @NotBlank(message = "is required") String ownerUserId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateOrganizationRequest(
            @NotBlank(message = "is required") String legalName,
            String tradeName,
            @NotBlank(message = "is required") String businessType) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateSiteRequest(
            @NotBlank(message = "is required") String siteCode,
            @NotBlank(message = "is required") String name,
            String address,
            String district,
            BigDecimal floorAreaM2,
            @NotNull(message = "is required") BigDecimal contractedPowerKw,
            @NotBlank(message = "is required") String tariffCategory,
            // Solo true si la distribuidora concedio la exclusion a solicitud del cliente.
            Boolean excludesSundaysFromPeak) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateSiteRequest(
            @NotBlank(message = "is required") String name,
            String address,
            String district,
            BigDecimal floorAreaM2,
            @NotNull(message = "is required") BigDecimal contractedPowerKw,
            @NotBlank(message = "is required") String tariffCategory,
            Boolean excludesSundaysFromPeak) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateZoneRequest(
            @NotBlank(message = "is required") String name,
            @NotBlank(message = "is required") String zoneType,
            Boolean operatesOffHours) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateZoneRequest(
            @NotBlank(message = "is required") String name,
            @NotBlank(message = "is required") String zoneType,
            boolean operatesOffHours) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GrantMembershipRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String role,
            String siteId) {
    }

    // ------------------------------------------------------------- respuestas

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrganizationResource(String organizationId, String legalName, String tradeName,
                                       String taxId, String businessType, String status,
                                       Instant createdAt, Instant updatedAt) {

        public static OrganizationResource from(Organization o) {
            return new OrganizationResource(o.getOrganizationId().toString(), o.getLegalName(),
                    o.getTradeName(), o.getTaxId(), o.getBusinessType().name(),
                    o.getStatus().name(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SiteResource(String siteId, String organizationId, String siteCode, String name,
                               String address, String district, BigDecimal floorAreaM2,
                               BigDecimal contractedPowerKw, String tariffCategory,
                               boolean chargesForDemand, boolean excludesSundaysFromPeak,
                               String status, Instant createdAt, Instant updatedAt) {

        public static SiteResource from(Site s) {
            return new SiteResource(s.getSiteId().toString(), s.getOrganizationId().toString(),
                    s.getSiteCode(), s.getName(), s.getAddress(), s.getDistrict(),
                    s.getFloorAreaM2(), s.getContractedPowerKw(), s.getTariffCategory().name(),
                    s.getTariffCategory().cobraPorPotencia(), s.isExcludesSundaysFromPeak(),
                    s.getStatus().name(), s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ZoneResource(String zoneId, String siteId, String name, String zoneType,
                               boolean operatesOffHours, String status, Instant createdAt) {

        public static ZoneResource from(Zone z) {
            return new ZoneResource(z.getZoneId().toString(), z.getSiteId().toString(), z.getName(),
                    z.getZoneType().name(), z.isOperatesOffHours(), z.getStatus().name(),
                    z.getCreatedAt());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MembershipResource(String membershipId, String organizationId, String userId,
                                     String role, String siteId, String status,
                                     Instant createdAt) {

        public static MembershipResource from(Membership m) {
            return new MembershipResource(m.getMembershipId().toString(),
                    m.getOrganizationId().toString(), m.getUserId().toString(), m.getRole().name(),
                    m.getSiteId() == null ? null : m.getSiteId().toString(), m.getStatus().name(),
                    m.getCreatedAt());
        }
    }

    /** Organizacion con el papel que tiene ahi quien pregunta. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyOrganizationResource(OrganizationResource organization, String role,
                                         String siteId) {

        public static MyOrganizationResource from(Organization o, Membership m) {
            return new MyOrganizationResource(OrganizationResource.from(o), m.getRole().name(),
                    m.getSiteId() == null ? null : m.getSiteId().toString());
        }
    }
}
