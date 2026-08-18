using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class UsageSegmentCompactor
{
    public static bool TryExtendMutableSegment(IList<UsageSegment> segments, UsageSegment incoming)
    {
        ArgumentNullException.ThrowIfNull(segments);
        ArgumentNullException.ThrowIfNull(incoming);

        if (!UsageSegmentId.IsMutable(incoming.SegmentId)
            || incoming.EndUnixSeconds <= incoming.StartUnixSeconds)
        {
            return false;
        }

        for (var index = segments.Count - 1; index >= 0; index--)
        {
            var existing = segments[index];
            if (!UsageSegmentId.IsMutable(existing.SegmentId)
                || !string.Equals(existing.DeviceId, incoming.DeviceId, StringComparison.Ordinal)
                || !string.Equals(existing.Platform, incoming.Platform, StringComparison.Ordinal)
                || !string.Equals(existing.Source, incoming.Source, StringComparison.Ordinal)
                || existing.LocalDate != incoming.LocalDate
                || incoming.StartUnixSeconds < existing.StartUnixSeconds
                || incoming.StartUnixSeconds > existing.EndUnixSeconds)
            {
                continue;
            }

            existing.EndUnixSeconds = Math.Max(existing.EndUnixSeconds, incoming.EndUnixSeconds);
            existing.UpdatedAtUnixSeconds = Math.Max(existing.UpdatedAtUnixSeconds, incoming.UpdatedAtUnixSeconds);
            return true;
        }

        return false;
    }
}
