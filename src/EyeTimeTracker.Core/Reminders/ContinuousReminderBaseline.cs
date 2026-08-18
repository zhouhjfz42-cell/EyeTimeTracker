using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.Core.Reminders;

public static class ContinuousReminderBaseline
{
    public static long Resolve(
        ReminderRuntimeState localState,
        ReminderRuntimeState? peerState,
        bool peerOnline)
    {
        if (localState is null
            || !localState.IsCounting
            || localState.CurrentSessionStartedUnixSeconds <= 0)
        {
            return 0;
        }

        if (peerOnline
            && peerState is not null
            && peerState.IsCounting
            && peerState.CurrentSessionStartedUnixSeconds > 0)
        {
            return Math.Min(
                localState.CurrentSessionStartedUnixSeconds,
                peerState.CurrentSessionStartedUnixSeconds);
        }

        return localState.CurrentSessionStartedUnixSeconds;
    }
}
