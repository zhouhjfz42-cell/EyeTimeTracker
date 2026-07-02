package com.eyetimetracker.android;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public final class UsageSegmentMerger {
    private static final long BUCKET_SECONDS = 10L;
    private static final long SESSION_BREAK_SECONDS = 180L;

    private UsageSegmentMerger() {
    }

    public static DailySummary buildDailySummary(String date, List<UsageSegment> segments) {
        LocalDate targetDate = LocalDate.parse(date);
        SortedSet<Long> buckets = new TreeSet<>();
        if (segments != null) {
            for (UsageSegment segment : segments) {
                if (segment == null || segment.endUnixSeconds <= segment.startUnixSeconds) {
                    continue;
                }

                long startBucket = floorBucket(segment.startUnixSeconds);
                long endBucket = ceilBucket(segment.endUnixSeconds);
                for (long bucket = startBucket; bucket < endBucket; bucket++) {
                    LocalDate bucketDate = localDateForBucket(bucket);
                    if (targetDate.equals(bucketDate)) {
                        buckets.add(bucket);
                    }
                }
            }
        }

        return new DailySummary(
                date,
                buckets.size() * BUCKET_SECONDS,
                buildHourlySeconds(targetDate, buckets),
                buildSessionSeconds(buckets),
                0L,
                false,
                0);
    }

    private static long[] buildHourlySeconds(LocalDate targetDate, SortedSet<Long> buckets) {
        long[] hourly = new long[24];
        for (long bucket : buckets) {
            java.time.LocalDateTime local = Instant.ofEpochSecond(bucket * BUCKET_SECONDS)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            if (targetDate.equals(local.toLocalDate())) {
                hourly[local.getHour()] += BUCKET_SECONDS;
            }
        }
        return hourly;
    }

    private static long[] buildSessionSeconds(SortedSet<Long> buckets) {
        List<Long> sessions = new ArrayList<>();
        Long previousBucket = null;
        long currentSeconds = 0L;

        for (long bucket : buckets) {
            if (previousBucket != null) {
                long gapSeconds = (bucket - previousBucket) * BUCKET_SECONDS;
                if (gapSeconds > SESSION_BREAK_SECONDS) {
                    if (currentSeconds > 0L) {
                        sessions.add(currentSeconds);
                    }
                    currentSeconds = 0L;
                }
            }

            currentSeconds += BUCKET_SECONDS;
            previousBucket = bucket;
        }

        if (currentSeconds > 0L) {
            sessions.add(currentSeconds);
        }

        long[] values = new long[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) {
            values[i] = sessions.get(i);
        }
        return values;
    }

    private static LocalDate localDateForBucket(long bucket) {
        return Instant.ofEpochSecond(bucket * BUCKET_SECONDS)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private static long floorBucket(long unixSeconds) {
        return unixSeconds / BUCKET_SECONDS;
    }

    private static long ceilBucket(long unixSeconds) {
        return (unixSeconds + BUCKET_SECONDS - 1L) / BUCKET_SECONDS;
    }
}
