using System.Windows.Forms;
using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.UI;
using EyeTimeTracker.Core.DesktopActivity;
using EyeTimeTracker.Core.DesktopReminders;

namespace EyeTimeTracker.App.Platform;

/// <summary>
/// 默认提醒投递：声音＋轻通知（应用自绘小窗，5 秒自动消失，无需点击）。
/// 投递成功仅表示应用确实播放／展示，不能表示用户看到或听到；
/// 每次投递保留 deliveryEvidence。合并组只投递活动提醒，远望随组展示。
/// </summary>
public sealed class DesktopReminderDispatcher
{
    private readonly Icon? _appIcon;
    private readonly Control _dispatcher;

    public DesktopReminderDispatcher(Icon? appIcon, Control dispatcher)
    {
        _appIcon = appIcon;
        _dispatcher = dispatcher ?? throw new ArgumentNullException(nameof(dispatcher));
    }

    /// <summary>投递一组实例（单条或合并组）。就地更新实例状态与证据。</summary>
    public void Dispatch(IReadOnlyList<ReminderInstance> instances, DesktopSettings settings)
    {
        ArgumentNullException.ThrowIfNull(instances);
        ArgumentNullException.ThrowIfNull(settings);
        if (instances.Count == 0)
        {
            return;
        }

        if (_dispatcher.InvokeRequired)
        {
            _dispatcher.BeginInvoke(() => Dispatch(instances, settings));
            return;
        }

        var now = DateTimeOffset.UtcNow;
        var movement = instances.FirstOrDefault(
            instance => instance.ReminderType == "movement"
            && instance.Status == ReminderInstance.StatusDispatching);
        var eye = instances.FirstOrDefault(
            instance => instance.ReminderType == "eye"
            && instance.Status == ReminderInstance.StatusDispatching);

        var soundOk = false;
        var soundError = string.Empty;
        try
        {
            // 合成双音：活动「叮-咚」与远望单音一听即分；合并组只播活动音，不连播
            if (movement is not null && settings.MovementSoundEnabled)
            {
                ReminderTones.PlayMovement();
                soundOk = true;
            }
            else if (movement is null && eye is not null && settings.EyeSoundEnabled)
            {
                ReminderTones.PlayEye();
                soundOk = true;
            }
        }
        catch (Exception exception)
        {
            soundError = exception.Message;
        }

        var toastShown = false;
        var toastError = string.Empty;
        var notificationEnabled = movement is not null
            ? settings.MovementNotificationEnabled
            : settings.EyeNotificationEnabled;
        if (notificationEnabled)
        {
            try
            {
                // 应用自绘轻通知（系统气球在部分 Windows 11 环境被静默吞掉）；
                // 合并组只出活动一条，远望随组展示
                var (title, body) = BuildToastText(movement, eye, settings);
                DesktopReminderToast.ShowToast(movement is not null, title, body, _appIcon);
                toastShown = true;
            }
            catch (Exception exception)
            {
                toastError = exception.Message;
            }
        }

        foreach (var instance in instances)
        {
            if (instance.Status != ReminderInstance.StatusDispatching)
            {
                continue;
            }

            instance.DispatchAtUtc = now;
            instance.ClosedAtUtc = now;
            var evidence = new List<string>();
            if (soundOk)
            {
                evidence.Add("sound=played");
            }
            else if (!string.IsNullOrEmpty(soundError))
            {
                evidence.Add("sound=failed:" + soundError);
            }

            if (toastShown)
            {
                evidence.Add("toast=shown");
            }
            else if (notificationEnabled && !string.IsNullOrEmpty(toastError))
            {
                evidence.Add("toast=failed:" + toastError);
            }
            else if (!notificationEnabled)
            {
                evidence.Add("toast=disabled");
            }

            instance.DeliveryEvidence = string.Join(";", evidence);
            var anyDelivered = soundOk || toastShown;
            // 只播声音：有触发及声音投递结果，没有通知展示计数
            instance.Status = anyDelivered
                ? ReminderInstance.StatusDelivered
                : ReminderInstance.StatusDeliveryFailed;
        }
    }

    private static (string Title, string Body) BuildToastText(
        ReminderInstance? movement,
        ReminderInstance? eye,
        DesktopSettings settings)
    {
        if (movement is not null)
        {
            var minutes = Math.Max(1, settings.MovementSuggestedSeconds / 60).ToString();
            var titleKey = settings.Desk == DeskType.Ordinary
                ? "desktop.movement.toastTitle.ordinary"
                : "desktop.movement.toastTitle.other";
            var title = AppText.Format(titleKey, ("minutes", minutes));
            var body = eye is not null
                ? AppText.Get("desktop.movement.toastBody")
                : AppText.Get("desktop.movement.toastBody");
            return (title, body);
        }

        var seconds = settings.EyeSuggestedSeconds.ToString();
        return (
            AppText.Get("desktop.eye.toastTitle"),
            AppText.Format("desktop.eye.toastBody", ("seconds", seconds)));
    }
}
