package com.eyetimetracker.android;

public final class ReminderNotificationProfile {
    public static final String CHANNEL_ID = "eye_time_tracker_reminders_v3";
    public static final String[] LEGACY_CHANNEL_IDS = new String[] {
            "eye_time_tracker_reminders",
            "eye_time_tracker_reminders_v2"
    };
    public static final int CHANNEL_IMPORTANCE = 4;
    public static final int NOTIFICATION_PRIORITY = 2;
    public static final boolean USE_FULL_SCREEN_INTENT = true;
    public static final boolean ENABLE_SOUND = true;
    public static final long[] VIBRATION_PATTERN = new long[] { 0L, 220L, 120L, 220L };

    private ReminderNotificationProfile() {
    }

    public static boolean isLegacyChannelId(String channelId) {
        if (channelId == null) {
            return false;
        }
        for (String legacyChannelId : LEGACY_CHANNEL_IDS) {
            if (channelId.equals(legacyChannelId)) {
                return true;
            }
        }
        return false;
    }
}
