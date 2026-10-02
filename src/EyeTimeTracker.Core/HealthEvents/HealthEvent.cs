namespace EyeTimeTracker.Core.HealthEvents;

/// <summary>
/// 中性健康事件信封。eventId 稳定，revision 递增；删除通过 tombstone（deletedAtUtc）。
/// 本地匿名使用，不强制 personId；授权共享时由适配器绑定。
/// </summary>
public sealed record HealthEvent
{
    public required string EventId { get; init; }
    public required string EventType { get; init; }
    public int SchemaVersion { get; init; } = 1;
    public int Revision { get; init; } = 1;
    public required string DeviceId { get; init; }
    public required string Platform { get; init; }
    public string? SessionId { get; init; }
    public required DateTimeOffset StartedAtUtc { get; init; }
    public DateTimeOffset? EndedAtUtc { get; init; }
    public required DateTimeOffset ObservedAtUtc { get; init; }
    public string TimeZoneId { get; init; } = string.Empty;
    public int UtcOffsetMinutes { get; init; }
    public required string Source { get; init; }
    public required string Evidence { get; init; }
    public required string PolicyVersion { get; init; }
    public required DateTimeOffset CreatedAtUtc { get; init; }
    public required DateTimeOffset UpdatedAtUtc { get; init; }
    public DateTimeOffset? DeletedAtUtc { get; init; }
    public required object Payload { get; init; }

    public const string DesktopActivity = nameof(DesktopActivity);
    public const string ActivitySession = nameof(ActivitySession);
    public const string SedentaryBreak = nameof(SedentaryBreak);
    public const string ReminderEvent = nameof(ReminderEvent);
    public const string ReminderResponse = nameof(ReminderResponse);
    public const string CoverageGap = nameof(CoverageGap);
    public const string DeskProfileChanged = nameof(DeskProfileChanged);
}

public sealed record DesktopActivityPayload(
    string DeskType,
    string ActivityEvidence,
    long ActiveSeconds,
    bool IsProvisional);

/// <summary>结束原因：inferred_away / suspend / shutdown / unknown_gap。</summary>
public sealed record ActivitySessionPayload(
    long ActiveSeconds,
    string DeskType,
    string? EndReason);

/// <summary>
/// SedentaryBreak 是健康交换层兼容名称，不能凭名称断言坐姿中断。
/// scope=desktop_inactivity 与 evidence 必须随事件导出。
/// </summary>
public sealed record SedentaryBreakPayload(
    string BreakId,
    string Scope,
    string DeskType,
    DateTimeOffset AbsenceStartedAtUtc,
    DateTimeOffset ThresholdReachedAtUtc,
    DateTimeOffset? ReturnedAtUtc,
    long? DurationSeconds,
    bool Inferred,
    bool PostureVerified,
    bool MovementVerified,
    string Reason,
    string? LinkedReminderId)
{
    public const string ScopeDesktopInactivity = "desktop_inactivity";
    public const string ReasonIdleTimeout = "idle_timeout";
    public const string ReasonLockScreen = "lock_screen";
}

/// <summary>状态：Due / Suppressed / Dispatching / Delivered / DeliveryFailed / Closed。</summary>
public sealed record ReminderEventPayload(
    string ReminderId,
    string ReminderType,
    long DueActiveSeconds,
    DateTimeOffset ScheduledAtUtc,
    DateTimeOffset? DispatchAtUtc,
    string Status,
    IReadOnlyList<string> Channels,
    string? DeliveryEvidence,
    string? DeliveryGroupId,
    string? SuppressionReason)
{
    public const string TypeEye = "eye";
    public const string TypeMovement = "movement";
    public const string StatusDue = "Due";
    public const string StatusSuppressed = "Suppressed";
    public const string StatusDispatching = "Dispatching";
    public const string StatusDelivered = "Delivered";
    public const string StatusDeliveryFailed = "DeliveryFailed";
    public const string StatusClosed = "Closed";
}

/// <summary>响应类型：inferred_desktop_break / self_reported_eye。</summary>
public sealed record ReminderResponsePayload(
    string ReminderId,
    string? BreakId,
    string ResponseType,
    DateTimeOffset ObservedAtUtc)
{
    public const string TypeInferredDesktopBreak = "inferred_desktop_break";
    public const string TypeSelfReportedEye = "self_reported_eye";
}

/// <summary>不可观察缺口：sleep / app_closed / signal_unavailable / clock_jump。不能变成健康收益。</summary>
public sealed record CoverageGapPayload(
    string Reason)
{
    public const string ReasonSleep = "sleep";
    public const string ReasonAppClosed = "app_closed";
    public const string ReasonSignalUnavailable = "signal_unavailable";
    public const string ReasonClockJump = "clock_jump";
}

public sealed record DeskProfileChangedPayload(
    string NewDeskType,
    DateTimeOffset EffectiveAtUtc);
