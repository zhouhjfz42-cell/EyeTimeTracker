using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.Core.Models;

public sealed class SyncSettings
{
    public static SyncSettings Unpaired => new();

    public bool IsPaired { get; set; }
    public string PeerDeviceId { get; set; } = string.Empty;
    public string PeerPlatform { get; set; } = string.Empty;
    public string SharedSecret { get; set; } = string.Empty;
    public string LastKnownHost { get; set; } = string.Empty;
    public int LastKnownPort { get; set; }
    public long LastSyncUnixSeconds { get; set; }
    public string LastError { get; set; } = string.Empty;
    public ReminderRuntimeState LocalReminderState { get; set; } = new();
    public ReminderRuntimeState PeerReminderState { get; set; } = new();
}
