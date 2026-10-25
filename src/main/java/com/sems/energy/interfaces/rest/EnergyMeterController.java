package com.sems.energy.interfaces.rest;

import com.sems.energy.application.EnergyCommandService;
import com.sems.energy.application.EnergyQueryService;
import com.sems.energy.interfaces.rest.resources.EnergyResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
    @PostMapping
    public ResponseEntity<MeterResponse> register(@Valid @RequestBody RegisterMeterRequest request) {
        var meter = commands.registerMeter(request.userId(), request.meterSerial(), request.model(),
                request.brand(), request.location(), request.firmwareVersion(), request.maxPowerWatts());
        return ResponseEntity.status(HttpStatus.CREATED).body(MeterResponse.from(meter));
    }

    @Operation(summary = "Lists a user's meters")
    @GetMapping("/user/{userId}")
    public List<MeterResponse> byUser(@PathVariable String userId) {
        return queries.metersByUser(userId).stream().map(MeterResponse::from).toList();
    }

    @Operation(summary = "Unlinks a meter from its account")
    @DeleteMapping("/{meterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable UUID meterId) {
        commands.unlinkMeter(meterId);
    }

    @Operation(summary = "Deactivates a meter")
    @PatchMapping("/{meterId}/deactivate")
    public MeterResponse deactivate(@PathVariable UUID meterId) {
        return MeterResponse.from(commands.deactivateMeter(meterId));
    }

    @Operation(summary = "Gets a meter by its identifier")
    @GetMapping("/{meterId}")
    public MeterResponse byId(@PathVariable UUID meterId) {
        return MeterResponse.from(queries.meterById(meterId));
    }
}
