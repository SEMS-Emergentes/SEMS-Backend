package com.sems.energy.interfaces.rest;

import com.sems.energy.application.DeviceConsumptionService;
import com.sems.energy.application.EnergyQueryService;
import com.sems.energy.interfaces.rest.resources.EnergyResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Resumenes de consumo agregados por dispositivo y periodo.
 *
 * <p>Los resumenes por usuario se calculan con las lecturas del periodo pedido;
 * no son filas guardadas, de modo que su {@code id} identifica la respuesta y
 * no un recurso que se pueda volver a pedir por separado. Quien pinte la
 * tarjeta debe agrupar por {@code device_id}, que es la clave estable.
 */
@Tag(name = "Device Consumptions", description = "Consumption aggregated by device")
@RestController
@RequestMapping("/api/v1/device-consumptions")
@RequiredArgsConstructor
public class DeviceConsumptionController {

    private final DeviceConsumptionService consumos;
    private final EnergyQueryService queries;

    @Operation(summary = "A user's consumption per device, including what was measured "
            + "with no device assigned")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/user/{userId}")
    public List<ConsumptionResponse> byUser(@PathVariable String userId,
                                            @RequestParam(defaultValue = "30") int days) {
        return consumos.byUser(userId, days).stream().map(ConsumptionResponse::from).toList();
    }

    @Operation(summary = "A user's highest-consuming devices; what was measured with no "
            + "device assigned is left out, since it is not a device")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/user/{userId}/top")
    public List<ConsumptionResponse> topByUser(@PathVariable String userId,
                                               @RequestParam(defaultValue = "10") int limit,
                                               @RequestParam(defaultValue = "30") int days) {
        return consumos.topByUser(userId, days, limit).stream()
                .map(ConsumptionResponse::from).toList();
    }

    @Operation(summary = "Gets a stored summary by its identifier")
    @PreAuthorize("@accesoEnergia.consumo(#consumptionId)")
    @GetMapping("/{consumptionId}")
    public ConsumptionResponse byId(@PathVariable UUID consumptionId) {
        return ConsumptionResponse.from(queries.consumptionById(consumptionId));
    }
}
