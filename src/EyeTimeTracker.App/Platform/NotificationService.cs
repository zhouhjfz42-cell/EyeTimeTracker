using System.Windows.Forms;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;

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

    public void ShowDailyReminder(TrackerSettings settings)
    {
        if (_dispatcher.IsDisposed || !_dispatcher.IsHandleCreated)
        {
            return;
        }

        if (_dispatcher.InvokeRequired)
        {
            try
            {
                _dispatcher.BeginInvoke(() => ShowDailyReminderCore(settings));
            }
            catch (InvalidOperationException)
            {
            }

            return;
        }

        ShowDailyReminderCore(settings);
    }

    private void ShowDailyReminderCore(TrackerSettings settings)
    {
        var title = ReminderMessage.Title;
        var body = ReminderMessage.Body(settings.ReminderThresholdSeconds);
        ShowTopMostReminder(title, body);
        _notifyIcon.BalloonTipTitle = title;
        _notifyIcon.BalloonTipText = body;
        _notifyIcon.BalloonTipIcon = ToolTipIcon.Info;
        _notifyIcon.ShowBalloonTip(5000);
    }

    private static void ShowTopMostReminder(string title, string body)
    {
        using var owner = new Form
        {
            StartPosition = FormStartPosition.Manual,
            Size = new System.Drawing.Size(1, 1),
            Location = new System.Drawing.Point(-2000, -2000),
            ShowInTaskbar = false,
            TopMost = true
        };

        owner.Show();
        owner.Hide();
        MessageBox.Show(
            owner,
            body,
            title,
            MessageBoxButtons.OK,
            MessageBoxIcon.Information);
    }
}
