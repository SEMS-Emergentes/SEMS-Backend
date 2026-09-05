package com.sems.subscriptions.infrastructure.persistence.jpa.repositories;

import com.sems.subscriptions.infrastructure.persistence.jpa.entities.SubscriptionJpaEntities.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de Spring Data para plan en el modulo de suscripciones.
 *
 * <p>Va en su propio archivo, y no anidado dentro de una clase contenedora,
 * porque Spring Data solo detecta interfaces de nivel superior: una anidada
 * compila igual pero nunca llega a registrarse como bean.</p>
 */
public interface PlanJpa extends JpaRepository<PlanRow, UUID> {
        Optional<PlanRow> findByName(String name);
        List<PlanRow> findByActiveTrueOrderByPriceAsc();
    
}
