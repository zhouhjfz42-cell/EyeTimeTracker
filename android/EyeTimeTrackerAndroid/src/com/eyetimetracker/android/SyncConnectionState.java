package com.eyetimetracker.android;

public final class SyncConnectionState {
    private static final long PEER_OFFLINE_AFTER_SECONDS = 90L;

    private SyncConnectionState() {
    }

    public static boolean shouldClearPairingAfterSync(String responseJson, String lastError) {
        return AndroidSyncResponseReader.isPeerUnpaired(responseJson);
    }

    public static boolean isPeerOnline(SyncSettings settings, long nowUnixSeconds) {
        if (settings == null || !settings.isPaired || settings.lastSyncUnixSeconds <= 0L) {
            return false;
        }
        if (settings.lastError != null && !settings.lastError.trim().isEmpty()) {
            return false;
        }
        long ageSeconds = nowUnixSeconds - settings.lastSyncUnixSeconds;
        return ageSeconds >= 0L && ageSeconds <= PEER_OFFLINE_AFTER_SECONDS;
    }
}
