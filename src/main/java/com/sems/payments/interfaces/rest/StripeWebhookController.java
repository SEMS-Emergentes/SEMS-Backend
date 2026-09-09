package com.sems.payments.interfaces.rest;

import com.sems.payments.application.WebhookCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Punto de entrada de los avisos de Stripe.
 *
 * <p>El cuerpo se recibe como texto crudo a proposito: la verificacion de firma
 * se calcula sobre los bytes exactos que envio Stripe, y cualquier
 * deserializacion intermedia los alteraria y haria fallar la comprobacion.
 */
@Tag(name = "Webhooks", description = "Notifications from the payment provider")
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final WebhookCommandService webhooks;

    @Operation(summary = "Receives a Stripe event")
    @PostMapping("/stripe")
    public ResponseEntity<Map<String, Object>> handleStripe(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "missing Stripe-Signature header"));
        }
        boolean processed = webhooks.handleStripe(payload, signature);
        // Siempre 200: un duplicado no es un error y responder otra cosa haria
        // que Stripe siguiera reintentando indefinidamente.
        return ResponseEntity.ok(Map.of("received", true, "processed", processed));
    }
}
