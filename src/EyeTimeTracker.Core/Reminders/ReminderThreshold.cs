namespace EyeTimeTracker.Core.Reminders;

public static class ReminderThreshold
{
    public const int MinMinutes = 1;
    public const int MaxMinutes = 10080;

    public static int FromMinutes(int minutes)
    {
        if (minutes < MinMinutes || minutes > MaxMinutes)
        {
            throw new ArgumentOutOfRangeException(nameof(minutes));
        }

        return checked(minutes * 60);
    }

    public static int ToMinutes(int seconds)
    {
        return Math.Clamp((int)Math.Ceiling(Math.Max(0, seconds) / 60D), MinMinutes, MaxMinutes);
    }

    public static string Format(int seconds)
    {
        var totalMinutes = ToMinutes(seconds);
        var hours = totalMinutes / 60;
        var minutes = totalMinutes % 60;

        if (hours <= 0)
        {
            return string.Format("{0}\u5206\u949f", minutes);
        }

        return minutes > 0
            ? string.Format("{0}\u5c0f\u65f6{1:00}\u5206", hours, minutes)
            : string.Format("{0}\u5c0f\u65f6", hours);
    }
}
