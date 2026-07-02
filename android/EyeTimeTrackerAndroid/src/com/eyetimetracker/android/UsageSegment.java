package com.eyetimetracker.android;

public final class UsageSegment {
    public final String segmentId;
    public final String deviceId;
    public final String platform;
    public final String source;
    public final long startUnixSeconds;
    public final long endUnixSeconds;
    public final String localDate;
    public final long createdAtUnixSeconds;
    public final long updatedAtUnixSeconds;

    public UsageSegment(
            String segmentId,
            String deviceId,
            String platform,
            String source,
            long startUnixSeconds,
            long endUnixSeconds,
            String localDate,
            long createdAtUnixSeconds,
            long updatedAtUnixSeconds) {
        this.segmentId = segmentId == null ? "" : segmentId;
        this.deviceId = deviceId == null ? "" : deviceId;
        this.platform = platform == null ? "" : platform;
        this.source = source == null ? "" : source;
        this.startUnixSeconds = startUnixSeconds;
        this.endUnixSeconds = endUnixSeconds;
        this.localDate = localDate == null ? "" : localDate;
        this.createdAtUnixSeconds = createdAtUnixSeconds;
        this.updatedAtUnixSeconds = updatedAtUnixSeconds;
    }

    public long durationSeconds() {
        return Math.max(0L, endUnixSeconds - startUnixSeconds);
    }
}
