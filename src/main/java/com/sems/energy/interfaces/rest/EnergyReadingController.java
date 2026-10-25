package com.sems.energy.interfaces.rest;

import com.sems.energy.application.EnergyCommandService;
import com.sems.energy.application.EnergyQueryService;
import com.sems.energy.interfaces.rest.resources.EnergyResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Lecturas individuales enviadas por los medidores. */
@Tag(name = "Energy Readings", description = "History of electrical measurements")
@RestController
@RequestMapping("/api/v1/energy-readings")
@RequiredArgsConstructor
public class EnergyReadingController {

    private final EnergyCommandService commands;
    private final EnergyQueryService queries;

    @Operation(summary = "Records a new reading")
    @PreAuthorize("@accesoUsuario.esMio(#request.userId())")
    @PostMapping
    public ResponseEntity<ReadingResponse> create(@Valid @RequestBody CreateReadingRequest request) {
        var reading = commands.recordReading(request.userId(), request.meterId(), request.deviceId(),
                request.powerWatts(), request.voltage(), request.current(), request.frequency(),
                request.energyKwh(), request.timestamp(), request.readingType(), request.phase());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReadingResponse.from(reading));
    }

    @Operation(summary = "A user's readings, newest first")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/user/{userId}")
    public List<ReadingResponse> byUser(@PathVariable String userId,
                                        @RequestParam(defaultValue = "100") int limit) {
        return queries.readingsByUser(userId, limit).stream().map(ReadingResponse::from).toList();
    }

    @Operation(summary = "Readings from a device")
    @PreAuthorize("@accesoEnergia.lecturasDelDispositivo(#deviceId)")
    @GetMapping("/device/{deviceId}")
    public List<ReadingResponse> byDevice(@PathVariable String deviceId,
                                          @RequestParam(defaultValue = "50") int limit,
                                          @RequestParam(defaultValue = "0") int skip) {
        return queries.readingsByDevice(deviceId, limit, skip).stream()
                .map(ReadingResponse::from).toList();
    }

    @Operation(summary = "A user's readings within a date range")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/range")
    public List<ReadingResponse> byRange(@RequestParam String userId,
                                         @RequestParam Instant from,
                                         @RequestParam Instant to) {
        return queries.readingsByRange(userId, from, to).stream().map(ReadingResponse::from).toList();
    }

    @Operation(summary = "Latest reading from a meter")
    @PreAuthorize("@accesoEnergia.lecturasDelMedidor(#meterId)")
    @GetMapping("/meter/{meterId}/latest")
    public ReadingResponse latestByMeter(@PathVariable String meterId) {
        return ReadingResponse.from(queries.latestByMeter(meterId));
    }

    @Operation(summary = "Gets a reading by its identifier")
    @PreAuthorize("@accesoEnergia.lectura(#readingId)")
    @GetMapping("/{readingId}")
    public ReadingResponse byId(@PathVariable UUID readingId) {
        return ReadingResponse.from(queries.readingById(readingId));
    }
}
