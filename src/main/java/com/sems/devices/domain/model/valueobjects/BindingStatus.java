package com.sems.devices.domain.model.valueobjects;

import com.sems.shared.errors.AppException;

/** Estado del vinculo entre un dispositivo y quien lo opera. */
public enum BindingStatus {
    LINKED,
    UNLINKED,
    PENDING;

    public static BindingStatus of(String value) {
        if (value == null) {
            throw AppException.validation("invalid binding status");
        }
        try {
            return BindingStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw AppException.validation("invalid binding status");
        }
    }
}
