package com.eyetimetracker.android;

public final class ContinuousReminderGuard {
    private ContinuousReminderGuard() {
    }

    // 认领记录只在"记录的会话起点 == 当前会话起点"时有效，否则视为新会话、次数清零。
    // 调用方分别用本端起点和共享（基线）起点各算一次，再与对端认领次数取最大值，
    // 这样手机解锁/基线变化都不会补弹，双端都弹过的次数不会重复弹。
    public static int matchingLastStep(
            long storedSessionStartedUnixSeconds,
            long sessionStartedUnixSeconds,
            int storedLastStep) {
        if (storedSessionStartedUnixSeconds <= 0L
                || sessionStartedUnixSeconds <= 0L
                || storedSessionStartedUnixSeconds != sessionStartedUnixSeconds) {
            return 0;
        }
        return Math.max(0, storedLastStep);
    }

    public static boolean shouldClaim(
            long sessionStartedUnixSeconds,
            int step,
            int effectiveLastStep) {
        return sessionStartedUnixSeconds > 0L && step > 0 && step > Math.max(0, effectiveLastStep);
    }
}
