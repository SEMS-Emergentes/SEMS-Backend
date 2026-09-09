package com.sems.alerts.domain.model.entities;

import com.sems.alerts.domain.model.valueobjects.DemandLevel;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Regla que vigila la demanda de un local contra su potencia contratada.
 *
 * <p>Es la alerta que no tenia sentido en el segmento residencial: una vivienda
 * no paga por potencia, asi que su pico de demanda no le cuesta dinero. Un local
 * si, y ademas de una forma cruel: el pico mas alto del mes fija el cargo de los
 * treinta dias, aunque haya durado quince minutos.
 *
 * <p>Por eso el aviso llega <b>antes</b> de superar lo contratado y no despues:
 * una vez superado, el recargo del mes ya esta hecho y avisar solo sirve para
 * dar la mala noticia. Con margen, en cambio, todavia se puede apagar algo o
 * retrasar el arranque de un equipo.
 */
@Getter
public class DemandRule {

    private final UUID demandRuleId;
    private final UUID siteId;
    private final UUID userId;
    private String ruleName;
    private double contractedPowerKw;
    private double warningPercent;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    private DemandRule(UUID demandRuleId, UUID siteId, UUID userId, String ruleName,
                       double contractedPowerKw, double warningPercent, boolean active,
                       Instant createdAt, Instant updatedAt) {
        this.demandRuleId = demandRuleId;
        this.siteId = siteId;
        this.userId = userId;
        this.ruleName = ruleName;
        this.contractedPowerKw = contractedPowerKw;
        this.warningPercent = warningPercent;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DemandRule create(UUID siteId, UUID userId, String ruleName,
                                    double contractedPowerKw, Double warningPercent,
                                    Boolean active) {
        if (siteId == null) {
            throw AppException.validation("site_id is required");
        }
        if (contractedPowerKw <= 0) {
            throw AppException.validation("contracted_power_kw must be greater than zero");
        }

        // Por defecto se avisa al 85%. Deja margen para reaccionar sin generar
        // avisos constantes en un local que normalmente trabaja al 70-80%.
        double aviso = warningPercent == null ? 85d : warningPercent;
        if (aviso <= 0 || aviso > 100) {
            throw AppException.validation("warning_percent must be between 1 and 100");
        }

        Instant ahora = Instant.now();
        return new DemandRule(UUID.randomUUID(), siteId, userId, ruleName, contractedPowerKw,
                aviso, active == null || active, ahora, ahora);
    }

    public static DemandRule rehydrate(UUID demandRuleId, UUID siteId, UUID userId, String ruleName,
                                       double contractedPowerKw, double warningPercent,
                                       boolean active, Instant createdAt, Instant updatedAt) {
        return new DemandRule(demandRuleId, siteId, userId, ruleName, contractedPowerKw,
                warningPercent, active, createdAt, updatedAt);
    }

    /** Demanda a la que se dispara el aviso, en kW. */
    public double umbralDeAvisoKw() {
        return contractedPowerKw * warningPercent / 100d;
    }

    /** En que nivel cae una demanda medida. */
    public DemandLevel evaluar(double demandaKw) {
        if (!active) {
            return DemandLevel.OK;
        }
        if (demandaKw > contractedPowerKw) {
            return DemandLevel.EXCEEDED;
        }
        return demandaKw >= umbralDeAvisoKw() ? DemandLevel.WARNING : DemandLevel.OK;
    }

    /** Cuantos kW quedan hasta superar lo contratado. Negativo si ya se supero. */
    public double margenKw(double demandaKw) {
        return contractedPowerKw - demandaKw;
    }

    public void updateDetails(String ruleName, double contractedPowerKw, double warningPercent) {
        if (contractedPowerKw <= 0) {
            throw AppException.validation("contracted_power_kw must be greater than zero");
        }
        if (warningPercent <= 0 || warningPercent > 100) {
            throw AppException.validation("warning_percent must be between 1 and 100");
        }
        this.ruleName = ruleName;
        this.contractedPowerKw = contractedPowerKw;
        this.warningPercent = warningPercent;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
