package com.eyetimetracker.android;

public final class SyncConnectionState {
    private SyncConnectionState() {
    }

    public static boolean shouldClearPairingAfterSync(String responseJson, String lastError) {
        return AndroidSyncResponseReader.isPeerUnpaired(responseJson);
    }
}
