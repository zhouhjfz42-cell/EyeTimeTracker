package com.eyetimetracker.android;

public enum TodayTone {
    SAFE,
    WARN,
    DANGER;

    private static final long SAFE_SECONDS = 6L * 3600L;
    private static final long WARN_SECONDS = 8L * 3600L;

    public static TodayTone fromSeconds(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        if (safeSeconds <= SAFE_SECONDS) {
            return SAFE;
        }
        if (safeSeconds <= WARN_SECONDS) {
            return WARN;
        }
        return DANGER;
    }
}
