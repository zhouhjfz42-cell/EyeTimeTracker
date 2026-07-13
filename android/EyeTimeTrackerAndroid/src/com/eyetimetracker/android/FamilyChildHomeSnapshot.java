package com.eyetimetracker.android;

public final class FamilyChildHomeSnapshot {
    public final String date;
    public final long todaySeconds;
    public final long yesterdaySeconds;
    public final long weekSeconds;
    public final long monthSeconds;
    public final String topAppName;
    public final long topAppSeconds;
    public final long updatedAtUnixSeconds;

    public FamilyChildHomeSnapshot(
            String date,
            long todaySeconds,
            long yesterdaySeconds,
            long weekSeconds,
            long monthSeconds,
            String topAppName,
            long topAppSeconds,
            long updatedAtUnixSeconds) {
        this.date = date == null ? "" : date;
        this.todaySeconds = Math.max(0L, todaySeconds);
        this.yesterdaySeconds = Math.max(0L, yesterdaySeconds);
        this.weekSeconds = Math.max(0L, weekSeconds);
        this.monthSeconds = Math.max(0L, monthSeconds);
        this.topAppName = topAppName == null ? "" : topAppName;
        this.topAppSeconds = Math.max(0L, topAppSeconds);
        this.updatedAtUnixSeconds = Math.max(0L, updatedAtUnixSeconds);
    }

    public boolean isValidForDate(String targetDate) {
        return !date.trim().isEmpty()
                && date.equals(targetDate)
                && updatedAtUnixSeconds > 0L;
    }
}
