package com.eyetimetracker.android;

public final class ReminderExemptionPeriod {
    public static final int MINUTE_MIN = 0;
    public static final int MINUTE_MAX = 1439;

    public final int startMinutes;
    public final int endMinutes;

    private ReminderExemptionPeriod(int startMinutes, int endMinutes) {
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
    }

    public static ReminderExemptionPeriod create(int startMinutes, int endMinutes) {
        return new ReminderExemptionPeriod(
                clampMinute(startMinutes),
                clampMinute(endMinutes));
    }

    public static ReminderExemptionPeriod defaults() {
        return create(22 * 60, 7 * 60 + 30);
    }

    public boolean isValid() {
        return startMinutes != endMinutes;
    }

    public boolean containsMinuteOfDay(int minuteOfDay) {
        if (!isValid() || minuteOfDay < MINUTE_MIN || minuteOfDay > MINUTE_MAX) {
            return false;
        }
        return startMinutes < endMinutes
                ? minuteOfDay >= startMinutes && minuteOfDay < endMinutes
                : minuteOfDay >= startMinutes || minuteOfDay < endMinutes;
    }

    public String summaryText() {
        String end = formatMinuteOfDay(endMinutes);
        if (startMinutes > endMinutes) {
            end = "次日 " + end;
        }
        return formatMinuteOfDay(startMinutes) + " - " + end;
    }

    public static String formatMinuteOfDay(int minuteOfDay) {
        int safe = clampMinute(minuteOfDay);
        return twoDigits(safe / 60) + ":" + twoDigits(safe % 60);
    }

    private static int clampMinute(int value) {
        return Math.max(MINUTE_MIN, Math.min(MINUTE_MAX, value));
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
