package com.sems.devices.interfaces.rest;

import com.sems.devices.application.DeviceCommandService;
import com.sems.devices.application.DeviceQueryService;
import com.sems.devices.interfaces.rest.resources.DeviceResources.*;
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

/** Bitacora de hechos de cada dispositivo. */
@Tag(name = "Device Events", description = "Device event history")
@RestController
@RequestMapping("/api/v1/device-management")
@RequiredArgsConstructor
public class DeviceEventController {

    private final DeviceCommandService commands;
    private final DeviceQueryService queries;

    @Operation(summary = "Records a device event")
    @PreAuthorize("@accesoDispositivos.dispositivo(#deviceId)")
    @PostMapping("/devices/{deviceId}/events")
    public ResponseEntity<DeviceEventResource> create(@PathVariable UUID deviceId,
                                                      @Valid @RequestBody CreateEventRequest request) {
        var event = commands.recordEvent(deviceId, request.eventType(),
                request.description(), request.occurredAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceEventResource.from(event));
    }

    @Operation(summary = "Lists a device's events, newest first")
    @PreAuthorize("@accesoDispositivos.dispositivo(#deviceId)")
    @GetMapping("/devices/{deviceId}/events")
    public List<DeviceEventResource> byDevice(@PathVariable UUID deviceId) {
        return queries.eventsByDevice(deviceId).stream().map(DeviceEventResource::from).toList();
    }
}
