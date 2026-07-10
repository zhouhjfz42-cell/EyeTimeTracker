package com.eyetimetracker.android;

public final class ActivityDecisionModelTest {
    public static void main(String[] args) {
        shouldCountScreenOnTimeWithoutMotionOrMedia();
        shouldPauseWhenScreenOff();
        System.out.println("All Android activity decision model tests passed.");
    }

    private static void shouldCountScreenOnTimeWithoutMotionOrMedia() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 600_000L, false, 600_000L, 180_000L);
        assertEquals(true, decision.isCounting(), "screen-on time counts without motion or media");
    }

    private static void shouldPauseWhenScreenOff() {
        ActivityDecision decision = ActivityDecision.evaluate(false, 0L, true, 0L, 180_000L);
        assertEquals(false, decision.isCounting(), "screen off pauses");
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", actual " + actual);
        }
    }
}
