using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Reminders;

public sealed class DailyReminderPolicy
{
    public bool ShouldNotify(DailyRecord record, TrackerSettings settings)
    {
        return !record.ReminderShown && record.TotalSeconds >= settings.ReminderThresholdSeconds;
    }

    public void MarkShown(DailyRecord record)
    {
        record.ReminderShown = true;
    }
}
