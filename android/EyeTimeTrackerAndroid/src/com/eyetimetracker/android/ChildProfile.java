package com.eyetimetracker.android;

public final class ChildProfile {
    public static final String AGE_BAND_UNKNOWN = "unknown";

    public final String childId;
    public final String nickname;
    public final String ageBand;
    public final long createdAtUnixSeconds;
    public final long updatedAtUnixSeconds;

    public ChildProfile(
            String childId,
            String nickname,
            String ageBand,
            long createdAtUnixSeconds,
            long updatedAtUnixSeconds) {
        this.childId = safe(childId).trim();
        this.nickname = safe(nickname).trim();
        this.ageBand = normalizeAgeBand(ageBand);
        this.createdAtUnixSeconds = Math.max(0L, createdAtUnixSeconds);
        this.updatedAtUnixSeconds = Math.max(0L, updatedAtUnixSeconds);
    }

    public boolean isValid() {
        return !childId.isEmpty() && !nickname.isEmpty();
    }

    public static String normalizeAgeBand(String value) {
        String safeValue = safe(value).trim();
        return safeValue.isEmpty() ? AGE_BAND_UNKNOWN : safeValue;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
