using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Reminders;

public static class ContinuousReminderExemptionPolicy
{
    public static bool IsExempt(DateTimeOffset now, IEnumerable<ReminderExemptionPeriod>? periods)
    {
        var minuteOfDay = now.Hour * 60 + now.Minute;
        return periods?.Any(period => period is not null && period.ContainsMinuteOfDay(minuteOfDay)) == true;
    }
}
