package com.eyetimetracker.android;

public final class DailySummaryReconciler {
    private DailySummaryReconciler() {
    }

    public static DailySummary preserveVisibleTotal(DailySummary legacy, DailySummary segmented) {
        if (legacy == null) {
            return segmented == null
                    ? new DailySummary("", 0L, false, 0)
                    : segmented;
        }
        if (segmented == null || legacy.totalSeconds >= segmented.totalSeconds) {
            return legacy;
        }

        return new DailySummary(
                segmented.date,
                segmented.totalSeconds,
                segmented.hourlySeconds,
                segmented.sessionSeconds,
                segmented.currentSessionSeconds,
                legacy.reminderShown,
                legacy.lastReminderStep);
    }
}
