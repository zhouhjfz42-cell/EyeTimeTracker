package com.eyetimetracker.android;

public final class ReminderRuntimeState {
    public String deviceId = "";
    public String platform = "";
    public boolean isCounting;
    public long currentSessionStartedUnixSeconds;

    public ReminderRuntimeState() {
    }

    public ReminderRuntimeState(String deviceId, String platform, boolean isCounting, long currentSessionStartedUnixSeconds) {
        this.deviceId = deviceId == null ? "" : deviceId;
        this.platform = platform == null ? "" : platform;
        this.isCounting = isCounting;
        this.currentSessionStartedUnixSeconds = Math.max(0L, currentSessionStartedUnixSeconds);
    }
}
