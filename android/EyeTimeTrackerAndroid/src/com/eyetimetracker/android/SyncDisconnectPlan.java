package com.eyetimetracker.android;

public final class SyncDisconnectPlan {
    public final SyncSettings localSettings;
    public final SyncSettings peerNotificationSettings;

    private SyncDisconnectPlan(SyncSettings localSettings, SyncSettings peerNotificationSettings) {
        this.localSettings = localSettings;
        this.peerNotificationSettings = peerNotificationSettings;
    }

    public static SyncDisconnectPlan create(SyncSettings current) {
        return new SyncDisconnectPlan(SyncSettings.unpaired(), copy(current));
    }

    private static SyncSettings copy(SyncSettings source) {
        SyncSettings copy = new SyncSettings();
        if (source == null) {
            return copy;
        }
        copy.isPaired = source.isPaired;
        copy.peerDeviceId = source.peerDeviceId;
        copy.peerPlatform = source.peerPlatform;
        copy.peerHost = source.peerHost;
        copy.peerPort = source.peerPort;
        copy.sharedSecret = source.sharedSecret;
        copy.lastSyncUnixSeconds = source.lastSyncUnixSeconds;
        copy.lastError = source.lastError;
        return copy;
    }
}
