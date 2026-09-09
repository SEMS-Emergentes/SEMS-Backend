package com.sems.alerts.domain.model.valueobjects;

/** Que tan cerca esta la demanda de la potencia contratada. */
public enum DemandLevel {
    /** Por debajo del aviso. No hay nada que decir. */
    OK,

    /** Se acerca a lo contratado. Aun se puede evitar el recargo. */
    WARNING,

    /** Ya lo supero. El recargo del mes esta hecho. */
    EXCEEDED
}
