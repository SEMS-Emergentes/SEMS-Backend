package com.sems.organizations.interfaces.rest;

import com.sems.organizations.application.OrganizationCommandService;
import com.sems.organizations.application.OrganizationQueryService;
import com.sems.organizations.interfaces.rest.resources.OrganizationResources.*;
import com.sems.shared.errors.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** API REST de organizaciones: la cadena, sus locales y quien tiene acceso. */
@Tag(name = "Organizations", description = "Chains, sites and access permissions")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationCommandService commands;
    private final OrganizationQueryService queries;

    @Operation(summary = "Registers a chain and makes its creator the administrator")
    @PostMapping("/organizations")
    public ResponseEntity<OrganizationResource> create(
            @Valid @RequestBody CreateOrganizationRequest request) {
        var organizacion = commands.register(request.legalName(), request.tradeName(),
                request.taxId(), request.businessType(),
                parseId(request.ownerUserId(), "owner_user_id"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrganizationResource.from(organizacion));
    }

    @Operation(summary = "Organizations a person belongs to, with their role")
    @GetMapping("/users/{userId}/organizations")
    public List<MyOrganizationResource> mine(@PathVariable String userId) {
        return queries.listMine(parseId(userId, "user_id")).stream()
                .map(e -> MyOrganizationResource.from(e.getKey(), e.getValue()))
                .toList();
    }

    @Operation(summary = "Gets an organization")
    @GetMapping("/organizations/{organizationId}")
    public OrganizationResource get(@PathVariable String organizationId) {
        return OrganizationResource.from(queries.get(parseId(organizationId, "organization_id")));
    }

    @Operation(summary = "Updates the organization details")
    @PutMapping("/organizations/{organizationId}")
    public OrganizationResource update(@PathVariable String organizationId,
                                       @Valid @RequestBody UpdateOrganizationRequest request) {
        return OrganizationResource.from(commands.update(parseId(organizationId, "organization_id"),
                request.legalName(), request.tradeName(), request.businessType()));
    }

    // ----------------------------------------------------------------- locales

    @Operation(summary = "Registers a site in the organization")
    @PostMapping("/organizations/{organizationId}/sites")
    public ResponseEntity<SiteResource> createSite(@PathVariable String organizationId,
                                                   @Valid @RequestBody CreateSiteRequest request) {
        var local = commands.registerSite(parseId(organizationId, "organization_id"),
                request.siteCode(), request.name(), request.address(), request.district(),
                request.floorAreaM2(), request.contractedPowerKw(), request.tariffCategory(),
                request.excludesSundaysFromPeak());
        return ResponseEntity.status(HttpStatus.CREATED).body(SiteResource.from(local));
    }

    @Operation(summary = "Active sites of the organization")
    @GetMapping("/organizations/{organizationId}/sites")
    public List<SiteResource> listSites(@PathVariable String organizationId) {
        return queries.listSites(parseId(organizationId, "organization_id")).stream()
                .map(SiteResource::from).toList();
    }

    @Operation(summary = "People with access to the organization")
    @GetMapping("/organizations/{organizationId}/members")
    public List<MembershipResource> listMembers(@PathVariable String organizationId) {
        return queries.listMemberships(parseId(organizationId, "organization_id")).stream()
                .map(MembershipResource::from).toList();
    }

    @Operation(summary = "Grants access to a person, or changes their existing access")
    @PostMapping("/organizations/{organizationId}/members")
    public ResponseEntity<MembershipResource> grantMembership(
            @PathVariable String organizationId, @Valid @RequestBody GrantMembershipRequest request) {
        var vinculo = commands.grantMembership(parseId(organizationId, "organization_id"),
                parseId(request.userId(), "user_id"), request.role(),
                request.siteId() == null || request.siteId().isBlank()
                        ? null : parseId(request.siteId(), "site_id"));
        return ResponseEntity.status(HttpStatus.CREATED).body(MembershipResource.from(vinculo));
    }

    @Operation(summary = "Revokes a person's access")
    @DeleteMapping("/organizations/{organizationId}/members/{membershipId}")
    public ResponseEntity<Void> revokeMembership(@PathVariable String organizationId,
                                                 @PathVariable String membershipId) {
        commands.revokeMembership(parseId(membershipId, "membership_id"));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ zonas

    @Operation(summary = "Gets a site")
    @GetMapping("/sites/{siteId}")
    public SiteResource getSite(@PathVariable String siteId) {
        return SiteResource.from(queries.getSite(parseId(siteId, "site_id")));
    }

    @Operation(summary = "Updates a site")
    @PutMapping("/sites/{siteId}")
    public SiteResource updateSite(@PathVariable String siteId,
                                   @Valid @RequestBody UpdateSiteRequest request) {
        return SiteResource.from(commands.updateSite(parseId(siteId, "site_id"), request.name(),
                request.address(), request.district(), request.floorAreaM2(),
                request.contractedPowerKw(), request.tariffCategory(),
                request.excludesSundaysFromPeak()));
    }

    @Operation(summary = "Archives a site")
    @DeleteMapping("/sites/{siteId}")
    public ResponseEntity<Void> archiveSite(@PathVariable String siteId) {
        commands.archiveSite(parseId(siteId, "site_id"));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Active zones of the site")
    @GetMapping("/sites/{siteId}/zones")
    public List<ZoneResource> listZones(@PathVariable String siteId) {
        return queries.listZones(parseId(siteId, "site_id")).stream().map(ZoneResource::from)
                .toList();
    }

    @Operation(summary = "Creates a zone within the site")
    @PostMapping("/sites/{siteId}/zones")
    public ResponseEntity<ZoneResource> createZone(@PathVariable String siteId,
                                                   @Valid @RequestBody CreateZoneRequest request) {
        var zona = commands.registerZone(parseId(siteId, "site_id"), request.name(),
                request.zoneType(), request.operatesOffHours());
        return ResponseEntity.status(HttpStatus.CREATED).body(ZoneResource.from(zona));
    }

    @Operation(summary = "Updates a zone")
    @PutMapping("/zones/{zoneId}")
    public ZoneResource updateZone(@PathVariable String zoneId,
                                   @Valid @RequestBody UpdateZoneRequest request) {
        return ZoneResource.from(commands.updateZone(parseId(zoneId, "zone_id"), request.name(),
                request.zoneType(), request.operatesOffHours()));
    }

    @Operation(summary = "Archives a zone")
    @DeleteMapping("/zones/{zoneId}")
    public ResponseEntity<Void> archiveZone(@PathVariable String zoneId) {
        commands.archiveZone(parseId(zoneId, "zone_id"));
        return ResponseEntity.noContent().build();
    }

    /**
     * Convierte el identificador de la ruta o del cuerpo en UUID.
     *
     * <p>Sin esto, un identificador mal formado provoca una excepcion de formato
     * que sale como 500, cuando en realidad la peticion viene mal: es un 400.
     */
    static UUID parseId(String value, String campo) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw AppException.validation(campo + " is not a valid identifier");
        }
    }
}
