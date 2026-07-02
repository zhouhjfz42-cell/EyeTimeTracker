using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class UsageSegmentMerger
{
    private const long BucketSeconds = 10;
    private const long SessionBreakSeconds = 180;

    public static DailyRecord BuildDailyRecord(DateOnly date, IEnumerable<UsageSegment> segments)
    {
        var buckets = new SortedSet<long>();
        foreach (var segment in segments)
        {
            if (segment.EndUnixSeconds <= segment.StartUnixSeconds)
            {
                continue;
            }

            var startBucket = FloorBucket(segment.StartUnixSeconds);
            var endBucket = CeilBucket(segment.EndUnixSeconds);
            for (var bucket = startBucket; bucket < endBucket; bucket++)
            {
                var bucketDate = DateOnly.FromDateTime(DateTimeOffset.FromUnixTimeSeconds(bucket * BucketSeconds).LocalDateTime);
                if (bucketDate == date)
                {
                    buckets.Add(bucket);
                }
            }
        }

        var record = new DailyRecord(date)
        {
            TotalSeconds = buckets.Count * BucketSeconds,
            HourlySeconds = BuildHourlySeconds(date, buckets),
            SessionSeconds = BuildSessionSeconds(buckets),
            CurrentSessionSeconds = 0
        };
        return record;
    }

    private static long[] BuildHourlySeconds(DateOnly date, IEnumerable<long> buckets)
    {
        var hourly = new long[24];
        foreach (var bucket in buckets)
        {
            var local = DateTimeOffset.FromUnixTimeSeconds(bucket * BucketSeconds).LocalDateTime;
            if (DateOnly.FromDateTime(local) == date)
            {
                hourly[local.Hour] += BucketSeconds;
            }
        }

        return hourly;
    }

    private static List<long> BuildSessionSeconds(IEnumerable<long> buckets)
    {
        var sessions = new List<long>();
        long? previousBucket = null;
        var currentSeconds = 0L;

        foreach (var bucket in buckets)
        {
            if (previousBucket is not null)
            {
                var gapSeconds = (bucket - previousBucket.Value) * BucketSeconds;
                if (gapSeconds > SessionBreakSeconds)
                {
                    if (currentSeconds > 0)
                    {
                        sessions.Add(currentSeconds);
                    }

                    currentSeconds = 0;
                }
            }

            currentSeconds += BucketSeconds;
            previousBucket = bucket;
        }

        if (currentSeconds > 0)
        {
            sessions.Add(currentSeconds);
        }

        return sessions;
    }

    private static long FloorBucket(long unixSeconds)
    {
        return unixSeconds / BucketSeconds;
    }

    private static long CeilBucket(long unixSeconds)
    {
        return (unixSeconds + BucketSeconds - 1) / BucketSeconds;
    }
}
