namespace EyeTimeTracker.Core.DesktopReminders;

/// <summary>
/// 提醒实例。状态：Due → Suppressed / Dispatching → Delivered / DeliveryFailed → Closed。
/// 幂等键为 deviceId + sessionId + reminderType + occurrenceIndex + policyVersion，
/// 落盘后投递，崩溃恢复不重复播音。展示成功与规则触发分开记录。
/// </summary>
public sealed record ReminderInstance
{
    public required string IdempotencyKey { get; init; }
    public required string ReminderId { get; init; }
    public required string SessionId { get; init; }
    public required string ReminderType { get; init; }
    public int OccurrenceIndex { get; init; }
    public required string PolicyVersion { get; init; }
    public required long DueActiveSeconds { get; init; }
    public required DateTimeOffset DueAtUtc { get; init; }
    public string Status { get; set; } = StatusDue;
    public string? SuppressionReason { get; set; }
    public string? DeliveryGroupId { get; set; }
    public bool MergedIntoGroup { get; set; }
    public DateTimeOffset? DispatchAtUtc { get; set; }
    public DateTimeOffset? ClosedAtUtc { get; set; }
    public string? DeliveryEvidence { get; set; }
    public int RedeliveryCount { get; set; }

    public const string StatusDue = "Due";
    public const string StatusSuppressed = "Suppressed";
    public const string StatusDispatching = "Dispatching";
    public const string StatusDelivered = "Delivered";
    public const string StatusDeliveryFailed = "DeliveryFailed";
    public const string StatusClosed = "Closed";

    public const string ReasonNotActive = "not_active";
    public const string ReasonPaused = "paused";
    public const string ReasonQuietHours = "quiet_hours";
    public const string ReasonDispatchInterrupted = "dispatch_interrupted";

    public static string BuildIdempotencyKey(
        string deviceId,
        string sessionId,
        string reminderType,
        int occurrenceIndex,
        string policyVersion)
    {
        return string.Join(
            ":",
            deviceId,
            sessionId,
            reminderType,
            occurrenceIndex.ToString(),
            policyVersion);
    }
}
