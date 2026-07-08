package com.eyetimetracker.android;

import android.content.Context;

public final class ReminderAlert {
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
        if (repeatReminder && reminderStep > 0) {
            return context.getString(R.string.reminder_android_body_repeat)
                    .replace("{step}", String.valueOf(reminderStep))
                    .replace("{minutes}", String.valueOf(safeMinutes));
        }

        return context.getString(R.string.reminder_android_body_once)
                .replace("{duration}", ReminderThreshold.format(context, safeMinutes));
    }
}
