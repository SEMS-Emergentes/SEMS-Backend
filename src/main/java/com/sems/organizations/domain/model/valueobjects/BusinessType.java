package com.sems.organizations.domain.model.valueobjects;

import com.sems.shared.errors.AppException;

/**
 * Tipo de negocio del establecimiento.
 *
 * <p>No es decorativo: el perfil de consumo cambia radicalmente entre uno y
 * otro. Un supermercado gasta la mayor parte en refrigeracion, que funciona las
 * 24 horas; un restaurante concentra el gasto en cocina y en las horas de
 * servicio. La analitica compara cada local contra otros de su mismo tipo.
 */
public enum BusinessType {
    SUPERMARKET,
    CONVENIENCE_STORE,
    DEPARTMENT_STORE,
    RESTAURANT,
    WAREHOUSE,
    OTHER;

    public static BusinessType of(String value) {
        return OrgEnums.parse(BusinessType.class, value, "business_type");
    }
}
