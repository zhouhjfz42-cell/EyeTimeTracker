namespace EyeTimeTracker.Core.DesktopActivity;

/// <summary>
/// 桌型：决定主界面显示"估算久坐"（普通桌）还是"连续静态工作"（升降桌／不确定）。
/// 不预选普通桌；首次引导跳过记为 Unknown。
/// </summary>
public enum DeskType
{
    Unknown = 0,
    Ordinary = 1,
    Adjustable = 2
}

/// <summary>
/// 桌面策略。识别参数与建议动作时长是不同字段，
/// 修改"活动 3 分钟"不得改变"自动中断 2 分钟"的识别口径。
/// </summary>
public sealed record DesktopSettings
{
    public DeskType Desk { get; init; } = DeskType.Unknown;

    /// <summary>活动采样目标；允许最大一个采样周期检测延迟。</summary>
    public int ActivitySamplingSeconds { get; init; } = 5;

    /// <summary>短暂停顿容忍，超过后进入待确认离开。</summary>
    public int IdleGraceSeconds { get; init; } = 60;

    /// <summary>从最后输入起连续无输入达到此值，认定一次"推定桌面中断"。</summary>
    public int QualifyingAwaySeconds { get; init; } = 120;

    /// <summary>远望提醒间隔（秒）。</summary>
    public int EyeIntervalSeconds { get; init; } = 20 * 60;

    /// <summary>远望建议动作时长（秒）。</summary>
    public int EyeSuggestedSeconds { get; init; } = 20;

    /// <summary>活动提醒间隔（秒）。</summary>
    public int MovementIntervalSeconds { get; init; } = 40 * 60;

    /// <summary>活动建议动作时长（秒）。</summary>
    public int MovementSuggestedSeconds { get; init; } = 2 * 60;

    public bool EyeEnabled { get; init; } = true;
    public bool MovementEnabled { get; init; } = true;

    /// <summary>提醒通道：声音、轻通知、弹窗可组合；至少保留一种，否则该类提醒显示为已关闭。</summary>
    public bool EyeSoundEnabled { get; init; } = true;
    public bool EyeNotificationEnabled { get; init; } = true;
    public bool EyeDialogEnabled { get; init; }
    public bool MovementSoundEnabled { get; init; } = true;
    public bool MovementNotificationEnabled { get; init; } = true;
    public bool MovementDialogEnabled { get; init; }

    /// <summary>免打扰时段（分钟 of day，支持跨午夜）；均为 null 表示不启用。</summary>
    public int? QuietHoursStartMinuteOfDay { get; init; }
    public int? QuietHoursEndMinuteOfDay { get; init; }

    /// <summary>>40 分钟连续静态时间指标，固定 2400 秒，不随提醒设置变化。</summary>
    public const int LongSessionThresholdSeconds = 40 * 60;

    /// <summary>暂停提醒的默认时长（分钟）。</summary>
    public const int DefaultPauseReminderMinutes = 30;

    public string PolicyVersion { get; init; } = "desktop-v1";

    public static DesktopSettings Default { get; } = new();
}
