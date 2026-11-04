package com.sems.devices.interfaces.acl;

import java.util.List;
import java.util.UUID;

/**
 * Capa anticorrupcion del bounded context de dispositivos.
 *
 * <p>Nace porque el desglose de consumo se lee por nombre y no por
 * identificador: una tarjeta que enumera cuatro UUID no le dice nada a quien la
 * mira. El nombre del equipo es dato de este modulo, asi que se pregunta aqui
 * en lugar de copiarlo en energia, que es el mismo criterio con el que
 * organizaciones publica el nombre del local y de la zona.
 *
 * <p>Devuelve datos concretos y no el agregado, igual que {@code IamAcl}: un
 * cambio interno en {@code Device} no se propaga a quien pregunta.
 */
public interface DevicesAcl {

    /** Lo que otro modulo necesita de un equipo para nombrarlo en un desglose. */
    record DeviceSummary(UUID deviceId, String name, String deviceType) {
    }

    /**
     * Equipos de la persona, incluidos los que estan inactivos o desconectados.
     *
     * <p>Los inactivos entran a proposito: sus lecturas pasadas siguen contando
     * en el consumo del periodo, y dejarlos fuera convertiria ese consumo en
     * "sin asignar" solo por haber apagado el equipo.
     */
    List<DeviceSummary> devicesOf(UUID userId);
}
