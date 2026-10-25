package com.sems.subscriptions.interfaces.acl;

import com.sems.subscriptions.domain.model.entities.PlanFeature;
import com.sems.subscriptions.domain.model.entities.Subscription;
import com.sems.subscriptions.domain.model.entities.SubscriptionPlan;
import com.sems.subscriptions.domain.model.valueobjects.SubscriptionStatus;
import com.sems.subscriptions.domain.repositories.SubscriptionRepositories.PlanRepository;
import com.sems.subscriptions.domain.repositories.SubscriptionRepositories.SubscriptionRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementacion de la capa anticorrupcion de suscripciones. */
@Service
@RequiredArgsConstructor
public class SubscriptionsAclImpl implements SubscriptionsAcl {

    /** Caracteristica del plan que declara cuantos locales incluye. */
    private static final String SITES_LIMIT = "SITES_LIMIT";

    /** Valor con el que un plan declara que no tiene tope. */
    private static final String SIN_TOPE = "unlimited";

    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;

    @Override
    @Transactional(readOnly = true)
    public Optional<SiteAllowance> siteAllowanceFor(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return subscriptions.findByUserId(userId.toString()).stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .map(Subscription::getPlanId)
                .map(plans::findById)
                .flatMap(Optional::stream)
                .map(this::cupoDelPlan)
                .flatMap(Optional::stream)
                // Si alguien tiene varias suscripciones activas, manda la mas
                // generosa: cobrarle dos planes y aplicarle el menor seria
                // indefendible.
                .max((a, b) -> Integer.compare(a.sitesIncluded(), b.sitesIncluded()));
    }

    private Optional<SiteAllowance> cupoDelPlan(SubscriptionPlan plan) {
        return plan.getPlanFeatures().stream()
                .filter(f -> SITES_LIMIT.equals(f.getFeatureCode()))
                .map(PlanFeature::getFeatureValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .flatMap(valor -> aCupo(valor, plan.getName()));
    }

    private Optional<SiteAllowance> aCupo(String valor, String nombreDelPlan) {
        String limpio = valor.trim();
        if (SIN_TOPE.equalsIgnoreCase(limpio)) {
            return Optional.empty();
        }
        try {
            int incluidos = Integer.parseInt(limpio);
            // Un cupo de cero o negativo no significa "ningun local": significa
            // que el dato esta mal, y un dato mal puesto no debe impedir operar.
            return incluidos > 0
                    ? Optional.of(new SiteAllowance(incluidos, nombreDelPlan))
                    : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
