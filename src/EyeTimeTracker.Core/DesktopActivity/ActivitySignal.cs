namespace EyeTimeTracker.Core.DesktopActivity;

public enum ActivitySignalKind
{
    /// <summary>键盘或鼠标输入（不含内容）。</summary>
    Input,
    LockScreen,
    UnlockScreen,
    Suspend,
    Resume,
    /// <summary>应用退出、崩溃或采样大缺口；之前的时间记 unknown gap。</summary>
    ObservationLost,
    /// <summary>桌型修改：结束当前分类段并开始新段，不算身体中断。</summary>
    DeskProfileChanged
}

/// <summary>平台活动信号。ObservedAtUtc 为事件发生地墙上时间，MonotonicMs 为运行时单调时间。</summary>
public sealed record ActivitySignal(
    ActivitySignalKind Kind,
    DateTimeOffset ObservedAtUtc,
    long MonotonicMs,
    DeskType? NewDeskType = null);
