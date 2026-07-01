using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Reminders;

public sealed class DailyReminderPolicy
{
    public bool ShouldNotify(DailyRecord record, TrackerSettings settings)
    {
        var step = ReachedStep(record, settings);
        if (step <= 0)
        {
            return false;
        }

        return settings.RepeatReminder
            ? step > record.LastReminderStep
            : !record.ReminderShown;
    }

    public void MarkShown(DailyRecord record, TrackerSettings settings)
    {
        record.ReminderShown = true;
        record.LastReminderStep = Math.Max(record.LastReminderStep, ReachedStep(record, settings));
    }

    private static int ReachedStep(DailyRecord record, TrackerSettings settings)
    {
        if (settings.ReminderThresholdSeconds <= 0)
        {
            return 0;
        }

        return (int)Math.Max(0, record.TotalSeconds / settings.ReminderThresholdSeconds);
    }
}
