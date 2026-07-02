package com.eyetimetracker.android;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DeviceUsageBreakdown {
    private static final long BUCKET_SECONDS = 10L;
    private static final long HOUR_SECONDS = 3600L;

    public final long pcSeconds;
    public final long phoneSeconds;
    public final long[] pcHourlySeconds;
    public final long[] phoneHourlySeconds;

    public DeviceUsageBreakdown(long pcSeconds, long phoneSeconds) {
        this(pcSeconds, phoneSeconds, null, null);
    }

    public DeviceUsageBreakdown(long pcSeconds, long phoneSeconds, long[] pcHourlySeconds, long[] phoneHourlySeconds) {
        this.pcSeconds = Math.max(0L, pcSeconds);
        this.phoneSeconds = Math.max(0L, phoneSeconds);
        long[] normalizedPcHourly = normalizeHourly(pcHourlySeconds);
        long[] normalizedPhoneHourly = normalizeHourly(phoneHourlySeconds);
        capHourlySourceStacks(normalizedPcHourly, normalizedPhoneHourly);
        this.pcHourlySeconds = normalizedPcHourly;
        this.phoneHourlySeconds = normalizedPhoneHourly;
    }

    public static DeviceUsageBreakdown build(String date, List<UsageSegment> segments) {
        LocalDate targetDate = LocalDate.parse(date);
        Set<Long> pcBuckets = new HashSet<>();
        Set<Long> phoneBuckets = new HashSet<>();
        if (segments != null) {
            for (UsageSegment segment : segments) {
                if (segment == null || segment.endUnixSeconds <= segment.startUnixSeconds) {
                    continue;
                }

                Set<Long> target = isPhoneSegment(segment) ? phoneBuckets : pcBuckets;
                long startBucket = segment.startUnixSeconds / BUCKET_SECONDS;
                long endBucket = (segment.endUnixSeconds + BUCKET_SECONDS - 1L) / BUCKET_SECONDS;
                for (long bucket = startBucket; bucket < endBucket; bucket++) {
                    LocalDate bucketDate = Instant.ofEpochSecond(bucket * BUCKET_SECONDS)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    if (targetDate.equals(bucketDate)) {
                        target.add(bucket);
                    }
                }
            }
        }

        return new DeviceUsageBreakdown(
                pcBuckets.size() * BUCKET_SECONDS,
                phoneBuckets.size() * BUCKET_SECONDS,
                buildHourlySeconds(targetDate, pcBuckets),
                buildHourlySeconds(targetDate, phoneBuckets));
    }

    public int pcPercent() {
        return percent(pcSeconds);
    }

    public int phonePercent() {
        return percent(phoneSeconds);
    }

    private int percent(long seconds) {
        long total = pcSeconds + phoneSeconds;
        return total <= 0L ? 0 : (int) Math.round(seconds * 100.0 / total);
    }

    private static boolean isPhoneSegment(UsageSegment segment) {
        return "android".equalsIgnoreCase(segment.platform)
                || segment.source.toLowerCase(java.util.Locale.ROOT).startsWith("android");
    }

    private static long[] buildHourlySeconds(LocalDate targetDate, Set<Long> buckets) {
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

    private static long[] normalizeHourly(long[] source) {
        long[] normalized = new long[24];
        if (source != null) {
            System.arraycopy(source, 0, normalized, 0, Math.min(24, source.length));
        }
        return normalized;
    }

    private static void capHourlySourceStacks(long[] pcHourlySeconds, long[] phoneHourlySeconds) {
        for (int hour = 0; hour < 24; hour++) {
            long pc = Math.max(0L, pcHourlySeconds[hour]);
            long phone = Math.max(0L, phoneHourlySeconds[hour]);
            long total = pc + phone;
            if (total <= HOUR_SECONDS) {
                pcHourlySeconds[hour] = pc;
                phoneHourlySeconds[hour] = phone;
                continue;
            }

            long scaledPc = Math.round(pc * (double) HOUR_SECONDS / total);
            scaledPc = Math.max(0L, Math.min(HOUR_SECONDS, scaledPc));
            pcHourlySeconds[hour] = scaledPc;
            phoneHourlySeconds[hour] = HOUR_SECONDS - scaledPc;
        }
    }
}
