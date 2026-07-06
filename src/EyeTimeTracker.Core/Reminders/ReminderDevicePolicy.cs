using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.Core.Reminders;

public static class ReminderDevicePolicy
{
    public static bool ShouldShowOnLocalDevice(
        ReminderRuntimeState localState,
        ReminderRuntimeState? peerState,
        bool peerOnline)
    {
        if (!localState.IsCounting)
        {
            return false;
        }

        return true;
    }

    public static bool ShouldPcShowReminder(SyncSettings? sync, long nowUnixSeconds, int peerOfflineAfterSeconds)
    {
        var localState = sync?.LocalReminderState ?? new ReminderRuntimeState();
        var peerState = sync?.PeerReminderState;
        var peerOnline = sync is not null
            && SyncPeerConnectionState.IsOnline(sync, nowUnixSeconds, peerOfflineAfterSeconds);
        return ShouldShowOnLocalDevice(localState, peerState, peerOnline);
    }
}
