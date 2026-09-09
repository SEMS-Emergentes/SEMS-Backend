package com.sems.alerts.interfaces.rest;

import com.sems.alerts.application.AlertCommandService;
import com.sems.alerts.application.AlertQueryService;
import com.sems.alerts.interfaces.rest.resources.AlertResources.*;
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
 * API REST del bounded context de alertas.
 *
 * <p>Rutas identicas a las del microservicio en Go bajo {@code /api/v1}.
 */
@Tag(name = "Alerts", description = "Alerts, thresholds and notification preferences")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlertController {

    private final AlertCommandService commands;
    private final AlertQueryService queries;

    // ---------------------------------------------------------------- alertas

    @Operation(summary = "Creates an alert")
    @PostMapping("/alerts")
    public ResponseEntity<AlertResponse> create(@Valid @RequestBody CreateAlertRequest r) {
        var alert = commands.createAlert(UUID.fromString(r.userId()), UUID.fromString(r.deviceId()),
                optionalUuid(r.thresholdId()), optionalUuid(r.inactivityRuleId()),
                r.alertType(), r.title(), r.message(), r.severity(), r.status(), r.triggeredAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertResponse.from(alert));
    }

    @Operation(summary = "Lists every alert")
    @GetMapping("/alerts")
    public List<AlertResponse> all() {
        return queries.allAlerts().stream().map(AlertResponse::from).toList();
    }

    @Operation(summary = "Gets an alert by its identifier")
    @GetMapping("/alerts/{id}")
    public AlertResponse byId(@PathVariable UUID id) {
        return AlertResponse.from(queries.alertById(id));
    }

    @Operation(summary = "Changes the status of an alert")
    @PatchMapping("/alerts/{id}/status")
    public AlertResponse updateStatus(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateAlertStatusRequest r) {
        return AlertResponse.from(commands.updateStatus(id, r.status(), r.resolvedAt()));
    }

    @Operation(summary = "Alerts for a user")
    @GetMapping("/users/{userId}/alerts")
    public List<AlertResponse> byUser(@PathVariable UUID userId) {
        return queries.alertsByUser(userId).stream().map(AlertResponse::from).toList();
    }

    // --------------------------------------------------------------- umbrales

    @Operation(summary = "Creates a consumption threshold")
    @PostMapping("/thresholds")
    public ResponseEntity<ThresholdResponse> createThreshold(
            @Valid @RequestBody CreateThresholdRequest r) {
        var threshold = commands.createThreshold(UUID.fromString(r.userId()),
                UUID.fromString(r.deviceId()), r.thresholdName(), r.metric(), r.operator(),
                r.thresholdValue(), r.active());
        return ResponseEntity.status(HttpStatus.CREATED).body(ThresholdResponse.from(threshold));
    }

    @Operation(summary = "A user's thresholds")
    @GetMapping("/users/{userId}/thresholds")
    public List<ThresholdResponse> thresholdsByUser(@PathVariable UUID userId) {
        return queries.thresholdsByUser(userId).stream().map(ThresholdResponse::from).toList();
    }

    // ---------------------------------------------------- reglas de inactividad

    @Operation(summary = "Creates an inactivity rule")
    @PostMapping("/inactivity-rules")
    public ResponseEntity<InactivityRuleResponse> createRule(
            @Valid @RequestBody CreateInactivityRuleRequest r) {
        var rule = commands.createInactivityRule(UUID.fromString(r.userId()),
                UUID.fromString(r.deviceId()), r.ruleName(), r.maxInactiveMinutes(), r.active());
        return ResponseEntity.status(HttpStatus.CREATED).body(InactivityRuleResponse.from(rule));
    }

    @Operation(summary = "A user's inactivity rules")
    @GetMapping("/users/{userId}/inactivity-rules")
    public List<InactivityRuleResponse> rulesByUser(@PathVariable UUID userId) {
        return queries.rulesByUser(userId).stream().map(InactivityRuleResponse::from).toList();
    }

    // ---------------------------------------------- preferencias de notificacion

    @Operation(summary = "Saves a notification preference")
    @PostMapping("/notification-preferences")
    public ResponseEntity<PreferenceResponse> createPreference(
            @Valid @RequestBody CreatePreferenceRequest r) {
        var preference = commands.createPreference(UUID.fromString(r.userId()), r.channel(),
                r.enabled(), r.minSeverity(), r.quietHoursStart(), r.quietHoursEnd());
        return ResponseEntity.status(HttpStatus.CREATED).body(PreferenceResponse.from(preference));
    }

    @Operation(summary = "A user's notification preferences")
    @GetMapping("/users/{userId}/notification-preferences")
    public List<PreferenceResponse> preferencesByUser(@PathVariable UUID userId) {
        return queries.preferencesByUser(userId).stream().map(PreferenceResponse::from).toList();
    }

    // ------------------------------------------------------ reglas de demanda

    @Operation(summary = "Creates a demand-watch rule for a site")
    @PostMapping("/demand-rules")
    public ResponseEntity<DemandRuleResponse> createDemandRule(
            @Valid @RequestBody CreateDemandRuleRequest request) {
        var regla = commands.createDemandRule(UUID.fromString(request.siteId()),
                UUID.fromString(request.userId()), request.ruleName(),
                request.contractedPowerKw(), request.warningPercent(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED).body(DemandRuleResponse.from(regla));
    }

    @Operation(summary = "Active demand rules for a site")
    @GetMapping("/sites/{siteId}/demand-rules")
    public List<DemandRuleResponse> demandRulesBySite(@PathVariable UUID siteId) {
        return commands.demandRulesBySite(siteId).stream().map(DemandRuleResponse::from).toList();
    }

    @Operation(summary = "Evaluates a measured demand and raises the corresponding alerts")
    @PostMapping("/sites/{siteId}/demand-evaluations")
    public List<AlertResponse> evaluateDemand(@PathVariable UUID siteId,
                                              @Valid @RequestBody EvaluateDemandRequest request) {
        return commands.evaluateDemand(siteId, request.demandKw()).stream()
                .map(AlertResponse::from).toList();
    }

    private static UUID optionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}
