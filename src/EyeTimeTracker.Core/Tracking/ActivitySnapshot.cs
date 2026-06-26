namespace EyeTimeTracker.Core.Tracking;

public sealed record ActivitySnapshot(
    DateTimeOffset Timestamp,
    TimeSpan IdleTime,
    bool IsAudioActive,
    bool IsSessionUnlocked,
    bool IsSuspended);
