package com.eyetimetracker.android;

public final class ContinuousReminderBaseline {
    private ContinuousReminderBaseline() {
    }

    public static long resolve(
            ReminderRuntimeState localState,
            ReminderRuntimeState peerState,
            boolean peerOnline) {
        if (localState == null
                || !localState.isCounting
                || localState.currentSessionStartedUnixSeconds <= 0L) {
            return 0L;
        }

        if (peerOnline
                && peerState != null
                && peerState.isCounting
                && peerState.currentSessionStartedUnixSeconds > 0L) {
            return Math.min(
                    localState.currentSessionStartedUnixSeconds,
                    peerState.currentSessionStartedUnixSeconds);
        }

        return localState.currentSessionStartedUnixSeconds;
    }
}
