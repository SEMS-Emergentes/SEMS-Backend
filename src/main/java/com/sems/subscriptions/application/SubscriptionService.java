package com.sems.subscriptions.application;

import com.sems.payments.interfaces.acl.PaymentsAcl;
import com.sems.payments.interfaces.acl.PaymentsAcl.ChargeReceipt;
import com.sems.payments.interfaces.acl.PaymentsAcl.SimulatedCard;
import com.sems.shared.errors.AppException;
import com.sems.shared.events.DomainEventBus;
import com.sems.shared.events.DomainEvents;
import com.sems.subscriptions.domain.model.entities.Subscription;
import com.sems.subscriptions.domain.model.entities.SubscriptionPlan;
import com.sems.subscriptions.domain.model.valueobjects.SubscriptionStatus;
import com.sems.subscriptions.domain.repositories.SubscriptionRepositories.*;
import com.sems.subscriptions.domain.services.SubscriptionManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso del modulo de suscripciones. */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionManager manager;
    private final DomainEventBus bus;
    private final PaymentsAcl payments;

    /** Resultado de comprar un plan: la suscripcion vigente y, si hubo cobro, su registro. */
    public record PurchaseResult(Subscription subscription, ChargeReceipt charge,
                                 String declineReason) {

        public boolean declined() {
            return declineReason != null;
        }
    }

    // ---------------------------------------------------------------- planes

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> activePlans() {
        return plans.findAllActive();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan planById(UUID planId) {
        return plans.findById(planId)
                .orElseThrow(() -> AppException.notFound("plan not found"));
    }

    // --------------------------------------------------------- suscripciones

    @Transactional(readOnly = true)
    public Subscription subscriptionById(UUID subscriptionId) {
        return subscriptions.findById(subscriptionId)
                .orElseThrow(() -> AppException.notFound("subscription not found"));
    }

    @Transactional(readOnly = true)
    public List<Subscription> subscriptionsByUser(String userId) {
        return subscriptions.findByUserId(userId);
    }

    @Transactional
    public Subscription create(String userId, UUID planId, String stripeSubscriptionId) {
        // El plan debe existir antes de cobrar nada.
        rechazarSiEsDePago(planById(planId));
        return start(userId, planId, stripeSubscriptionId);
    }

    private Subscription start(String userId, UUID planId, String stripeSubscriptionId) {
        // Una suscripcion activa por usuario. Antes se podian acumular, y con
        // dos planes vivos a la vez ya no habia respuesta a "en que plan esta
        // este cliente": el cupo de locales tomaba el mas amplio y la
        // facturacion podia cobrar los dos. Para cambiar de plan esta
        // changePlan, que es la operacion que el cliente quiere de verdad.
        boolean yaTieneUna = subscriptions.findByUserId(userId).stream()
                .anyMatch(s -> s.getStatus() == SubscriptionStatus.ACTIVE);
        if (yaTieneUna) {
            throw AppException.conflict(
                    "this user already has an active subscription; change its plan instead");
        }

        Subscription saved = subscriptions.save(
                Subscription.start(userId, planId, stripeSubscriptionId));
        publishChange(saved);
        return saved;
    }

    @Transactional
    public Subscription cancel(UUID subscriptionId) {
        Subscription subscription = subscriptionById(subscriptionId);
        manager.ensureCanCancel(subscription.getStatus());
        subscription.cancel();
        Subscription saved = subscriptions.save(subscription);
        publishChange(saved);
        return saved;
    }

    @Transactional
    public Subscription changePlan(UUID subscriptionId, UUID newPlanId) {
        Subscription subscription = subscriptionById(subscriptionId);
        manager.ensureCanChangePlan(subscription.getStatus());
        rechazarSiEsDePago(planById(newPlanId));
        return switchPlan(subscription, newPlanId);
    }

    private Subscription switchPlan(Subscription subscription, UUID newPlanId) {
        subscription.changePlan(newPlanId);
        Subscription saved = subscriptions.save(subscription);
        publishChange(saved);
        return saved;
    }

    /**
     * Contrata un plan, cobrandolo si cuesta.
     *
     * <p>Es la unica via para un plan de pago. Antes el alta y el cambio de plan
     * activaban cualquier plan sin cobrar, y el navegador aplicaba el cambio al
     * volver de la pasarela con {@code ?paid=1}: bastaba con abrir esa URL o
     * llamar a la API para tener Enterprise gratis. Aqui el importe sale del
     * plan guardado, nunca del cliente, y el plan solo cambia si el cobro se
     * aprueba.
     */
    @Transactional
    public PurchaseResult purchase(String userId, UUID planId, SimulatedCard card) {
        SubscriptionPlan plan = planById(planId);
        Optional<Subscription> vigente = subscriptions.findByUserId(userId).stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .findFirst();
        if (vigente.isPresent()) {
            manager.ensureCanChangePlan(vigente.get().getStatus());
            // Cobrar de nuevo el plan que ya tiene seria cobrarle dos veces lo mismo.
            if (vigente.get().getPlanId().equals(planId)) {
                throw AppException.conflict("the user is already on this plan");
            }
        }

        if (plan.getPrice() <= 0) {
            return new PurchaseResult(aplicar(userId, planId, vigente), null, null);
        }
        if (card == null) {
            throw AppException.validation("card is required for a paid plan");
        }

        var decision = payments.reviewCard(card);
        UUID usuario = UUID.fromString(userId);
        if (!decision.approved()) {
            // El rechazo queda en el historial y el plan no se toca.
            ChargeReceipt fallido = payments.recordCharge(usuario,
                    vigente.map(Subscription::getSubscriptionId).orElse(null),
                    plan.getPrice(), plan.getCurrency(), decision);
            return new PurchaseResult(vigente.orElse(null), fallido, decision.declineReason());
        }

        Subscription contratada = aplicar(userId, planId, vigente);
        ChargeReceipt cobro = payments.recordCharge(usuario, contratada.getSubscriptionId(),
                plan.getPrice(), plan.getCurrency(), decision);
        return new PurchaseResult(contratada, cobro, null);
    }

    private Subscription aplicar(String userId, UUID planId, Optional<Subscription> vigente) {
        return vigente.isPresent()
                ? switchPlan(vigente.get(), planId)
                : start(userId, planId, null);
    }

    // Un plan de pago entra solo por purchase, que cobra antes de activarlo.
    private void rechazarSiEsDePago(SubscriptionPlan plan) {
        if (plan.getPrice() > 0) {
            throw AppException.conflict(
                    "paid plans are activated with POST /api/v1/subscriptions/purchase, which charges the card first");
        }
    }

    /** Usado por el webhook de Stripe para reflejar el estado real del cobro. */
    @Transactional
    public Subscription updateStatusFromStripe(String stripeSubscriptionId, SubscriptionStatus status) {
        Subscription subscription = subscriptions.findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> AppException.notFound("subscription not found for stripe id"));
        subscription.updateStatus(status);
        Subscription saved = subscriptions.save(subscription);
        publishChange(saved);
        return saved;
    }

    /**
     * Avisa al resto del sistema del cambio de plan.
     *
     * <p>Lo escucha el control de funciones por plan del frontend a traves de la
     * consulta de suscripcion. Antes viajaba por el topic
     * {@code subscriptions.events}.
     */
    private void publishChange(Subscription subscription) {
        String planName = plans.findById(subscription.getPlanId())
                .map(SubscriptionPlan::getName).orElse("unknown");
        try {
            bus.publish(new DomainEvents.SubscriptionChanged(
                    UUID.fromString(subscription.getUserId()), subscription.getSubscriptionId(),
                    planName, subscription.getStatus().name()));
        } catch (IllegalArgumentException e) {
            // El identificador de usuario no es un UUID: no se emite el evento,
            // pero la operacion de negocio no debe fallar por eso.
        }
    }
}
