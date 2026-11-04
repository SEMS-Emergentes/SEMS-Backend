package com.sems.devices.interfaces.acl;

import com.sems.devices.domain.repositories.DeviceRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementacion de la capa anticorrupcion de dispositivos. */
@Service
@RequiredArgsConstructor
public class DevicesAclImpl implements DevicesAcl {

    private final DeviceRepository devices;

    @Override
    @Transactional(readOnly = true)
    public List<DeviceSummary> devicesOf(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return devices.findByUserId(userId).stream()
                .map(d -> new DeviceSummary(d.getDeviceId(), d.getDeviceName(), d.getDeviceType()))
                .toList();
    }
}
