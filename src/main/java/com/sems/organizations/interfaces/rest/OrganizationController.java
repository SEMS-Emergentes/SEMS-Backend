package com.sems.organizations.interfaces.rest;

import com.sems.iam.interfaces.acl.IamAcl;
import com.sems.organizations.application.OrganizationCommandService;
import com.sems.organizations.application.OrganizationQueryService;
import com.sems.organizations.interfaces.rest.resources.OrganizationResources.*;
import com.sems.shared.errors.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
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
    // La unica puerta por la que este modulo pregunta por un usuario.
    private final IamAcl iam;

    @Operation(summary = "Registers a chain and makes its creator the administrator")
    @PreAuthorize("@accesoUsuario.esMio(#request.ownerUserId())")
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
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/users/{userId}/organizations")
    public List<MyOrganizationResource> mine(@PathVariable String userId) {
        return queries.listMine(parseId(userId, "user_id")).stream()
                .map(e -> MyOrganizationResource.from(e.getKey(), e.getValue()))
                .toList();
    }

    @Operation(summary = "Gets an organization")
    @PreAuthorize("@accesoOrganizaciones.organizacion(#organizationId)")
    @GetMapping("/organizations/{organizationId}")
    public OrganizationResource get(@PathVariable String organizationId) {
        return OrganizationResource.from(queries.get(parseId(organizationId, "organization_id")));
    }

    @Operation(summary = "Updates the organization details")
    @PreAuthorize("@accesoOrganizaciones.administraOrganizacion(#organizationId)")
    @PutMapping("/organizations/{organizationId}")
    public OrganizationResource update(@PathVariable String organizationId,
                                       @Valid @RequestBody UpdateOrganizationRequest request) {
        return OrganizationResource.from(commands.update(parseId(organizationId, "organization_id"),
                request.legalName(), request.tradeName(), request.businessType()));
    }

    // ----------------------------------------------------------------- locales

    @Operation(summary = "Registers a site in the organization")
    @PreAuthorize("@accesoOrganizaciones.administraOrganizacion(#organizationId)")
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
    @PreAuthorize("@accesoOrganizaciones.organizacion(#organizationId)")
    @GetMapping("/organizations/{organizationId}/sites")
    public List<SiteResource> listSites(@PathVariable String organizationId) {
        return queries.listSites(parseId(organizationId, "organization_id")).stream()
                .map(SiteResource::from).toList();
    }

    @Operation(summary = "People with access to the organization")
    @PreAuthorize("@accesoOrganizaciones.organizacion(#organizationId)")
    @GetMapping("/organizations/{organizationId}/members")
    public List<MembershipResource> listMembers(@PathVariable String organizationId) {
        return queries.listMemberships(parseId(organizationId, "organization_id")).stream()
                .map(m -> MembershipResource.from(m, iam.emailOf(m.getUserId()).orElse(null)))
                .toList();
    }

    @Operation(summary = "Grants access to a person, or changes their existing access")
    @PreAuthorize("@accesoOrganizaciones.administraOrganizacion(#organizationId)")
    @PostMapping("/organizations/{organizationId}/members")
    public ResponseEntity<MembershipResource> grantMembership(
            @PathVariable String organizationId, @Valid @RequestBody GrantMembershipRequest request) {
        // El correo se traduce a identificador aqui, en la frontera: el modulo de
        // organizaciones sigue trabajando con identificadores y no sabe que IAM
        // guarda correos.
        UUID userId = iam.idOf(request.email())
                .orElseThrow(() -> AppException.notFound(
                        "No user is registered with '" + request.email() + "'"));
        var vinculo = commands.grantMembership(parseId(organizationId, "organization_id"),
                userId, request.role(),
                request.siteId() == null || request.siteId().isBlank()
                        ? null : parseId(request.siteId(), "site_id"));
        // Se devuelve el correo tal como esta guardado, no tal como vino: si el
        // administrador lo escribio con mayusculas, la respuesta no debe
        // sugerir que se guardo asi.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MembershipResource.from(vinculo, iam.emailOf(userId).orElse(null)));
    }

    @Operation(summary = "Revokes a person's access")
    @PreAuthorize("@accesoOrganizaciones.administraOrganizacion(#organizationId)")
    @DeleteMapping("/organizations/{organizationId}/members/{membershipId}")
    public ResponseEntity<Void> revokeMembership(@PathVariable String organizationId,
                                                 @PathVariable String membershipId) {
        commands.revokeMembership(parseId(membershipId, "membership_id"));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ zonas

    @Operation(summary = "Gets a site")
    @PreAuthorize("@accesoOrganizaciones.local(#siteId)")
    @GetMapping("/sites/{siteId}")
    public SiteResource getSite(@PathVariable String siteId) {
        return SiteResource.from(queries.getSite(parseId(siteId, "site_id")));
    }

    @Operation(summary = "Updates a site")
    @PreAuthorize("@accesoOrganizaciones.modificaLocal(#siteId)")
    @PutMapping("/sites/{siteId}")
    public SiteResource updateSite(@PathVariable String siteId,
                                   @Valid @RequestBody UpdateSiteRequest request) {
        return SiteResource.from(commands.updateSite(parseId(siteId, "site_id"), request.name(),
                request.address(), request.district(), request.floorAreaM2(),
                request.contractedPowerKw(), request.tariffCategory(),
                request.excludesSundaysFromPeak()));
    }

    @Operation(summary = "Archives a site")
    @PreAuthorize("@accesoOrganizaciones.modificaLocal(#siteId)")
    @DeleteMapping("/sites/{siteId}")
    public ResponseEntity<Void> archiveSite(@PathVariable String siteId) {
        commands.archiveSite(parseId(siteId, "site_id"));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Active zones of the site")
    @PreAuthorize("@accesoOrganizaciones.local(#siteId)")
    @GetMapping("/sites/{siteId}/zones")
    public List<ZoneResource> listZones(@PathVariable String siteId) {
        return queries.listZones(parseId(siteId, "site_id")).stream().map(ZoneResource::from)
                .toList();
    }

    @Operation(summary = "Creates a zone within the site")
    @PreAuthorize("@accesoOrganizaciones.modificaLocal(#siteId)")
    @PostMapping("/sites/{siteId}/zones")
    public ResponseEntity<ZoneResource> createZone(@PathVariable String siteId,
                                                   @Valid @RequestBody CreateZoneRequest request) {
        var zona = commands.registerZone(parseId(siteId, "site_id"), request.name(),
                request.zoneType(), request.operatesOffHours());
        return ResponseEntity.status(HttpStatus.CREATED).body(ZoneResource.from(zona));
    }

    @Operation(summary = "Updates a zone")
    @PreAuthorize("@accesoOrganizaciones.modificaZona(#zoneId)")
    @PutMapping("/zones/{zoneId}")
    public ZoneResource updateZone(@PathVariable String zoneId,
                                   @Valid @RequestBody UpdateZoneRequest request) {
        return ZoneResource.from(commands.updateZone(parseId(zoneId, "zone_id"), request.name(),
                request.zoneType(), request.operatesOffHours()));
    }

    @Operation(summary = "Archives a zone")
    @PreAuthorize("@accesoOrganizaciones.modificaZona(#zoneId)")
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
