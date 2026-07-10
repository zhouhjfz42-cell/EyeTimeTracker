package com.eyetimetracker.android;

public final class FamilySettingsPolicy {
    private FamilySettingsPolicy() {
    }

    public static boolean showJoinAsChildEntry(DeviceRole role) {
        return role == DeviceRole.PERSONAL_DEVICE;
    }

    public static boolean canOpenAddChildDeviceBinding(DeviceRole role, boolean hasBoundChildDevice) {
        return role == DeviceRole.PARENT_DEVICE && !hasBoundChildDevice;
    }
}
