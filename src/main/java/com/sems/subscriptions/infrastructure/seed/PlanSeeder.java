package com.sems.subscriptions.infrastructure.seed;

import com.sems.subscriptions.domain.model.entities.PlanFeature;
import com.sems.subscriptions.domain.model.entities.SubscriptionPlan;
import com.sems.subscriptions.domain.repositories.SubscriptionRepositories.PlanRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Carga los tres planes por defecto la primera vez que arranca el sistema.
 *
 * <p>Porta el seeder del servicio en Go. Solo actua si no hay ningun plan, de
 * modo que reiniciar la aplicacion nunca duplica ni sobreescribe lo existente.
 *
 * <p>El limite de dispositivos vive como caracteristica del plan
 * ({@code LINKED_DEVICES_LIMIT}) y no en el codigo: asi se puede cambiar sin
 * volver a desplegar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSeeder implements ApplicationRunner {

    private final PlanRepository plans;

    @Value("${stripe.price.free:}")
    private String stripePriceFree;

    @Value("${stripe.price.plus:}")
    private String stripePricePlus;

    @Value("${stripe.price.pro:}")
    private String stripePricePro;

    @Override
    public void run(ApplicationArguments args) {
        if (plans.count() > 0) {
            return;
        }
        log.info("No hay planes registrados; cargando los tres por defecto");

        // Los planes se miden en LOCALES, no en dispositivos.
        //
        // En el segmento anterior el limite de dispositivos tenia sentido: una
        // vivienda tiene unos pocos. Un supermercado tiene decenas de medidores
        // en un solo local, asi que un tope de tres o diez no separa a un
        // cliente pequeno de una cadena; solo estorba. Lo que de verdad escala
        // con el tamano del cliente es cuantos locales gestiona.
        plans.save(withFeatures(
                SubscriptionPlan.create("Starter",
                        "One site, with the essentials to start measuring", 0, "PEN", "monthly"),
                stripePriceFree,
                feature("BASIC_DASHBOARD", "Site consumption dashboard", "enabled"),
                feature("CONSUMPTION_ALERTS", "Consumption alerts", "enabled"),
                feature("SITES_LIMIT", "Sites included", "1"),
                feature("DEVICES_PER_SITE_LIMIT", "Meters per site", "10")));

        plans.save(withFeatures(
                SubscriptionPlan.create("Business",
                        "For small chains, with demand control", 149, "PEN", "monthly"),
                stripePricePlus,
                feature("BASIC_INCLUDED", "Everything in Starter", "enabled"),
                feature("SITES_LIMIT", "Sites included", "5"),
                feature("DEVICES_PER_SITE_LIMIT", "Meters per site", "50"),
                feature("ZONE_ANALYTICS", "Consumption broken down by zone", "enabled"),
                // El aviso de demanda es lo que justifica el salto de plan:
                // evitar un solo pico al mes ya paga la diferencia.
                feature("DEMAND_ALERTS",
                        "Demand warning before exceeding the contracted power", "enabled"),
                feature("PEAK_HOUR_REPORTS", "Peak-hour consumption reports", "enabled")));

        plans.save(withFeatures(
                SubscriptionPlan.create("Enterprise",
                        "Large chains, with site-to-site benchmarking", 399, "PEN", "monthly"),
                stripePricePro,
                feature("BUSINESS_INCLUDED", "Everything in Business", "enabled"),
                feature("SITES_LIMIT", "Sites included", "unlimited"),
                feature("DEVICES_PER_SITE_LIMIT", "Meters per site", "unlimited"),
                feature("SITE_BENCHMARKING", "Performance benchmarking across sites", "enabled"),
                feature("TARIFF_OPTIMIZATION", "Tariff category analysis", "enabled"),
                feature("PRIORITY_SUPPORT", "Priority support", "enabled")));
    }

    /** Adjunta las caracteristicas al plan y, si existe, el precio de Stripe. */
    private SubscriptionPlan withFeatures(SubscriptionPlan plan, String stripePriceId,
                                          FeatureSpec... specs) {
        List<PlanFeature> features = new ArrayList<>();
        for (FeatureSpec spec : specs) {
            features.add(PlanFeature.create(plan.getPlanId(), spec.code(), spec.name(), spec.value()));
        }
        if (stripePriceId != null && !stripePriceId.isBlank()) {
            features.add(PlanFeature.create(plan.getPlanId(), PlanFeature.STRIPE_PRICE_ID,
                    "Stripe price id", stripePriceId));
        }
        return new SubscriptionPlan(plan.getPlanId(), plan.getName(), plan.getDescription(),
                plan.getPrice(), plan.getCurrency(), plan.getBillingPeriod(), plan.isActive(),
                plan.getCreatedAt(), features);
    }

    private static FeatureSpec feature(String code, String name, String value) {
        return new FeatureSpec(code, name, value);
    }

    private record FeatureSpec(String code, String name, String value) {
    }
}
