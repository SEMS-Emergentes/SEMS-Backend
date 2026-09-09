package com.sems.devices.domain.model.entities;

import com.sems.devices.domain.model.valueobjects.BindingStatus;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Vinculo entre un dispositivo y la persona que lo opera, en un local. */
@Getter
public class DeviceBinding {

    private final UUID bindingId;
    private final UUID deviceId;
    private final UUID userId;
    private final UUID siteId;
    private BindingStatus bindingStatus;
    private final Instant linkedAt;
    private Instant unlinkedAt;
    private Instant updatedAt;

    private DeviceBinding(UUID bindingId, UUID deviceId, UUID userId, UUID siteId,
                          BindingStatus bindingStatus, Instant linkedAt,
                          Instant unlinkedAt, Instant updatedAt) {
        this.bindingId = bindingId;
        this.deviceId = deviceId;
        this.userId = userId;
        this.siteId = siteId;
        this.bindingStatus = bindingStatus;
        this.linkedAt = linkedAt;
        this.unlinkedAt = unlinkedAt;
        this.updatedAt = updatedAt;
    }

    public static DeviceBinding create(UUID deviceId, UUID userId, UUID siteId) {
        if (deviceId == null) {
            throw AppException.validation("device_id is required");
        }
        if (userId == null) {
            throw AppException.validation("user_id is required");
        }
        Instant now = Instant.now();
        return new DeviceBinding(UUID.randomUUID(), deviceId, userId, siteId,
                BindingStatus.LINKED, now, null, now);
    }

    public static DeviceBinding rehydrate(UUID bindingId, UUID deviceId, UUID userId, UUID siteId,
                                          BindingStatus status, Instant linkedAt,
                                          Instant unlinkedAt, Instant updatedAt) {
        return new DeviceBinding(bindingId, deviceId, userId, siteId, status,
                linkedAt, unlinkedAt, updatedAt);
    }

    /** Desvincular dos veces es un conflicto, no una operacion idempotente. */
    public void unlink() {
        if (this.bindingStatus == BindingStatus.UNLINKED) {
            throw AppException.conflict("binding is already unlinked");
        }
        Instant now = Instant.now();
        this.bindingStatus = BindingStatus.UNLINKED;
        this.unlinkedAt = now;
        this.updatedAt = now;
    }
}
