using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;

namespace EyeTimeTracker.App.UI;

public sealed class TrayApplicationContext : ApplicationContext
{
    private readonly Icon _appIcon;
    private readonly NotifyIcon _notifyIcon;
    private readonly Control _uiDispatcher;
    private readonly NotificationService _notificationService;
    private readonly DesktopTrackingController _controller;
    private readonly StartupManager _startupManager;
    private readonly TrayMenuForm _trayMenu;
    private MainForm? _mainForm;
    private bool _exiting;

    public TrayApplicationContext()
    {
        _uiDispatcher = new Control();
        _ = _uiDispatcher.Handle;
        _appIcon = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application;

        _notifyIcon = new NotifyIcon
        {
            Icon = _appIcon,
            Text = AppText.Get("app.name"),
            Visible = true
        };
        _notifyIcon.DoubleClick += (_, _) => OpenMainWindow();
        _notifyIcon.MouseUp += (_, e) =>
        {
            if (e.Button == MouseButtons.Right)
            {
                ShowTrayMenu();
            }
        };

        _notificationService = new NotificationService(_notifyIcon, _uiDispatcher);
        _controller = new DesktopTrackingController(
            new DesktopReminderDispatcher(_appIcon, _uiDispatcher));
        _controller.Updated += (_, _) => UpdateTrayMenuState();
        // 手机线冻结：不再启动配对/同步服务，本机独立运行
        _startupManager = new StartupManager();
        ApplyStartupSetting();
        _trayMenu = new TrayMenuForm(
            _appIcon,
            OpenMainWindow,
            ShowSettingsWindow,
            ToggleReminders,
            ExitApplication);
        UpdateTrayMenuState();

        if (!_controller.HasCompletedOnboarding)
        {
            using var onboarding = new DeskOnboardingForm(_controller, _startupManager, _appIcon);
            onboarding.ShowDialog();
        }

        OpenMainWindow();
    }

    private void OpenMainWindow()
    {
        if (_mainForm is null || _mainForm.IsDisposed)
        {
            _mainForm = new MainForm(_controller, _startupManager, _appIcon);
        }

        if (!_mainForm.Visible)
        {
            _mainForm.Show();
        }

        if (_mainForm.WindowState == FormWindowState.Minimized)
        {
            _mainForm.WindowState = FormWindowState.Normal;
        }

        _mainForm.Activate();
    }

    private void ShowSettingsWindow()
    {
        using var settingsForm = new SettingsForm(_controller, _startupManager, _appIcon);
        if (_mainForm is { IsDisposed: false, Visible: true })
        {
            settingsForm.ShowDialog(_mainForm);
            return;
        }

        settingsForm.ShowDialog();
    }

    private void ShowTrayMenu()
    {
        UpdateTrayMenuState();
        _trayMenu.ShowNearCursor();
    }

    private void UpdateTrayMenuState()
    {
        if (_trayMenu is null || _trayMenu.IsDisposed)
        {
            return;
        }

        _trayMenu.UpdateReminderToggle(_controller.RemindersPaused);
    }

    private void ToggleReminders()
    {
        if (_controller.RemindersPaused)
        {
            _controller.ResumeReminders();
        }
        else
        {
            _controller.PauseReminders();
        }

        _trayMenu.UpdateReminderToggle(_controller.RemindersPaused);
    }

    private void ApplyStartupSetting()
    {
        try
        {
            _startupManager.SetEnabled(_controller.Settings.StartWithWindows);
        }
        catch (Exception)
        {
        }
    }

    private void ExitApplication()
    {
        if (_exiting)
        {
            return;
        }

        _exiting = true;

        if (_mainForm is not null && !_mainForm.IsDisposed)
        {
            _mainForm.CloseForExit();
            _mainForm.Dispose();
        }

        _controller.Dispose();
        _trayMenu.Dispose();
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _appIcon.Dispose();
        _uiDispatcher.Dispose();
        ExitThread();
    }
}
