namespace EyeTimeTracker.Core.Models;

public sealed class AppState
{
    public TrackerSettings Settings { get; set; } = TrackerSettings.Default;
    public DailyRecord Today { get; set; } = new(DateOnly.FromDateTime(DateTime.Today));
}
