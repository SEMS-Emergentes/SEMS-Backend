package com.sems.energy.domain.services;

import com.sems.energy.domain.model.valueobjects.CommercialTariff;
import com.sems.energy.domain.model.valueobjects.EnergyPrice;

/**
 * Puerto hacia el proveedor externo de tarifas electricas.
 *
 * <p>El dominio solo conoce esta interfaz. Hoy detras hay un adaptador
 * simulado; manana puede haber una integracion real sin tocar nada mas.
 */
public interface EnergyPricingProvider {

    EnergyPrice currentPrice();

    /**
     * Tarifa comercial vigente para una categoria del pliego.
     *
     * <p>La categoria llega como texto y no como enum de otro modulo a
     * proposito: energia no debe depender del bounded context de
     * organizaciones. Es quien llama el que traduce.
     */
    CommercialTariff currentTariff(String tariffCategory);
}
