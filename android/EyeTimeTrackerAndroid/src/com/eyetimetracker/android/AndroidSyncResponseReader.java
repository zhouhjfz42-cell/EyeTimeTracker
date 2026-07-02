package com.eyetimetracker.android;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AndroidSyncResponseReader {
    private static final Pattern SEGMENTS_ARRAY = Pattern.compile("\"(?:Segments|segments)\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern OBJECT = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);

    private AndroidSyncResponseReader() {
    }

    public static List<UsageSegment> readSegments(String responseJson) {
        List<UsageSegment> segments = new ArrayList<>();
        String json = safe(responseJson);
        if (!isAccepted(json)) {
            return segments;
        }

        Matcher arrayMatcher = SEGMENTS_ARRAY.matcher(json);
        if (!arrayMatcher.find()) {
            return segments;
        }

        Matcher objectMatcher = OBJECT.matcher(arrayMatcher.group(1));
        while (objectMatcher.find()) {
            UsageSegment segment = readSegment(objectMatcher.group(1));
            if (!segment.segmentId.isEmpty() && segment.endUnixSeconds > segment.startUnixSeconds) {
                segments.add(segment);
            }
        }
        return segments;
    }

    public static String readError(String responseJson) {
        String json = safe(responseJson);
        if (json.isEmpty() || isAccepted(json)) {
            return "";
        }

        String error = readString(json, "Error", "error");
        return error.isEmpty() ? "Sync response was rejected." : error;
    }

    public static boolean isPeerUnpaired(String responseJson) {
        String error = readError(responseJson);
        return "PC is not paired.".equals(error) || error.toLowerCase(java.util.Locale.ROOT).contains("not paired");
    }

    private static UsageSegment readSegment(String json) {
        return new UsageSegment(
                readString(json, "SegmentId", "segmentId"),
                readString(json, "DeviceId", "deviceId"),
                readString(json, "Platform", "platform"),
                readString(json, "Source", "source"),
                readLong(json, "StartUnixSeconds", "startUnixSeconds"),
                readLong(json, "EndUnixSeconds", "endUnixSeconds"),
                readString(json, "LocalDate", "localDate"),
                readLong(json, "CreatedAtUnixSeconds", "createdAtUnixSeconds"),
                readLong(json, "UpdatedAtUnixSeconds", "updatedAtUnixSeconds"));
    }

    private static boolean isAccepted(String json) {
        return readBoolean(json, "Accepted", "accepted");
    }

    private static boolean readBoolean(String json, String pascalName, String camelName) {
        String raw = readRawValue(json, pascalName, camelName);
        return "true".equalsIgnoreCase(raw);
    }

    private static long readLong(String json, String pascalName, String camelName) {
        String raw = readRawValue(json, pascalName, camelName);
        if (raw.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String readString(String json, String pascalName, String camelName) {
        String raw = readRawValue(json, pascalName, camelName);
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return raw;
    }

    private static String readRawValue(String json, String pascalName, String camelName) {
        String value = readRawValue(json, pascalName);
        return value.isEmpty() ? readRawValue(json, camelName) : value;
    }

    private static String readRawValue(String json, String name) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(\"(?:\\\\.|[^\"])*\"|-?\\d+|true|false|null)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1);
        return "null".equals(value) ? "" : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
