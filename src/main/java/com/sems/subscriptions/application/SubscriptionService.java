package com.sems.subscriptions.application;

import com.sems.shared.errors.AppException;
import com.sems.shared.events.DomainEventBus;
import com.sems.shared.events.DomainEvents;
import com.sems.subscriptions.domain.model.entities.Subscription;
import com.sems.subscriptions.domain.model.entities.SubscriptionPlan;
import com.sems.subscriptions.domain.model.valueobjects.SubscriptionStatus;
import com.sems.subscriptions.domain.repositories.SubscriptionRepositories.*;
import com.sems.subscriptions.domain.services.SubscriptionManager;
import java.util.List;
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
        planById(planId);

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
        planById(newPlanId);
        subscription.changePlan(newPlanId);
        Subscription saved = subscriptions.save(subscription);
        publishChange(saved);
        return saved;
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
