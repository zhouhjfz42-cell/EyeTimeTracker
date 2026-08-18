package com.eyetimetracker.android;

public final class ContinuousReminderGuard {
    private ContinuousReminderGuard() {
    }

    public static int effectiveLastStep(
            long storedLocalSessionStartedUnixSeconds,
            long localSessionStartedUnixSeconds,
            int storedLastStep) {
        if (storedLocalSessionStartedUnixSeconds <= 0L
                || localSessionStartedUnixSeconds <= 0L
                || storedLocalSessionStartedUnixSeconds != localSessionStartedUnixSeconds) {
            return 0;
        }
        return Math.max(0, storedLastStep);
    }

    public static boolean shouldClaim(
            long localSessionStartedUnixSeconds,
            long sessionSeconds,
            int thresholdMinutes,
            long storedLocalSessionStartedUnixSeconds,
            int storedLastStep) {
        if (localSessionStartedUnixSeconds <= 0L) {
            return false;
        }
        int step = ReminderPolicy.reachedStep(sessionSeconds, thresholdMinutes);
        if (step <= 0) {
            return false;
        }
        int lastStep = effectiveLastStep(
                storedLocalSessionStartedUnixSeconds,
                localSessionStartedUnixSeconds,
                storedLastStep);
        return step > lastStep;
    }
}
