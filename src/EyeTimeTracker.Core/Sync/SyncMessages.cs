using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class SyncMessageTypes
{
    public const string PairRequest = "pairRequest";
    public const string PairAccept = "pairAccept";
    public const string SyncRequest = "syncRequest";
    public const string SyncResponse = "syncResponse";
    public const string DisconnectRequest = "disconnectRequest";
    public const string DisconnectResponse = "disconnectResponse";
    public const string ReminderClaim = "reminderClaim";
    public const string DiscoveryRequest = "discoveryRequest";
    public const string DiscoveryResponse = "discoveryResponse";
}

public sealed class PairRequest
{
    public string Type { get; set; } = SyncMessageTypes.PairRequest;
    public string PairingCode { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = "android";
    public long TimestampUnixSeconds { get; set; }
}

public sealed class PairAccept
{
    public string Type { get; set; } = SyncMessageTypes.PairAccept;
    public bool Accepted { get; set; }
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = "windows";
    public string SharedSecret { get; set; } = string.Empty;
    public string Error { get; set; } = string.Empty;
    public long TimestampUnixSeconds { get; set; }
}

public sealed class DiscoveryRequest
{
    public string Type { get; set; } = SyncMessageTypes.DiscoveryRequest;
    public string Platform { get; set; } = "android";
}

public sealed class DiscoveryResponse
{
    public string Type { get; set; } = SyncMessageTypes.DiscoveryResponse;
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = "windows";
    public int Port { get; set; }
}

public sealed class SyncRequest
{
    public string Type { get; set; } = SyncMessageTypes.SyncRequest;
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = string.Empty;
    public List<UsageSegment> Segments { get; set; } = new();
    public List<AppUsageEntry> AppUsageEntries { get; set; } = new();
    public TrackerSettings Settings { get; set; } = TrackerSettings.Default;
    public ReminderRuntimeState ReminderState { get; set; } = new();
    public bool SupportsMutableSegments { get; set; }
    public long SinceUnixSeconds { get; set; }
    public long TimestampUnixSeconds { get; set; }
    public string Signature { get; set; } = string.Empty;
}

public sealed class SyncResponse
{
    public string Type { get; set; } = SyncMessageTypes.SyncResponse;
    public bool Accepted { get; set; }
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = string.Empty;
    public List<UsageSegment> Segments { get; set; } = new();
    public List<AppUsageEntry> AppUsageEntries { get; set; } = new();
    public ReminderRuntimeState ReminderState { get; set; } = new();
    public bool SupportsMutableSegments { get; set; }
    public string Error { get; set; } = string.Empty;
    public long TimestampUnixSeconds { get; set; }
    public string Signature { get; set; } = string.Empty;
}

public sealed class DisconnectRequest
{
    public string Type { get; set; } = SyncMessageTypes.DisconnectRequest;
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = string.Empty;
    public long TimestampUnixSeconds { get; set; }
}

public sealed class DisconnectResponse
{
    public string Type { get; set; } = SyncMessageTypes.DisconnectResponse;
    public bool Accepted { get; set; }
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = string.Empty;
    public string Error { get; set; } = string.Empty;
    public long TimestampUnixSeconds { get; set; }
}

public sealed class ReminderClaimMessage
{
    public string Type { get; set; } = SyncMessageTypes.ReminderClaim;
    public string DeviceId { get; set; } = string.Empty;
    public string ReminderKey { get; set; } = string.Empty;
    public bool DeviceActive { get; set; }
    public long TimestampUnixSeconds { get; set; }
    public string Signature { get; set; } = string.Empty;
}

public sealed class ReminderRuntimeState
{
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = string.Empty;
    public bool IsCounting { get; set; }
    public long CurrentSessionStartedUnixSeconds { get; set; }
    // 连续用眼提醒认领状态：会话起点（共享基线）和已提醒到第几次，双端同步后取较大值对齐
    public long ContinuousClaimSessionStartedUnixSeconds { get; set; }
    public int ContinuousClaimLastStep { get; set; }
}
