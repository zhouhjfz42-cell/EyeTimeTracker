namespace EyeTimeTracker.Core.Models;

public sealed record TrackerSettings(
    int IdleThresholdSeconds,
    bool CountAudio,
    int ReminderThresholdSeconds,
    bool StartWithWindows,
    bool RepeatReminder)
{
    public static TrackerSettings Default { get; } = new(
        IdleThresholdSeconds: 180,
        CountAudio: true,
        ReminderThresholdSeconds: 5 * 3600 + 30 * 60,
        StartWithWindows: true,
        RepeatReminder: false);
}
