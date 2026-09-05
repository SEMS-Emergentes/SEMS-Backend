package com.sems.alerts.infrastructure.persistence.jpa.repositories;

import com.sems.alerts.infrastructure.persistence.jpa.entities.AlertJpaEntities.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de Spring Data para inactivityrule en el modulo de alertas.
 *
 * <p>Va en su propio archivo, y no anidado dentro de una clase contenedora,
 * porque Spring Data solo detecta interfaces de nivel superior: una anidada
 * compila igual pero nunca llega a registrarse como bean.</p>
 */
public interface InactivityRuleJpa extends JpaRepository<InactivityRuleRow, UUID> {
        List<InactivityRuleRow> findByUserIdOrderByCreatedAtDesc(UUID userId);
        List<InactivityRuleRow> findByActiveTrue();
    
}
