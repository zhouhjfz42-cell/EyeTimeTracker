using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;

namespace EyeTimeTracker.App.UI;

public sealed class TrayApplicationContext : ApplicationContext
{
    private readonly Icon _appIcon;
    private readonly NotifyIcon _notifyIcon;
    private readonly ContextMenuStrip _menu;
    private readonly Control _uiDispatcher;
    private readonly TrackingController _controller;
    private readonly StartupManager _startupManager;
    private MainForm? _mainForm;
    private bool _exiting;

    public TrayApplicationContext()
    {
        _menu = new ContextMenuStrip();
        _menu.Items.Add(new ToolStripMenuItem("\u6253\u5f00", null, (_, _) => OpenMainWindow()));
        _menu.Items.Add(new ToolStripSeparator());
        _menu.Items.Add(new ToolStripMenuItem("\u9000\u51fa", null, (_, _) => ExitApplication()));

        _uiDispatcher = new Control();
        _ = _uiDispatcher.Handle;
        _appIcon = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application;

        _notifyIcon = new NotifyIcon
        {
            Icon = _appIcon,
            Text = "\u7528\u773c\u65f6\u95f4\u8bb0\u5f55",
            ContextMenuStrip = _menu,
            Visible = true
        };
        _notifyIcon.DoubleClick += (_, _) => OpenMainWindow();

        _controller = new TrackingController(new NotificationService(_notifyIcon, _uiDispatcher));
        _startupManager = new StartupManager();
        ApplyStartupSetting();
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
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _appIcon.Dispose();
        _uiDispatcher.Dispose();
        _menu.Dispose();
        ExitThread();
    }
}
