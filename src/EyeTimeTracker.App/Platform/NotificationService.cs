using System.Windows.Forms;

namespace EyeTimeTracker.App.Platform;

public sealed class NotificationService
{
    private readonly NotifyIcon _notifyIcon;
    private readonly Control _dispatcher;

    public NotificationService(NotifyIcon notifyIcon, Control dispatcher)
    {
        _notifyIcon = notifyIcon ?? throw new ArgumentNullException(nameof(notifyIcon));
        _dispatcher = dispatcher ?? throw new ArgumentNullException(nameof(dispatcher));
    }

    public void ShowDailyReminder()
    {
        if (_dispatcher.IsDisposed || !_dispatcher.IsHandleCreated)
        {
            return;
        }

        if (_dispatcher.InvokeRequired)
        {
            try
            {
                _dispatcher.BeginInvoke(ShowDailyReminderCore);
            }
            catch (InvalidOperationException)
            {
            }

            return;
        }

        ShowDailyReminderCore();
    }

    private void ShowDailyReminderCore()
    {
        _notifyIcon.BalloonTipTitle = "用眼提醒";
        _notifyIcon.BalloonTipText = "今天的屏幕使用时间已经达到提醒线，建议休息一下眼睛。";
        _notifyIcon.BalloonTipIcon = ToolTipIcon.Info;
        _notifyIcon.ShowBalloonTip(5000);
    }
}
