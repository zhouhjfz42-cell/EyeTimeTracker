package com.eyetimetracker.android;

public final class AndroidSyncTriggerPolicy {
    public static final long SERVICE_SYNC_INTERVAL_MS = 60_000L;
    public static final long LOCAL_CHANGE_DEBOUNCE_MS = 30_000L;
    public static final long UPCOMING_REMINDER_SYNC_INTERVAL_MS = 10_000L;
    public static final long UPCOMING_REMINDER_WINDOW_SECONDS = 60L;

    private long lastSyncAttemptAtMs = Long.MIN_VALUE;
    private long pendingLocalChangeAtMs = Long.MIN_VALUE;

    public void markLocalChange(long nowMs) {
        if (pendingLocalChangeAtMs == Long.MIN_VALUE) {
            pendingLocalChangeAtMs = nowMs;
        }
    }

    public boolean shouldSyncForServiceTick(long nowMs) {
        return lastSyncAttemptAtMs == Long.MIN_VALUE
                || nowMs - lastSyncAttemptAtMs >= SERVICE_SYNC_INTERVAL_MS;
    }

    public boolean shouldSyncForLocalChange(long nowMs) {
        return pendingLocalChangeAtMs != Long.MIN_VALUE
                && nowMs - pendingLocalChangeAtMs >= LOCAL_CHANGE_DEBOUNCE_MS;
    }

    public boolean shouldSyncForUpcomingReminder(
            long nowMs,
            long totalSeconds,
            int reminderMinutes,
            boolean repeatEnabled,
            boolean reminderShown,
            int lastReminderStep) {
        long thresholdSeconds = ReminderThreshold.toSeconds(reminderMinutes);
        if (thresholdSeconds <= 0L) {
            return false;
        }

        long nextReminderSeconds;
        if (repeatEnabled) {
            nextReminderSeconds = Math.max(1, lastReminderStep + 1L) * thresholdSeconds;
        } else {
            if (reminderShown) {
                return false;
            }
            nextReminderSeconds = thresholdSeconds;
        }

        long remainingSeconds = nextReminderSeconds - Math.max(0L, totalSeconds);
        if (remainingSeconds < 0L || remainingSeconds > UPCOMING_REMINDER_WINDOW_SECONDS) {
            return false;
        }

        return lastSyncAttemptAtMs == Long.MIN_VALUE
                || nowMs - lastSyncAttemptAtMs >= UPCOMING_REMINDER_SYNC_INTERVAL_MS;
    }

    public boolean shouldSyncForStatsOpen() {
        return true;
    }

    public void markSyncAttempt(long nowMs) {
        lastSyncAttemptAtMs = nowMs;
        pendingLocalChangeAtMs = Long.MIN_VALUE;
    }
}
