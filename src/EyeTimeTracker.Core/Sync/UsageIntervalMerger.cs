using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class UsageIntervalMerger
{
    private const long SessionBreakSeconds = 180;

    public static DailyRecord BuildDailyRecord(DateOnly date, IEnumerable<UsageSegment> segments)
    {
        var intervals = BuildClippedIntervals(date, segments)
            .Select(interval => new Interval(interval.StartUnixSeconds, interval.EndUnixSeconds))
            .ToList();
        var merged = MergeOverlappingIntervals(intervals);

        return new DailyRecord(date)
        {
            TotalSeconds = merged.Sum(interval => interval.DurationSeconds),
            HourlySeconds = BuildHourlySeconds(date, merged),
            SessionSeconds = BuildSessionSeconds(intervals),
            CurrentSessionSeconds = 0
        };
    }

    public static UsageDeviceBreakdown BuildDeviceBreakdown(DateOnly date, IEnumerable<UsageSegment> segments)
    {
        var intervals = BuildClippedIntervals(date, segments);
        var eventsByTime = new SortedDictionary<long, SourceEvent>();

        foreach (var interval in intervals)
        {
            AddEvent(eventsByTime, interval.StartUnixSeconds, interval.IsPhone, 1);
            AddEvent(eventsByTime, interval.EndUnixSeconds, interval.IsPhone, -1);
        }

        var pcHourly = new long[24];
        var phoneHourly = new long[24];
        var activePc = 0;
        var activePhone = 0;
        long? previousUnixSeconds = null;

        foreach (var (unixSeconds, sourceEvent) in eventsByTime)
        {
            if (previousUnixSeconds is not null && unixSeconds > previousUnixSeconds.Value)
            {
                AddSourceHourlySeconds(
                    date,
                    pcHourly,
                    phoneHourly,
                    previousUnixSeconds.Value,
                    unixSeconds,
                    activePc > 0,
                    activePhone > 0);
            }

            activePc += sourceEvent.PcDelta;
            activePhone += sourceEvent.PhoneDelta;
            previousUnixSeconds = unixSeconds;
        }

        return new UsageDeviceBreakdown(pcHourly.Sum(), phoneHourly.Sum(), pcHourly, phoneHourly);
    }

    private static List<ClippedSourceInterval> BuildClippedIntervals(DateOnly date, IEnumerable<UsageSegment> segments)
    {
        var startOfDay = LocalUnixSeconds(date, 0);
        var endOfDay = LocalUnixSeconds(date.AddDays(1), 0);
        var intervals = new List<ClippedSourceInterval>();

        foreach (var segment in segments ?? Array.Empty<UsageSegment>())
        {
            if (segment.EndUnixSeconds <= segment.StartUnixSeconds)
            {
                continue;
            }

            var start = Math.Max(segment.StartUnixSeconds, startOfDay);
            var end = Math.Min(segment.EndUnixSeconds, endOfDay);
            if (end <= start)
            {
                continue;
            }

            intervals.Add(new ClippedSourceInterval(start, end, IsPhoneSegment(segment)));
        }

        intervals.Sort((left, right) =>
        {
            var startComparison = left.StartUnixSeconds.CompareTo(right.StartUnixSeconds);
            return startComparison != 0
                ? startComparison
                : left.EndUnixSeconds.CompareTo(right.EndUnixSeconds);
        });
        return intervals;
    }

    private static List<Interval> MergeOverlappingIntervals(IEnumerable<Interval> intervals)
    {
        var merged = new List<Interval>();

        foreach (var interval in intervals.OrderBy(interval => interval.StartUnixSeconds).ThenBy(interval => interval.EndUnixSeconds))
        {
            if (merged.Count == 0 || interval.StartUnixSeconds > merged[^1].EndUnixSeconds)
            {
                merged.Add(interval);
                continue;
            }

            var previous = merged[^1];
            merged[^1] = previous with
            {
                EndUnixSeconds = Math.Max(previous.EndUnixSeconds, interval.EndUnixSeconds)
            };
        }

        return merged;
    }

    private static long[] BuildHourlySeconds(DateOnly date, IEnumerable<Interval> intervals)
    {
        var hourly = new long[24];
        foreach (var interval in intervals)
        {
            AddHourlySeconds(date, hourly, interval.StartUnixSeconds, interval.EndUnixSeconds);
        }

        return hourly;
    }

    private static void AddHourlySeconds(DateOnly date, long[] hourly, long startUnixSeconds, long endUnixSeconds)
    {
        for (var hour = 0; hour < 24; hour++)
        {
            var hourStart = LocalUnixSeconds(date, hour);
            var hourEnd = hour == 23
                ? LocalUnixSeconds(date.AddDays(1), 0)
                : LocalUnixSeconds(date, hour + 1);
            var start = Math.Max(startUnixSeconds, hourStart);
            var end = Math.Min(endUnixSeconds, hourEnd);
            if (end > start)
            {
                hourly[hour] += end - start;
            }
        }
    }

    private static void AddSourceHourlySeconds(
        DateOnly date,
        long[] pcHourly,
        long[] phoneHourly,
        long startUnixSeconds,
        long endUnixSeconds,
        bool hasPc,
        bool hasPhone)
    {
        for (var hour = 0; hour < 24; hour++)
        {
            var hourStart = LocalUnixSeconds(date, hour);
            var hourEnd = hour == 23
                ? LocalUnixSeconds(date.AddDays(1), 0)
                : LocalUnixSeconds(date, hour + 1);
            var start = Math.Max(startUnixSeconds, hourStart);
            var end = Math.Min(endUnixSeconds, hourEnd);
            if (end <= start)
            {
                continue;
            }

            var durationSeconds = end - start;
            if (hasPc && hasPhone)
            {
                var pcShare = durationSeconds / 2;
                pcHourly[hour] += pcShare;
                phoneHourly[hour] += durationSeconds - pcShare;
            }
            else if (hasPc)
            {
                pcHourly[hour] += durationSeconds;
            }
            else if (hasPhone)
            {
                phoneHourly[hour] += durationSeconds;
            }
        }
    }

    private static List<long> BuildSessionSeconds(IEnumerable<Interval> intervals)
    {
        var sessions = new List<long>();
        var mergedActive = MergeOverlappingIntervals(intervals);
        long? previousEnd = null;
        var currentSeconds = 0L;

        foreach (var interval in mergedActive)
        {
            if (previousEnd is not null && interval.StartUnixSeconds - previousEnd.Value > SessionBreakSeconds)
            {
                if (currentSeconds > 0)
                {
                    sessions.Add(currentSeconds);
                }

                currentSeconds = 0;
            }

            currentSeconds += interval.DurationSeconds;
            previousEnd = interval.EndUnixSeconds;
        }

        if (currentSeconds > 0)
        {
            sessions.Add(currentSeconds);
        }

        return sessions;
    }

    private static void AddEvent(SortedDictionary<long, SourceEvent> eventsByTime, long unixSeconds, bool isPhone, int delta)
    {
        eventsByTime.TryGetValue(unixSeconds, out var sourceEvent);
        sourceEvent = isPhone
            ? sourceEvent with { PhoneDelta = sourceEvent.PhoneDelta + delta }
            : sourceEvent with { PcDelta = sourceEvent.PcDelta + delta };
        eventsByTime[unixSeconds] = sourceEvent;
    }

    private static bool IsPhoneSegment(UsageSegment segment)
    {
        return string.Equals(segment.Platform, "android", StringComparison.OrdinalIgnoreCase)
            || segment.Source.StartsWith("android", StringComparison.OrdinalIgnoreCase);
    }

    private static long LocalUnixSeconds(DateOnly date, int hour)
    {
        var local = date.ToDateTime(new TimeOnly(hour, 0));
        return new DateTimeOffset(local, TimeZoneInfo.Local.GetUtcOffset(local)).ToUnixTimeSeconds();
    }

    private sealed record ClippedSourceInterval(long StartUnixSeconds, long EndUnixSeconds, bool IsPhone)
    {
        public long DurationSeconds => EndUnixSeconds - StartUnixSeconds;
    }

    private sealed record Interval(long StartUnixSeconds, long EndUnixSeconds)
    {
        public long DurationSeconds => EndUnixSeconds - StartUnixSeconds;
    }

    private readonly record struct SourceEvent(int PcDelta, int PhoneDelta);
}
