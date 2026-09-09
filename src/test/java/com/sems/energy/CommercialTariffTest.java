package com.sems.energy;

import static org.junit.jupiter.api.Assertions.*;

import com.sems.energy.domain.model.valueobjects.BillBreakdown;
import com.sems.energy.domain.model.valueobjects.CommercialTariff;
import com.sems.energy.domain.model.valueobjects.FranjaHoraria;
import com.sems.energy.domain.model.valueobjects.HorarioPunta;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tarifa comercial: franjas horarias y cargo por potencia.
 *
 * <p>Es la parte del cambio de segmento con consecuencias en dinero. Un error
 * aqui no da ningun sintoma visible: la aplicacion sigue mostrando una factura
 * estimada, solo que equivocada, y nadie lo nota hasta que llega el recibo real.
 */
class CommercialTariffTest {

    private static CommercialTariff tarifaMT2() {
        return new CommercialTariff("Plus Energia", "MT2", "PEN",
                new BigDecimal("0.2810"), new BigDecimal("0.2395"), new BigDecimal("58.40"),
                new BigDecimal("87.60"), new BigDecimal("12.80"), new BigDecimal("0.18"),
                Instant.now());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // ------------------------------------------------------- horario de punta

    @Test
    @DisplayName("Las siete de la tarde de un martes es hora punta")
    void martesPorLaTardeEsPunta() {
        // 19:00 en Peru son las 00:00 UTC del dia siguiente. Si se evaluara en
        // UTC saldria fuera de punta, que es justo lo contrario.
        Instant martes19hLocal = Instant.parse("2026-09-09T00:00:00Z");
        assertEquals(FranjaHoraria.PUNTA, HorarioPunta.franjaDe(martes19hLocal));
    }

    @Test
    @DisplayName("Las diez de la manana no es hora punta")
    void mananaNoEsPunta() {
        Instant martes10hLocal = Instant.parse("2026-09-08T15:00:00Z");
        assertEquals(FranjaHoraria.FUERA_DE_PUNTA, HorarioPunta.franjaDe(martes10hLocal));
    }

    @Test
    @DisplayName("El domingo por defecto SI es hora punta")
    void domingoEsPuntaPorDefecto() {
        // El pliego fija la hora punta de 18:00 a 23:00 de cada dia del ano. La
        // exclusion de domingos existe, pero solo "a solicitud del cliente": no es el
        // caso general. Darla por concedida subestima la factura del domingo, que para
        // un supermercado es uno de sus dias de mayor afluencia.
        Instant domingo20hLocal = Instant.parse("2026-09-14T01:00:00Z");

        assertEquals(DayOfWeek.SUNDAY, HorarioPunta.aHoraLocal(domingo20hLocal).getDayOfWeek());
        assertEquals(FranjaHoraria.PUNTA, HorarioPunta.franjaDe(domingo20hLocal));
    }

    @Test
    @DisplayName("El domingo queda fuera de punta si el suministro tiene la exclusion")
    void domingoFueraDePuntaConExclusion() {
        Instant domingo20hLocal = Instant.parse("2026-09-14T01:00:00Z");

        assertEquals(FranjaHoraria.FUERA_DE_PUNTA,
                HorarioPunta.franjaDe(domingo20hLocal, true));
    }

    @Test
    @DisplayName("La exclusion de domingos no afecta al resto de dias")
    void laExclusionSoloAplicaADomingos() {
        // Un suministro con la exclusion concedida sigue teniendo punta de lunes a
        // sabado: la excepcion es solo para domingos y feriados.
        Instant martes19hLocal = Instant.parse("2026-09-09T00:00:00Z");

        assertEquals(FranjaHoraria.PUNTA, HorarioPunta.franjaDe(martes19hLocal, true));
    }

    @Test
    @DisplayName("Las veintitres en punto ya esta fuera de punta")
    void limiteSuperiorExcluido() {
        // La franja es [18:00, 23:00).
        Instant martes23hLocal = Instant.parse("2026-09-09T04:00:00Z");
        assertEquals(FranjaHoraria.FUERA_DE_PUNTA, HorarioPunta.franjaDe(martes23hLocal));
    }

    // ------------------------------------------------------ cargo por potencia

    @Test
    @DisplayName("Un pico puntual encarece el mes entero sin gastar mas energia")
    void elPicoCuestaDinero() {
        // Este es el motivo de modelar la potencia. Mismo consumo de energia,
        // misma factura de energia, y sin embargo el total sube mas de tres mil
        // soles solo por el pico.
        CommercialTariff tarifa = tarifaMT2();

        BillBreakdown sinPico = tarifa.calcular(bd("6000"), bd("24000"), bd("120"), bd("120"));
        BillBreakdown conPico = tarifa.calcular(bd("6000"), bd("24000"), bd("150"), bd("120"));

        assertEquals(0, sinPico.costoEnergia().compareTo(conPico.costoEnergia()));
        assertTrue(conPico.total().subtract(sinPico.total()).compareTo(bd("3000")) > 0,
                "el pico deberia costar mas de S/3000");
    }

    @Test
    @DisplayName("El exceso sobre lo contratado se cobra a precio de penalizacion")
    void excesoSePenaliza() {
        BillBreakdown factura = tarifaMT2()
                .calcular(BigDecimal.ZERO, BigDecimal.ZERO, bd("150"), bd("120"));

        // 120 kW al precio normal y 30 kW al de penalizacion, no 150 al normal.
        BigDecimal esperado = bd("120").multiply(bd("58.40"))
                .add(bd("30").multiply(bd("87.60")));

        assertEquals(0, factura.costoPotencia().compareTo(esperado.setScale(2)));
        assertTrue(factura.hayExcesoDePotencia());
        assertEquals(0, factura.excesoDePotenciaKw().compareTo(bd("30")));
    }

    @Test
    @DisplayName("Sin superar lo contratado no hay penalizacion")
    void sinExcesoNoHayPenalizacion() {
        BillBreakdown factura = tarifaMT2()
                .calcular(BigDecimal.ZERO, BigDecimal.ZERO, bd("100"), bd("120"));

        assertFalse(factura.hayExcesoDePotencia());
        assertEquals(0, factura.excesoDePotenciaKw().compareTo(BigDecimal.ZERO));
        assertEquals(0, factura.costoPotencia()
                .compareTo(bd("100").multiply(bd("58.40")).setScale(2)));
    }

    @Test
    @DisplayName("Sin demanda registrada no se cobra potencia")
    void sinDemandaNoSeCobraPotencia() {
        // Un local recien dado de alta todavia no tiene lecturas. Cobrarle
        // potencia sobre una demanda de cero seria inventarse un cargo.
        BillBreakdown factura = tarifaMT2()
                .calcular(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("120"));

        assertEquals(0, factura.costoPotencia().compareTo(BigDecimal.ZERO.setScale(2)));
    }

    // -------------------------------------------------------------- la factura

    @Test
    @DisplayName("Mover consumo fuera de punta abarata la factura")
    void moverFueraDePuntaAbarata() {
        // Es la recomendacion principal que la aplicacion le dara a un local:
        // desplazar cargas que admiten horario, como el bombeo o el
        // precongelado.
        CommercialTariff tarifa = tarifaMT2();

        BillBreakdown muchaPunta = tarifa.calcular(bd("12000"), bd("18000"), bd("120"), bd("120"));
        BillBreakdown pocaPunta = tarifa.calcular(bd("3000"), bd("27000"), bd("120"), bd("120"));

        assertTrue(pocaPunta.total().compareTo(muchaPunta.total()) < 0);
        // La energia total es la misma: solo cambia en que franja se consume.
        assertEquals(0, muchaPunta.kwhPunta().add(muchaPunta.kwhFueraDePunta())
                .compareTo(bd("30000")));
        assertEquals(0, pocaPunta.kwhPunta().add(pocaPunta.kwhFueraDePunta())
                .compareTo(bd("30000")));
    }

    @Test
    @DisplayName("El IGV se aplica sobre el subtotal y el total cuadra")
    void elIgvCuadra() {
        BillBreakdown factura = tarifaMT2().calcular(bd("1000"), bd("2000"), bd("50"), bd("100"));

        assertEquals(0, factura.costoEnergia().add(factura.costoPotencia()).add(factura.cargoFijo())
                .compareTo(factura.subtotal()));
        assertEquals(0, factura.subtotal().add(factura.igv()).compareTo(factura.total()));
    }
}
