package com.sems.subscriptions.infrastructure.persistence.jpa.adapters;

import com.sems.subscriptions.domain.model.entities.*;
import com.sems.subscriptions.domain.repositories.SubscriptionRepositories.*;
import com.sems.subscriptions.infrastructure.persistence.jpa.entities.SubscriptionJpaEntities.*;
import com.sems.subscriptions.infrastructure.persistence.jpa.repositories.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Implementaciones JPA de los puertos del modulo de suscripciones. */
public final class SubscriptionAdapters {

    private SubscriptionAdapters() {
    }

    /**
     * Las caracteristicas se guardan en su propia tabla, pero el plan siempre se
     * lee completo: al guardar se reemplazan en bloque para que la fila y sus
     * caracteristicas no queden desincronizadas.
     */
    @Repository
    @RequiredArgsConstructor
    public static class PlanAdapter implements PlanRepository {

        private final PlanJpa plans;
        private final FeatureJpa features;

        @Override
        @Transactional
        public SubscriptionPlan save(SubscriptionPlan p) {
            plans.save(new PlanRow(p.getPlanId(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getCurrency(), p.getBillingPeriod(), p.isActive(), p.getCreatedAt()));
            features.deleteByPlanId(p.getPlanId());
            for (PlanFeature f : p.getPlanFeatures()) {
                features.save(new FeatureRow(f.getFeatureId(), p.getPlanId(), f.getFeatureCode(),
                        f.getFeatureName(), f.getFeatureValue(), f.getCreatedAt()));
            }
            return p;
        }

        @Override
        public Optional<SubscriptionPlan> findById(UUID planId) {
            return plans.findById(planId).map(this::toDomain);
        }

        @Override
        public Optional<SubscriptionPlan> findByName(String name) {
            return plans.findByName(name).map(this::toDomain);
        }

        @Override
        public List<SubscriptionPlan> findAllActive() {
            return plans.findByActiveTrueOrderByPriceAsc().stream().map(this::toDomain).toList();
        }

        @Override
        public long count() {
            return plans.count();
        }

        private SubscriptionPlan toDomain(PlanRow r) {
            List<PlanFeature> planFeatures = features.findByPlanId(r.getPlanId()).stream()
                    .map(f -> new PlanFeature(f.getFeatureId(), f.getPlanId(), f.getFeatureCode(),
                            f.getFeatureName(), f.getFeatureValue(), f.getCreatedAt()))
                    .toList();
            return new SubscriptionPlan(r.getPlanId(), r.getName(), r.getDescription(), r.getPrice(),
                    r.getCurrency(), r.getBillingPeriod(), r.isActive(), r.getCreatedAt(), planFeatures);
        }
    }

    @Repository
    @RequiredArgsConstructor
    public static class SubscriptionAdapter implements SubscriptionRepository {

        private final SubscriptionJpa jpa;

        @Override
        public Subscription save(Subscription s) {
            return toDomain(jpa.save(new SubscriptionRow(s.getSubscriptionId(), s.getUserId(),
                    s.getPlanId(), s.getStatus(), s.getStartDate(), s.getEndDate(),
                    s.getStripeSubscriptionId(), s.getCreatedAt())));
        }

        @Override
        public Optional<Subscription> findById(UUID subscriptionId) {
            return jpa.findById(subscriptionId).map(SubscriptionAdapter::toDomain);
        }

        @Override
        public List<Subscription> findByUserId(String userId) {
            return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(SubscriptionAdapter::toDomain).toList();
        }

        @Override
        public Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId) {
            return jpa.findByStripeSubscriptionId(stripeSubscriptionId)
                    .map(SubscriptionAdapter::toDomain);
        }

        private static Subscription toDomain(SubscriptionRow r) {
            return new Subscription(r.getSubscriptionId(), r.getUserId(), r.getPlanId(),
                    r.getStatus(), r.getStartDate(), r.getEndDate(), r.getStripeSubscriptionId(),
                    r.getCreatedAt());
        }
    }
}
