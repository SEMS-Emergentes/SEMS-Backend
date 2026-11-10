package com.sems.subscriptions.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sems.payments.interfaces.acl.PaymentsAcl;
import com.sems.subscriptions.domain.model.entities.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

/**
 * Contrato JSON del modulo de suscripciones.
 *
 * <p><b>Es un contrato mixto y hay que respetarlo tal cual.</b> El servicio en
 * Go leia los cuerpos con etiquetas snake_case, pero devolvia las entidades
 * directamente, sin etiquetas, de modo que Go serializaba usando el nombre del
 * campo en PascalCase. El frontend esta escrito contra eso y lo documenta en
 * {@code subscriptions.service.ts}.
 *
 * <p>Por eso: <b>peticiones en snake_case, respuestas en PascalCase</b>.
 */
public final class SubscriptionResources {

    private SubscriptionResources() {
    }

    // ----------------------------------------------- peticiones (snake_case)

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateSubscriptionRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String planId,
            String stripeCustomerId) {
    }

    /**
     * Compra de un plan. La tarjeta solo hace falta si el plan cuesta; el
     * importe no viaja en la peticion porque lo fija el plan guardado.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PurchaseRequest(
            @NotBlank(message = "is required") String userId,
            @NotBlank(message = "is required") String planId,
            String cardNumber,
            Integer expMonth,
            Integer expYear,
            String cvc) {

        // Sin esto, cualquier log de la peticion escribiria el numero y el CVC.
        @Override
        public String toString() {
            return "PurchaseRequest[userId=" + userId + ", planId=" + planId + ", card=****]";
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ChangePlanRequest(
            @NotBlank(message = "is required") String newPlanId) {
    }

    // --------------------------------------------- respuestas (PascalCase)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanFeatureResource(
            @JsonProperty("FeatureID") String featureId,
            @JsonProperty("PlanID") String planId,
            @JsonProperty("FeatureCode") String featureCode,
            @JsonProperty("FeatureName") String featureName,
            @JsonProperty("FeatureValue") String featureValue,
            @JsonProperty("CreatedAt") Instant createdAt) {

        public static PlanFeatureResource from(PlanFeature f) {
            return new PlanFeatureResource(f.getFeatureId().toString(), f.getPlanId().toString(),
                    f.getFeatureCode(), f.getFeatureName(), f.getFeatureValue(), f.getCreatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanResource(
            @JsonProperty("PlanID") String planId,
            @JsonProperty("Name") String name,
            @JsonProperty("Description") String description,
            @JsonProperty("Price") double price,
            @JsonProperty("Currency") String currency,
            @JsonProperty("BillingPeriod") String billingPeriod,
            @JsonProperty("Active") boolean active,
            @JsonProperty("CreatedAt") Instant createdAt,
            @JsonProperty("PlanFeatures") List<PlanFeatureResource> planFeatures) {

        public static PlanResource from(SubscriptionPlan p) {
            return new PlanResource(p.getPlanId().toString(), p.getName(), p.getDescription(),
                    p.getPrice(), p.getCurrency(), p.getBillingPeriod(), p.isActive(),
                    p.getCreatedAt(),
                    p.getPlanFeatures().stream().map(PlanFeatureResource::from).toList());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubscriptionResource(
            @JsonProperty("SubscriptionID") String subscriptionId,
            @JsonProperty("UserID") String userId,
            @JsonProperty("PlanID") String planId,
            @JsonProperty("Status") String status,
            @JsonProperty("StartDate") Instant startDate,
            @JsonProperty("EndDate") Instant endDate,
            @JsonProperty("StripeSubscriptionID") String stripeSubscriptionId,
            @JsonProperty("CreatedAt") Instant createdAt) {

        public static SubscriptionResource from(Subscription s) {
            return new SubscriptionResource(s.getSubscriptionId().toString(), s.getUserId(),
                    s.getPlanId().toString(), s.getStatus().name(), s.getStartDate(),
                    s.getEndDate(), s.getStripeSubscriptionId(), s.getCreatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChargeResource(
            @JsonProperty("PaymentID") String paymentId,
            @JsonProperty("Status") String status,
            @JsonProperty("Amount") double amount,
            @JsonProperty("Currency") String currency,
            @JsonProperty("InvoiceNumber") String invoiceNumber) {

        public static ChargeResource from(PaymentsAcl.ChargeReceipt c) {
            return c == null ? null : new ChargeResource(c.paymentId().toString(), c.status(),
                    c.amount(), c.currency(), c.invoiceNumber());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PurchaseResource(
            @JsonProperty("Subscription") SubscriptionResource subscription,
            @JsonProperty("Payment") ChargeResource payment) {
    }
}
