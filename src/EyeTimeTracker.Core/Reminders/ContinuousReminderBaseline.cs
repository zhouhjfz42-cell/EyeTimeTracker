using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.Core.Reminders;

public static class ContinuousReminderBaseline
{
    private const long MaxPlausibleSessionSeconds = 24 * 60 * 60; // 24 小时

    public static long Resolve(
        ReminderRuntimeState localState,
        ReminderRuntimeState? peerState,
        bool peerOnline,
        long nowUnixSeconds = 0)
    {
        if (localState is null
            || !localState.IsCounting
            || localState.CurrentSessionStartedUnixSeconds <= 0)
        {
            return 0;
        }

        var now = nowUnixSeconds > 0
            ? nowUnixSeconds
            : DateTimeOffset.Now.ToUnixTimeSeconds();

        var localStart = localState.CurrentSessionStartedUnixSeconds;
        if (now - localStart > MaxPlausibleSessionSeconds)
        {
            return 0;
        }

        if (peerOnline
            && peerState is not null
            && peerState.IsCounting
            && peerState.CurrentSessionStartedUnixSeconds > 0)
        {
            var peerStart = peerState.CurrentSessionStartedUnixSeconds;
            if (now - peerStart > MaxPlausibleSessionSeconds)
            {
                return localStart;
            }

            return Math.Min(localStart, peerStart);
        }

        return localStart;
    }
}
