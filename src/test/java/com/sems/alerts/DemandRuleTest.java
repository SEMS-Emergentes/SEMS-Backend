package com.sems.alerts;

import static org.junit.jupiter.api.Assertions.*;

import com.sems.alerts.domain.model.entities.DemandRule;
import com.sems.alerts.domain.model.valueobjects.DemandLevel;
import com.sems.shared.errors.AppException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vigilancia de la demanda contra la potencia contratada.
 *
 * <p>El valor de esta regla esta en avisar <b>antes</b> de superar lo
 * contratado. Si el umbral se calcula mal, el aviso llega tarde y ya no sirve
 * para nada: el recargo del mes esta hecho.
 */
class DemandRuleTest {

    private static DemandRule regla() {
        return regla(120d, null);
    }

    private static DemandRule regla(double contratada, Double aviso) {
        return DemandRule.create(UUID.randomUUID(), UUID.randomUUID(), "T-001", contratada, aviso,
                true);
    }

    @Test
    @DisplayName("El umbral de aviso es el porcentaje de lo contratado")
    void umbralDeAviso() {
        assertEquals(102d, regla(120d, 85d).umbralDeAvisoKw(), 0.0001);
        assertEquals(90d, regla(120d, 75d).umbralDeAvisoKw(), 0.0001);
    }

    @Test
    @DisplayName("Por defecto se avisa al ochenta y cinco por ciento")
    void avisoPorDefecto() {
        // Deja margen para reaccionar sin disparar avisos en un local que
        // normalmente trabaja al 70-80%.
        assertEquals(85d, regla(120d, null).getWarningPercent(), 0.0001);
    }

    @Test
    @DisplayName("Por debajo del umbral no hay aviso")
    void debajoDelUmbral() {
        assertEquals(DemandLevel.OK, regla().evaluar(90d));
        assertEquals(DemandLevel.OK, regla().evaluar(101.9d));
    }

    @Test
    @DisplayName("Entre el umbral y lo contratado se avisa con margen")
    void entreUmbralYContratado() {
        DemandRule r = regla();

        assertEquals(DemandLevel.WARNING, r.evaluar(102d));
        assertEquals(DemandLevel.WARNING, r.evaluar(119d));
        // Justo en lo contratado todavia es aviso, no exceso: no se ha pasado.
        assertEquals(DemandLevel.WARNING, r.evaluar(120d));
    }

    @Test
    @DisplayName("Por encima de lo contratado es exceso")
    void porEncimaEsExceso() {
        assertEquals(DemandLevel.EXCEEDED, regla().evaluar(120.1d));
        assertEquals(DemandLevel.EXCEEDED, regla().evaluar(150d));
    }

    @Test
    @DisplayName("El margen indica cuanto queda y cuanto se paso")
    void elMargen() {
        DemandRule r = regla();

        assertEquals(15d, r.margenKw(105d), 0.0001);
        assertEquals(-30d, r.margenKw(150d), 0.0001);
    }

    @Test
    @DisplayName("Una regla desactivada no avisa de nada")
    void desactivadaNoAvisa() {
        DemandRule r = regla();
        r.deactivate();

        assertEquals(DemandLevel.OK, r.evaluar(500d));
    }

    @Test
    @DisplayName("Se rechaza una potencia contratada no positiva")
    void potenciaNoPositiva() {
        assertThrows(AppException.class, () -> DemandRule.create(UUID.randomUUID(),
                UUID.randomUUID(), null, 0d, null, true));
    }

    @Test
    @DisplayName("Se rechaza un porcentaje de aviso fuera de rango")
    void porcentajeFueraDeRango() {
        assertThrows(AppException.class, () -> DemandRule.create(UUID.randomUUID(),
                UUID.randomUUID(), null, 120d, 0d, true));
        assertThrows(AppException.class, () -> DemandRule.create(UUID.randomUUID(),
                UUID.randomUUID(), null, 120d, 120d, true));
    }
}
