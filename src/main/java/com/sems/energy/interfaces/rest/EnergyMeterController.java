package com.sems.energy.interfaces.rest;

import com.sems.energy.application.EnergyCommandService;
import com.sems.energy.application.EnergyQueryService;
import com.sems.energy.interfaces.rest.resources.EnergyResources.*;
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

/** Medidores EOS vinculados a cada usuario. */
@Tag(name = "Energy Meters", description = "Registration and management of smart meters")
@RestController
@RequestMapping("/api/v1/energy-meters")
@RequiredArgsConstructor
public class EnergyMeterController {

    private final EnergyCommandService commands;
    private final EnergyQueryService queries;

    @Operation(summary = "Registers a new meter")
    @PreAuthorize("@accesoUsuario.esMio(#request.userId())")
    @PostMapping
    public ResponseEntity<MeterResponse> register(@Valid @RequestBody RegisterMeterRequest request) {
        var meter = commands.registerMeter(request.userId(), request.siteId(), request.zoneId(),
                request.meterSerial(), request.model(), request.brand(), request.location(),
                request.firmwareVersion(), request.maxPowerWatts());
        return ResponseEntity.status(HttpStatus.CREATED).body(MeterResponse.from(meter));
    }

    @Operation(summary = "Lists a user's meters")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/user/{userId}")
    public List<MeterResponse> byUser(@PathVariable String userId) {
        return queries.metersByUser(userId).stream().map(MeterResponse::from).toList();
    }

    @Operation(summary = "Lists the meters installed in a site")
    @PreAuthorize("@accesoOrganizaciones.local(#siteId)")
    @GetMapping("/site/{siteId}")
    public List<MeterResponse> bySite(@PathVariable String siteId) {
        return queries.metersBySite(siteId).stream().map(MeterResponse::from).toList();
    }

    @Operation(summary = "Lists the meters of a zone")
    @PreAuthorize("@accesoOrganizaciones.zona(#zoneId)")
    @GetMapping("/zone/{zoneId}")
    public List<MeterResponse> byZone(@PathVariable String zoneId) {
        return queries.metersByZone(zoneId).stream().map(MeterResponse::from).toList();
    }

    @Operation(summary = "Reassigns a meter to another zone of the same site")
    @PreAuthorize("@accesoEnergia.medidor(#meterId)")
    @PatchMapping("/{meterId}/zone")
    public MeterResponse reassignZone(@PathVariable UUID meterId,
                                      @Valid @RequestBody ReassignMeterZoneRequest request) {
        return MeterResponse.from(commands.reassignMeterZone(meterId, request.zoneId()));
    }

    @Operation(summary = "Unlinks a meter from its account")
    @PreAuthorize("@accesoEnergia.medidor(#meterId)")
    @DeleteMapping("/{meterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable UUID meterId) {
        commands.unlinkMeter(meterId);
    }

    @Operation(summary = "Deactivates a meter")
    @PreAuthorize("@accesoEnergia.medidor(#meterId)")
    @PatchMapping("/{meterId}/deactivate")
    public MeterResponse deactivate(@PathVariable UUID meterId) {
        return MeterResponse.from(commands.deactivateMeter(meterId));
    }

    @Operation(summary = "Gets a meter by its identifier")
    @PreAuthorize("@accesoEnergia.medidor(#meterId)")
    @GetMapping("/{meterId}")
    public MeterResponse byId(@PathVariable UUID meterId) {
        return MeterResponse.from(queries.meterById(meterId));
    }
}
