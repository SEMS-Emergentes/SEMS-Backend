package com.sems.payments.interfaces.rest;

import com.sems.payments.application.PaymentCommandService;
import com.sems.payments.application.PaymentQueryService;
import com.sems.payments.interfaces.rest.resources.PaymentResources.*;
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

/** Cobros y sesiones de pago. */
@Tag(name = "Payments", description = "Charges, receipts and Stripe Checkout sessions")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCommandService commands;
    private final PaymentQueryService queries;

    @Operation(summary = "Charges a saved card")
    @PreAuthorize("@accesoUsuario.esMio(#request.userId())")
    @PostMapping("/process")
    public ResponseEntity<ProcessPaymentResponse> process(
            @Valid @RequestBody ProcessPaymentRequest request) {
        var result = commands.process(UUID.fromString(request.subscriptionId()),
                UUID.fromString(request.userId()), UUID.fromString(request.paymentMethodId()),
                request.amount(), request.currency(), request.paymentMethod());
        var body = new ProcessPaymentResponse(PaymentResponse.from(result.payment()),
                result.invoice() == null ? null : InvoiceResponse.from(result.invoice()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * Abre una sesion de Stripe Checkout.
     *
     * <p>Devuelve la URL a la que la aplicacion web debe redirigir. Los datos de
     * la tarjeta se introducen en la pagina de Stripe, nunca en SEMS.
     */
    @Operation(summary = "Creates a Stripe Checkout session")
    @PreAuthorize("@accesoUsuario.esMio(#request.userId())")
    @PostMapping("/checkout-session")
    public CheckoutSessionResponse createCheckoutSession(
            @Valid @RequestBody CreateCheckoutRequest request) {
        var session = commands.createCheckoutSession(
                UUID.fromString(request.userId()),
                request.subscriptionId() == null || request.subscriptionId().isBlank()
                        ? null : UUID.fromString(request.subscriptionId()),
                request.planName(), request.amount(), request.currency(),
                request.successUrl(), request.cancelUrl());
        return new CheckoutSessionResponse(session.sessionId(), session.url());
    }

    @Operation(summary = "A user's payments")
    @PreAuthorize("@accesoUsuario.esMio(#userId)")
    @GetMapping("/user/{userId}")
    public List<PaymentResponse> byUser(@PathVariable UUID userId) {
        return queries.paymentsByUser(userId).stream().map(PaymentResponse::from).toList();
    }

    @Operation(summary = "Payments of a subscription")
    @PreAuthorize("@accesoSuscripciones.suscripcion(#subscriptionId)")
    @GetMapping("/subscription/{subscriptionId}")
    public List<PaymentResponse> bySubscription(@PathVariable UUID subscriptionId) {
        return queries.paymentsBySubscription(subscriptionId).stream()
                .map(PaymentResponse::from).toList();
    }

    @Operation(summary = "Gets a payment by its identifier")
    @PreAuthorize("@accesoPagos.pago(#paymentId)")
    @GetMapping("/{paymentId}")
    public PaymentResponse byId(@PathVariable UUID paymentId) {
        return PaymentResponse.from(queries.paymentById(paymentId));
    }
}
