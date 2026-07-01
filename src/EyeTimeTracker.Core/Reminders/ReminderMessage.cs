namespace EyeTimeTracker.Core.Reminders;

public static class ReminderMessage
{
    public const string Title = "\u7528\u773c\u63d0\u9192";

    public static string Body(int reminderThresholdSeconds)
    {
        return Body(reminderThresholdSeconds, repeatReminder: false, reminderStep: 0);
    }

    public static string Body(int reminderThresholdSeconds, bool repeatReminder, int reminderStep)
    {
        if (repeatReminder && reminderStep > 0)
        {
            return string.Format(
                "\u4eca\u5929\u7684\u5c4f\u5e55\u4f7f\u7528\u65f6\u95f4\u5df2\u7ecf\u7b2c{0}\u6b21\u8fbe\u5230{1}\u5206\u949f\u4e86\uff0c\u5efa\u8bae\u4f11\u606f\u4e00\u4e0b\u773c\u775b\u3002",
                reminderStep,
                ReminderThreshold.ToMinutes(reminderThresholdSeconds));
        }

        return string.Format(
            "\u4eca\u5929\u7684\u5c4f\u5e55\u4f7f\u7528\u65f6\u95f4\u5df2\u8fbe\u5230{0}\uff0c\u5efa\u8bae\u4f11\u606f\u4e00\u4e0b\u773c\u775b\u3002",
            ReminderThreshold.Format(reminderThresholdSeconds));
    }
}
