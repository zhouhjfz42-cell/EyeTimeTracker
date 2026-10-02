namespace EyeTimeTracker.Core.DesktopActivity;

/// <summary>
/// 新版桌面状态的持久化快照，写入独立版本空间（desktop-state.json）。
/// 旧 state.json 冻结为 LegacyScreenUsage，不伪造为久坐历史；迁移只写 migrationVersion，不重复导入。
/// </summary>
public sealed record DesktopStateSnapshot
{
    public const int CurrentSchemaVersion = 1;
    public const int CurrentMigrationVersion = 1;

    public int SchemaVersion { get; set; } = CurrentSchemaVersion;
    public int MigrationVersion { get; set; } = CurrentMigrationVersion;
    /// <summary>首次引导是否已完成（桌型选择或明确跳过都算完成；Unknown 是有效选择）。</summary>
    public bool HasCompletedOnboarding { get; set; }
    public required DesktopSettings Settings { get; set; }
    public bool StartWithWindows { get; set; } = true;
    public DateTimeOffset? StartedTrackingAtUtc { get; set; }
    public DateTimeOffset SavedAtUtc { get; set; }
    public string DeviceId { get; set; } = string.Empty;
    public List<SessionBoundary> Sessions { get; set; } = new();
    public List<CommittedInterval> Intervals { get; set; } = new();
    public List<InferredBreak> Breaks { get; set; } = new();
    public List<CoverageGapRecord> Gaps { get; set; } = new();
    public ClassifierState LastClassifierState { get; set; } = ClassifierState.Unknown;
    public DesktopReminders.ReminderSchedulerState? ReminderState { get; set; }
    public List<DesktopReminders.ReminderInstance> ReminderInstances { get; set; } = new();
}
