package com.eyetimetracker.android;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class UsageIntervalMerger {
    private static final long SESSION_BREAK_SECONDS = 180L;

    private UsageIntervalMerger() {
    }

    public static DailySummary buildDailySummary(String date, List<UsageSegment> segments) {
        LocalDate targetDate = LocalDate.parse(date);
        List<SourceInterval> sourceIntervals = buildClippedIntervals(targetDate, segments);
        List<Interval> intervals = new ArrayList<>();
        for (SourceInterval interval : sourceIntervals) {
            intervals.add(new Interval(interval.startUnixSeconds, interval.endUnixSeconds));
        }
        List<Interval> merged = mergeOverlappingIntervals(intervals);

        return new DailySummary(
                date,
                sumDurations(merged),
                buildHourlySeconds(targetDate, merged),
                buildSessionSeconds(intervals),
                0L,
                false,
                0);
    }

    public static DeviceUsageBreakdown buildDeviceBreakdown(String date, List<UsageSegment> segments) {
        LocalDate targetDate = LocalDate.parse(date);
        TreeMap<Long, SourceEvent> eventsByTime = new TreeMap<>();
        for (SourceInterval interval : buildClippedIntervals(targetDate, segments)) {
            addEvent(eventsByTime, interval.startUnixSeconds, interval.isPhone, 1);
            addEvent(eventsByTime, interval.endUnixSeconds, interval.isPhone, -1);
        }

        long[] pcHourly = new long[24];
        long[] phoneHourly = new long[24];
        int activePc = 0;
        int activePhone = 0;
        Long previousUnixSeconds = null;

        for (Map.Entry<Long, SourceEvent> entry : eventsByTime.entrySet()) {
            long unixSeconds = entry.getKey();
            if (previousUnixSeconds != null && unixSeconds > previousUnixSeconds) {
                addSourceHourlySeconds(
                        targetDate,
                        pcHourly,
                        phoneHourly,
                        previousUnixSeconds,
                        unixSeconds,
                        activePc > 0,
                        activePhone > 0);
            }

            SourceEvent sourceEvent = entry.getValue();
            activePc += sourceEvent.pcDelta;
            activePhone += sourceEvent.phoneDelta;
            previousUnixSeconds = unixSeconds;
        }

        return new DeviceUsageBreakdown(sumSeconds(pcHourly), sumSeconds(phoneHourly), pcHourly, phoneHourly);
    }

    private static List<SourceInterval> buildClippedIntervals(LocalDate date, List<UsageSegment> segments) {
        long startOfDay = localUnixSeconds(date, 0);
        long endOfDay = localUnixSeconds(date.plusDays(1), 0);
        List<SourceInterval> intervals = new ArrayList<>();
        if (segments == null) {
            return intervals;
        }

        for (UsageSegment segment : segments) {
            if (segment == null || segment.endUnixSeconds <= segment.startUnixSeconds) {
                continue;
            }

            long start = Math.max(segment.startUnixSeconds, startOfDay);
            long end = Math.min(segment.endUnixSeconds, endOfDay);
            if (end <= start) {
                continue;
            }

            intervals.add(new SourceInterval(start, end, isPhoneSegment(segment)));
        }

        intervals.sort(Comparator
                .comparingLong((SourceInterval interval) -> interval.startUnixSeconds)
                .thenComparingLong(interval -> interval.endUnixSeconds));
        return intervals;
    }

    private static List<Interval> mergeOverlappingIntervals(List<Interval> intervals) {
        List<Interval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator
                .comparingLong((Interval interval) -> interval.startUnixSeconds)
                .thenComparingLong(interval -> interval.endUnixSeconds));

        List<Interval> merged = new ArrayList<>();
        for (Interval interval : sorted) {
            if (merged.isEmpty() || interval.startUnixSeconds > merged.get(merged.size() - 1).endUnixSeconds) {
                merged.add(interval);
                continue;
            }

            Interval previous = merged.get(merged.size() - 1);
            merged.set(merged.size() - 1, new Interval(
                    previous.startUnixSeconds,
                    Math.max(previous.endUnixSeconds, interval.endUnixSeconds)));
        }
        return merged;
    }

    private static long[] buildHourlySeconds(LocalDate date, List<Interval> intervals) {
        long[] hourly = new long[24];
        for (Interval interval : intervals) {
            addHourlySeconds(date, hourly, interval.startUnixSeconds, interval.endUnixSeconds);
        }
        return hourly;
    }

    private static void addHourlySeconds(LocalDate date, long[] hourly, long startUnixSeconds, long endUnixSeconds) {
        for (int hour = 0; hour < 24; hour++) {
            long hourStart = localUnixSeconds(date, hour);
            long hourEnd = hour == 23 ? localUnixSeconds(date.plusDays(1), 0) : localUnixSeconds(date, hour + 1);
            long start = Math.max(startUnixSeconds, hourStart);
            long end = Math.min(endUnixSeconds, hourEnd);
            if (end > start) {
                hourly[hour] += end - start;
            }
        }
    }

    private static void addSourceHourlySeconds(
            LocalDate date,
            long[] pcHourly,
            long[] phoneHourly,
            long startUnixSeconds,
            long endUnixSeconds,
            boolean hasPc,
            boolean hasPhone) {
        for (int hour = 0; hour < 24; hour++) {
            long hourStart = localUnixSeconds(date, hour);
            long hourEnd = hour == 23 ? localUnixSeconds(date.plusDays(1), 0) : localUnixSeconds(date, hour + 1);
            long start = Math.max(startUnixSeconds, hourStart);
            long end = Math.min(endUnixSeconds, hourEnd);
            if (end <= start) {
                continue;
            }

            long durationSeconds = end - start;
            if (hasPc && hasPhone) {
                long pcShare = durationSeconds / 2L;
                pcHourly[hour] += pcShare;
                phoneHourly[hour] += durationSeconds - pcShare;
            } else if (hasPc) {
                pcHourly[hour] += durationSeconds;
            } else if (hasPhone) {
                phoneHourly[hour] += durationSeconds;
            }
        }
    }

    private static long[] buildSessionSeconds(List<Interval> intervals) {
        List<Interval> mergedActive = mergeOverlappingIntervals(intervals);
        List<Long> sessions = new ArrayList<>();
        Long previousEnd = null;
        long currentSeconds = 0L;

        for (Interval interval : mergedActive) {
            if (previousEnd != null && interval.startUnixSeconds - previousEnd > SESSION_BREAK_SECONDS) {
                if (currentSeconds > 0L) {
                    sessions.add(currentSeconds);
                }
                currentSeconds = 0L;
            }

            currentSeconds += interval.durationSeconds();
            previousEnd = interval.endUnixSeconds;
        }

        if (currentSeconds > 0L) {
            sessions.add(currentSeconds);
        }

        long[] values = new long[sessions.size()];
        for (int index = 0; index < sessions.size(); index++) {
            values[index] = sessions.get(index);
        }
        return values;
    }

    private static long sumDurations(List<Interval> intervals) {
        long total = 0L;
        for (Interval interval : intervals) {
            total += interval.durationSeconds();
        }
        return total;
    }

    private static long sumSeconds(long[] values) {
        long total = 0L;
        for (long value : values) {
            total += value;
        }
        return total;
    }

    private static void addEvent(TreeMap<Long, SourceEvent> eventsByTime, long unixSeconds, boolean isPhone, int delta) {
        SourceEvent sourceEvent = eventsByTime.get(unixSeconds);
        if (sourceEvent == null) {
            sourceEvent = new SourceEvent(0, 0);
        }
        sourceEvent = isPhone
                ? new SourceEvent(sourceEvent.pcDelta, sourceEvent.phoneDelta + delta)
                : new SourceEvent(sourceEvent.pcDelta + delta, sourceEvent.phoneDelta);
        eventsByTime.put(unixSeconds, sourceEvent);
    }

    private static boolean isPhoneSegment(UsageSegment segment) {
        return "android".equalsIgnoreCase(segment.platform)
                || segment.source.toLowerCase(Locale.ROOT).startsWith("android");
    }

    private static long localUnixSeconds(LocalDate date, int hour) {
        return LocalDateTime.of(date, LocalTime.of(hour, 0))
                .atZone(ZoneId.systemDefault())
                .toEpochSecond();
    }

    private static final class SourceInterval {
        final long startUnixSeconds;
        final long endUnixSeconds;
        final boolean isPhone;

        SourceInterval(long startUnixSeconds, long endUnixSeconds, boolean isPhone) {
            this.startUnixSeconds = startUnixSeconds;
            this.endUnixSeconds = endUnixSeconds;
            this.isPhone = isPhone;
        }
    }

    private static final class Interval {
        final long startUnixSeconds;
        final long endUnixSeconds;

        Interval(long startUnixSeconds, long endUnixSeconds) {
            this.startUnixSeconds = startUnixSeconds;
            this.endUnixSeconds = endUnixSeconds;
        }

        long durationSeconds() {
            return endUnixSeconds - startUnixSeconds;
        }
    }

    private static final class SourceEvent {
        final int pcDelta;
        final int phoneDelta;

        SourceEvent(int pcDelta, int phoneDelta) {
            this.pcDelta = pcDelta;
            this.phoneDelta = phoneDelta;
        }
    }
}
