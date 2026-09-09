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

/**
 * API REST del bounded context de dispositivos.
 *
 * <p>Las rutas son identicas a las del microservicio original bajo
 * {@code /api/v1/device-management}. Mantenerlas iguales es lo que permite
 * cambiar el backend sin tocar el frontend.
 */
@Tag(name = "Device Management", description = "Device registration, linking and configuration")
@RestController
@RequestMapping("/api/v1/device-management")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceCommandService commands;
    private final DeviceQueryService queries;

    @Operation(summary = "Registers a new device")
    @PostMapping("/devices")
    public ResponseEntity<DeviceResource> create(@Valid @RequestBody CreateDeviceRequest request) {
        var device = commands.register(
                request.externalDeviceCode(), UUID.fromString(request.userId()),
                UUID.fromString(request.siteId()), optionalId(request.zoneId()),
                request.deviceName(), request.deviceType(),
                request.brand(), request.model(), request.connectionProtocol());
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceResource.from(device));
    }

    @Operation(summary = "Lists every device")
    @GetMapping("/devices")
    public List<DeviceResource> list() {
        return queries.allDevices().stream().map(DeviceResource::from).toList();
    }

    @Operation(summary = "Gets a device by its identifier")
    @GetMapping("/devices/{deviceId}")
    public DeviceResource byId(@PathVariable UUID deviceId) {
        return DeviceResource.from(queries.deviceById(deviceId));
    }

    @Operation(summary = "Lists a user's devices")
    @GetMapping("/users/{userId}/devices")
    public List<DeviceResource> byUser(@PathVariable UUID userId) {
        return queries.devicesByUser(userId).stream().map(DeviceResource::from).toList();
    }

    @Operation(summary = "Updates the editable details of a device")
    @PutMapping("/devices/{deviceId}")
    public DeviceResource update(@PathVariable UUID deviceId,
                                 @Valid @RequestBody UpdateDeviceRequest request) {
        return DeviceResource.from(commands.update(deviceId, request.deviceName(),
                request.deviceType(), request.brand(), request.model(),
                request.connectionProtocol(), optionalId(request.zoneId())));
    }

    @Operation(summary = "Devices installed at a site")
    @GetMapping("/sites/{siteId}/devices")
    public List<DeviceResource> bySite(@PathVariable UUID siteId) {
        return queries.devicesBySite(siteId).stream().map(DeviceResource::from).toList();
    }

    @Operation(summary = "Devices in a specific zone")
    @GetMapping("/zones/{zoneId}/devices")
    public List<DeviceResource> byZone(@PathVariable UUID zoneId) {
        return queries.devicesByZone(zoneId).stream().map(DeviceResource::from).toList();
    }

    /** Convierte un identificador opcional del cuerpo en UUID. */
    private static UUID optionalId(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    @Operation(summary = "Changes the status of a device")
    @PatchMapping("/devices/{deviceId}/status")
    public DeviceResource changeStatus(@PathVariable UUID deviceId,
                                       @Valid @RequestBody UpdateDeviceStatusRequest request) {
        return DeviceResource.from(commands.changeStatus(deviceId, request.status()));
    }

    @Operation(summary = "Deletes a device (soft delete)")
    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> remove(@PathVariable UUID deviceId) {
        commands.remove(deviceId);
        return ResponseEntity.noContent().build();
    }
}
