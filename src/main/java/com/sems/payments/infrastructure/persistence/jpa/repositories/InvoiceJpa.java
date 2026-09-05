package com.sems.payments.infrastructure.persistence.jpa.repositories;

import com.sems.payments.infrastructure.persistence.jpa.entities.PaymentJpaEntities.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de Spring Data para invoice en el modulo de pagos.
 *
 * <p>Va en su propio archivo, y no anidado dentro de una clase contenedora,
 * porque Spring Data solo detecta interfaces de nivel superior: una anidada
 * compila igual pero nunca llega a registrarse como bean.</p>
 */
public interface InvoiceJpa extends JpaRepository<InvoiceRow, UUID> {
        Optional<InvoiceRow> findByPaymentId(UUID paymentId);
    
}
