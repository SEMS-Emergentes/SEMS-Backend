package com.sems.alerts;

import static org.junit.jupiter.api.Assertions.*;

import com.sems.alerts.domain.model.entities.Alert;
import com.sems.alerts.domain.model.entities.AlertThreshold;
import com.sems.alerts.domain.model.entities.InactivityRule;
import com.sems.alerts.domain.model.entities.NotificationLog;
import com.sems.alerts.domain.model.valueobjects.AlertStatus;
import com.sems.alerts.domain.model.valueobjects.Operator;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlertDomainTest {

    @Test
    @DisplayName("El estado del aviso se normaliza al vocabulario del servidor")
    void elEstadoSeNormaliza() {
        // La web manda "open" donde el servidor dice "active". Aceptarlo y
        // normalizarlo es lo que evita tener dos palabras para lo mismo
        // guardadas en la base, sin obligar a desplegar ambos lados a la vez.
        assertEquals("active", AlertStatus.of("open").wire());
        assertEquals("active", AlertStatus.of("OPEN").wire());
        assertEquals("active", AlertStatus.of("  Active ").wire());
        assertEquals("acknowledged", AlertStatus.of("acknowledged").wire());
        assertEquals("resolved", AlertStatus.of("closed").wire());
        assertEquals("resolved", AlertStatus.of("dismissed").wire());

        // Sin estado, el aviso nace activo.
        assertEquals("active", AlertStatus.of(null).wire());
        assertEquals("active", AlertStatus.of("   ").wire());
    }

    @Test
    @DisplayName("Un estado inventado se rechaza en vez de guardarse")
    void elEstadoInventadoSeRechaza() {
        // Guardarlo era lo que dejaba el aviso fuera de todos los filtros: no
        // fallaba nada y el aviso simplemente desaparecia de la pantalla.
        AppException e = assertThrows(AppException.class, () -> AlertStatus.of("INVENTADO"));
        assertTrue(e.getMessage().contains("INVENTADO"), e.getMessage());

        Alert aviso = Alert.raise(UUID.randomUUID(), UUID.randomUUID(), null, null,
                "THRESHOLD", "t", "m", "WARNING", null, null);
        assertThrows(AppException.class, () -> aviso.updateStatus("cualquier-cosa", null));
        assertEquals("active", aviso.getStatus(), "el aviso conserva su estado anterior");
    }

    @Test
    @DisplayName("Resolver un aviso sella la fecha; los demas estados no")
    void resolverSellaLaFecha() {
        Alert aviso = Alert.raise(UUID.randomUUID(), UUID.randomUUID(), null, null,
                "THRESHOLD", "t", "m", "WARNING", "open", null);
        assertEquals("active", aviso.getStatus());
        assertNull(aviso.getResolvedAt());

        aviso.updateStatus("acknowledged", null);
        assertEquals("acknowledged", aviso.getStatus());
        assertNull(aviso.getResolvedAt(), "solo resolver sella la fecha");

        aviso.updateStatus("closed", null);
        assertEquals("resolved", aviso.getStatus());
        assertNotNull(aviso.getResolvedAt());
    }

    @Test
    @DisplayName("Cada operador compara como corresponde")
    void operatorsCompareCorrectly() {
        assertTrue(Operator.of(">").test(10, 5));
        assertFalse(Operator.of(">").test(5, 10));
        assertTrue(Operator.of(">=").test(5, 5));
        assertTrue(Operator.of("<").test(3, 5));
        assertTrue(Operator.of("<=").test(5, 5));
        assertTrue(Operator.of("==").test(5, 5));
    }

    @Test
    @DisplayName("Un operador desconocido se rechaza en lugar de pasar en silencio")
    void unknownOperatorIsRejected() {
        assertThrows(AppException.class, () -> Operator.of("=>"));
        assertThrows(AppException.class, () -> Operator.of(null));
    }

    @Test
    @DisplayName("Un umbral desactivado no dispara aunque se supere el valor")
    void inactiveThresholdNeverFires() {
        AlertThreshold active = AlertThreshold.create(UUID.randomUUID(), UUID.randomUUID(),
                "Consumo alto", "kwh", Operator.GREATER_THAN, 10.0, true);
        assertTrue(active.isBreachedBy(15.0));
        assertFalse(active.isBreachedBy(5.0));

        active.deactivate();
        assertFalse(active.isBreachedBy(15.0), "un umbral desactivado no debe disparar");
    }

    @Test
    @DisplayName("Un limite de inactividad no positivo desactiva la regla")
    void nonPositiveWindowDisablesRule() {
        Instant now = Instant.now();
        Instant hourAgo = now.minus(1, ChronoUnit.HOURS);

        InactivityRule normal = InactivityRule.create(UUID.randomUUID(), UUID.randomUUID(),
                "Sin reportar", 30, true);
        assertTrue(normal.isInactive(hourAgo, now));
        assertFalse(normal.isInactive(now.minus(5, ChronoUnit.MINUTES), now));

        InactivityRule broken = InactivityRule.create(UUID.randomUUID(), UUID.randomUUID(),
                "Mal configurada", 0, true);
        assertFalse(broken.isInactive(hourAgo, now),
                "sin esta guarda todo dispositivo estaria siempre inactivo");
        assertFalse(normal.isInactive(null, now), "sin ultima actividad no se puede concluir");
    }

    @Test
    @DisplayName("Resolver una alerta sella la fecha aunque el cliente no la envie")
    void resolvingAlertStampsDate() {
        Alert alert = Alert.raise(UUID.randomUUID(), UUID.randomUUID(), null, null,
                "threshold_exceeded", "Consumo alto", "Se supero el umbral", "high", null, null);

        assertEquals(Alert.STATUS_ACTIVE, alert.getStatus());
        assertNull(alert.getResolvedAt());

        alert.updateStatus(Alert.STATUS_RESOLVED, null);

        assertEquals(Alert.STATUS_RESOLVED, alert.getStatus());
        assertNotNull(alert.getResolvedAt());
    }

    @Test
    @DisplayName("La bitacora distingue un envio correcto de uno fallido")
    void notificationLogRecordsOutcome() {
        UUID alertId = UUID.randomUUID();

        NotificationLog ok = NotificationLog.sent(alertId, "email", "a@b.pe");
        assertEquals(NotificationLog.STATUS_SENT, ok.getStatus());
        assertNotNull(ok.getSentAt());
        assertNull(ok.getErrorMessage());

        NotificationLog ko = NotificationLog.failed(alertId, "email", "a@b.pe", "SMTP timeout");
        assertEquals(NotificationLog.STATUS_FAILED, ko.getStatus());
        assertNull(ko.getSentAt());
        assertEquals("SMTP timeout", ko.getErrorMessage());
    }
}
