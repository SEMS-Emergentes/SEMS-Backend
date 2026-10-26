package com.sems.alerts.domain.model.valueobjects;

import com.sems.shared.errors.AppException;
import java.util.Locale;
import java.util.Map;

/**
 * Estado de un aviso, validado y normalizado.
 *
 * <p>Antes era texto libre: el backend guardaba cualquier cosa que llegara. Un
 * estado inventado se almacenaba tal cual y quedaba fuera de todos los filtros,
 * asi que el aviso se volvia invisible sin que nada fallara. Es la misma perdida
 * silenciosa que las notificaciones huerfanas.
 *
 * <p>Y habia un problema de fondo: cliente y servidor usaban palabras distintas
 * para lo mismo. El servidor conocia {@code active} y {@code resolved}; la web
 * enviaba {@code open} y {@code acknowledged}. Funcionaba por accidente, porque
 * la web normaliza de vuelta al leer cualquier valor desconocido.
 *
 * <p>Se resuelve aceptando el vocabulario que la web ya usa y normalizandolo al
 * guardar, en lugar de imponer el del servidor. Asi no hace falta desplegar
 * ambos lados a la vez ni migrar lo que ya esta escrito: los alias viven aqui,
 * en el borde, y lo que llega a la base es siempre una de estas tres palabras.
 */
public enum AlertStatus {

    ACTIVE("active"),
    ACKNOWLEDGED("acknowledged"),
    RESOLVED("resolved");

    /**
     * Lo que se escribe en la base y se devuelve en la API.
     *
     * <p>En minuscula porque es lo que ya habia guardado y lo que la web espera
     * leer; cambiarlo seria romper por pulcritud.
     */
    private final String wire;

    /** Palabras de entrada que significan lo mismo que una de las tres. */
    private static final Map<String, AlertStatus> ALIAS = Map.of(
            "open", ACTIVE,
            "pending", ACTIVE,
            "closed", RESOLVED,
            "dismissed", RESOLVED);

    AlertStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public boolean esResuelta() {
        return this == RESOLVED;
    }

    /**
     * Interpreta el estado que llega de fuera.
     *
     * <p>Vacio o nulo es ACTIVE, que es como nace un aviso. Una palabra que no
     * se reconoce se rechaza: aceptarla era justamente lo que hacia desaparecer
     * avisos de los listados.
     */
    public static AlertStatus of(String valor) {
        if (valor == null || valor.isBlank()) {
            return ACTIVE;
        }
        String limpio = valor.trim().toLowerCase(Locale.ROOT);
        for (AlertStatus estado : values()) {
            if (estado.wire.equals(limpio)) {
                return estado;
            }
        }
        AlertStatus alias = ALIAS.get(limpio);
        if (alias != null) {
            return alias;
        }
        throw AppException.validation("status '" + valor.trim()
                + "' is not a valid alert status; expected active, acknowledged or resolved");
    }
}
