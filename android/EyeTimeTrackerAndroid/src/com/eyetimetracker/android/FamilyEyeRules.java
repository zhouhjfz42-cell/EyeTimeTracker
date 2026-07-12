package com.eyetimetracker.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FamilyEyeRules {
    public static final int DEFAULT_CONTINUOUS_USE_MINUTES = 30;
    public static final int DEFAULT_REST_MINUTES = 5;
    public static final int DEFAULT_DISABLED_PERIOD_START_MINUTES = 21 * 60;
    public static final int DEFAULT_DISABLED_PERIOD_END_MINUTES = 7 * 60 + 30;
    public static final int DEFAULT_DISABLED_PERIOD_REMINDER_INTERVAL_MINUTES = 15;

    private static final int MINUTE_OF_DAY_MIN = 0;
    private static final int MINUTE_OF_DAY_MAX = 1439;

    public final String childId;
    public final boolean continuousUseReminderEnabled;
    public final int continuousUseMinutes;
    public final int restMinutes;
    public final boolean disabledPeriodEnabled;
    public final int disabledPeriodStartMinutes;
    public final int disabledPeriodEndMinutes;
    public final int disabledPeriodReminderIntervalMinutes;
    public final List<DisabledPeriod> disabledPeriods;
    public final long updatedAtUnixSeconds;

    private FamilyEyeRules(
            String childId,
            boolean continuousUseReminderEnabled,
            int continuousUseMinutes,
            int restMinutes,
            boolean disabledPeriodEnabled,
            List<DisabledPeriod> disabledPeriods,
            int disabledPeriodReminderIntervalMinutes,
            long updatedAtUnixSeconds) {
        this.childId = safe(childId).trim();
        this.continuousUseReminderEnabled = continuousUseReminderEnabled;
        this.continuousUseMinutes = normalizeContinuousUseMinutes(continuousUseMinutes);
        this.restMinutes = normalizeRestMinutes(restMinutes);
        this.disabledPeriodEnabled = disabledPeriodEnabled;
        this.disabledPeriods = Collections.unmodifiableList(normalizeDisabledPeriods(disabledPeriods));
        DisabledPeriod primary = this.disabledPeriods.isEmpty()
                ? DisabledPeriod.defaults()
                : this.disabledPeriods.get(0);
        this.disabledPeriodStartMinutes = primary.startMinutes;
        this.disabledPeriodEndMinutes = primary.endMinutes;
        this.disabledPeriodReminderIntervalMinutes = normalizeReminderInterval(disabledPeriodReminderIntervalMinutes);
        this.updatedAtUnixSeconds = Math.max(0L, updatedAtUnixSeconds);
    }

    public static FamilyEyeRules defaults(String childId) {
        return createWithDisabledPeriods(
                childId,
                false,
                DEFAULT_CONTINUOUS_USE_MINUTES,
                DEFAULT_REST_MINUTES,
                false,
                Collections.singletonList(DisabledPeriod.defaults()),
                DEFAULT_DISABLED_PERIOD_REMINDER_INTERVAL_MINUTES,
                0L);
    }

    public static FamilyEyeRules create(
            String childId,
            boolean continuousUseReminderEnabled,
            int continuousUseMinutes,
            int restMinutes,
            boolean disabledPeriodEnabled,
            int disabledPeriodStartMinutes,
            int disabledPeriodEndMinutes,
            int disabledPeriodReminderIntervalMinutes,
            long updatedAtUnixSeconds) {
        return createWithDisabledPeriods(
                childId,
                continuousUseReminderEnabled,
                continuousUseMinutes,
                restMinutes,
                disabledPeriodEnabled,
                Collections.singletonList(DisabledPeriod.create(disabledPeriodStartMinutes, disabledPeriodEndMinutes)),
                disabledPeriodReminderIntervalMinutes,
                updatedAtUnixSeconds);
    }

    public static FamilyEyeRules createWithDisabledPeriods(
            String childId,
            boolean continuousUseReminderEnabled,
            int continuousUseMinutes,
            int restMinutes,
            boolean disabledPeriodEnabled,
            List<DisabledPeriod> disabledPeriods,
            int disabledPeriodReminderIntervalMinutes,
            long updatedAtUnixSeconds) {
        return new FamilyEyeRules(
                childId,
                continuousUseReminderEnabled,
                continuousUseMinutes,
                restMinutes,
                disabledPeriodEnabled,
                disabledPeriods,
                disabledPeriodReminderIntervalMinutes,
                updatedAtUnixSeconds);
    }

    public String summaryText() {
        String continuous = continuousUseReminderEnabled ? "连续 " + continuousUseMinutes + " 分钟" : "";
        String disabled = disabledPeriodEnabled ? disabledPeriodSummaryText() : "";
        if (!continuous.isEmpty() && !disabled.isEmpty()) {
            return continuous + "，" + disabled;
        }
        if (!continuous.isEmpty()) {
            return continuous;
        }
        if (!disabled.isEmpty()) {
            return disabled;
        }
        return "暂未开启";
    }

    public String disabledPeriodSummaryText() {
        if (disabledPeriods.isEmpty()) {
            return "";
        }
        String first = disabledPeriods.get(0).summaryText();
        if (disabledPeriods.size() <= 1) {
            return first;
        }
        return first + " 等" + disabledPeriods.size() + "段";
    }

    public boolean isInDisabledPeriod(int hour, int minute) {
        if (!disabledPeriodEnabled) {
            return false;
        }
        int minuteOfDay = hour * 60 + minute;
        if (minuteOfDay < MINUTE_OF_DAY_MIN || minuteOfDay > MINUTE_OF_DAY_MAX) {
            return false;
        }
        for (DisabledPeriod period : disabledPeriods) {
            if (period.containsMinuteOfDay(minuteOfDay)) {
                return true;
            }
        }
        return false;
    }

    public FamilyEyeRules withContinuousUse(boolean enabled, int continuousMinutes, int restMinutes, long updatedAtUnixSeconds) {
        return createWithDisabledPeriods(
                childId,
                enabled,
                continuousMinutes,
                restMinutes,
                disabledPeriodEnabled,
                disabledPeriods,
                disabledPeriodReminderIntervalMinutes,
                updatedAtUnixSeconds);
    }

    public FamilyEyeRules withDisabledPeriods(boolean enabled, List<DisabledPeriod> periods, long updatedAtUnixSeconds) {
        return createWithDisabledPeriods(
                childId,
                continuousUseReminderEnabled,
                continuousUseMinutes,
                restMinutes,
                enabled,
                periods,
                disabledPeriodReminderIntervalMinutes,
                updatedAtUnixSeconds);
    }

    public static String formatMinuteOfDay(int minuteOfDay) {
        int safeMinute = normalizeMinuteOfDay(minuteOfDay, 0);
        int hour = safeMinute / 60;
        int minute = safeMinute % 60;
        return twoDigits(hour) + ":" + twoDigits(minute);
    }

    public static int clampFreeMinutes(int minutes, int fallback, int min, int max) {
        if (minutes < min || minutes > max) {
            return fallback;
        }
        return minutes;
    }

    static String encodeDisabledPeriods(List<DisabledPeriod> periods) {
        StringBuilder encoded = new StringBuilder();
        List<DisabledPeriod> normalized = normalizeDisabledPeriods(periods);
        for (DisabledPeriod period : normalized) {
            if (encoded.length() > 0) {
                encoded.append(";");
            }
            encoded.append(period.startMinutes).append("-").append(period.endMinutes);
        }
        return encoded.toString();
    }

    static List<DisabledPeriod> decodeDisabledPeriods(String encoded) {
        List<DisabledPeriod> periods = new ArrayList<>();
        String[] parts = safe(encoded).split(";");
        for (String part : parts) {
            String[] range = part.trim().split("-");
            if (range.length != 2) {
                continue;
            }
            try {
                periods.add(DisabledPeriod.create(Integer.parseInt(range[0]), Integer.parseInt(range[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        return normalizeDisabledPeriods(periods);
    }

    private static List<DisabledPeriod> normalizeDisabledPeriods(List<DisabledPeriod> periods) {
        List<DisabledPeriod> normalized = new ArrayList<>();
        if (periods != null) {
            for (DisabledPeriod period : periods) {
                if (period != null && period.isValid()) {
                    normalized.add(period);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(DisabledPeriod.defaults());
        }
        return normalized;
    }

    private static int normalizeContinuousUseMinutes(int minutes) {
        return clampFreeMinutes(minutes, DEFAULT_CONTINUOUS_USE_MINUTES, 1, 720);
    }

    private static int normalizeRestMinutes(int minutes) {
        return clampFreeMinutes(minutes, DEFAULT_REST_MINUTES, 1, 180);
    }

    private static int normalizeReminderInterval(int minutes) {
        return clampFreeMinutes(minutes, DEFAULT_DISABLED_PERIOD_REMINDER_INTERVAL_MINUTES, 1, 180);
    }

    private static int normalizeMinuteOfDay(int minuteOfDay, int fallback) {
        if (minuteOfDay < MINUTE_OF_DAY_MIN || minuteOfDay > MINUTE_OF_DAY_MAX) {
            return fallback;
        }
        return minuteOfDay;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class DisabledPeriod {
        public final int startMinutes;
        public final int endMinutes;

        private DisabledPeriod(int startMinutes, int endMinutes) {
            this.startMinutes = normalizeMinuteOfDay(startMinutes, DEFAULT_DISABLED_PERIOD_START_MINUTES);
            this.endMinutes = normalizeMinuteOfDay(endMinutes, DEFAULT_DISABLED_PERIOD_END_MINUTES);
        }

        public static DisabledPeriod defaults() {
            return create(DEFAULT_DISABLED_PERIOD_START_MINUTES, DEFAULT_DISABLED_PERIOD_END_MINUTES);
        }

        public static DisabledPeriod create(int startMinutes, int endMinutes) {
            return new DisabledPeriod(startMinutes, endMinutes);
        }

        public boolean isValid() {
            return startMinutes != endMinutes;
        }

        public boolean containsMinuteOfDay(int minuteOfDay) {
            if (!isValid()) {
                return false;
            }
            if (startMinutes < endMinutes) {
                return minuteOfDay >= startMinutes && minuteOfDay < endMinutes;
            }
            return minuteOfDay >= startMinutes || minuteOfDay < endMinutes;
        }

        public String summaryText() {
            return formatMinuteOfDay(startMinutes) + "-" + formatMinuteOfDay(endMinutes);
        }
    }
}
