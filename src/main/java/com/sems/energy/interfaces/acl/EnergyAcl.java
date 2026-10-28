package com.sems.energy.interfaces.acl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Capa anticorrupcion del bounded context de energia.
 *
 * <p>La necesita analitica: para detectar una anomalia o proponer un ahorro
 * hace falta el consumo medido, y ese dato es de energia. Sin esta puerta,
 * analitica tendria que leer los repositorios de otro contexto, que es
 * exactamente lo que la separacion en modulos pretende evitar.
 *
 * <p>Devuelve consumo ya agregado por dia y por medidor, no lecturas sueltas:
 * una semana de medicion son miles de lecturas y ninguna regla de analitica
 * trabaja a ese nivel de detalle.
 */
public interface EnergyAcl {

    /**
     * Consumo de un dia en un medidor.
     *
     * @param peakKwh   parte del consumo que cayo en hora punta
     * @param maxPowerKw potencia maxima vista ese dia, que es lo que fija el
     *                   cargo por potencia del mes
     */
    record DailyUsage(UUID meterId, UUID siteId, UUID zoneId, LocalDate day,
                      double kwh, double peakKwh, double maxPowerKw) {
    }

    /** Precio de la energia del local, por franja. */
    record SitePrices(UUID siteId, String tariffCategory, BigDecimal peakPerKwh,
                      BigDecimal offPeakPerKwh, BigDecimal powerPerKwMonth, String currency) {
    }

    /** Consumo diario por medidor de los ultimos dias, del mas antiguo al mas reciente. */
    List<DailyUsage> dailyUsage(String userId, int days);

    /** Precios vigentes del local, o vacio si no tiene tarifa reconocible. */
    Optional<SitePrices> pricesOf(UUID siteId);
}
