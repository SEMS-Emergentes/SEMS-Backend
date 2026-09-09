package com.sems.analytics.domain.services;

/**
 * Lo unico que la analitica necesita de las tarifas.
 *
 * <p>La analitica no tiene por que saber que existe una hora punta, un cargo por
 * exceso ni el IGV: solo necesita convertir un consumo previsto en un importe.
 * Ese calculo vive en el modulo de energia, que es su dueno, y aqui solo entra
 * el resultado.
 */
public interface BillCalculator {

    /** Importe estimado de un recibo, desglosado. */
    record EstimatedBill(double energyCost, double powerCost, double fixedCharge, double total,
                         String currency, double tariffUsed) {
    }

    EstimatedBill estimate(String tariffCategory, double kwhPeak, double kwhOffPeak,
                           double maxDemandKw, double contractedPowerKw);
}
