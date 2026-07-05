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

        if (!peerOnline || peerState == null || !peerState.isCounting) {
            return true;
        }

        if (localState.currentSessionStartedUnixSeconds != peerState.currentSessionStartedUnixSeconds) {
            return localState.currentSessionStartedUnixSeconds > peerState.currentSessionStartedUnixSeconds;
        }

        String localDevice = localState.deviceId == null ? "" : localState.deviceId;
        String peerDevice = peerState.deviceId == null ? "" : peerState.deviceId;
        return localDevice.compareTo(peerDevice) >= 0;
    }
}
