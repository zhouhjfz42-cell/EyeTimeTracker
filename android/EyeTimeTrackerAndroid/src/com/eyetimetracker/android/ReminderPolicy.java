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

    public static int alignedStepAfterSettingsChange(long totalSeconds, int thresholdMinutes) {
        return reachedStep(totalSeconds, thresholdMinutes);
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

    public static int displayCount(long totalSeconds, int thresholdMinutes, boolean repeatEnabled) {
        int step = reachedStep(totalSeconds, thresholdMinutes);
        if (repeatEnabled) {
            return step;
        }
        return step > 0 ? 1 : 0;
    }
}
