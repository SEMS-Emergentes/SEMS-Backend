package com.sems.energy.domain.model.valueobjects;

/**
 * Franja horaria de un consumo.
 *
 * <p>En el segmento residencial no existia: una vivienda paga lo mismo a
 * cualquier hora. Un establecimiento en tarifa comercial no.
 */
public enum FranjaHoraria {
    /** Hora punta. */
    PUNTA,

    /** Fuera de punta. */
    FUERA_DE_PUNTA
}
