package com.eyetimetracker.android;

import android.content.Context;

public final class ReminderThreshold {
    public static final int DEFAULT_MINUTES = 330;
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 10_080;

    private ReminderThreshold() {
    }

    public static int clampMinutes(int minutes) {
        if (minutes < MIN_MINUTES) {
            return MIN_MINUTES;
        }
        if (minutes > MAX_MINUTES) {
            return MAX_MINUTES;
        }
        return minutes;
    }

    public static long toSeconds(int minutes) {
        return (long) clampMinutes(minutes) * 60L;
    }

    public static String format(int minutes) {
        int safeMinutes = clampMinutes(minutes);
        int hours = safeMinutes / 60;
        int remainingMinutes = safeMinutes % 60;
        if (hours > 0 && remainingMinutes > 0) {
            return hours + "小时" + remainingMinutes + "分";
        }
        if (hours > 0) {
            return hours + "小时";
        }
        return remainingMinutes + "分钟";
    }

    public static String format(Context context, int minutes) {
        int safeMinutes = clampMinutes(minutes);
        int hours = safeMinutes / 60;
        int remainingMinutes = safeMinutes % 60;
        if (hours > 0 && remainingMinutes > 0) {
            return formatResource(context, R.string.duration_hours_minutes, "hours", hours, "minutes", remainingMinutes);
        }
        if (hours > 0) {
            return formatResource(context, R.string.duration_hours, "hours", hours);
        }
        return formatResource(context, R.string.duration_minutes, "minutes", remainingMinutes);
    }

    public static String formatEquivalent(int minutes) {
        return "即" + format(minutes);
    }

    public static String formatEquivalent(Context context, int minutes) {
        return formatResource(context, R.string.duration_equivalent, "duration", format(context, minutes));
    }

    public static String formatRepeatLabel(int minutes) {
        clampMinutes(minutes);
        return "反复提醒（每达到时间就提醒一次，一天\n内可能出现多次提醒）";
    }

    public static String formatRepeatLabel(Context context, int minutes) {
        clampMinutes(minutes);
        return context.getString(R.string.reminder_repeat_label);
    }

    private static String formatResource(Context context, int resId, Object... pairs) {
        String text = context.getString(resId);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return text;
    }
}
