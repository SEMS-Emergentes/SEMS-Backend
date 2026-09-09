package com.sems.organizations.infrastructure.persistence.jpa.repositories;

import com.sems.organizations.domain.model.valueobjects.OrgStatus;
import com.sems.organizations.infrastructure.persistence.jpa.entities.SiteJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteJpa extends JpaRepository<SiteJpaEntity, UUID> {

    /**
     * Locales vigentes de la organizacion, ordenados por codigo.
     *
     * <p>Los archivados quedan en la base porque de ellos cuelga el historico de
     * consumo, pero no se listan: si salieran, un local cerrado seguiria
     * apareciendo en el panel y contando en las comparaciones entre tiendas.
     */
    List<SiteJpaEntity> findByOrganizationIdAndStatusNotOrderBySiteCodeAsc(UUID organizationId,
                                                                          OrgStatus status);

    boolean existsByOrganizationIdAndSiteCode(UUID organizationId, String siteCode);
}
