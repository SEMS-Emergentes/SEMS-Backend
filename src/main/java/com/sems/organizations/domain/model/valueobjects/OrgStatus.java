package com.sems.organizations.domain.model.valueobjects;

/** Estado de una organizacion, local o zona. */
public enum OrgStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED;

    public static OrgStatus of(String value) {
        return OrgEnums.parse(OrgStatus.class, value, "status");
    }
}
