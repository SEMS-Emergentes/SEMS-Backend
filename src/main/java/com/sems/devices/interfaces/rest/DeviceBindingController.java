package com.sems.devices.interfaces.rest;

import com.sems.devices.application.DeviceCommandService;
import com.sems.devices.application.DeviceQueryService;
import com.sems.devices.interfaces.rest.resources.DeviceResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Vinculacion de dispositivos con usuarios y hogares. */
@Tag(name = "Device Bindings", description = "Linking and unlinking of devices")
@RestController
@RequestMapping("/api/v1/device-management")
@RequiredArgsConstructor
public class DeviceBindingController {

    private final DeviceCommandService commands;
    private final DeviceQueryService queries;

    @Operation(summary = "Links a device to a user")
    @PostMapping("/devices/{deviceId}/bindings")
    public ResponseEntity<DeviceBindingResource> bind(@PathVariable UUID deviceId,
                                                      @Valid @RequestBody CreateBindingRequest request) {
        var binding = commands.bind(deviceId, UUID.fromString(request.userId()),
                request.siteId() == null || request.siteId().isBlank()
                        ? null : UUID.fromString(request.siteId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceBindingResource.from(binding));
    }

    @Operation(summary = "Lists the bindings of a device")
    @GetMapping("/devices/{deviceId}/bindings")
    public List<DeviceBindingResource> byDevice(@PathVariable UUID deviceId) {
        return queries.bindingsByDevice(deviceId).stream().map(DeviceBindingResource::from).toList();
    }

    @Operation(summary = "Lists the bindings of a user")
    @GetMapping("/users/{userId}/bindings")
    public List<DeviceBindingResource> byUser(@PathVariable UUID userId) {
        return queries.bindingsByUser(userId).stream().map(DeviceBindingResource::from).toList();
    }

    @Operation(summary = "Unlinks a device")
    @PatchMapping("/bindings/{bindingId}/unlink")
    public DeviceBindingResource unlink(@PathVariable UUID bindingId) {
        return DeviceBindingResource.from(commands.unbind(bindingId));
    }
}
