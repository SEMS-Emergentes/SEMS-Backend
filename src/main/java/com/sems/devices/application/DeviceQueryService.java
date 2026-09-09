package com.sems.devices.application;

import com.sems.devices.domain.model.aggregates.Device;
import com.sems.devices.domain.model.entities.DeviceBinding;
import com.sems.devices.domain.model.entities.DeviceConfiguration;
import com.sems.devices.domain.model.entities.DeviceEvent;
import com.sems.devices.domain.repositories.*;
import com.sems.shared.errors.AppException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de solo lectura del modulo de dispositivos. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceQueryService {

    private final DeviceRepository devices;
    private final DeviceBindingRepository bindings;
    private final DeviceConfigurationRepository configurations;
    private final DeviceEventRepository events;

    public List<Device> allDevices() {
        return devices.findAll();
    }

    public Device deviceById(UUID deviceId) {
        return devices.findById(deviceId)
                .orElseThrow(() -> AppException.notFound("device not found"));
    }

    public List<Device> devicesByUser(UUID userId) {
        return devices.findByUserId(userId);
    }

    /** Dispositivos instalados en un local. */
    public List<Device> devicesBySite(UUID siteId) {
        return devices.findBySiteId(siteId);
    }

    /** Dispositivos de una zona concreta. */
    public List<Device> devicesByZone(UUID zoneId) {
        return devices.findByZoneId(zoneId);
    }

    public List<DeviceBinding> bindingsByDevice(UUID deviceId) {
        return bindings.findByDeviceId(deviceId);
    }

    public List<DeviceBinding> bindingsByUser(UUID userId) {
        return bindings.findByUserId(userId);
    }

    public List<DeviceConfiguration> configurationsByDevice(UUID deviceId) {
        return configurations.findByDeviceId(deviceId);
    }

    public List<DeviceEvent> eventsByDevice(UUID deviceId) {
        return events.findByDeviceId(deviceId);
    }
}
