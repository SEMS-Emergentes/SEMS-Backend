package com.sems.analytics.application;

import com.sems.analytics.domain.repositories.AnalyticsRepositories.AnomalyRepository;
import com.sems.analytics.domain.repositories.AnalyticsRepositories.RecommendationRepository;
import com.sems.shared.security.AccesoUsuario;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Comprueba que la recomendacion o la anomalia son del usuario. */
@Component("accesoAnalitica")
@RequiredArgsConstructor
public class AccesoAnalitica {

    private final RecommendationRepository recommendations;
    private final AnomalyRepository anomalies;
    private final AccesoUsuario accesoUsuario;

    @Transactional(readOnly = true)
    public boolean recomendacion(UUID recommendationId) {
        if (recommendationId == null) {
            return true;
        }
        return recommendations.findById(recommendationId)
                .map(r -> accesoUsuario.esMio(r.getUserId()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean anomalia(UUID anomalyId) {
        if (anomalyId == null) {
            return true;
        }
        return anomalies.findById(anomalyId)
                .map(a -> accesoUsuario.esMio(a.getUserId()))
                .orElse(true);
    }
}
