package com.eyetimetracker.android;

public final class ReminderNotificationProfile {
    public static final String CHANNEL_ID = "eye_time_tracker_reminders_v3";
    public static final String[] LEGACY_CHANNEL_IDS = new String[] {
            "eye_time_tracker_reminders",
            "eye_time_tracker_reminders_v2"
    };
    public static final int CHANNEL_IMPORTANCE = 4;
    public static final int NOTIFICATION_PRIORITY = 2;
    // 不再申请全屏弹窗，提醒只以顶部横幅通知出现，由系统自动收起
    public static final boolean USE_FULL_SCREEN_INTENT = false;
    // 连续用眼=绿色（轻提醒），累计用眼=橙红（当天总量超标，需要正式休息）
    public static final int ACCENT_CONTINUOUS = 0xFF16A67D;
    public static final int ACCENT_CUMULATIVE = 0xFFE07A28;
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
