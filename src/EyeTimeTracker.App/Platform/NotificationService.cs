using System.Media;
using System.Windows.Forms;
using EyeTimeTracker.App.Diagnostics;
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

    public void ShowContinuousReminder(string requestId)
    {
        if (_dispatcher.IsDisposed || !_dispatcher.IsHandleCreated)
        {
            ReminderDiagnosticLog.RecordEvent(
                "连续用眼提醒/调度跳过",
                DateTimeOffset.Now,
                requestId,
                $"dispatcherDisposed={_dispatcher.IsDisposed};handleCreated={_dispatcher.IsHandleCreated}");
            return;
        }

        if (_dispatcher.InvokeRequired)
        {
            try
            {
                _dispatcher.BeginInvoke(() => ShowContinuousReminderCore(requestId));
                ReminderDiagnosticLog.RecordEvent(
                    "连续用眼提醒/已提交UI线程",
                    DateTimeOffset.Now,
                    requestId);
            }
            catch (Exception exception)
            {
                ReminderDiagnosticLog.RecordEvent(
                    "连续用眼提醒/UI调度失败",
                    DateTimeOffset.Now,
                    requestId,
                    $"{exception.GetType().Name}: {exception.Message}");
            }

            return;
        }

        ShowContinuousReminderCore(requestId);
    }

    private void ShowDailyReminderCore(TrackerSettings settings, int reminderStep)
    {
        var title = ReminderText.Title;
        var body = ReminderText.Body(settings.ReminderThresholdSeconds, true, reminderStep);
        using var dialog = new PcReminderDialog(title, body, _notifyIcon.Icon);
        PlayReminderSound();
        dialog.ShowDialog();
    }

    private void ShowContinuousReminderCore(string requestId)
    {
        try
        {
            ReminderDiagnosticLog.RecordEvent(
                "连续用眼提醒/开始创建弹窗",
                DateTimeOffset.Now,
                requestId);

            using var dialog = new PcReminderDialog(
                AppText.Get("eyeCareReminders.continuousAlertTitle"),
                AppText.Get("eyeCareReminders.continuousAlertMessage"),
                _notifyIcon.Icon);

            ReminderDiagnosticLog.RecordEvent(
                "连续用眼提醒/弹窗已创建",
                DateTimeOffset.Now,
                requestId);
            PlayGentleReminderSound();
            ReminderDiagnosticLog.RecordEvent(
                "连续用眼提醒/开始显示",
                DateTimeOffset.Now,
                requestId);
            var result = dialog.ShowDialog();
            ReminderDiagnosticLog.RecordEvent(
                "连续用眼提醒/弹窗已关闭",
                DateTimeOffset.Now,
                requestId,
                $"result={result}");
        }
        catch (Exception exception)
        {
            ReminderDiagnosticLog.RecordEvent(
                "连续用眼提醒/弹窗显示失败",
                DateTimeOffset.Now,
                requestId,
                $"{exception.GetType().Name}: {exception.Message}");
        }
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

    private static void PlayGentleReminderSound()
    {
        try
        {
            SystemSounds.Asterisk.Play();
        }
        catch (InvalidOperationException)
        {
        }
    }
}
