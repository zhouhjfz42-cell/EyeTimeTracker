using System.Media;
using System.Windows.Forms;
using EyeTimeTracker.App.Localization;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.App.UI;

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

    public void ShowDailyReminder(TrackerSettings settings, int reminderStep)
    {
        if (_dispatcher.IsDisposed || !_dispatcher.IsHandleCreated)
        {
            return;
        }

        if (_dispatcher.InvokeRequired)
        {
            try
            {
                _dispatcher.BeginInvoke(() => ShowDailyReminderCore(settings, reminderStep));
            }
            catch (InvalidOperationException)
            {
            }

            return;
        }

        ShowDailyReminderCore(settings, reminderStep);
    }

    private void ShowDailyReminderCore(TrackerSettings settings, int reminderStep)
    {
        var title = ReminderText.Title;
        var body = ReminderText.Body(settings.ReminderThresholdSeconds, settings.RepeatReminder, reminderStep);
        using var dialog = new PcReminderDialog(title, body, _notifyIcon.Icon);
        PlayReminderSound();
        dialog.ShowDialog();
    }

    private static void PlayReminderSound()
    {
        try
        {
            SystemSounds.Exclamation.Play();
        }
        catch (InvalidOperationException)
        {
        }
    }
}
