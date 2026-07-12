package com.eyetimetracker.android;

public final class AppUsageEntry {
    public final String entryId;
    public final String deviceId;
    public final String platform;
    public final String source;
    public final String appId;
    public final String appName;
    public final String localDate;
    public final long durationSeconds;
    public final long updatedAtUnixSeconds;

    public AppUsageEntry(
            String entryId,
            String deviceId,
            String platform,
            String source,
            String appId,
            String appName,
            String localDate,
            long durationSeconds,
            long updatedAtUnixSeconds) {
        this.entryId = safe(entryId);
        this.deviceId = safe(deviceId);
        this.platform = safe(platform);
        this.source = safe(source);
        this.appId = safe(appId);
        this.appName = safe(appName);
        this.localDate = safe(localDate);
        this.durationSeconds = Math.max(0L, durationSeconds);
        this.updatedAtUnixSeconds = Math.max(0L, updatedAtUnixSeconds);
    }

    public static String createId(String deviceId, String platform, String source, String appId, String localDate) {
        return "app:"
                + safeKey(deviceId) + ":"
                + safeKey(platform) + ":"
                + safeKey(source) + ":"
                + safeKey(appId) + ":"
                + safeKey(localDate);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeKey(String value) {
        String safe = safe(value).trim().toLowerCase(java.util.Locale.ROOT);
        return safe.isEmpty() ? "_" : safe;
    }
}
