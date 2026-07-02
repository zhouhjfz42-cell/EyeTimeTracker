using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class SyncPeerConnectionState
{
    public static bool IsOnline(SyncSettings sync, long nowUnixSeconds, int offlineAfterSeconds)
    {
        if (sync is null || !sync.IsPaired || sync.LastSyncUnixSeconds <= 0)
        {
            return false;
        }

        var ageSeconds = nowUnixSeconds - sync.LastSyncUnixSeconds;
        return ageSeconds <= Math.Max(1, offlineAfterSeconds);
    }
}
