package com.sems.devices.application;

import com.sems.devices.domain.model.aggregates.Device;
import com.sems.devices.domain.model.entities.DeviceBinding;
import com.sems.devices.domain.model.entities.DeviceConfiguration;
import com.sems.devices.domain.model.entities.DeviceEvent;
import com.sems.devices.domain.model.valueobjects.ConnectionProtocol;
import com.sems.devices.domain.model.valueobjects.DeviceStatus;
import com.sems.organizations.interfaces.acl.OrganizationsAcl;
import com.sems.devices.domain.repositories.*;
import com.sems.shared.errors.AppException;
import com.sems.shared.events.DomainEventBus;
import com.sems.shared.events.DomainEvents;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso que modifican el estado del modulo de dispositivos.
 *
 * <p>Orquesta agregados y repositorios, pero no contiene reglas de negocio:
 * esas viven en el dominio. Publica eventos al bus para que otros modulos
 * reaccionen, igual que el servicio original publicaba a los topics de Kafka.
 */
@Service
@RequiredArgsConstructor
public class DeviceCommandService {

    private final DeviceRepository devices;
    private final DeviceBindingRepository bindings;
    private final DeviceConfigurationRepository configurations;
    private final DeviceEventRepository events;
    private final OrganizationsAcl sites;
    private final DomainEventBus bus;

    @Transactional
    public Device register(String externalCode, UUID userId, UUID siteId, UUID zoneId, String name,
                           String type, String brand, String model, String protocol) {
        if (externalCode != null && devices.existsByExternalCode(externalCode.trim())) {
            throw AppException.conflict("a device with that external_device_code already exists");
        }
        validarUbicacion(siteId, zoneId);
        Device device = Device.register(externalCode, userId, siteId, zoneId, name, type, brand,
                model, ConnectionProtocol.of(protocol));
        Device saved = devices.save(device);
        bus.publish(new DomainEvents.DeviceRegistered(
                saved.getUserId(), saved.getDeviceId(), saved.getDeviceName(), saved.getDeviceType()));
        return saved;
    }

    @Transactional
    public Device update(UUID deviceId, String name, String type, String brand,
                         String model, String protocol, UUID zoneId) {
        Device device = require(deviceId);

        // La zona nueva tiene que ser del mismo local. Sin comprobarlo se podria
        // mover un equipo al local de otra empresa pasando el identificador a
        // mano, y su consumo empezaria a contar en la factura equivocada.
        if (zoneId != null && !sites.zoneBelongsToSite(zoneId, device.getSiteId())) {
            throw AppException.validation("the zone does not belong to this device's site");
        }

        device.updateDetails(name, type, brand, model, ConnectionProtocol.of(protocol), zoneId);
        return devices.save(device);
    }

    /** Comprueba que el local existe y que la zona es suya. */
    private void validarUbicacion(UUID siteId, UUID zoneId) {
        if (siteId == null) {
            throw AppException.validation("site_id is required");
        }
        // Mismo criterio que en energia y en organizaciones: no existe es 404,
        // archivado es 409. Antes esta linea devolvia 404 para ambos casos.
        if (!sites.siteExists(siteId)) {
            throw AppException.notFound("site not found");
        }
        if (!sites.siteIsActive(siteId)) {
            throw AppException.conflict("the site is not active");
        }
        if (zoneId != null && !sites.zoneBelongsToSite(zoneId, siteId)) {
            throw AppException.validation("the zone does not belong to the given site");
        }
    }

    @Transactional
    public Device changeStatus(UUID deviceId, String status) {
        Device device = require(deviceId);
        device.changeStatus(DeviceStatus.of(status));
        Device saved = devices.save(device);
        bus.publish(new DomainEvents.DeviceStatusUpdated(
                saved.getUserId(), saved.getDeviceId(), saved.getStatus().name()));
        return saved;
    }

    /** Borrado logico: el dispositivo pasa a REMOVED y deja de listarse. */
    @Transactional
    public void remove(UUID deviceId) {
        Device device = require(deviceId);
        // Dar de baja lo ya dado de baja no es un error: el cliente pidio un
        // estado y ese estado es el que hay. Antes la segunda llamada devolvia
        // 409 "invalid device status transition", mientras desvincular un
        // medidor dos veces respondia 204 las dos veces. Dos operaciones
        // hermanas no deberian responder distinto a lo mismo.
        if (device.isRemoved()) {
            return;
        }
        device.remove();
        devices.save(device);
        bus.publish(new DomainEvents.DeviceStatusUpdated(
                device.getUserId(), device.getDeviceId(), DeviceStatus.REMOVED.name()));
    }

    @Transactional
    public DeviceBinding bind(UUID deviceId, UUID userId, UUID siteId) {
        Device device = require(deviceId);
        device.ensureCanBeBound();
        if (bindings.findActiveByDeviceId(deviceId).isPresent()) {
            throw AppException.conflict("device already has an active binding");
        }
        DeviceBinding saved = bindings.save(DeviceBinding.create(deviceId, userId, siteId));
        bus.publish(new DomainEvents.DeviceLinked(userId, deviceId, saved.getBindingId()));
        return saved;
    }

    @Transactional
    public DeviceBinding unbind(UUID bindingId) {
        DeviceBinding binding = bindings.findById(bindingId)
                .orElseThrow(() -> AppException.notFound("binding not found"));
        binding.unlink();
        DeviceBinding saved = bindings.save(binding);
        bus.publish(new DomainEvents.DeviceUnlinked(
                saved.getUserId(), saved.getDeviceId(), saved.getBindingId()));
        return saved;
    }

    /** Si la clave ya existe para ese dispositivo, se actualiza su valor. */
    @Transactional
    public DeviceConfiguration upsertConfiguration(UUID deviceId, String key, String value) {
        Device device = require(deviceId);
        device.ensureCanUpdateConfiguration();
        return configurations.findByDeviceIdAndKey(deviceId, key == null ? null : key.trim())
                .map(existing -> {
                    existing.update(value);
                    return configurations.save(existing);
                })
                .orElseGet(() -> configurations.save(DeviceConfiguration.create(deviceId, key, value)));
    }

    @Transactional
    public DeviceConfiguration updateConfiguration(UUID configurationId, String value) {
        DeviceConfiguration configuration = configurations.findById(configurationId)
                .orElseThrow(() -> AppException.notFound("configuration not found"));
        configuration.update(value);
        return configurations.save(configuration);
    }

    @Transactional
    public DeviceEvent recordEvent(UUID deviceId, String eventType, String description, Instant occurredAt) {
        require(deviceId);
        return events.save(DeviceEvent.create(deviceId, eventType, description, occurredAt));
    }

    private Device require(UUID deviceId) {
        return devices.findById(deviceId)
                .orElseThrow(() -> AppException.notFound("device not found"));
    }
}
