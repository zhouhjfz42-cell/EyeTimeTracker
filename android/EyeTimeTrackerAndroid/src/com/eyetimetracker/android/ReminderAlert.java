package com.eyetimetracker.android;

import android.content.Context;

public final class ReminderAlert {
    // 连续用眼阈值不超过该值时用短休息文案（远眺默念），超过时用起身活动文案
    public static final int CONTINUOUS_LONG_THRESHOLD_MINUTES = 30;

    private ReminderAlert() {
    }

    public static String title(Context context) {
        return context.getString(R.string.reminder_alert_title);
    }

    public static String message(Context context, int reminderMinutes) {
        return message(context, reminderMinutes, false, 0);
    }

    public static String message(Context context, int reminderMinutes, boolean repeatReminder, int reminderStep) {
        int safeMinutes = ReminderThreshold.clampMinutes(reminderMinutes);
        int totalMinutes = totalMinutes(safeMinutes, reminderStep);
        String totalDuration = ReminderThreshold.format(context, totalMinutes);
        return context.getString(R.string.reminder_android_body_once)
                .replace("{duration}", totalDuration);
    }

    public static String cumulativeTitle(Context context, int reminderMinutes, int reminderStep) {
        int safeMinutes = ReminderThreshold.clampMinutes(reminderMinutes);
        return cumulativeTitle(context, ReminderThreshold.format(context, totalMinutes(safeMinutes, reminderStep)));
    }

    public static String cumulativeTitle(Context context, String totalDuration) {
        return context.getString(R.string.reminder_cumulative_title)
                .replace("{duration}", totalDuration);
    }

    public static String continuousTitle(Context context, int thresholdMinutes) {
        return context.getString(R.string.eye_care_reminders_continuous_alert_title)
                .replace("{minutes}", String.valueOf(thresholdMinutes));
    }

    public static String continuousMessage(Context context, int thresholdMinutes) {
        return context.getString(thresholdMinutes <= CONTINUOUS_LONG_THRESHOLD_MINUTES
                ? R.string.eye_care_reminders_continuous_alert_message
                : R.string.eye_care_reminders_continuous_alert_message_long);
    }

    public static String continuousEmphasis(Context context, int thresholdMinutes) {
        // 正文中需要加粗加色的关键短语；找不到时横幅会退回纯文本，不影响展示
        return context.getString(thresholdMinutes <= CONTINUOUS_LONG_THRESHOLD_MINUTES
                ? R.string.eye_care_reminders_continuous_alert_emphasis_short
                : R.string.eye_care_reminders_continuous_alert_emphasis_long);
    }

    private static int totalMinutes(int safeMinutes, int reminderStep) {
        return safeMinutes * Math.max(1, reminderStep);
    }
}
