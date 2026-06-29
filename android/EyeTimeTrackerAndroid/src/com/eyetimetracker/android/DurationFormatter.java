package com.eyetimetracker.android;

public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String format(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long totalMinutes = safeSeconds / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L) {
            return String.format("%d小时 %02d分钟", hours, minutes);
        }
        return String.format("%d分钟", minutes);
    }
}