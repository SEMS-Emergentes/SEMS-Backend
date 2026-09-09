package com.sems.iam.domain.model.valueobjects;

/**
 * Rol de la persona en la plataforma.
 *
 * <p>Es distinto del papel que tiene dentro de una organizacion, que vive en el
 * modulo de organizaciones y decide que locales puede ver. Aqui solo se
 * distingue a quien usa el producto de quien lo administra.
 *
 * <p>Antes el rol por defecto se llamaba RESIDENT, del segmento residencial. Ya
 * no describe a nadie: quien entra ahora es el personal de un establecimiento.
 */
public enum RoleName {
    /** Administrador de la plataforma. */
    ADMIN,

    /** Personal de un establecimiento. Es el rol por defecto. */
    STAFF;

    public static RoleName from(String raw) {
        return RoleName.valueOf(raw.trim().toUpperCase());
    }
}
