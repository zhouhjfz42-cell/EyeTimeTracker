package com.eyetimetracker.android;

public final class SyncSettings {
    public boolean isPaired;
    public String peerDeviceId = "";
    public String peerPlatform = "";
    public String peerHost = "";
    public int peerPort;
    public String sharedSecret = "";
    public long lastSyncUnixSeconds;
    public String lastError = "";
    public ReminderRuntimeState peerReminderState = new ReminderRuntimeState();

    public static SyncSettings unpaired() {
        return new SyncSettings();
    }
}
