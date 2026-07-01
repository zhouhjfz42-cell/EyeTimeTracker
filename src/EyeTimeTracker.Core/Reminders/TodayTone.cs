namespace EyeTimeTracker.Core.Reminders;

public enum TodayTone
{
    Safe,
    Warn,
    Danger
}

public static class TodayTonePolicy
{
    private const long SafeSeconds = 6L * 3600L;
    private const long WarnSeconds = 8L * 3600L;

    public static TodayTone FromSeconds(long totalSeconds)
    {
        var safeSeconds = Math.Max(0, totalSeconds);
        if (safeSeconds <= SafeSeconds)
        {
            return TodayTone.Safe;
        }

        return safeSeconds <= WarnSeconds ? TodayTone.Warn : TodayTone.Danger;
    }
}
