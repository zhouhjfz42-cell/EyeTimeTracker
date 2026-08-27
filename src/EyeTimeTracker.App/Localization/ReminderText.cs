using EyeTimeTracker.Core.Reminders;

namespace EyeTimeTracker.App.Localization;

public static class ReminderText
{
    public static string Title => AppText.Get("reminder.alertTitle");

    public static string Body(int reminderThresholdSeconds, bool repeatReminder, int reminderStep)
    {
        if (repeatReminder && reminderStep > 0)
        {
            return AppText.Format(
                "reminder.body.repeat",
                ("step", reminderStep),
                ("minutes", ReminderThreshold.ToMinutes(reminderThresholdSeconds)));
        }

        return AppText.Format(
            "reminder.body.once",
            ("duration", ReminderThreshold.Format(reminderThresholdSeconds)));
    }

    // 累计提醒标题/正文使用当天实际累计量（阈值×第几次），反复提醒时同步放大
    public static string CumulativeTitle(int reminderThresholdSeconds, int reminderStep)
    {
        return AppText.Format(
            "reminder.cumulativeTitle",
            ("duration", ReminderThreshold.Format(reminderThresholdSeconds * Math.Max(1, reminderStep))));
    }

    public static string CumulativeBody(int reminderThresholdSeconds, int reminderStep)
    {
        return AppText.Format(
            "reminder.body.once",
            ("duration", ReminderThreshold.Format(reminderThresholdSeconds * Math.Max(1, reminderStep))));
    }

    // 连续提醒标题固定显示用户设定的分钟数：默认用户弹窗后已经放松，新一轮重新计
    public static string ContinuousTitle(int thresholdSeconds)
    {
        return AppText.Format(
            "eyeCareReminders.continuousAlertTitle",
            ("minutes", ReminderThreshold.ToMinutes(thresholdSeconds)));
    }

    public static string ContinuousBody(int thresholdSeconds)
    {
        return AppText.Get(thresholdSeconds <= 30 * 60
            ? "eyeCareReminders.continuousAlertMessage"
            : "eyeCareReminders.continuousAlertMessageLong");
    }

    // 正文中需要加粗加色的关键短语
    public static string ContinuousEmphasis(int thresholdSeconds)
    {
        return AppText.Get(thresholdSeconds <= 30 * 60
            ? "eyeCareReminders.continuousAlertEmphasisShort"
            : "eyeCareReminders.continuousAlertEmphasisLong");
    }

    public static string CumulativeEmphasis(int reminderThresholdSeconds, int reminderStep)
    {
        return ReminderThreshold.Format(reminderThresholdSeconds * Math.Max(1, reminderStep));
    }
}
