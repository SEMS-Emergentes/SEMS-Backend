package com.sems.energy.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sems.energy.application.ConsumptionBreakdownService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reparto del consumo por zona y entre locales (US26 y US39).
 *
 * <p>Las rutas cuelgan de {@code /sites} y {@code /organizations} aunque viven
 * en energia: quien pregunta piensa en el local, no en el modulo que guarda las
 * lecturas. Es el mismo criterio con el que alertas publica las reglas de
 * demanda bajo {@code /sites/{id}/demand-rules}.
 */
@Tag(name = "Consumption Breakdown", description = "Usage split by zone and across sites")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConsumptionBreakdownController {

    private final ConsumptionBreakdownService breakdown;

    @Operation(summary = "Usage of a site split across its zones")
    @PreAuthorize("@accesoOrganizaciones.local(#siteId)")
    @GetMapping("/sites/{siteId}/zone-consumption")
    public ZoneBreakdownResponse byZone(
            @PathVariable UUID siteId,
            @RequestParam(defaultValue = "30") int days) {
        return ZoneBreakdownResponse.from(breakdown.byZone(siteId, days));
    }

    @Operation(summary = "Usage of each site of an organization, for comparison")
    @PreAuthorize("@accesoOrganizaciones.organizacion(#organizationId)")
    @GetMapping("/organizations/{organizationId}/site-consumption")
    public SiteComparisonResponse bySite(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "30") int days) {
        return SiteComparisonResponse.from(breakdown.bySite(organizationId, days));
    }

    // Las respuestas viven aqui porque no las comparte nadie mas; el modulo de
    // energia responde en snake_case, como el resto de sus recursos.

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ZoneShareResource(UUID zoneId, String zoneName, String zoneType, int meterCount,
                                    double kwh, double sharePercent, Double estimatedEnergyCost) {

        static ZoneShareResource from(ConsumptionBreakdownService.ZoneShare z) {
            return new ZoneShareResource(z.zoneId(), z.zoneName(), z.zoneType(), z.meterCount(),
                    z.kwh(), z.sharePercent(), z.estimatedEnergyCost());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ZoneBreakdownResponse(UUID siteId, String siteName, int days,
                                        java.time.Instant from, java.time.Instant to,
                                        double totalKwh, String currency,
                                        java.util.List<ZoneShareResource> zones,
                                        ZoneShareResource unassigned) {

        static ZoneBreakdownResponse from(ConsumptionBreakdownService.ZoneBreakdown b) {
            return new ZoneBreakdownResponse(b.siteId(), b.siteName(), b.days(), b.from(), b.to(),
                    b.totalKwh(), b.currency(),
                    b.zones().stream().map(ZoneShareResource::from).toList(),
                    ZoneShareResource.from(b.unassigned()));
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SiteShareResource(UUID siteId, String siteCode, String siteName,
                                    java.math.BigDecimal contractedPowerKw, String tariffCategory,
                                    int meterCount, double kwh, double sharePercent,
                                    Double estimatedEnergyCost, Double kwhPerContractedKw,
                                    double peakDemandKw) {

        static SiteShareResource from(ConsumptionBreakdownService.SiteShare s) {
            return new SiteShareResource(s.siteId(), s.siteCode(), s.siteName(),
                    s.contractedPowerKw(), s.tariffCategory(), s.meterCount(), s.kwh(),
                    s.sharePercent(), s.estimatedEnergyCost(), s.kwhPerContractedKw(),
                    s.peakDemandKw());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SiteComparisonResponse(UUID organizationId, int days, java.time.Instant from,
                                         java.time.Instant to, double totalKwh, String currency,
                                         java.util.List<SiteShareResource> sites) {

        static SiteComparisonResponse from(ConsumptionBreakdownService.SiteComparison c) {
            return new SiteComparisonResponse(c.organizationId(), c.days(), c.from(), c.to(),
                    c.totalKwh(), c.currency(),
                    c.sites().stream().map(SiteShareResource::from).toList());
        }
    }
}
