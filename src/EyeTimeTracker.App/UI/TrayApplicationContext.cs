using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Sync;
using EyeTimeTracker.App.Tracking;

namespace EyeTimeTracker.App.UI;

public sealed class TrayApplicationContext : ApplicationContext
{
    private readonly Icon _appIcon;
    private readonly NotifyIcon _notifyIcon;
    private readonly Control _uiDispatcher;
    private readonly NotificationService _notificationService;
    private readonly TrackingController _controller;
    private readonly PcPairingCodeProvider _pairingCodes;
    private readonly PcSyncCoordinator _syncCoordinator;
    private readonly PcSyncServer _syncServer;
    private readonly PcDiscoveryServer _discoveryServer;
    private readonly StartupManager _startupManager;
    private readonly TrayMenuForm _trayMenu;
    private MainForm? _mainForm;
    private bool _exiting;
    private bool _remindersSuppressed;

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
        _controller = new TrackingController(_notificationService);
        _controller.Updated += (_, _) => UpdateTrayMenuState();
        _pairingCodes = new PcPairingCodeProvider();
        _syncCoordinator = _controller.CreateSyncCoordinator();
        _syncServer = new PcSyncServer(
            _syncCoordinator,
            expectedPairingCode: _pairingCodes.GetExpectedCode,
            pairingAccepted: _pairingCodes.Clear);
        _discoveryServer = new PcDiscoveryServer(() => _syncCoordinator.CreateDiscoveryResponse(_syncServer.Port));
        TryStartSyncServer();
        TryStartDiscoveryServer();
        _startupManager = new StartupManager();
        ApplyStartupSetting();
        _trayMenu = new TrayMenuForm(
            _appIcon,
            OpenMainWindow,
            ShowStatsWindow,
            ToggleReminders,
            ExitApplication);
        UpdateTrayMenuState();
        OpenMainWindow();
    }

    private void OpenMainWindow()
    {
        if (_mainForm is null || _mainForm.IsDisposed)
        {
            _mainForm = new MainForm(_controller, _startupManager, _appIcon, ShowPairingDialog, DisconnectPairing);
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

    private void ShowStatsWindow()
    {
        using var statsForm = new StatsForm(_controller, _appIcon);
        if (_mainForm is { IsDisposed: false, Visible: true })
        {
            statsForm.ShowDialog(_mainForm);
            return;
        }

        statsForm.ShowDialog();
    }

    private void ShowTrayMenu()
    {
        UpdateTrayMenuState();
        _trayMenu.ShowNearCursor();
    }

    private void HandlePairingAction()
    {
        if (_controller.IsPaired)
        {
            DisconnectPairing();
            return;
        }

        ShowPairingDialog();
    }

    private void DisconnectPairing()
    {
        using var dialog = new PcDisconnectDialog(_appIcon);
        var result = _mainForm is { IsDisposed: false }
            ? dialog.ShowDialog(_mainForm)
            : dialog.ShowDialog();
        if (result != DialogResult.OK)
        {
            return;
        }

        _pairingCodes.Clear();
        _controller.DisconnectSyncPeer();
        UpdateTrayMenuState();
        _notifyIcon.ShowBalloonTip(
            4000,
            AppText.Get("tray.disconnectTitle"),
            AppText.Get("tray.disconnectBody"),
            ToolTipIcon.Info);
    }

    private void UpdateTrayMenuState()
    {
        if (_trayMenu is null || _trayMenu.IsDisposed)
        {
            return;
        }

        _trayMenu.UpdateState(
            _controller.Current.IsCounting ? AppText.Get("main.status.tracking") : AppText.Get("main.status.paused"),
            _controller.IsPaired && _controller.IsPeerOnline);
    }

    private void ToggleReminders()
    {
        _remindersSuppressed = !_remindersSuppressed;
        _notificationService.SuppressReminders = _remindersSuppressed;
        _trayMenu.UpdateReminderToggle(_remindersSuppressed);
    }

    private void ShowPairingDialog()
    {
        TryStartSyncServer();
        TryStartDiscoveryServer();
        using var dialog = new PcPairingDialog(_appIcon);
        if (dialog.ShowDialog() != DialogResult.OK)
        {
            return;
        }

        if (!_pairingCodes.TryAllowCode(dialog.PairingCode, out var error))
        {
            MessageBox.Show(
                error,
                AppText.Get("pair.pc.title"),
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);
            return;
        }

        _notifyIcon.ShowBalloonTip(
            5000,
            AppText.Get("pair.pc.title"),
            AppText.Get("tray.pairWaitingBody"),
            ToolTipIcon.Info);
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

    private void TryStartSyncServer()
    {
        try
        {
            _syncServer.Start();
        }
        catch (Exception)
        {
        }
    }

    private void TryStartDiscoveryServer()
    {
        try
        {
            _discoveryServer.Start();
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
        _discoveryServer.Dispose();
        _syncServer.Dispose();
        _trayMenu.Dispose();
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _appIcon.Dispose();
        _uiDispatcher.Dispose();
        ExitThread();
    }
}
