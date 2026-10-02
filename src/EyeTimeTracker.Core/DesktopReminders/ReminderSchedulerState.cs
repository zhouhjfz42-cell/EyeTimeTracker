namespace EyeTimeTracker.Core.DesktopReminders;

/// <summary>
/// 提醒调度器的持久化状态。周期基准记录"上次调度（或策略修改）时的会话有效秒数"，
/// 修改提醒配置不追补旧点、不清 sessionActiveSeconds；有效离开后两个计时重置。
/// </summary>
public sealed record ReminderSchedulerState
{
    public long EyeCycleBaseActiveSeconds { get; set; }
    public long MovementCycleBaseActiveSeconds { get; set; }
    public int EyeSequence { get; set; }
    public int MovementSequence { get; set; }
    public string PolicyVersion { get; set; } = string.Empty;
    public DateTimeOffset? PausedUntilUtc { get; set; }
    public string? ActiveSessionId { get; set; }
    public int DeliveryGroupSequence { get; set; }
}
