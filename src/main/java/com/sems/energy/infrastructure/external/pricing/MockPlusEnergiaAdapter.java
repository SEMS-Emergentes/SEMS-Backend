package com.sems.energy.infrastructure.external.pricing;

import com.sems.energy.domain.model.valueobjects.CommercialTariff;
import com.sems.energy.domain.model.valueobjects.EnergyPrice;
import com.sems.energy.domain.services.EnergyPricingProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Adaptador simulado del proveedor ficticio Plus Energia.
 *
 * <p>Porta el comportamiento exacto del adaptador en Python: la semilla del
 * generador es la fecha del dia, de modo que el precio es aleatorio entre
 * usuarios pero <b>estable durante toda la jornada</b>. Sin esa semilla la
 * tarifa cambiaria en cada peticion y los costes mostrados al usuario bailarian.
 */
@Component
public class MockPlusEnergiaAdapter implements EnergyPricingProvider {

    private static final DateTimeFormatter DAY_KEY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Override
    public EnergyPrice currentPrice() {
        Instant now = Instant.now();
        Random rng = new Random(DAY_KEY.format(now).hashCode());
        double price = Math.round((0.68 + rng.nextDouble() * (0.92 - 0.68)) * 100.0) / 100.0;
        return new EnergyPrice("Plus Energia", price, "PEN", now);
    }

    /**
     * Tarifa comercial simulada para la categoria pedida.
     *
     * <p>Los importes son <b>referenciales</b>, del mismo orden de magnitud que
     * los del pliego tarifario, pero no son los oficiales: este adaptador simula
     * al proveedor. Cuando se integre uno real, se sustituye esta clase y no
     * cambia nada mas, que es el motivo de que exista el puerto.
     *
     * <p>BT5B es el caso aparte: es la tarifa sin cargo por potencia, la misma
     * que tendria una vivienda. Se mantiene porque un local pequeno puede estar
     * en ella, y devolver cargos de potencia en cero es mas honesto que negarse
     * a responder.
     */
    @Override
    public CommercialTariff currentTariff(String tariffCategory) {
        String categoria = tariffCategory == null || tariffCategory.isBlank()
                ? "MT2"
                : tariffCategory.trim().toUpperCase();
        Instant ahora = Instant.now();

        // Sin cargo por potencia: el local paga solo energia, a precio unico.
        if (categoria.equals("BT5B")) {
            BigDecimal plano = BigDecimal.valueOf(currentPrice().pricePerKwh());
            return new CommercialTariff("Plus Energia", categoria, "PEN",
                    plano, plano, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("4.50"), new BigDecimal("0.18"), ahora);
        }

        // Media tension frente a baja tension: la media es mas barata en energia
        // y mas cara en potencia, que es justo lo que hace que a un local grande
        // le compense y a uno pequeno no.
        boolean esMediaTension = categoria.startsWith("MT");

        return new CommercialTariff("Plus Energia", categoria, "PEN",
                new BigDecimal(esMediaTension ? "0.2810" : "0.3120"),
                new BigDecimal(esMediaTension ? "0.2395" : "0.2680"),
                new BigDecimal(esMediaTension ? "58.40" : "42.10"),
                // El exceso sobre lo contratado se penaliza. Que sea mas caro que
                // la potencia normal no es un adorno: es lo que convierte un pico
                // puntual en una linea visible de la factura.
                new BigDecimal(esMediaTension ? "87.60" : "63.15"),
                new BigDecimal(esMediaTension ? "12.80" : "8.40"),
                new BigDecimal("0.18"), ahora);
    }
}
