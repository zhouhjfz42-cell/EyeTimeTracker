using System.Windows.Forms;

namespace EyeTimeTracker.App.Platform;

public sealed class NotificationService
{
    private readonly NotifyIcon _notifyIcon;

    public NotificationService(NotifyIcon notifyIcon)
    {
        _notifyIcon = notifyIcon ?? throw new ArgumentNullException(nameof(notifyIcon));
    }

    public void ShowDailyReminder()
    {
        _notifyIcon.BalloonTipTitle = "用眼提醒";
        _notifyIcon.BalloonTipText = "今天的屏幕使用时间已经达到提醒线，建议休息一下眼睛。";
        _notifyIcon.BalloonTipIcon = ToolTipIcon.Info;
        _notifyIcon.ShowBalloonTip(5000);
    }
}
