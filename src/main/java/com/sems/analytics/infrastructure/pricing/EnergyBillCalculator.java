package com.sems.analytics.infrastructure.pricing;

import com.sems.analytics.domain.services.BillCalculator;
import com.sems.energy.domain.model.valueobjects.BillBreakdown;
import com.sems.energy.domain.model.valueobjects.CommercialTariff;
import com.sems.energy.domain.services.EnergyPricingProvider;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resuelve {@link BillCalculator} delegando en el modulo de energia.
 *
 * <p>La dependencia entre modulos queda aqui, en infraestructura, y en una sola
 * direccion: analitica conoce a energia, nunca al reves.
 */
@Component
@RequiredArgsConstructor
public class EnergyBillCalculator implements BillCalculator {

    private final EnergyPricingProvider pricing;

    @Override
    public EstimatedBill estimate(String tariffCategory, double kwhPeak, double kwhOffPeak,
                                  double maxDemandKw, double contractedPowerKw) {
        CommercialTariff tarifa = pricing.currentTariff(tariffCategory);
        BillBreakdown desglose = tarifa.calcular(BigDecimal.valueOf(kwhPeak),
                BigDecimal.valueOf(kwhOffPeak), BigDecimal.valueOf(maxDemandKw),
                BigDecimal.valueOf(contractedPowerKw));

        return new EstimatedBill(desglose.costoEnergia().doubleValue(),
                desglose.costoPotencia().doubleValue(), desglose.cargoFijo().doubleValue(),
                desglose.total().doubleValue(), desglose.currency(),
                // Se guarda el precio de la energia fuera de punta como "tarifa
                // usada" porque es la que aplica a la mayor parte del consumo.
                // El desglose completo va en los campos de la prediccion.
                tarifa.energiaFueraDePuntaPorKwh().doubleValue());
    }
}
