package com.sems.organizations.domain.model.valueobjects;

/**
 * Categoria tarifaria del suministro, segun el pliego de OSINERGMIN.
 *
 * <p>Aqui esta la diferencia economica de fondo con el segmento residencial. Una
 * vivienda en BT5B paga un precio unico por kWh. Un establecimiento en BT3, BT4,
 * MT2 o MT3 paga tres cosas distintas: la energia consumida en hora punta, la
 * consumida fuera de punta, y un cargo por potencia sobre la demanda maxima
 * registrada en el mes.
 *
 * <p>Ese cargo por potencia es lo que sorprende a un local: un pico de quince
 * minutos al arrancar los compresores encarece la factura del mes entero, aunque
 * la energia total no haya subido.
 */
public enum TariffCategory {
    /** Baja tension sin cargo por potencia. Solo para locales muy pequenos. */
    BT5B,
    BT3,
    BT4,
    MT2,
    MT3;

    public static TariffCategory of(String value) {
        return OrgEnums.parse(TariffCategory.class, value, "tariff_category");
    }

    /** Si esta categoria cobra por potencia ademas de por energia. */
    public boolean cobraPorPotencia() {
        return this != BT5B;
    }
}
