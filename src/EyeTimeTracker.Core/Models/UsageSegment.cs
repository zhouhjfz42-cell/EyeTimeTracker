namespace EyeTimeTracker.Core.Models;

public sealed class UsageSegment
{
    public string SegmentId { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = string.Empty;
    public string Source { get; set; } = string.Empty;
    public long StartUnixSeconds { get; set; }
    public long EndUnixSeconds { get; set; }
    public DateOnly LocalDate { get; set; }
    public long CreatedAtUnixSeconds { get; set; }
    public long UpdatedAtUnixSeconds { get; set; }

    public long DurationSeconds => Math.Max(0, EndUnixSeconds - StartUnixSeconds);
}
