package com.sems.devices.domain.model.aggregates;

import com.sems.devices.domain.model.valueobjects.ConnectionProtocol;
import com.sems.devices.domain.model.valueobjects.DeviceStatus;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Raiz del agregado Device.
 *
 * <p>Portado del agregado en Go. Toda modificacion pasa por sus metodos, de modo
 * que las reglas de negocio no se pueden saltar: el estado invalido no es
 * representable desde fuera del agregado.
 *
 * <p>Es dominio puro, sin anotaciones de persistencia. El mapeo a tabla vive en
 * la capa de infraestructura, igual que en el servicio original separaba el
 * modelo de dominio del modelo de GORM.
 */
@Getter
public class Device {

    private final UUID deviceId;
    private final String externalDeviceCode;
    private final UUID userId;

    /**
     * Local en el que esta instalado. Obligatorio.
     *
     * <p>El medidor, el contrato y la factura son del local, no de la persona.
     * Sin este dato no se puede saber a que suministro imputar el consumo.
     */
    private final UUID siteId;

    /**
     * Zona concreta dentro del local. Opcional.
     *
     * <p>Se admite vacia porque al abrir un local puede que aun no esten
     * definidas las zonas, y bloquear el alta del medidor por eso seria molesto.
     */
    private UUID zoneId;

    private String deviceName;
    private String deviceType;
    private String brand;
    private String model;
    private ConnectionProtocol connectionProtocol;
    private DeviceStatus status;
    private final Instant registeredAt;
    private Instant updatedAt;

    private Device(UUID deviceId, String externalDeviceCode, UUID userId, UUID siteId, UUID zoneId,
                   String deviceName, String deviceType, String brand, String model,
                   ConnectionProtocol connectionProtocol, DeviceStatus status, Instant registeredAt,
                   Instant updatedAt) {
        this.deviceId = deviceId;
        this.externalDeviceCode = externalDeviceCode;
        this.userId = userId;
        this.siteId = siteId;
        this.zoneId = zoneId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.brand = brand;
        this.model = model;
        this.connectionProtocol = connectionProtocol;
        this.status = status;
        this.registeredAt = registeredAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Unica forma correcta de crear un dispositivo nuevo. Valida cada campo
     * obligatorio antes de construir, de modo que un Device recien creado
     * siempre es valido.
     */
    public static Device register(String externalCode, UUID userId, UUID siteId, UUID zoneId,
                                  String name, String deviceType, String brand, String model,
                                  ConnectionProtocol protocol) {
        if (isBlank(externalCode)) {
            throw AppException.validation("external_device_code is required");
        }
        if (userId == null) {
            throw AppException.validation("user_id is required");
        }
        if (siteId == null) {
            throw AppException.validation("site_id is required");
        }
        if (isBlank(name)) {
            throw AppException.validation("device_name is required");
        }
        if (isBlank(deviceType)) {
            throw AppException.validation("device_type is required");
        }
        if (protocol == null) {
            throw AppException.validation("connection_protocol is invalid");
        }
        Instant now = Instant.now();
        return new Device(UUID.randomUUID(), externalCode.trim(), userId, siteId, zoneId,
                name.trim(), deviceType.trim(), normalizeOptional(brand), normalizeOptional(model),
                protocol, DeviceStatus.ACTIVE, now, now);
    }

    /**
     * Reconstruye un dispositivo a partir de datos ya persistidos.
     *
     * <p>A diferencia de {@link #register}, no revalida: esos datos ya eran
     * validos cuando se guardaron.
     */
    public static Device rehydrate(UUID deviceId, String externalCode, UUID userId, UUID siteId,
                                   UUID zoneId, String name, String deviceType, String brand,
                                   String model, ConnectionProtocol protocol, DeviceStatus status,
                                   Instant registeredAt, Instant updatedAt) {
        return new Device(deviceId, externalCode, userId, siteId, zoneId, name, deviceType, brand,
                model, protocol, status, registeredAt, updatedAt);
    }

    /** Un dispositivo eliminado queda congelado y ya no admite ediciones. */
    public void updateDetails(String name, String deviceType, String brand, String model,
                              ConnectionProtocol protocol, UUID zoneId) {
        if (isRemoved()) {
            throw AppException.conflict("removed devices cannot be updated");
        }
        if (isBlank(name)) {
            throw AppException.validation("device_name is required");
        }
        if (isBlank(deviceType)) {
            throw AppException.validation("device_type is required");
        }
        if (protocol == null) {
            throw AppException.validation("connection_protocol is invalid");
        }
        this.deviceName = name.trim();
        this.deviceType = deviceType.trim();
        this.brand = normalizeOptional(brand);
        this.model = normalizeOptional(model);
        this.connectionProtocol = protocol;
        // La zona si se puede cambiar: un equipo se traslada de la sala de
        // ventas al almacen sin dejar de ser el mismo equipo. El local no, que
        // seria otro suministro y romperia la continuidad del historico.
        this.zoneId = zoneId;
        this.updatedAt = Instant.now();
    }

    /** La transicion la decide el value object, no el agregado. */
    public void changeStatus(DeviceStatus next) {
        if (next == null) {
            throw AppException.validation("device status is invalid");
        }
        if (!this.status.canTransitionTo(next)) {
            throw AppException.conflict("invalid device status transition");
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    /** Borrado logico: la fila permanece, solo cambia el estado. */
    public void remove() {
        changeStatus(DeviceStatus.REMOVED);
    }

    public void ensureCanBeBound() {
        if (isRemoved()) {
            throw AppException.conflict("removed devices cannot be linked");
        }
    }

    public void ensureCanUpdateConfiguration() {
        if (isRemoved()) {
            throw AppException.conflict("removed devices cannot update configuration");
        }
    }

    public boolean isRemoved() {
        return this.status == DeviceStatus.REMOVED;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Trata "en blanco" y "no informado" como lo mismo: ausencia de valor. */
    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
