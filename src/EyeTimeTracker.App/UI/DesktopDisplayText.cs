using EyeTimeTracker.App.Localization;
using EyeTimeTracker.Core.DesktopActivity;

namespace EyeTimeTracker.App.UI;

/// <summary>页面共用的展示格式化（时长、桌型、倒计时、中断率）。</summary>
public static class DesktopDisplayText
{
    /// <summary>「4小时18分」/「52分钟」，不足一分钟按 1 分钟向下取整为 0分钟。</summary>
    public static string Duration(long seconds)
    {
        var totalMinutes = Math.Max(0, seconds) / 60;
        var hours = totalMinutes / 60;
        var minutes = totalMinutes % 60;
        return hours > 0
            ? AppText.Format("desktop.duration.hoursMinutes", ("hours", hours), ("minutes", minutes))
            : AppText.Format("desktop.duration.minutes", ("minutes", minutes));
    }

    public static string Count(int count)
    {
        return AppText.Format("desktop.duration.count", ("count", count));
    }

    public static string Sessions(int count)
    {
        return AppText.Format("desktop.duration.sessions", ("count", count));
    }

    public static string DeskLabel(DeskType desk)
    {
        return desk switch
        {
            DeskType.Ordinary => AppText.Get("desktop.desk.ordinary"),
            DeskType.Adjustable => AppText.Get("desktop.desk.adjustable"),
            _ => AppText.Get("desktop.desk.mixed")
        };
    }

    /// <summary>中断率展示：无分母显示「—」，不能显示 0%。</summary>
    public static string Rate(ResponseEvaluation evaluation)
    {
        return evaluation.RatePercent is { } percent
            ? percent + "%"
            : AppText.Get("desktop.common.dash");
    }

    /// <summary>「约 8 分钟后」；不足 1 分钟显示「即将到期」。</summary>
    public static string DueIn(long activeSecondsRemaining)
    {
        if (activeSecondsRemaining <= 60)
        {
            return AppText.Get("desktop.main.plan.dueSoon");
        }

        var minutes = (long)Math.Ceiling(activeSecondsRemaining / 60.0);
        return AppText.Format("desktop.main.plan.dueIn", ("minutes", minutes));
    }

    public static string WeekdayLabel(DayOfWeek dayOfWeek)
    {
        var key = dayOfWeek switch
        {
            DayOfWeek.Monday => "calendar.weekday.mon",
            DayOfWeek.Tuesday => "calendar.weekday.tue",
            DayOfWeek.Wednesday => "calendar.weekday.wed",
            DayOfWeek.Thursday => "calendar.weekday.thu",
            DayOfWeek.Friday => "calendar.weekday.fri",
            DayOfWeek.Saturday => "calendar.weekday.sat",
            _ => "calendar.weekday.sun"
        };
        return AppText.Get(key);
    }
}
