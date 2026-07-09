package com.eyetimetracker.android;

public enum DeviceRole {
    PERSONAL_DEVICE("personal_device"),
    PARENT_DEVICE("parent_device"),
    CHILD_DEVICE("child_device"),
    PC_COMPANION("pc_companion");

    private final String storageValue;

    DeviceRole(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static DeviceRole fromStorageValue(String value) {
        if (value != null) {
            for (DeviceRole role : values()) {
                if (role.storageValue.equals(value.trim())) {
                    return role;
                }
            }
        }
        return PERSONAL_DEVICE;
    }
}
