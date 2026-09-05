package com.sems.alerts.infrastructure.persistence.jpa.repositories;

import com.sems.alerts.infrastructure.persistence.jpa.entities.AlertJpaEntities.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de Spring Data para preference en el modulo de alertas.
 *
 * <p>Va en su propio archivo, y no anidado dentro de una clase contenedora,
 * porque Spring Data solo detecta interfaces de nivel superior: una anidada
 * compila igual pero nunca llega a registrarse como bean.</p>
 */
public interface PreferenceJpa extends JpaRepository<PreferenceRow, UUID> {
        List<PreferenceRow> findByUserId(UUID userId);
        Optional<PreferenceRow> findByUserIdAndChannel(UUID userId, String channel);
    
}
