package com.sems.iam.domain.model.valueobjects;

/**
 * Segmento comercial del usuario, los dos a los que apunta el producto.
 *
 * <p>Es un dato del perfil, no un permiso: no decide lo que se puede hacer,
 * solo con que cara se presenta la aplicacion.
 */
public enum UserSegment {
    /** Cadena con varios establecimientos. */
    RETAIL_CHAIN,
    /** Establecimiento independiente. */
    INDEPENDENT;

    /**
     * Acepta el valor tal como lo escribe el cliente, sin distinguir mayusculas
     * ni espacios. Devuelve null cuando no viene nada, porque el segmento es
     * opcional: el alta funciona igual sin declararlo.
     */
    public static UserSegment from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizado = value.trim().toUpperCase().replace('-', '_');
        for (UserSegment segmento : values()) {
            if (segmento.name().equals(normalizado)) {
                return segmento;
            }
        }
        throw new IllegalArgumentException(
                "segment must be one of RETAIL_CHAIN, INDEPENDENT");
    }
}
