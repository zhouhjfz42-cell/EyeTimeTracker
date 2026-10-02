namespace EyeTimeTracker.Core.DesktopActivity;

public enum ClassifierState
{
    /// <summary>启动后尚无新活动信号；不补计关闭程序期间时间。</summary>
    Unknown,
    Active,
    /// <summary>无输入超过容忍窗口：冻结提醒，撤回暂存尾部，等待确认离开。</summary>
    IdleCandidate,
    /// <summary>已认定离开；会话已结束，中断事件待返回后关闭。</summary>
    Away,
    /// <summary>锁屏缺席：立即暂停活动，持续达到阈值可生成推定中断。</summary>
    LockAbsence,
    /// <summary>休眠或观察丢失；恢复后新建会话。</summary>
    Unavailable
}

/// <summary>已确认有效活动区间；committed 不可反向修改。</summary>
public sealed record CommittedInterval(
    string SessionId,
    DateTimeOffset StartUtc,
    DateTimeOffset EndUtc,
    DeskType DeskType)
{
    public long DurationSeconds => Math.Max(0, (long)(EndUtc - StartUtc).TotalSeconds);
}

/// <summary>推定桌面中断。结束时间要到新输入后才确定（ReturnedAtUtc 回填，同一 breakId 只更新）。</summary>
public sealed record InferredBreak(
    string BreakId,
    string SessionId,
    DeskType DeskType,
    DateTimeOffset AbsenceStartedAtUtc,
    DateTimeOffset ThresholdReachedAtUtc,
    string Reason)
{
    public DateTimeOffset? ReturnedAtUtc { get; set; }
    public string? LinkedReminderId { get; set; }
}

public sealed record CoverageGapRecord(
    DateTimeOffset FromUtc,
    string Reason)
{
    public DateTimeOffset? ToUtc { get; set; }
}

/// <summary>会话边界。结束原因：inferred_away / suspend / shutdown / unknown_gap。</summary>
public sealed record SessionBoundary(
    string SessionId,
    DeskType DeskType,
    DateTimeOffset StartedAtUtc)
{
    public DateTimeOffset? EndedAtUtc { get; set; }
    public string? EndReason { get; set; }

    public const string ReasonInferredAway = "inferred_away";
    public const string ReasonSuspend = "suspend";
    public const string ReasonShutdown = "shutdown";
    public const string ReasonUnknownGap = "unknown_gap";
}
