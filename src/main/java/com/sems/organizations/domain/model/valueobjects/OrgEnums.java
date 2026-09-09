package com.sems.organizations.domain.model.valueobjects;

import com.sems.shared.errors.AppException;

/**
 * Conversion de texto no confiable a los enums del modulo.
 *
 * <p>Se hace en un solo sitio para que todos rechacen la entrada invalida de la
 * misma forma y nombrando el campo, en vez de dejar un mensaje generico que no
 * dice cual de los cinco campos venia mal.
 */
public final class OrgEnums {

    private OrgEnums() {
    }

    static <T extends Enum<T>> T parse(Class<T> tipo, String value, String campo) {
        if (value == null || value.isBlank()) {
            throw AppException.validation(campo + " is required");
        }
        try {
            return Enum.valueOf(tipo, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw AppException.validation(campo + " is invalid");
        }
    }
}
