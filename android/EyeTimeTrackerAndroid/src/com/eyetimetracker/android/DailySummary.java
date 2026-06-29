package com.eyetimetracker.android;

public final class DailySummary {
    public final String date;
    public final long totalSeconds;
    public final boolean reminderShown;

    public DailySummary(String date, long totalSeconds, boolean reminderShown) {
        this.date = date;
        this.totalSeconds = totalSeconds;
        this.reminderShown = reminderShown;
    }
}