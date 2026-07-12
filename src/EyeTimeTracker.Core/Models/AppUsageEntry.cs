namespace EyeTimeTracker.Core.Models;

public sealed class AppUsageEntry
{
    public string EntryId { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = string.Empty;
    public string Source { get; set; } = string.Empty;
    public string AppId { get; set; } = string.Empty;
    public string AppName { get; set; } = string.Empty;
    public DateOnly LocalDate { get; set; }
    public long DurationSeconds { get; set; }
    public long UpdatedAtUnixSeconds { get; set; }
}
