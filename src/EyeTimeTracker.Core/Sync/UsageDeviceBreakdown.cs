using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public sealed class UsageDeviceBreakdown
{
    private const long HourSeconds = 3600L;

    public UsageDeviceBreakdown(long pcSeconds, long phoneSeconds, long[]? pcHourlySeconds = null, long[]? phoneHourlySeconds = null)
    {
        PcSeconds = Math.Max(0, pcSeconds);
        PhoneSeconds = Math.Max(0, phoneSeconds);
        var normalizedPcHourly = NormalizeHourly(pcHourlySeconds);
        var normalizedPhoneHourly = NormalizeHourly(phoneHourlySeconds);
        CapHourlySourceStacks(normalizedPcHourly, normalizedPhoneHourly);
        PcHourlySeconds = normalizedPcHourly;
        PhoneHourlySeconds = normalizedPhoneHourly;
    }

    public long PcSeconds { get; }
    public long PhoneSeconds { get; }
    public long[] PcHourlySeconds { get; }
    public long[] PhoneHourlySeconds { get; }
    public int PcPercent => Percent(PcSeconds);
    public int PhonePercent => Percent(PhoneSeconds);

    public static UsageDeviceBreakdown Build(DateOnly date, IEnumerable<UsageSegment> segments)
    {
        var pcBuckets = new HashSet<long>();
        var phoneBuckets = new HashSet<long>();
        foreach (var segment in segments)
        {
            if (segment.EndUnixSeconds <= segment.StartUnixSeconds)
            {
                continue;
            }

            var target = IsPhoneSegment(segment) ? phoneBuckets : pcBuckets;
            var startBucket = segment.StartUnixSeconds / 10;
            var endBucket = (segment.EndUnixSeconds + 9) / 10;
            for (var bucket = startBucket; bucket < endBucket; bucket++)
            {
                var bucketDate = DateOnly.FromDateTime(DateTimeOffset.FromUnixTimeSeconds(bucket * 10).LocalDateTime);
                if (bucketDate == date)
                {
                    target.Add(bucket);
                }
            }
        }

        return new UsageDeviceBreakdown(
            pcBuckets.Count * 10L,
            phoneBuckets.Count * 10L,
            BuildHourlySeconds(date, pcBuckets),
            BuildHourlySeconds(date, phoneBuckets));
    }

    private int Percent(long seconds)
    {
        var total = PcSeconds + PhoneSeconds;
        return total <= 0 ? 0 : (int)Math.Round(seconds * 100D / total);
    }

    private static bool IsPhoneSegment(UsageSegment segment)
    {
        return string.Equals(segment.Platform, "android", StringComparison.OrdinalIgnoreCase)
            || segment.Source.StartsWith("android", StringComparison.OrdinalIgnoreCase);
    }

    private static long[] BuildHourlySeconds(DateOnly date, IEnumerable<long> buckets)
    {
        var hourly = new long[24];
        foreach (var bucket in buckets)
        {
            var local = DateTimeOffset.FromUnixTimeSeconds(bucket * 10).LocalDateTime;
            if (DateOnly.FromDateTime(local) == date)
            {
                hourly[local.Hour] += 10;
            }
        }

        return hourly;
    }

    private static long[] NormalizeHourly(long[]? source)
    {
        var normalized = new long[24];
        if (source is not null)
        {
            Array.Copy(source, normalized, Math.Min(24, source.Length));
        }

        return normalized;
    }

    private static void CapHourlySourceStacks(long[] pcHourlySeconds, long[] phoneHourlySeconds)
    {
        for (var hour = 0; hour < 24; hour++)
        {
            var pcSeconds = Math.Max(0, pcHourlySeconds[hour]);
            var phoneSeconds = Math.Max(0, phoneHourlySeconds[hour]);
            var totalSeconds = pcSeconds + phoneSeconds;
            if (totalSeconds <= HourSeconds)
            {
                pcHourlySeconds[hour] = pcSeconds;
                phoneHourlySeconds[hour] = phoneSeconds;
                continue;
            }

            var scaledPcSeconds = (long)Math.Round(pcSeconds * (double)HourSeconds / totalSeconds);
            scaledPcSeconds = Math.Clamp(scaledPcSeconds, 0, HourSeconds);
            pcHourlySeconds[hour] = scaledPcSeconds;
            phoneHourlySeconds[hour] = HourSeconds - scaledPcSeconds;
        }
    }
}
