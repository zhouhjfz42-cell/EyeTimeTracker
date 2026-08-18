namespace EyeTimeTracker.Core.Models;

public sealed record TrackerSettings(
    int IdleThresholdSeconds,
    bool CountAudio,
    int ReminderThresholdSeconds,
    bool StartWithWindows,
    bool RepeatReminder)
{
    public bool ContinuousReminderEnabled { get; init; } = true;
    public int ContinuousReminderThresholdSeconds { get; init; } = 20 * 60;
    public List<ReminderExemptionPeriod> ContinuousReminderExemptionPeriods { get; init; } = new();

    public static TrackerSettings Default { get; } = new(
        IdleThresholdSeconds: 180,
        CountAudio: true,
        ReminderThresholdSeconds: 5 * 3600 + 30 * 60,
        StartWithWindows: true,
        RepeatReminder: true);
}
