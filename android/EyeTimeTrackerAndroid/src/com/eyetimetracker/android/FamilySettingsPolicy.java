package com.eyetimetracker.android;

public final class FamilySettingsPolicy {
    private FamilySettingsPolicy() {
    }

    public static boolean showJoinAsChildEntry(DeviceRole role) {
        return role == DeviceRole.PERSONAL_DEVICE;
    }
}
