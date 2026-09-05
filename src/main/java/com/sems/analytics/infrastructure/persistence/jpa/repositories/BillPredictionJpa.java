package com.sems.analytics.infrastructure.persistence.jpa.repositories;

import com.sems.analytics.infrastructure.persistence.jpa.entities.AnalyticsJpaEntities.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de Spring Data para billprediction en el modulo de analitica.
 *
 * <p>Va en su propio archivo, y no anidado dentro de una clase contenedora,
 * porque Spring Data solo detecta interfaces de nivel superior: una anidada
 * compila igual pero nunca llega a registrarse como bean.</p>
 */
public interface BillPredictionJpa extends JpaRepository<BillPredictionRow, UUID> {
        List<BillPredictionRow> findByUserIdOrderByGeneratedAtDesc(String userId);
    
}
