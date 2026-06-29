package com.eyetimetracker.android;

public final class CoreLogicTest {
    public static void main(String[] args) {
        shouldCountWhenScreenOnAndMotionRecent();
        shouldCountWhenScreenOnAndMediaActive();
        shouldPauseWhenScreenOff();
        shouldPauseWhenScreenOnButIdleAndNoMedia();
        shouldFormatDurations();
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
        assertEquals("1小时 05分钟", DurationFormatter.format(3900), "hours and minutes");
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", actual " + actual);
        }
    }
}
