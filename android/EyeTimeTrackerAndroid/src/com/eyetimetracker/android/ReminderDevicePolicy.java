package com.eyetimetracker.android;

public final class ReminderDevicePolicy {
    private ReminderDevicePolicy() {
    }

    public static boolean shouldShowOnLocalDevice(
            ReminderRuntimeState localState,
            ReminderRuntimeState peerState,
            boolean peerOnline) {
        if (localState == null || !localState.isCounting) {
            return false;
        }

        return true;
    }
}
