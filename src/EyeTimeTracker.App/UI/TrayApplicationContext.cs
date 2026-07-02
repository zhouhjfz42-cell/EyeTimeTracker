using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Sync;
using EyeTimeTracker.App.Tracking;

namespace EyeTimeTracker.App.UI;

public sealed class TrayApplicationContext : ApplicationContext
{
    private readonly Icon _appIcon;
    private readonly NotifyIcon _notifyIcon;
    private readonly ContextMenuStrip _menu;
    private readonly ToolStripMenuItem _pairingMenuItem;
    private readonly Control _uiDispatcher;
    private readonly TrackingController _controller;
    private readonly PcPairingCodeProvider _pairingCodes;
    private readonly PcSyncCoordinator _syncCoordinator;
    private readonly PcSyncServer _syncServer;
    private readonly PcDiscoveryServer _discoveryServer;
    private readonly StartupManager _startupManager;
    private MainForm? _mainForm;
    private bool _exiting;

    public TrayApplicationContext()
    {
        _menu = CreateTrayMenu();
        _menu.Items.Add(CreateMenuItem("\u6253\u5f00", (_, _) => OpenMainWindow()));
        _pairingMenuItem = CreateMenuItem("\u624b\u673a\u914d\u5bf9", (_, _) => HandlePairingAction());
        _menu.Items.Add(_pairingMenuItem);
        _menu.Items.Add(new ToolStripSeparator { Margin = new Padding(10, 5, 10, 5) });
        _menu.Items.Add(CreateMenuItem("\u9000\u51fa", (_, _) => ExitApplication()));

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
        _controller.Updated += (_, _) => UpdatePairingEntryText();
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
        OpenMainWindow();
    }

    private static ContextMenuStrip CreateTrayMenu()
    {
        return new ContextMenuStrip
        {
            ShowImageMargin = false,
            ShowCheckMargin = false,
            AutoSize = true,
            Padding = new Padding(8, 8, 8, 8),
            BackColor = Color.FromArgb(252, 254, 253),
            ForeColor = Color.FromArgb(17, 24, 39),
            Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Regular, GraphicsUnit.Point),
            Renderer = new TrayMenuRenderer()
        };
    }

    private static ToolStripMenuItem CreateMenuItem(string text, EventHandler onClick)
    {
        return new ToolStripMenuItem(text, null, onClick)
        {
            AutoSize = false,
            Width = 168,
            Height = 38,
            Padding = new Padding(16, 0, 16, 0),
            Margin = new Padding(0, 1, 0, 1),
            ForeColor = Color.FromArgb(17, 24, 39)
        };
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
        UpdatePairingEntryText();
        _notifyIcon.ShowBalloonTip(
            4000,
            "\u5df2\u65ad\u5f00\u624b\u673a",
            "\u518d\u6b21\u8fde\u63a5\u65f6\u9700\u8981\u91cd\u65b0\u914d\u5bf9\u3002",
            ToolTipIcon.Info);
    }

    private void UpdatePairingEntryText()
    {
        _pairingMenuItem.Text = _controller.IsPaired ? "\u65ad\u5f00\u8fde\u63a5" : "\u624b\u673a\u914d\u5bf9";
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
                "\u624b\u673a\u914d\u5bf9",
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);
            return;
        }

        _notifyIcon.ShowBalloonTip(
            5000,
            "\u624b\u673a\u914d\u5bf9",
            "\u7535\u8111\u5df2\u5f00\u59cb\u7b49\u5f85\u624b\u673a\u8fde\u63a5\uff0c5\u5206\u949f\u5185\u6709\u6548\u3002",
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
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _appIcon.Dispose();
        _uiDispatcher.Dispose();
        _menu.Dispose();
        ExitThread();
    }

    private sealed class TrayMenuRenderer : ToolStripProfessionalRenderer
    {
        private static readonly Color MenuBackground = Color.FromArgb(252, 254, 253);
        private static readonly Color HoverBackground = Color.FromArgb(238, 249, 245);
        private static readonly Color Border = Color.FromArgb(217, 238, 231);
        private static readonly Color Text = Color.FromArgb(17, 24, 39);
        private static readonly Color Muted = Color.FromArgb(101, 114, 137);

        protected override void OnRenderToolStripBackground(ToolStripRenderEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            using var brush = new SolidBrush(MenuBackground);
            e.Graphics.FillRectangle(brush, e.AffectedBounds);
        }

        protected override void OnRenderToolStripBorder(ToolStripRenderEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, e.ToolStrip.Width - 1, e.ToolStrip.Height - 1);
            using var path = RoundedRect(bounds, 12);
            using var pen = new Pen(Border);
            e.Graphics.DrawPath(pen, path);
        }

        protected override void OnRenderMenuItemBackground(ToolStripItemRenderEventArgs e)
        {
            if (!e.Item.Selected)
            {
                return;
            }

            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(4, 2, e.Item.Width - 8, e.Item.Height - 4);
            using var path = RoundedRect(bounds, 10);
            using var brush = new SolidBrush(HoverBackground);
            e.Graphics.FillPath(brush, path);
        }

        protected override void OnRenderItemText(ToolStripItemTextRenderEventArgs e)
        {
            e.TextColor = e.Item.Enabled ? Text : Muted;
            e.TextFormat = TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPadding;
            base.OnRenderItemText(e);
        }

        protected override void OnRenderSeparator(ToolStripSeparatorRenderEventArgs e)
        {
            var y = e.Item.Height / 2;
            using var pen = new Pen(Border);
            e.Graphics.DrawLine(pen, 8, y, e.Item.Width - 8, y);
        }

        private static GraphicsPath RoundedRect(Rectangle bounds, int radius)
        {
            var path = new GraphicsPath();
            var diameter = radius * 2;
            path.AddArc(bounds.Left, bounds.Top, diameter, diameter, 180, 90);
            path.AddArc(bounds.Right - diameter, bounds.Top, diameter, diameter, 270, 90);
            path.AddArc(bounds.Right - diameter, bounds.Bottom - diameter, diameter, diameter, 0, 90);
            path.AddArc(bounds.Left, bounds.Bottom - diameter, diameter, diameter, 90, 90);
            path.CloseFigure();
            return path;
        }
    }
}
