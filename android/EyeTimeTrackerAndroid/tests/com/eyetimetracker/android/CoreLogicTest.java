package com.eyetimetracker.android;

public final class CoreLogicTest {
    public static void main(String[] args) {
        shouldCountWhenScreenOnAndMotionRecent();
        shouldCountWhenScreenOnAndMediaActive();
        shouldPauseWhenScreenOff();
        shouldPauseWhenScreenOnButIdleAndNoMedia();
        shouldFormatDurations();
        shouldClassifyTodayToneByFixedHealthyThresholds();
        shouldFormatReminderThresholds();
        shouldFormatReminderAlertText();
        shouldNotifyOnceOrAtRepeatMultiples();
        System.out.println("All Android core tests passed.");
    }

    private static void shouldCountWhenScreenOnAndMotionRecent() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 20_000L, true, 60_000L, 180_000L);
        assertEquals(true, decision.isCounting(), "motion recent counts");
    }

    private static void shouldCountWhenScreenOnAndMediaActive() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 600_000L, true, 600_000L, 180_000L);
        assertEquals(true, decision.isCounting(), "media counts when screen on");
    }

    private static void shouldPauseWhenScreenOff() {
        ActivityDecision decision = ActivityDecision.evaluate(false, 0L, true, 0L, 180_000L);
        assertEquals(false, decision.isCounting(), "screen off pauses");
    }

    private static void shouldPauseWhenScreenOnButIdleAndNoMedia() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 600_000L, false, 600_000L, 180_000L);
        assertEquals(false, decision.isCounting(), "idle without media pauses");
    }

    private static void shouldFormatDurations() {
        assertEquals("0分钟", DurationFormatter.format(59), "under one minute floors to zero");
        assertEquals("4分钟", DurationFormatter.format(299), "minutes only");
        assertEquals("1小时05分", DurationFormatter.format(3900), "hours and minutes");
    }

    private static void shouldClassifyTodayToneByFixedHealthyThresholds() {
        assertEquals(TodayTone.SAFE, TodayTone.fromSeconds(6L * 3600L), "six hours is still green");
        assertEquals(TodayTone.WARN, TodayTone.fromSeconds(6L * 3600L + 1L), "after six hours is yellow");
        assertEquals(TodayTone.WARN, TodayTone.fromSeconds(8L * 3600L), "eight hours is still yellow");
        assertEquals(TodayTone.DANGER, TodayTone.fromSeconds(8L * 3600L + 1L), "after eight hours is red");
    }

    private static void shouldFormatReminderThresholds() {
        assertEquals(330, ReminderThreshold.clampMinutes(330), "keeps valid reminder minutes");
        assertEquals(1, ReminderThreshold.clampMinutes(-5), "clamps reminder lower bound");
        assertEquals("5小时30分", ReminderThreshold.format(330), "formats reminder value");
        assertEquals("即5小时30分", ReminderThreshold.formatEquivalent(330), "formats equivalent hint");
        assertEquals("反复提醒（当天内每330分钟提醒一次）", ReminderThreshold.formatRepeatLabel(330), "formats repeat label");
    }

    private static void shouldFormatReminderAlertText() {
        assertEquals("用眼提醒", ReminderAlert.title(), "formats reminder title");
        assertEquals("今天用眼时间已达到 5小时30分，建议休息一下眼睛。", ReminderAlert.message(330), "formats reminder message");
        assertEquals("今天用眼时间已经第2次达到330分钟了，建议休息一下眼睛。", ReminderAlert.message(330, true, 2), "formats repeat reminder message");
        assertEquals("今天用眼时间已达到 5小时30分，建议休息一下眼睛。", ReminderAlert.message(330, false, 2), "formats once reminder message");
    }

    private static void shouldNotifyOnceOrAtRepeatMultiples() {
        assertEquals(false, ReminderPolicy.shouldNotify(329L * 60L, 330, false, false, 0), "once policy waits for threshold");
        assertEquals(true, ReminderPolicy.shouldNotify(330L * 60L, 330, false, false, 0), "once policy notifies at threshold");
        assertEquals(false, ReminderPolicy.shouldNotify(660L * 60L, 330, false, true, 1), "once policy only notifies once per day");
        assertEquals(false, ReminderPolicy.shouldNotify(329L * 60L, 330, true, false, 0), "repeat policy waits for first threshold");
        assertEquals(true, ReminderPolicy.shouldNotify(330L * 60L, 330, true, false, 0), "repeat policy notifies at first threshold");
        assertEquals(false, ReminderPolicy.shouldNotify(500L * 60L, 330, true, true, 1), "repeat policy does not notify before next multiple");
        assertEquals(true, ReminderPolicy.shouldNotify(660L * 60L, 330, true, true, 1), "repeat policy notifies at second threshold");
        assertEquals(2, ReminderPolicy.reachedStep(660L * 60L, 330), "repeat step is based on today's total");
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", actual " + actual);
        }
    }
}
