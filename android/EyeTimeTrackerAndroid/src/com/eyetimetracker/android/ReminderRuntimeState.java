package com.eyetimetracker.android;

public final class ReminderRuntimeState {
    public String deviceId = "";
    public String platform = "";
    public boolean isCounting;
    public long currentSessionStartedUnixSeconds;
    // 连续用眼提醒认领状态：会话起点（共享基线）和已提醒到第几次，双端同步后取较大值对齐
    public long continuousClaimSessionStartedUnixSeconds;
    public int continuousClaimLastStep;

    public ReminderRuntimeState() {
    }

    public ReminderRuntimeState(String deviceId, String platform, boolean isCounting, long currentSessionStartedUnixSeconds) {
        this(deviceId, platform, isCounting, currentSessionStartedUnixSeconds, 0L, 0);
    }

    public ReminderRuntimeState(
            String deviceId,
            String platform,
            boolean isCounting,
            long currentSessionStartedUnixSeconds,
            long continuousClaimSessionStartedUnixSeconds,
            int continuousClaimLastStep) {
        this.deviceId = deviceId == null ? "" : deviceId;
        this.platform = platform == null ? "" : platform;
        this.isCounting = isCounting;
        this.currentSessionStartedUnixSeconds = Math.max(0L, currentSessionStartedUnixSeconds);
        this.continuousClaimSessionStartedUnixSeconds = Math.max(0L, continuousClaimSessionStartedUnixSeconds);
        this.continuousClaimLastStep = Math.max(0, continuousClaimLastStep);
    }
}
