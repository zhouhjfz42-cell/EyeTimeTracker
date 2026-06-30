package com.eyetimetracker.android;

public final class DailySummary {
    public final String date;
    public final long totalSeconds;
    public final boolean reminderShown;
    public final int lastReminderStep;

    public DailySummary(String date, long totalSeconds, boolean reminderShown) {
        this(date, totalSeconds, reminderShown, reminderShown ? 1 : 0);
    }

    public DailySummary(String date, long totalSeconds, boolean reminderShown, int lastReminderStep) {
        this.date = date;
        this.totalSeconds = totalSeconds;
        this.reminderShown = reminderShown;
        this.lastReminderStep = Math.max(0, lastReminderStep);
    }
}
