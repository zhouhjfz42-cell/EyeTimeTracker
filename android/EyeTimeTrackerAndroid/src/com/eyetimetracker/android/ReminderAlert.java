package com.eyetimetracker.android;

public final class ReminderAlert {
    private ReminderAlert() {
    }

    public static String title() {
        return "\u7528\u773c\u63d0\u9192";
    }

    public static String message(int reminderMinutes) {
        return "\u4eca\u5929\u7528\u773c\u65f6\u95f4\u5df2\u8fbe\u5230 "
                + ReminderThreshold.format(reminderMinutes)
                + "\uff0c\u5efa\u8bae\u4f11\u606f\u4e00\u4e0b\u773c\u775b\u3002";
    }
}
