package com.eyetimetracker.android;

public final class ReminderPolicy {
    private ReminderPolicy() {
    }

    public static int reachedStep(long totalSeconds, int thresholdMinutes) {
        long thresholdSeconds = ReminderThreshold.toSeconds(thresholdMinutes);
        if (thresholdSeconds <= 0L) {
            return 0;
        }
        return (int) Math.max(0L, totalSeconds / thresholdSeconds);
    }

    public static boolean shouldNotify(
            long totalSeconds,
            int thresholdMinutes,
            boolean repeatEnabled,
            boolean reminderShown,
            int lastReminderStep) {
        int step = reachedStep(totalSeconds, thresholdMinutes);
        if (step <= 0) {
            return false;
        }
        if (repeatEnabled) {
            return step > lastReminderStep;
        }
        return !reminderShown;
    }
}
