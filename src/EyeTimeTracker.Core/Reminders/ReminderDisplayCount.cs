using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Reminders;

public static class ReminderDisplayCount
{
    public static int FromSeconds(long totalSeconds, TrackerSettings settings)
    {
        var thresholdSeconds = Math.Max(1, settings.ReminderThresholdSeconds);
        var reached = (int)Math.Max(0, totalSeconds / thresholdSeconds);
        if (settings.RepeatReminder)
        {
            return reached;
        }

        return reached > 0 ? 1 : 0;
    }
}
