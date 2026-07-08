package com.eyetimetracker.android;

import android.content.Context;

public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String format(Context context, long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long totalMinutes = safeSeconds / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L) {
            return formatResource(
                    context,
                    R.string.duration_hours_minutes_padded,
                    "hours",
                    hours,
                    "minutes:00",
                    String.format("%02d", minutes));
        }
        return formatResource(context, R.string.duration_minutes, "minutes", minutes);
    }

    public static String format(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long totalMinutes = safeSeconds / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L) {
            return String.format("%d小时%02d分", hours, minutes);
        }
        return String.format("%d分钟", minutes);
    }

    public static String formatMainCard(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long totalMinutes = safeSeconds / 60L;
        if (totalMinutes <= 10L * 60L) {
            return format(safeSeconds);
        }

        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (minutes > 30L) {
            hours++;
        }
        return String.format("约%d小时", hours);
    }

    public static String formatMainCard(Context context, long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long totalMinutes = safeSeconds / 60L;
        if (totalMinutes <= 10L * 60L) {
            return format(context, safeSeconds);
        }

        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (minutes > 30L) {
            hours++;
        }
        return formatResource(context, R.string.duration_about_hours, "hours", hours);
    }

    public static String formatTooltipMinutes(long totalSeconds) {
        long minutes = Math.max(0L, totalSeconds) / 60L;
        return String.format("%d分钟", minutes);
    }

    public static String formatTooltipMinutes(Context context, long totalSeconds) {
        return formatResource(context, R.string.duration_minutes, "minutes", Math.max(0L, totalSeconds) / 60L);
    }

    public static String formatTooltipHours(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long halfHourUnits = Math.round(safeSeconds / 1800.0);
        long wholeHours = halfHourUnits / 2L;
        if (halfHourUnits % 2L == 0L) {
            return String.format("%d小时", wholeHours);
        }
        return String.format("%d.5小时", wholeHours);
    }

    public static String formatTooltipHours(Context context, long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long halfHourUnits = Math.round(safeSeconds / 1800.0);
        long wholeHours = halfHourUnits / 2L;
        if (halfHourUnits % 2L == 0L) {
            return formatResource(context, R.string.duration_hours, "hours", wholeHours);
        }
        return formatResource(context, R.string.duration_half_hours, "hours", wholeHours);
    }

    private static String formatResource(Context context, int resId, Object... pairs) {
        String text = context.getString(resId);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return text;
    }
}
