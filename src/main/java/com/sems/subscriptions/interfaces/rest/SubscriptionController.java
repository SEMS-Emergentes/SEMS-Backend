package com.sems.subscriptions.interfaces.rest;

import com.sems.subscriptions.application.SubscriptionService;
import com.sems.subscriptions.interfaces.rest.resources.SubscriptionResources.*;
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

/**
 * API REST del bounded context de suscripciones.
 *
 * <p>Rutas identicas a las del microservicio en Go. Ojo con el orden de los
 * segmentos: {@code /subscriptions/users/{userId}} debe declararse antes que
 * {@code /subscriptions/{subscriptionId}} para que "users" no se interprete
 * como un identificador.
 */
@Tag(name = "Subscriptions", description = "User plans and subscriptions")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;

    // ---------------------------------------------------------------- planes

    @Operation(summary = "Lists the available plans")
    @GetMapping("/subscription-plans")
    public List<PlanResource> plans() {
        return service.activePlans().stream().map(PlanResource::from).toList();
    }

    @Operation(summary = "Gets a plan by its identifier")
    @GetMapping("/subscription-plans/{planId}")
    public PlanResource planById(@PathVariable UUID planId) {
        return PlanResource.from(service.planById(planId));
    }

    // --------------------------------------------------------- suscripciones

    @Operation(summary = "A user's subscriptions")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/subscriptions/users/{userId}")
    public List<SubscriptionResource> byUser(@PathVariable String userId) {
        return service.subscriptionsByUser(userId).stream().map(SubscriptionResource::from).toList();
    }

    @Operation(summary = "Gets a subscription by its identifier")
    @PreAuthorize("@accesoSuscripciones.suscripcion(#subscriptionId)")
    @GetMapping("/subscriptions/{subscriptionId}")
    public SubscriptionResource byId(@PathVariable UUID subscriptionId) {
        return SubscriptionResource.from(service.subscriptionById(subscriptionId));
    }

    @Operation(summary = "Creates a subscription")
    @PreAuthorize("@accesoUsuario.esMio(#request.userId())")
    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionResource> create(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        var subscription = service.create(request.userId(), UUID.fromString(request.planId()), null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SubscriptionResource.from(subscription));
    }

    @Operation(summary = "Cancels a subscription")
    @PreAuthorize("@accesoSuscripciones.suscripcion(#subscriptionId)")
    @PatchMapping("/subscriptions/{subscriptionId}/cancel")
    public SubscriptionResource cancel(@PathVariable UUID subscriptionId) {
        return SubscriptionResource.from(service.cancel(subscriptionId));
    }

    @Operation(summary = "Changes the plan of a subscription")
    @PreAuthorize("@accesoSuscripciones.suscripcion(#subscriptionId)")
    @PatchMapping("/subscriptions/{subscriptionId}/change-plan")
    public SubscriptionResource changePlan(@PathVariable UUID subscriptionId,
                                           @Valid @RequestBody ChangePlanRequest request) {
        return SubscriptionResource.from(
                service.changePlan(subscriptionId, UUID.fromString(request.newPlanId())));
    }
}
