using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class LegacyUsageSegments
{
    public const string Source = "legacy-summary";

    public static List<UsageSegment> NormalizeEffectiveSegments(
        IEnumerable<UsageSegment> storedSegments,
        IEnumerable<DailyRecord> localRecords,
        string deviceId,
        string platform)
    {
        var stored = storedSegments?.ToList() ?? new List<UsageSegment>();
        var modernSegments = stored
            .Where(segment => !string.Equals(segment.Source, Source, StringComparison.Ordinal))
            .Select(Clone)
            .ToList();
        var peerLegacySegments = stored
            .Where(segment => string.Equals(segment.Source, Source, StringComparison.Ordinal))
            .Where(segment => !string.Equals(segment.DeviceId, deviceId, StringComparison.Ordinal))
            .ToList();

        var effective = new List<UsageSegment>();
        effective.AddRange(modernSegments);
        effective.AddRange(NormalizePersistedLegacySegments(peerLegacySegments, modernSegments));
        effective.AddRange(FromDailyRecords(
            localRecords,
            modernSegments.Where(segment => string.Equals(segment.DeviceId, deviceId, StringComparison.Ordinal)),
            deviceId,
            platform));
        return effective;
    }

    public static List<UsageSegment> FromDailyRecords(
        IEnumerable<DailyRecord> records,
        IEnumerable<UsageSegment> existingSegments,
        string deviceId,
        string platform)
    {
        var existingLegacySegmentIds = existingSegments
            .Where(segment => string.Equals(segment.DeviceId, deviceId, StringComparison.Ordinal))
            .Where(segment => string.Equals(segment.Source, Source, StringComparison.Ordinal))
            .Where(segment => !string.IsNullOrWhiteSpace(segment.SegmentId))
            .Select(segment => segment.SegmentId)
            .ToHashSet(StringComparer.Ordinal);

        var existingForDevice = existingSegments
            .Where(segment => string.Equals(segment.DeviceId, deviceId, StringComparison.Ordinal))
            .ToList();

        return records
            .Where(record => record.Date != default)
            .SelectMany(record => FromDailyRecord(
                record,
                deviceId,
                platform,
                CoveredModernSecondsByHour(record.Date, existingForDevice)))
            .Where(segment => existingLegacySegmentIds.Add(segment.SegmentId))
            .ToList();
    }

    private static IEnumerable<UsageSegment> FromDailyRecord(
        DailyRecord record,
        string deviceId,
        string platform,
        long[] coveredSecondsByHour)
    {
        var hourly = NormalizeHourly(record.HourlySeconds);
        var hourlyTotal = hourly.Sum();
        if (hourlyTotal <= 0 && record.TotalSeconds > 0)
        {
            hourly = DistributeAcrossDay(record.TotalSeconds);
        }

        for (var hour = 0; hour < hourly.Length; hour++)
        {
            var seconds = Math.Clamp(hourly[hour] - coveredSecondsByHour[hour], 0, 3600);
            if (seconds <= 0)
            {
                continue;
            }

            var localStart = record.Date.ToDateTime(TimeOnly.FromTimeSpan(TimeSpan.FromHours(hour)));
            var start = new DateTimeOffset(localStart, TimeZoneInfo.Local.GetUtcOffset(localStart));
            var end = start.AddSeconds(seconds);
            yield return new UsageSegment
            {
                SegmentId = UsageSegmentId.Create(deviceId, Source, start, end),
                DeviceId = deviceId,
                Platform = string.IsNullOrWhiteSpace(platform) ? "windows" : platform,
                Source = Source,
                StartUnixSeconds = start.ToUnixTimeSeconds(),
                EndUnixSeconds = end.ToUnixTimeSeconds(),
                LocalDate = record.Date,
                CreatedAtUnixSeconds = start.ToUnixTimeSeconds(),
                UpdatedAtUnixSeconds = end.ToUnixTimeSeconds()
            };
        }
    }

    private static long[] CoveredModernSecondsByHour(DateOnly date, IEnumerable<UsageSegment> existingSegments)
    {
        var covered = new long[24];
        foreach (var segment in existingSegments)
        {
            if (segment.LocalDate != date
                || string.Equals(segment.Source, Source, StringComparison.Ordinal)
                || segment.EndUnixSeconds <= segment.StartUnixSeconds)
            {
                continue;
            }

            AddCoveredSeconds(covered, segment);
        }

        for (var hour = 0; hour < covered.Length; hour++)
        {
            covered[hour] = Math.Clamp(covered[hour], 0, 3600);
        }

        return covered;
    }

    private static void AddCoveredSeconds(long[] covered, UsageSegment segment)
    {
        var start = DateTimeOffset.FromUnixTimeSeconds(segment.StartUnixSeconds).ToLocalTime();
        var end = DateTimeOffset.FromUnixTimeSeconds(segment.EndUnixSeconds).ToLocalTime();
        if (end <= start)
        {
            return;
        }

        var cursor = start;
        while (cursor < end)
        {
            var hourStart = new DateTimeOffset(
                cursor.Year,
                cursor.Month,
                cursor.Day,
                cursor.Hour,
                0,
                0,
                cursor.Offset);
            var hourEnd = hourStart.AddHours(1);
            var sliceEnd = end < hourEnd ? end : hourEnd;
            covered[cursor.Hour] += Math.Max(0, (long)(sliceEnd - cursor).TotalSeconds);
            cursor = sliceEnd;
        }
    }

    private static long[] NormalizeHourly(long[]? source)
    {
        var hourly = new long[24];
        if (source is not null)
        {
            Array.Copy(source, hourly, Math.Min(hourly.Length, source.Length));
        }

        return hourly;
    }

    private static long[] DistributeAcrossDay(long totalSeconds)
    {
        var hourly = new long[24];
        var remaining = Math.Max(0, totalSeconds);
        for (var hour = 0; hour < hourly.Length && remaining > 0; hour++)
        {
            hourly[hour] = Math.Min(3600, remaining);
            remaining -= hourly[hour];
        }

        return hourly;
    }

    private static IEnumerable<UsageSegment> NormalizePersistedLegacySegments(
        IEnumerable<UsageSegment> legacySegments,
        IEnumerable<UsageSegment> modernSegments)
    {
        var modern = modernSegments.ToList();
        return legacySegments
            .Where(IsUsableLegacy)
            .GroupBy(segment => new LegacyHourKey(
                segment.DeviceId,
                segment.Platform,
                segment.LocalDate,
                DateTimeOffset.FromUnixTimeSeconds(segment.StartUnixSeconds).ToLocalTime().Hour))
            .Select(group => CreateNormalizedLegacySegment(group.Key, group.Max(segment => segment.DurationSeconds), modern))
            .Where(segment => segment is not null)
            .Select(segment => segment!);
    }

    private static UsageSegment? CreateNormalizedLegacySegment(
        LegacyHourKey key,
        long legacySeconds,
        IEnumerable<UsageSegment> modernSegments)
    {
        var covered = CoveredModernSecondsByHour(
            key.Date,
            modernSegments.Where(segment => string.Equals(segment.DeviceId, key.DeviceId, StringComparison.Ordinal)))[key.Hour];
        var seconds = Math.Clamp(legacySeconds - covered, 0, 3600);
        if (seconds <= 0)
        {
            return null;
        }

        var localStart = key.Date.ToDateTime(TimeOnly.FromTimeSpan(TimeSpan.FromHours(key.Hour)));
        var start = new DateTimeOffset(localStart, TimeZoneInfo.Local.GetUtcOffset(localStart));
        var end = start.AddSeconds(seconds);
        return new UsageSegment
        {
            SegmentId = UsageSegmentId.Create(key.DeviceId, Source, start, end),
            DeviceId = key.DeviceId,
            Platform = string.IsNullOrWhiteSpace(key.Platform) ? "windows" : key.Platform,
            Source = Source,
            StartUnixSeconds = start.ToUnixTimeSeconds(),
            EndUnixSeconds = end.ToUnixTimeSeconds(),
            LocalDate = key.Date,
            CreatedAtUnixSeconds = start.ToUnixTimeSeconds(),
            UpdatedAtUnixSeconds = end.ToUnixTimeSeconds()
        };
    }

    private static bool IsUsableLegacy(UsageSegment segment)
    {
        return !string.IsNullOrWhiteSpace(segment.DeviceId)
            && string.Equals(segment.Source, Source, StringComparison.Ordinal)
            && segment.LocalDate != default
            && segment.EndUnixSeconds > segment.StartUnixSeconds;
    }

    private static UsageSegment Clone(UsageSegment segment)
    {
        return new UsageSegment
        {
            SegmentId = segment.SegmentId,
            DeviceId = segment.DeviceId,
            Platform = segment.Platform,
            Source = segment.Source,
            StartUnixSeconds = segment.StartUnixSeconds,
            EndUnixSeconds = segment.EndUnixSeconds,
            LocalDate = segment.LocalDate,
            CreatedAtUnixSeconds = segment.CreatedAtUnixSeconds,
            UpdatedAtUnixSeconds = segment.UpdatedAtUnixSeconds
        };
    }

    private sealed record LegacyHourKey(string DeviceId, string Platform, DateOnly Date, int Hour);
}
