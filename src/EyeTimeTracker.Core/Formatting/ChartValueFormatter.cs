namespace EyeTimeTracker.Core.Formatting;

public static class ChartValueFormatter
{
    public static string FormatMinutes(long totalSeconds)
    {
        var minutes = Math.Max(0L, totalSeconds) / 60L;
        return $"{minutes}分钟";
    }

    public static string FormatCompactHours(long totalSeconds)
    {
        var safeSeconds = Math.Max(0L, totalSeconds);
        var halfHourUnits = (long)Math.Round(safeSeconds / 1800D, MidpointRounding.AwayFromZero);
        var wholeHours = halfHourUnits / 2L;

        return halfHourUnits % 2L == 0L ? $"{wholeHours}小时" : $"{wholeHours}.5小时";
    }
}
