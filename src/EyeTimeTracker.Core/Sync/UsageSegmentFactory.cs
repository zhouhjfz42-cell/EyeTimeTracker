using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class UsageSegmentFactory
{
    public static UsageSegment Create(
        string deviceId,
        string platform,
        string source,
        DateTimeOffset intervalStart,
        DateTimeOffset intervalEnd)
    {
        if (intervalEnd <= intervalStart)
        {
            throw new ArgumentException("Segment end must be after start.", nameof(intervalEnd));
        }

        var startUnixSeconds = intervalStart.ToUnixTimeSeconds();
        var endUnixSeconds = intervalEnd.ToUnixTimeSeconds();
        return new UsageSegment
        {
            SegmentId = UsageSegmentId.Create(deviceId, source, intervalStart, intervalEnd),
            DeviceId = deviceId,
            Platform = platform,
            Source = source,
            StartUnixSeconds = startUnixSeconds,
            EndUnixSeconds = endUnixSeconds,
            LocalDate = DateOnly.FromDateTime(intervalStart.LocalDateTime),
            CreatedAtUnixSeconds = startUnixSeconds,
            UpdatedAtUnixSeconds = endUnixSeconds
        };
    }

    public static UsageSegment CreateMutable(
        string deviceId,
        string platform,
        string source,
        DateTimeOffset intervalStart,
        DateTimeOffset intervalEnd)
    {
        if (intervalEnd <= intervalStart)
        {
            throw new ArgumentException("Segment end must be after start.", nameof(intervalEnd));
        }

        var startUnixSeconds = intervalStart.ToUnixTimeSeconds();
        var endUnixSeconds = intervalEnd.ToUnixTimeSeconds();
        return new UsageSegment
        {
            SegmentId = UsageSegmentId.CreateMutable(deviceId, source, intervalStart),
            DeviceId = deviceId,
            Platform = platform,
            Source = source,
            StartUnixSeconds = startUnixSeconds,
            EndUnixSeconds = endUnixSeconds,
            LocalDate = DateOnly.FromDateTime(intervalStart.LocalDateTime),
            CreatedAtUnixSeconds = startUnixSeconds,
            UpdatedAtUnixSeconds = endUnixSeconds
        };
    }
}
