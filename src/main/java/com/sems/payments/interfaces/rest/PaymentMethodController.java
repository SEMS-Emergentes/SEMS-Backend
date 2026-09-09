package com.sems.payments.interfaces.rest;

import com.sems.payments.application.PaymentMethodCommandService;
import com.sems.payments.application.PaymentQueryService;
import com.sems.payments.interfaces.rest.resources.PaymentResources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Medios de pago guardados del usuario. */
@Tag(name = "Payment Methods", description = "Cards saved by the user")
@RestController
@RequestMapping("/api/v1/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodCommandService commands;
    private final PaymentQueryService queries;

    @Operation(summary = "Saves a payment method")
    @PostMapping
    public ResponseEntity<PaymentMethodResponse> register(
            @Valid @RequestBody RegisterPaymentMethodRequest request) {
        var method = commands.register(UUID.fromString(request.userId()), request.type(),
                request.stripePaymentMethodId(), request.isDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentMethodResponse.from(method));
    }

    @Operation(summary = "A user's payment methods")
    @GetMapping("/user/{userId}")
    public List<PaymentMethodResponse> byUser(@PathVariable UUID userId) {
        return queries.methodsByUser(userId).stream().map(PaymentMethodResponse::from).toList();
    }

    @Operation(summary = "Marks a payment method as the default one")
    @PutMapping("/{paymentMethodId}/default")
    public PaymentMethodResponse setDefault(@PathVariable UUID paymentMethodId) {
        return PaymentMethodResponse.from(commands.setDefault(paymentMethodId));
    }

    @Operation(summary = "Deletes a payment method")
    @DeleteMapping("/{paymentMethodId}")
    public ResponseEntity<Void> delete(@PathVariable UUID paymentMethodId) {
        commands.delete(paymentMethodId);
        return ResponseEntity.noContent().build();
    }
}
