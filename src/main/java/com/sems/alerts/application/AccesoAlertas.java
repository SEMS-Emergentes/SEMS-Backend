package com.sems.alerts.application;

import com.sems.alerts.domain.repositories.AlertRepositories.AlertRepository;
import com.sems.alerts.domain.repositories.AlertRepositories.ThresholdRepository;
import com.sems.alerts.domain.repositories.DemandRuleRepository;
import com.sems.shared.security.AccesoUsuario;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Comprueba que la alerta, el umbral o la regla de demanda son del usuario. */
@Component("accesoAlertas")
@RequiredArgsConstructor
public class AccesoAlertas {

    private final AlertRepository alerts;
    private final ThresholdRepository thresholds;
    private final DemandRuleRepository demandRules;
    private final AccesoUsuario accesoUsuario;

    @Transactional(readOnly = true)
    public boolean alerta(UUID alertId) {
        if (alertId == null) {
            return true;
        }
        return alerts.findById(alertId)
                .map(a -> accesoUsuario.esMio(a.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean umbral(UUID thresholdId) {
        if (thresholdId == null) {
            return true;
        }
        return thresholds.findById(thresholdId)
                .map(t -> accesoUsuario.esMio(t.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean reglaDeDemanda(UUID demandRuleId) {
        if (demandRuleId == null) {
            return true;
        }
        return demandRules.findById(demandRuleId)
                .map(r -> accesoUsuario.esMio(r.getUserId()))
                .orElse(true);
    }
}
