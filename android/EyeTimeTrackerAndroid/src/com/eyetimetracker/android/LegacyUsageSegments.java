package com.eyetimetracker.android;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LegacyUsageSegments {
    public static final String SOURCE = "legacy-summary";

    private LegacyUsageSegments() {
    }

    public static List<UsageSegment> normalizeEffectiveSegments(
            List<UsageSegment> storedSegments,
            List<DailySummary> localSummaries,
            String deviceId,
            String platform) {
        List<UsageSegment> stored = storedSegments == null ? new ArrayList<>() : storedSegments;
        List<UsageSegment> modern = new ArrayList<>();
        List<UsageSegment> peerLegacy = new ArrayList<>();
        for (UsageSegment segment : stored) {
            if (segment == null) {
                continue;
            }
            if (SOURCE.equals(segment.source)) {
                if (!safe(deviceId).equals(segment.deviceId)) {
                    peerLegacy.add(segment);
                }
            } else {
                modern.add(cloneSegment(segment));
            }
        }

        List<UsageSegment> effective = new ArrayList<>();
        effective.addAll(modern);
        effective.addAll(normalizePersistedLegacySegments(peerLegacy, modern));
        effective.addAll(fromDailySummaries(localSummaries, modern, deviceId, platform));
        return effective;
    }

    public static List<UsageSegment> fromDailySummaries(
            List<DailySummary> summaries,
            List<UsageSegment> existingSegments,
            String deviceId,
            String platform) {
        Set<String> existingLegacySegmentIds = new HashSet<>();
        List<UsageSegment> existingForDevice = new ArrayList<>();
        if (existingSegments != null) {
            for (UsageSegment segment : existingSegments) {
                if (segment != null
                        && safe(deviceId).equals(segment.deviceId)
                        && SOURCE.equals(segment.source)
                        && !safe(segment.segmentId).isEmpty()) {
                    existingLegacySegmentIds.add(segment.segmentId);
                }
                if (segment != null && safe(deviceId).equals(segment.deviceId)) {
                    existingForDevice.add(segment);
                }
            }
        }

        List<UsageSegment> values = new ArrayList<>();
        if (summaries == null) {
            return values;
        }

        for (DailySummary summary : summaries) {
            if (summary == null || summary.date == null || summary.date.trim().isEmpty()) {
                continue;
            }
            for (UsageSegment segment : fromDailySummary(
                    summary,
                    deviceId,
                    platform,
                    coveredModernSecondsByHour(summary.date, existingForDevice))) {
                if (existingLegacySegmentIds.add(segment.segmentId)) {
                    values.add(segment);
                }
            }
        }
        return values;
    }

    private static List<UsageSegment> fromDailySummary(
            DailySummary summary,
            String deviceId,
            String platform,
            long[] coveredSecondsByHour) {
        List<UsageSegment> values = new ArrayList<>();
        long[] hourly = summary.hourlySeconds == null ? new long[24] : summary.hourlySeconds.clone();
        long hourlyTotal = 0L;
        for (long value : hourly) {
            hourlyTotal += Math.max(0L, value);
        }
        if (hourlyTotal <= 0L && summary.totalSeconds > 0L) {
            hourly = distributeAcrossDay(summary.totalSeconds);
        }

        LocalDate date = LocalDate.parse(summary.date);
        for (int hour = 0; hour < 24; hour++) {
            long seconds = Math.max(0L, Math.min(3600L, hourly[hour] - coveredSecondsByHour[hour]));
            if (seconds <= 0L) {
                continue;
            }
            long startUnixSeconds = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0))
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond();
            long endUnixSeconds = startUnixSeconds + seconds;
            values.add(new UsageSegment(
                    UsageSegmentId.create(deviceId, SOURCE, startUnixSeconds, endUnixSeconds),
                    deviceId,
                    platform == null || platform.trim().isEmpty() ? "android" : platform,
                    SOURCE,
                    startUnixSeconds,
                    endUnixSeconds,
                    summary.date,
                    startUnixSeconds,
                    endUnixSeconds));
        }
        return values;
    }

    private static long[] coveredModernSecondsByHour(String dateValue, List<UsageSegment> existingSegments) {
        long[] covered = new long[24];
        if (dateValue == null || dateValue.trim().isEmpty()) {
            return covered;
        }
        for (UsageSegment segment : existingSegments) {
            if (segment == null
                    || SOURCE.equals(segment.source)
                    || !dateValue.equals(segment.localDate)
                    || segment.endUnixSeconds <= segment.startUnixSeconds) {
                continue;
            }
            addCoveredSeconds(covered, segment);
        }
        for (int hour = 0; hour < covered.length; hour++) {
            covered[hour] = Math.max(0L, Math.min(3600L, covered[hour]));
        }
        return covered;
    }

    private static void addCoveredSeconds(long[] covered, UsageSegment segment) {
        ZonedDateTime start = java.time.Instant.ofEpochSecond(segment.startUnixSeconds).atZone(ZoneId.systemDefault());
        ZonedDateTime end = java.time.Instant.ofEpochSecond(segment.endUnixSeconds).atZone(ZoneId.systemDefault());
        if (!end.isAfter(start)) {
            return;
        }

        ZonedDateTime cursor = start;
        while (cursor.isBefore(end)) {
            ZonedDateTime hourEnd = cursor.withMinute(0).withSecond(0).withNano(0).plusHours(1);
            ZonedDateTime sliceEnd = end.isBefore(hourEnd) ? end : hourEnd;
            covered[cursor.getHour()] += Math.max(0L, java.time.Duration.between(cursor, sliceEnd).getSeconds());
            cursor = sliceEnd;
        }
    }

    private static List<UsageSegment> normalizePersistedLegacySegments(
            List<UsageSegment> legacySegments,
            List<UsageSegment> modernSegments) {
        Map<String, LegacyHourValue> values = new HashMap<>();
        for (UsageSegment segment : legacySegments) {
            if (!isUsableLegacy(segment)) {
                continue;
            }
            int hour = java.time.Instant.ofEpochSecond(segment.startUnixSeconds)
                    .atZone(ZoneId.systemDefault())
                    .getHour();
            String key = segment.deviceId + "|" + segment.platform + "|" + segment.localDate + "|" + hour;
            LegacyHourValue value = values.get(key);
            if (value == null) {
                value = new LegacyHourValue(segment.deviceId, segment.platform, segment.localDate, hour);
                values.put(key, value);
            }
            value.seconds = Math.max(value.seconds, segment.durationSeconds());
        }

        List<UsageSegment> normalized = new ArrayList<>();
        for (LegacyHourValue value : values.values()) {
            long[] covered = coveredModernSecondsByHour(value.date, modernSegmentsForDevice(modernSegments, value.deviceId));
            long seconds = Math.max(0L, Math.min(3600L, value.seconds - covered[value.hour]));
            if (seconds <= 0L) {
                continue;
            }
            LocalDate date = LocalDate.parse(value.date);
            long startUnixSeconds = LocalDateTime.of(date, java.time.LocalTime.of(value.hour, 0))
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond();
            long endUnixSeconds = startUnixSeconds + seconds;
            normalized.add(new UsageSegment(
                    UsageSegmentId.create(value.deviceId, SOURCE, startUnixSeconds, endUnixSeconds),
                    value.deviceId,
                    value.platform == null || value.platform.trim().isEmpty() ? "android" : value.platform,
                    SOURCE,
                    startUnixSeconds,
                    endUnixSeconds,
                    value.date,
                    startUnixSeconds,
                    endUnixSeconds));
        }
        return normalized;
    }

    private static List<UsageSegment> modernSegmentsForDevice(List<UsageSegment> segments, String deviceId) {
        List<UsageSegment> values = new ArrayList<>();
        for (UsageSegment segment : segments) {
            if (segment != null && safe(deviceId).equals(segment.deviceId)) {
                values.add(segment);
            }
        }
        return values;
    }

    private static boolean isUsableLegacy(UsageSegment segment) {
        return segment != null
                && !safe(segment.deviceId).isEmpty()
                && SOURCE.equals(segment.source)
                && !safe(segment.localDate).isEmpty()
                && segment.endUnixSeconds > segment.startUnixSeconds;
    }

    private static UsageSegment cloneSegment(UsageSegment segment) {
        return new UsageSegment(
                segment.segmentId,
                segment.deviceId,
                segment.platform,
                segment.source,
                segment.startUnixSeconds,
                segment.endUnixSeconds,
                segment.localDate,
                segment.createdAtUnixSeconds,
                segment.updatedAtUnixSeconds);
    }

    private static final class LegacyHourValue {
        final String deviceId;
        final String platform;
        final String date;
        final int hour;
        long seconds;

        LegacyHourValue(String deviceId, String platform, String date, int hour) {
            this.deviceId = deviceId;
            this.platform = platform;
            this.date = date;
            this.hour = hour;
        }
    }

    private static long[] distributeAcrossDay(long totalSeconds) {
        long[] hourly = new long[24];
        long remaining = Math.max(0L, totalSeconds);
        for (int hour = 0; hour < hourly.length && remaining > 0L; hour++) {
            hourly[hour] = Math.min(3600L, remaining);
            remaining -= hourly[hour];
        }
        return hourly;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
