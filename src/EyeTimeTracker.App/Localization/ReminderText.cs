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
}
