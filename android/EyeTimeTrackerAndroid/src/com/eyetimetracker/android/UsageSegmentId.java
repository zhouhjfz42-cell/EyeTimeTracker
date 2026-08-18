package com.eyetimetracker.android;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class UsageSegmentId {
    private static final String MUTABLE_PREFIX = "m1_";

    private UsageSegmentId() {
    }

    public static String create(String deviceId, String source, long startUnixSeconds, long endUnixSeconds) {
        String raw = safe(deviceId) + ":" + safe(source) + ":" + startUnixSeconds + ":" + endUnixSeconds;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static String createMutable(String deviceId, String source, long startUnixSeconds) {
        return MUTABLE_PREFIX + hash("mutable:" + safe(deviceId) + ":" + safe(source) + ":" + startUnixSeconds);
    }

    public static boolean isMutable(String segmentId) {
        return segmentId != null && segmentId.startsWith(MUTABLE_PREFIX);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
