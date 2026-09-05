package com.sems.payments.infrastructure.persistence.jpa.repositories;

import com.sems.payments.infrastructure.persistence.jpa.entities.PaymentJpaEntities.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de Spring Data para payment en el modulo de pagos.
 *
 * <p>Va en su propio archivo, y no anidado dentro de una clase contenedora,
 * porque Spring Data solo detecta interfaces de nivel superior: una anidada
 * compila igual pero nunca llega a registrarse como bean.</p>
 */
public interface PaymentJpa extends JpaRepository<PaymentRow, UUID> {
        List<PaymentRow> findByUserIdOrderByCreatedAtDesc(UUID userId);
        List<PaymentRow> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);
        Optional<PaymentRow> findByStripePaymentIntentId(String stripePaymentIntentId);
    
}
