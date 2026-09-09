package com.sems.energy.interfaces.rest;

import com.sems.energy.application.EnergyCommandService;
import com.sems.energy.application.EnergyQueryService;
import com.sems.energy.interfaces.rest.resources.EnergyResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Alertas generadas por el propio modulo de monitoreo. */
@Tag(name = "Consumption Alerts", description = "Alerts for consumption outside the expected range")
@RestController
@RequestMapping("/api/v1/consumption-alerts")
@RequiredArgsConstructor
public class ConsumptionAlertController {

    private final EnergyCommandService commands;
    private final EnergyQueryService queries;

    @Operation(summary = "Alerts for a user")
    @GetMapping("/user/{userId}")
    public List<AlertResponse> byUser(@PathVariable String userId) {
        return queries.alertsByUser(userId).stream().map(AlertResponse::from).toList();
    }

    @Operation(summary = "Unread alerts for a user")
    @GetMapping("/user/{userId}/unread")
    public List<AlertResponse> unreadByUser(@PathVariable String userId) {
        return queries.unreadAlertsByUser(userId).stream().map(AlertResponse::from).toList();
    }

    @Operation(summary = "Gets an alert by its identifier")
    @GetMapping("/{alertId}")
    public AlertResponse byId(@PathVariable UUID alertId) {
        return AlertResponse.from(queries.alertById(alertId));
    }

    @Operation(summary = "Marks an alert as read")
    @PatchMapping("/{alertId}/read")
    public AlertResponse markRead(@PathVariable UUID alertId) {
        return AlertResponse.from(commands.markAlertRead(alertId));
    }

    @Operation(summary = "Marks an alert as resolved")
    @PatchMapping("/{alertId}/resolve")
    public AlertResponse resolve(@PathVariable UUID alertId) {
        return AlertResponse.from(commands.resolveAlert(alertId));
    }
}
