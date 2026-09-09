package com.sems.organizations.domain.model.valueobjects;

/**
 * Papel de una persona dentro de una organizacion.
 *
 * <p>Un establecimiento no lo lleva una sola persona, a diferencia de una casa.
 * El alcance de cada papel se resuelve en el vinculo: el administrador manda en
 * toda la cadena, el supervisor solo en su local.
 */
public enum MembershipRole {
    /** Gestiona la organizacion entera: locales, personas y suscripcion. */
    ORG_ADMIN,

    /** Gestiona el local que tiene asignado. */
    SUPERVISOR,

    /** Solo consulta. */
    OPERATOR;

    public static MembershipRole of(String value) {
        return OrgEnums.parse(MembershipRole.class, value, "role");
    }
}
