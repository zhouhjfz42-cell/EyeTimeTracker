package com.eyetimetracker.android;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public final class ReminderExemptionPolicy {
    private ReminderExemptionPolicy() {
    }

    public static boolean isExempt(long nowUnixSeconds, List<ReminderExemptionPeriod> periods) {
        if (periods == null || periods.isEmpty()) {
            return false;
        }
        int minuteOfDay = Instant.ofEpochSecond(nowUnixSeconds)
                .atZone(ZoneId.systemDefault())
                .getHour() * 60
                + Instant.ofEpochSecond(nowUnixSeconds)
                .atZone(ZoneId.systemDefault())
                .getMinute();
        for (ReminderExemptionPeriod period : periods) {
            if (period != null && period.containsMinuteOfDay(minuteOfDay)) {
                return true;
            }
        }
        return false;
    }
}
