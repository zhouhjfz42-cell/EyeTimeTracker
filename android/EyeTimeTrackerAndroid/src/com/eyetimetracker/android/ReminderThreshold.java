package com.eyetimetracker.android;

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

    public static String formatEquivalent(int minutes) {
        return "即" + format(minutes);
    }

    public static String formatRepeatLabel(int minutes) {
        return "反复提醒（当天内每" + clampMinutes(minutes) + "分钟提醒一次）";
    }
}
