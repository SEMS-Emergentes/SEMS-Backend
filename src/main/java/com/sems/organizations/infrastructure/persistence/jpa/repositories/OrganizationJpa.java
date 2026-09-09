package com.sems.organizations.infrastructure.persistence.jpa.repositories;

import com.sems.organizations.infrastructure.persistence.jpa.entities.OrganizationJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de Spring Data para organizaciones.
 *
 * <p>Va en su propio archivo y como interfaz de nivel superior: Spring Data no
 * detecta las interfaces anidadas dentro de otra clase, y si se anidan la
 * aplicacion arranca hasta que falla al inyectar el adaptador.
 */
public interface OrganizationJpa extends JpaRepository<OrganizationJpaEntity, UUID> {

    Optional<OrganizationJpaEntity> findByTaxId(String taxId);

    boolean existsByTaxId(String taxId);
}
