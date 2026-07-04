package com.eyetimetracker.android;

public final class ReminderNotificationProfile {
    public static final String CHANNEL_ID = "eye_time_tracker_reminders_v2";
    public static final int CHANNEL_IMPORTANCE = 4;
    public static final int NOTIFICATION_PRIORITY = 2;
    public static final long[] VIBRATION_PATTERN = new long[] { 0L, 220L, 120L, 220L };

    private ReminderNotificationProfile() {
    }
}
