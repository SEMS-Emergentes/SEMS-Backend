package com.sems.energy.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Tarifa electrica comercial: precios de energia por franja mas cargos por
 * potencia.
 *
 * <p>Es la diferencia de fondo con la tarifa de una vivienda, que solo tenia un
 * precio por kWh. Aqui la factura suma tres conceptos distintos y el segundo, la
 * potencia, no depende de cuanta energia se gasto sino de cual fue el pico mas
 * alto del mes.
 *
 * <p>Los importes son referenciales y los entrega el adaptador del proveedor:
 * este tipo solo sabe combinarlos.
 */
public record CommercialTariff(String provider, String tariffCategory, String currency,
                               BigDecimal energiaPuntaPorKwh, BigDecimal energiaFueraDePuntaPorKwh,
                               BigDecimal potenciaPorKwMes, BigDecimal excesoDePotenciaPorKwMes,
                               BigDecimal cargoFijoMensual, BigDecimal igv, Instant timestamp) {

    /** Coste de la energia consumida, sin impuestos. */
    public BigDecimal costoDeEnergia(BigDecimal kwhPunta, BigDecimal kwhFueraDePunta) {
        return kwhPunta.multiply(energiaPuntaPorKwh)
                .add(kwhFueraDePunta.multiply(energiaFueraDePuntaPorKwh));
    }

    /**
     * Coste de la potencia del mes, sin impuestos.
     *
     * <p>Se cobra sobre la demanda maxima registrada, y lo que exceda de la
     * potencia contratada se cobra ademas a precio de penalizacion. Un pico de
     * quince minutos encarece el mes entero: por eso conviene detectarlo y
     * avisar, no solo mostrar el consumo acumulado.
     */
    public BigDecimal costoDePotencia(BigDecimal demandaMaximaKw, BigDecimal potenciaContratadaKw) {
        if (demandaMaximaKw.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal dentroDeContrato = demandaMaximaKw.min(potenciaContratadaKw);
        BigDecimal exceso = demandaMaximaKw.subtract(potenciaContratadaKw).max(BigDecimal.ZERO);

        return dentroDeContrato.multiply(potenciaPorKwMes)
                .add(exceso.multiply(excesoDePotenciaPorKwMes));
    }

    /** Desglose completo de la factura estimada. */
    public BillBreakdown calcular(BigDecimal kwhPunta, BigDecimal kwhFueraDePunta,
                                  BigDecimal demandaMaximaKw, BigDecimal potenciaContratadaKw) {
        BigDecimal energia = costoDeEnergia(kwhPunta, kwhFueraDePunta);
        BigDecimal potencia = costoDePotencia(demandaMaximaKw, potenciaContratadaKw);
        BigDecimal subtotal = energia.add(potencia).add(cargoFijoMensual);
        BigDecimal impuesto = subtotal.multiply(igv);

        return new BillBreakdown(kwhPunta, kwhFueraDePunta, demandaMaximaKw, potenciaContratadaKw,
                demandaMaximaKw.subtract(potenciaContratadaKw).max(BigDecimal.ZERO),
                redondear(energia), redondear(potencia), redondear(cargoFijoMensual),
                redondear(subtotal), redondear(impuesto), redondear(subtotal.add(impuesto)),
                currency);
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
