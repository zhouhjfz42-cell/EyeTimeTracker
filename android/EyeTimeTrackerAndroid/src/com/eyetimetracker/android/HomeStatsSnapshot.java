package com.eyetimetracker.android;

public final class HomeStatsSnapshot {
    public final long todaySeconds;
    public final long yesterdaySeconds;
    public final long weekSeconds;
    public final long monthSeconds;

    public HomeStatsSnapshot(long todaySeconds, long yesterdaySeconds, long weekSeconds, long monthSeconds) {
        this.todaySeconds = Math.max(0L, todaySeconds);
        this.yesterdaySeconds = Math.max(0L, yesterdaySeconds);
        this.weekSeconds = Math.max(0L, weekSeconds);
        this.monthSeconds = Math.max(0L, monthSeconds);
    }

    public static HomeStatsSnapshot empty() {
        return new HomeStatsSnapshot(0L, 0L, 0L, 0L);
    }
}
