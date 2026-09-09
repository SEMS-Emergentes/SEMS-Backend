package com.sems.energy.interfaces.rest;

import com.sems.energy.application.EnergyQueryService;
import com.sems.energy.interfaces.rest.resources.EnergyResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Resumenes de consumo agregados por dispositivo y periodo. */
@Tag(name = "Device Consumptions", description = "Consumption aggregated by device")
@RestController
@RequestMapping("/api/v1/device-consumptions")
@RequiredArgsConstructor
public class DeviceConsumptionController {

    private final EnergyQueryService queries;

    @Operation(summary = "A user's consumption summaries")
    @GetMapping("/user/{userId}")
    public List<ConsumptionResponse> byUser(@PathVariable String userId) {
        return queries.consumptionsByUser(userId).stream().map(ConsumptionResponse::from).toList();
    }

    @Operation(summary = "A user's highest-consuming devices")
    @GetMapping("/user/{userId}/top")
    public List<ConsumptionResponse> topByUser(@PathVariable String userId,
                                               @RequestParam(defaultValue = "10") int limit) {
        return queries.topConsumersByUser(userId, limit).stream()
                .map(ConsumptionResponse::from).toList();
    }

    @Operation(summary = "Gets a summary by its identifier")
    @GetMapping("/{consumptionId}")
    public ConsumptionResponse byId(@PathVariable UUID consumptionId) {
        return ConsumptionResponse.from(queries.consumptionById(consumptionId));
    }
}
