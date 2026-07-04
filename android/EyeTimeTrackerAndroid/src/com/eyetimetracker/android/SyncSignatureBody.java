package com.eyetimetracker.android;

public final class SyncSignatureBody {
    private SyncSignatureBody() {
    }

    public static String forSyncRequest(String deviceId, String platform, long sinceUnixSeconds) {
        return "DeviceId=" + safe(deviceId)
                + "\nPlatform=" + safe(platform)
                + "\nSinceUnixSeconds=" + sinceUnixSeconds;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
