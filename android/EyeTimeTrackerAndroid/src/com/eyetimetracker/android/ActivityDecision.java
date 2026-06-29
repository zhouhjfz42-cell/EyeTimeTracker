package com.eyetimetracker.android;

public final class ActivityDecision {
    private final boolean counting;
    private final boolean screenOn;
    private final boolean motionRecent;
    private final boolean mediaActive;

    private ActivityDecision(boolean counting, boolean screenOn, boolean motionRecent, boolean mediaActive) {
        this.counting = counting;
        this.screenOn = screenOn;
        this.motionRecent = motionRecent;
        this.mediaActive = mediaActive;
    }

    public static ActivityDecision evaluate(
            boolean screenOn,
            long millisecondsSinceMotion,
            boolean mediaActive,
            long millisecondsSinceLastTick,
            long motionThresholdMilliseconds) {
        boolean motionRecent = millisecondsSinceMotion <= motionThresholdMilliseconds;
        boolean counting = screenOn && (motionRecent || mediaActive);
        return new ActivityDecision(counting, screenOn, motionRecent, mediaActive);
    }

    public boolean isCounting() {
        return counting;
    }

    public boolean isScreenOn() {
        return screenOn;
    }

    public boolean isMotionRecent() {
        return motionRecent;
    }

    public boolean isMediaActive() {
        return mediaActive;
    }
}