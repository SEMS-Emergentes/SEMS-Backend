package com.sems.organizations.domain.model.valueobjects;

/**
 * Zona dentro de un local.
 *
 * <p>Sustituye a la "habitacion" del segmento residencial. La diferencia no es
 * solo de nombre: una camara frigorifica no se apaga de noche y una sala de
 * ventas si, asi que una misma alerta de "consumo fuera de horario" es normal
 * en una y sospechosa en la otra.
 */
public enum ZoneType {
    SALES_FLOOR,
    COLD_STORAGE,
    WAREHOUSE,
    KITCHEN,
    OFFICES,
    HVAC,
    PARKING,
    OTHER;

    public static ZoneType of(String value) {
        return OrgEnums.parse(ZoneType.class, value, "zone_type");
    }

    /** Si por su naturaleza sigue consumiendo con el local cerrado. */
    public boolean funcionaFueraDeHorario() {
        return this == COLD_STORAGE || this == HVAC;
    }
}
