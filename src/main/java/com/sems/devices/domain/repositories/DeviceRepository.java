package com.sems.devices.domain.repositories;

import com.sems.devices.domain.model.aggregates.Device;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida del agregado Device.
 *
 * <p>Declarado en el dominio y hablado en objetos de dominio: la capa de
 * aplicacion no sabe si detras hay JPA, un documento o una llamada remota.
 */
public interface DeviceRepository {

    Device save(Device device);

    Optional<Device> findById(UUID deviceId);

    Optional<Device> findByExternalCode(String externalDeviceCode);

    List<Device> findAll();

    List<Device> findByUserId(UUID userId);

    /** Dispositivos vigentes instalados en un local. */
    List<Device> findBySiteId(UUID siteId);

    /** Dispositivos vigentes de una zona concreta. */
    List<Device> findByZoneId(UUID zoneId);

    boolean existsByExternalCode(String externalDeviceCode);
}
