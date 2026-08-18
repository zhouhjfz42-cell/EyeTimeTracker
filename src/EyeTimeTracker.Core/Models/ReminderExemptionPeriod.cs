namespace EyeTimeTracker.Core.Models;

public sealed record ReminderExemptionPeriod(int StartMinuteOfDay, int EndMinuteOfDay)
{
    public static ReminderExemptionPeriod Default { get; } = new(22 * 60, 7 * 60 + 30);

    public bool IsValid => IsValidMinute(StartMinuteOfDay) && IsValidMinute(EndMinuteOfDay)
        && StartMinuteOfDay != EndMinuteOfDay;

    public bool ContainsMinuteOfDay(int minuteOfDay)
    {
        if (!IsValid || !IsValidMinute(minuteOfDay))
        {
            return false;
        }

        return StartMinuteOfDay < EndMinuteOfDay
            ? minuteOfDay >= StartMinuteOfDay && minuteOfDay < EndMinuteOfDay
            : minuteOfDay >= StartMinuteOfDay || minuteOfDay < EndMinuteOfDay;
    }

    public static string FormatMinuteOfDay(int minuteOfDay)
    {
        var safe = Math.Clamp(minuteOfDay, 0, 1439);
        return $"{safe / 60:00}:{safe % 60:00}";
    }

    private static bool IsValidMinute(int value) => value is >= 0 and <= 1439;
}
