using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.UI.Controls;

namespace EyeTimeTracker.App.UI;

public sealed class TrayMenuForm : Form
{
    private static readonly Color Background = Color.FromArgb(252, 254, 253);
    private static readonly Color AccentBlue = Color.FromArgb(66, 133, 244);
    private static readonly Color AccentPurple = Color.FromArgb(139, 110, 246);
    private static readonly Color Danger = Color.FromArgb(215, 90, 90);
    private static readonly Color Amber = Color.FromArgb(210, 145, 40);
    private static readonly Color Border = Color.FromArgb(216, 238, 230);
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);
    private readonly MenuButton _reminderToggleButton;

    public TrayMenuForm(
        Icon appIcon,
        Action openMain,
        Action openSettings,
        Action toggleReminders,
        Action exitApplication)
    {
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = false;
        StartPosition = FormStartPosition.Manual;
        TopMost = true;
        BackColor = Background;
        Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel);
        Padding = new Padding(16);

        // 菜单宽度按最长菜单项文字测量，保证任何语言都不截断
        using (var measureFont = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel))
        {
            var texts = new[]
            {
                AppText.Get("tray.mainPage"),
                AppText.Get("desktop.nav.settings"),
                AppText.Get("tray.stopReminders"),
                AppText.Get("tray.resumeReminders"),
                AppText.Get("tray.quit")
            };
            var itemWidth = texts.Max(text => UiText.SingleLineWidth(text, measureFont)) + 48 + 24;
            var width = Math.Max(260, itemWidth + 32);
            ClientSize = new Size(width, 254);
            _itemWidth = width - 32;
        }

        Controls.Add(new MenuButton(AppText.Get("tray.mainPage"), "⌂", AccentBlue)
        {
            Bounds = new Rectangle(16, 14, _itemWidth, 48),
            ClickAction = openMain
        });
        Controls.Add(new MenuButton(AppText.Get("desktop.nav.settings"), "⚙", AccentPurple)
        {
            Bounds = new Rectangle(16, 70, _itemWidth, 48),
            ClickAction = openSettings
        });

        _reminderToggleButton = new MenuButton(AppText.Get("tray.stopReminders"), "⏸", Amber)
        {
            Bounds = new Rectangle(16, 126, _itemWidth, 48),
            ClickAction = toggleReminders
        };
        Controls.Add(_reminderToggleButton);

        Controls.Add(new Divider { Bounds = new Rectangle(16, 182, _itemWidth, 1) });

        Controls.Add(new MenuButton(AppText.Get("tray.quit"), "×", Danger)
        {
            Bounds = new Rectangle(16, 190, _itemWidth, 48),
            ClickAction = exitApplication
        });

        // 尺寸按设计像素固定（不跟随 DPI 缩放），并锁定，杜绝拉伸空白
        _ = Handle;
        MinimumSize = Size;
        MaximumSize = Size;
    }

    private readonly int _itemWidth;

    public void UpdateReminderToggle(bool paused)
    {
        if (paused)
        {
            _reminderToggleButton.TextValue = AppText.Get("tray.resumeReminders");
            _reminderToggleButton.IconValue = "▶";
        }
        else
        {
            _reminderToggleButton.TextValue = AppText.Get("tray.stopReminders");
            _reminderToggleButton.IconValue = "⏸";
        }
    }

    public void ShowNearCursor()
    {
        var cursor = Cursor.Position;
        var screen = Screen.FromPoint(cursor).WorkingArea;
        var x = Math.Clamp(cursor.X - Width + 8, screen.Left + 8, screen.Right - Width - 8);
        var y = Math.Clamp(cursor.Y - Height - 8, screen.Top + 8, screen.Bottom - Height - 8);
        Location = new Point(x, y);
        Show();
        Activate();
    }

    protected override void OnDeactivate(EventArgs e)
    {
        Hide();
        base.OnDeactivate(e);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        using var path = RoundedRect(new Rectangle(0, 0, Width - 1, Height - 1), 22);
        using var fill = new SolidBrush(Background);
        using var pen = new Pen(Border, 1.5F);
        e.Graphics.FillPath(fill, path);
        e.Graphics.DrawPath(pen, path);
    }

    protected override void OnResize(EventArgs e)
    {
        using var path = RoundedRect(new Rectangle(0, 0, Width, Height), 22);
        Region = new Region(path);
        base.OnResize(e);
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

    private sealed class MenuButton : Control
    {
        private static readonly Color SoftGreen = Color.FromArgb(233, 248, 243);
        private readonly Color _accent;
        private readonly bool _highlighted;
        private readonly bool _compact;
        private bool _hovered;
        private string _textValue;
        private string _iconValue;

        public MenuButton(string text, string icon, Color accent, bool highlighted = false, bool compact = false)
        {
            _textValue = text;
            _iconValue = icon;
            _accent = accent;
            _highlighted = highlighted;
            _compact = compact;
            Cursor = Cursors.Hand;
            DoubleBuffered = true;
            Font = AppFonts.Create(20, highlighted ? FontStyle.Bold : FontStyle.Regular, GraphicsUnit.Pixel);
        }

        public Action? ClickAction { get; init; }

        public string TextValue
        {
            get => _textValue;
            set
            {
                _textValue = value;
                Invalidate();
            }
        }

        public string IconValue
        {
            get => _iconValue;
            set
            {
                _iconValue = value;
                Invalidate();
            }
        }

        protected override void OnMouseEnter(EventArgs e)
        {
            _hovered = true;
            Invalidate();
            base.OnMouseEnter(e);
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            _hovered = false;
            Invalidate();
            base.OnMouseLeave(e);
        }

        protected override void OnClick(EventArgs e)
        {
            FindForm()?.Hide();
            ClickAction?.Invoke();
            base.OnClick(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var fillColor = _highlighted ? SoftGreen : (_hovered ? Color.FromArgb(245, 251, 249) : Background);
            using var path = RoundedRect(new Rectangle(0, 0, Width - 1, Height - 1), _compact ? 12 : 14);
            using var fill = new SolidBrush(fillColor);
            e.Graphics.FillPath(fill, path);

            var iconLeft = 12;
            var iconWidth = 24;
            var iconRect = new Rectangle(iconLeft, 0, iconWidth, Height);
            var textRect = new Rectangle(iconLeft + iconWidth + 12, 0, Width - iconLeft - iconWidth - 18, Height);
            TextRenderer.DrawText(
                e.Graphics,
                _iconValue,
                AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel),
                iconRect,
                _accent,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPrefix);
            TextRenderer.DrawText(
                e.Graphics,
                _textValue,
                Font,
                textRect,
                _highlighted ? Color.FromArgb(11, 127, 97) : TextPrimary,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
        }
    }

    private sealed class Divider : Control
    {
        protected override void OnPaint(PaintEventArgs e)
        {
            using var pen = new Pen(Border);
            e.Graphics.DrawLine(pen, 0, 0, Width, 0);
        }
    }

}
