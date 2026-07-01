package com.eyetimetracker.android;

public final class DailySummary {
    public final String date;
    public final long totalSeconds;
    public final long[] hourlySeconds;
    public final long[] sessionSeconds;
    public final long currentSessionSeconds;
    public final boolean reminderShown;
    public final int lastReminderStep;

    public DailySummary(String date, long totalSeconds, boolean reminderShown) {
        this(date, totalSeconds, new long[24], new long[0], 0L, reminderShown, reminderShown ? 1 : 0);
    }

    public DailySummary(String date, long totalSeconds, boolean reminderShown, int lastReminderStep) {
        this(date, totalSeconds, new long[24], new long[0], 0L, reminderShown, lastReminderStep);
    }

    public DailySummary(
            String date,
            long totalSeconds,
            long[] hourlySeconds,
            long[] sessionSeconds,
            long currentSessionSeconds,
            boolean reminderShown,
            int lastReminderStep) {
        this.date = date;
        this.totalSeconds = totalSeconds;
        this.hourlySeconds = normalizeHourly(hourlySeconds);
        this.sessionSeconds = sessionSeconds == null ? new long[0] : sessionSeconds.clone();
        this.currentSessionSeconds = Math.max(0L, currentSessionSeconds);
        this.reminderShown = reminderShown;
        this.lastReminderStep = Math.max(0, lastReminderStep);
    }

    private static long[] normalizeHourly(long[] source) {
        long[] normalized = new long[24];
        if (source != null) {
            System.arraycopy(source, 0, normalized, 0, Math.min(24, source.length));
        }
        return normalized;
    }
}
