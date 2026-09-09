package com.sems.devices.infrastructure.persistence.jpa.adapters;

import com.sems.devices.domain.model.aggregates.Device;
import com.sems.devices.domain.model.entities.DeviceBinding;
import com.sems.devices.domain.model.entities.DeviceConfiguration;
import com.sems.devices.domain.model.entities.DeviceEvent;
import com.sems.devices.infrastructure.persistence.jpa.entities.*;

/**
 * Traduce entre objetos de dominio y filas de base de datos.
 *
 * <p>Es el equivalente de {@code mappers.go}. Aisla al dominio de JPA: si
 * manana cambia el motor de persistencia, solo cambia esta clase.
 */
public final class DeviceMapper {

    private DeviceMapper() {
    }

    public static DeviceJpaEntity toEntity(Device device) {
        return new DeviceJpaEntity(
                device.getDeviceId(), device.getExternalDeviceCode(), device.getUserId(),
                device.getSiteId(), device.getZoneId(), device.getDeviceName(), device.getDeviceType(), device.getBrand(), device.getModel(),
                device.getConnectionProtocol(), device.getStatus(),
                device.getRegisteredAt(), device.getUpdatedAt());
    }

    public static Device toDomain(DeviceJpaEntity row) {
        return Device.rehydrate(row.getDeviceId(), row.getExternalDeviceCode(), row.getUserId(),
                row.getSiteId(), row.getZoneId(), row.getDeviceName(), row.getDeviceType(), row.getBrand(), row.getModel(),
                row.getConnectionProtocol(), row.getStatus(),
                row.getRegisteredAt(), row.getUpdatedAt());
    }

    public static DeviceBindingJpaEntity toEntity(DeviceBinding binding) {
        return new DeviceBindingJpaEntity(
                binding.getBindingId(), binding.getDeviceId(), binding.getUserId(), binding.getSiteId(),
                binding.getBindingStatus(), binding.getLinkedAt(),
                binding.getUnlinkedAt(), binding.getUpdatedAt());
    }

    public static DeviceBinding toDomain(DeviceBindingJpaEntity row) {
        return DeviceBinding.rehydrate(row.getBindingId(), row.getDeviceId(), row.getUserId(),
                row.getSiteId(), row.getBindingStatus(), row.getLinkedAt(),
                row.getUnlinkedAt(), row.getUpdatedAt());
    }

    public static DeviceConfigurationJpaEntity toEntity(DeviceConfiguration configuration) {
        return new DeviceConfigurationJpaEntity(
                configuration.getConfigurationId(), configuration.getDeviceId(),
                configuration.getConfigKey(), configuration.getConfigValue(),
                configuration.getUpdatedAt());
    }

    public static DeviceConfiguration toDomain(DeviceConfigurationJpaEntity row) {
        return DeviceConfiguration.rehydrate(row.getConfigurationId(), row.getDeviceId(),
                row.getConfigKey(), row.getConfigValue(), row.getUpdatedAt());
    }

    public static DeviceEventJpaEntity toEntity(DeviceEvent event) {
        return new DeviceEventJpaEntity(event.getEventId(), event.getDeviceId(),
                event.getEventType(), event.getDescription(), event.getOccurredAt());
    }

    public static DeviceEvent toDomain(DeviceEventJpaEntity row) {
        return DeviceEvent.rehydrate(row.getEventId(), row.getDeviceId(), row.getEventType(),
                row.getDescription(), row.getOccurredAt());
    }
}
