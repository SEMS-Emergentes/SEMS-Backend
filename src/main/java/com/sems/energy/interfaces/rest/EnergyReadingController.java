package com.sems.energy.interfaces.rest;

import com.sems.energy.application.EnergyCommandService;
import com.sems.energy.application.EnergyQueryService;
import com.sems.energy.application.ReadingPricingService;
import com.sems.energy.domain.model.entities.EnergyReading;
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
    private final ReadingPricingService precios;

    /** Cada lectura con su coste, calculado con la tarifa del local. */
    private List<ReadingResponse> conCoste(List<EnergyReading> lecturas) {
        var costes = precios.costOf(lecturas);
        return java.util.stream.IntStream.range(0, lecturas.size())
                .mapToObj(i -> ReadingResponse.from(lecturas.get(i), costes.get(i)))
                .toList();
    }

    @Operation(summary = "Records a new reading")
    @PreAuthorize("@accesoUsuario.esMio(#request.userId())")
    @PostMapping
    public ResponseEntity<ReadingResponse> create(@Valid @RequestBody CreateReadingRequest request) {
        var reading = commands.recordReading(request.userId(), request.meterId(), request.deviceId(),
                request.powerWatts(), request.voltage(), request.current(), request.frequency(),
                request.energyKwh(), request.timestamp(), request.readingType(), request.phase());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReadingResponse.from(reading, precios.costOf(reading)));
    }

    @Operation(summary = "A user's readings, newest first")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/user/{userId}")
    public List<ReadingResponse> byUser(@PathVariable String userId,
                                        @RequestParam(defaultValue = "100") int limit) {
        return conCoste(queries.readingsByUser(userId, limit));
    }

    @Operation(summary = "Readings from a device")
    @PreAuthorize("@accesoEnergia.lecturasDelDispositivo(#deviceId)")
    @GetMapping("/device/{deviceId}")
    public List<ReadingResponse> byDevice(@PathVariable String deviceId,
                                          @RequestParam(defaultValue = "50") int limit,
                                          @RequestParam(defaultValue = "0") int skip) {
        return conCoste(queries.readingsByDevice(deviceId, limit, skip));
    }

    @Operation(summary = "A user's readings within a date range")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/range")
    public List<ReadingResponse> byRange(@RequestParam String userId,
                                         @RequestParam Instant from,
                                         @RequestParam Instant to) {
        return conCoste(queries.readingsByRange(userId, from, to));
    }

    @Operation(summary = "Latest reading from a meter")
    @PreAuthorize("@accesoEnergia.lecturasDelMedidor(#meterId)")
    @GetMapping("/meter/{meterId}/latest")
    public ReadingResponse latestByMeter(@PathVariable String meterId) {
        EnergyReading lectura = queries.latestByMeter(meterId);
        return ReadingResponse.from(lectura, precios.costOf(lectura));
    }

    @Operation(summary = "Gets a reading by its identifier")
    @PreAuthorize("@accesoEnergia.lectura(#readingId)")
    @GetMapping("/{readingId}")
    public ReadingResponse byId(@PathVariable UUID readingId) {
        EnergyReading lectura = queries.readingById(readingId);
        return ReadingResponse.from(lectura, precios.costOf(lectura));
    }
}
