package com.sems.devices.infrastructure.persistence.jpa.adapters;

import com.sems.devices.domain.model.aggregates.Device;
import com.sems.devices.domain.model.valueobjects.DeviceStatus;
import com.sems.devices.domain.repositories.DeviceRepository;
import com.sems.devices.infrastructure.persistence.jpa.repositories.DeviceJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Implementacion del puerto {@link DeviceRepository} sobre JPA. */
@Repository
@RequiredArgsConstructor
public class DeviceRepositoryAdapter implements DeviceRepository {

    private final DeviceJpaRepository jpa;

    @Override
    public Device save(Device device) {
        return DeviceMapper.toDomain(jpa.save(DeviceMapper.toEntity(device)));
    }

    @Override
    public Optional<Device> findById(UUID deviceId) {
        return jpa.findById(deviceId).map(DeviceMapper::toDomain);
    }

    @Override
    public Optional<Device> findByExternalCode(String externalDeviceCode) {
        return jpa.findByExternalDeviceCode(externalDeviceCode).map(DeviceMapper::toDomain);
    }

    @Override
    public List<Device> findAll() {
        return jpa.findAll().stream().map(DeviceMapper::toDomain).toList();
    }

    @Override
    public List<Device> findByUserId(UUID userId) {
        return jpa.findByUserIdAndStatusNot(userId, DeviceStatus.REMOVED)
                  .stream().map(DeviceMapper::toDomain).toList();
    }

    @Override
    public boolean existsByExternalCode(String externalDeviceCode) {
        return jpa.existsByExternalDeviceCode(externalDeviceCode);
    }
}
