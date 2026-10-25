package com.sems.energy.domain.model.valueobjects;

import com.sems.shared.errors.AppException;
import java.util.Set;

/**
 * Categoria del pliego tarifario, validada.
 *
 * <p>Existe porque el adaptador de precios aceptaba cualquier cadena y
 * respondia con importes de aspecto creible: pedir la tarifa de "XX9" devolvia
 * 200 y una lista de precios. Para una pantalla que estima la factura del mes
 * eso es peor que un error, porque el numero equivocado no se distingue del
 * correcto.
 *
 * <p>La validacion vive en el dominio y no en el adaptador a proposito: el dia
 * que se conecte un proveedor real, el proveedor nuevo hereda la regla sin que
 * haya que acordarse de volver a escribirla.
 */
public final class TariffCategory {

    /**
     * Las categorias del pliego de OSINERGMIN que aplican a un local comercial.
     *
     * <p>BT es baja tension y MT media tension. BT5B es la unica sin cargo por
     * potencia; se mantiene porque un local pequeno puede estar en ella.
     */
    private static final Set<String> VALIDAS = Set.of("BT5B", "BT3", "BT4", "MT2", "MT3");

    /** Cuando no se indica categoria se asume media tension de dos franjas. */
    public static final String POR_DEFECTO = "MT2";

    private TariffCategory() {
    }

    /**
     * Normaliza la categoria o rechaza la peticion.
     *
     * <p>Vacio o nulo no es un error: es "no me consta", y se resuelve con la
     * categoria por defecto. Lo que si es un error es nombrar una categoria que
     * no existe, porque entonces quien pregunta cree estar pidiendo otra cosa.
     */
    public static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return POR_DEFECTO;
        }
        String categoria = valor.trim().toUpperCase();
        if (!VALIDAS.contains(categoria)) {
            throw AppException.validation(
                    "tariff_category '" + valor.trim() + "' is not in the schedule; expected one of "
                            + VALIDAS.stream().sorted().toList());
        }
        return categoria;
    }
}
