using System.Drawing.Drawing2D;

namespace EyeTimeTracker.App.UI;

public sealed class TrayMenuForm : Form
{
    private static readonly Color Background = Color.FromArgb(252, 254, 253);
    private static readonly Color SoftGreen = Color.FromArgb(233, 248, 243);
    private static readonly Color AccentGreen = Color.FromArgb(22, 166, 125);
    private static readonly Color AccentBlue = Color.FromArgb(78, 126, 243);
    private static readonly Color Danger = Color.FromArgb(215, 90, 90);
    private static readonly Color Border = Color.FromArgb(216, 238, 230);
    private static readonly Color Disabled = Color.FromArgb(156, 163, 175);
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);

    public TrayMenuForm(
        Icon appIcon,
        Action openMain,
        Action openStats,
        Action exitApplication)
    {
        AutoScaleMode = AutoScaleMode.None;
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = false;
        StartPosition = FormStartPosition.Manual;
        TopMost = true;
        BackColor = Background;
        ClientSize = new Size(190, 178);
        Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);
        Padding = new Padding(16);

        Controls.Add(new MenuButton("\u4e3b\u754c\u9762", "\u2302", AccentGreen)
        {
            Bounds = new Rectangle(16, 14, 158, 42),
            ClickAction = openMain
        });
        Controls.Add(new MenuButton("\u7edf\u8ba1\u9875", "\u25a5", AccentBlue)
        {
            Bounds = new Rectangle(16, 62, 158, 42),
            ClickAction = openStats
        });

        Controls.Add(new Divider { Bounds = new Rectangle(16, 116, 158, 1) });

        Controls.Add(new MenuButton("\u9000\u51fa", "\u00d7", Danger)
        {
            Bounds = new Rectangle(16, 124, 158, 42),
            ClickAction = exitApplication
        });
    }

    public void UpdateState(string statusText, bool phoneConnected)
    {
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
            Font = AppFonts.Create(compact ? 9F : 10F, highlighted ? FontStyle.Bold : FontStyle.Regular, GraphicsUnit.Point);
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

            var iconRect = new Rectangle(12, 0, 24, Height);
            var textRect = new Rectangle(48, 0, Width - 54, Height);
            TextRenderer.DrawText(
                e.Graphics,
                _iconValue,
                AppFonts.Create(_compact ? 12F : 14F, FontStyle.Bold, GraphicsUnit.Point),
                iconRect,
                _accent,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
            TextRenderer.DrawText(
                e.Graphics,
                _textValue,
                Font,
                textRect,
                _highlighted ? Color.FromArgb(11, 127, 97) : TextPrimary,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
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
