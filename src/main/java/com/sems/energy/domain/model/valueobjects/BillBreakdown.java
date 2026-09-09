package com.sems.energy.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Desglose de la factura estimada de un local.
 *
 * <p>Se devuelve entero, no solo el total, porque el valor para el cliente esta
 * en el desglose: ver que el cargo por potencia se come una parte de la factura
 * es lo que justifica actuar sobre los picos.
 */
public record BillBreakdown(BigDecimal kwhPunta, BigDecimal kwhFueraDePunta,
                            BigDecimal demandaMaximaKw, BigDecimal potenciaContratadaKw,
                            BigDecimal excesoDePotenciaKw, BigDecimal costoEnergia,
                            BigDecimal costoPotencia, BigDecimal cargoFijo, BigDecimal subtotal,
                            BigDecimal igv, BigDecimal total, String currency) {

    /** Que porcentaje del subtotal se lleva la potencia. */
    public BigDecimal pesoDeLaPotencia() {
        if (subtotal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return costoPotencia.multiply(BigDecimal.valueOf(100))
                .divide(subtotal, 1, RoundingMode.HALF_UP);
    }

    /** Si se supero la potencia contratada. */
    public boolean hayExcesoDePotencia() {
        return excesoDePotenciaKw.signum() > 0;
    }
}
