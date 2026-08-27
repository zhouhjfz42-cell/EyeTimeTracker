using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Localization;

namespace EyeTimeTracker.App.UI;

public sealed class PcReminderDialog : Form
{
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);
    private static readonly Color TextSecondary = Color.FromArgb(102, 112, 133);
    public static readonly Color AccentContinuous = Color.FromArgb(22, 166, 125);
    public static readonly Color AccentCumulative = Color.FromArgb(224, 122, 40);
    private static readonly Color BorderColor = Color.FromArgb(225, 232, 229);

    private readonly Color _accent;
    private readonly string? _emphasis;

    public PcReminderDialog(string title, string body, Icon? icon, Color? accent = null, string? emphasis = null)
    {
        _accent = accent ?? AccentContinuous;
        _emphasis = emphasis;
        AutoScaleMode = AutoScaleMode.None;
        Text = title;
        if (icon is not null)
        {
            Icon = (Icon)icon.Clone();
        }

        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.None;
        MaximizeBox = false;
        MinimizeBox = false;
        ShowInTaskbar = false;
        TopMost = true;
        ClientSize = new Size(560, 360);
        BackColor = Color.White;
        Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);

        Controls.Add(new StaticText
        {
            Text = title,
            Bounds = new Rectangle(32, 30, 420, 64),
            Font = AppFonts.Create(20F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = _accent,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft,
            WordWrap = true,
            UseEllipsis = false
        });

        var closeButton = new CloseIconButton
        {
            Bounds = new Rectangle(490, 34, 38, 38)
        };
        closeButton.Click += (_, _) =>
        {
            DialogResult = DialogResult.OK;
            Close();
        };
        Controls.Add(closeButton);

        Controls.Add(CreateBodyText(body, _emphasis));

        var okButton = new RoundedButton
        {
            Text = AppText.Get("common.gotIt"),
            Bounds = new Rectangle(180, 292, 200, 48),
            ButtonColor = _accent,
            HoverColor = ControlPaint.Dark(_accent, 0.1F),
            PressedColor = ControlPaint.Dark(_accent, 0.2F),
            TextColor = Color.White
        };
        okButton.Click += (_, _) =>
        {
            DialogResult = DialogResult.OK;
            Close();
        };
        Controls.Add(okButton);
    }

    protected override void OnResize(EventArgs e)
    {
        base.OnResize(e);
        using var path = CreateRoundRect(new Rectangle(0, 0, Width, Height), 26);
        Region = new Region(path);
    }

    private RichTextBox CreateBodyText(string body, string? emphasis)
    {
        var bodyText = new RichTextBox
        {
            Bounds = new Rectangle(32, 112, 496, 148),
            Font = AppFonts.Create(12F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.White,
            BorderStyle = BorderStyle.None,
            ReadOnly = true,
            ScrollBars = RichTextBoxScrollBars.None,
            DetectUrls = false,
            ShortcutsEnabled = false,
            TabStop = false,
            Text = body
        };
        if (!string.IsNullOrEmpty(emphasis))
        {
            var index = body.IndexOf(emphasis, StringComparison.Ordinal);
            if (index >= 0)
            {
                bodyText.Select(index, emphasis.Length);
                bodyText.SelectionColor = _accent;
                bodyText.SelectionFont = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point);
                bodyText.Select(0, 0);
            }
        }

        return bodyText;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
        using var path = CreateRoundRect(bounds, 26);
        using var fill = new SolidBrush(Color.White);
        using var border = new Pen(BorderColor);
        e.Graphics.FillPath(fill, path);
        // 顶部类型色带：绿=连续用眼，橙红=累计用眼，不看文字也能分辨
        e.Graphics.SetClip(path);
        using var accentBrush = new SolidBrush(_accent);
        e.Graphics.FillRectangle(accentBrush, new Rectangle(0, 0, Width, 10));
        e.Graphics.ResetClip();
        e.Graphics.DrawPath(border, path);
        base.OnPaint(e);
    }

    protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
    {
        if (keyData is Keys.Enter or Keys.Escape)
        {
            DialogResult = DialogResult.OK;
            Close();
            return true;
        }

        return base.ProcessCmdKey(ref msg, keyData);
    }

    private static GraphicsPath CreateRoundRect(Rectangle bounds, int radius)
    {
        var path = new GraphicsPath();
        var diameter = Math.Max(1, radius * 2);
        var arc = new Rectangle(bounds.Location, new Size(diameter, diameter));

        path.AddArc(arc, 180, 90);
        arc.X = bounds.Right - diameter;
        path.AddArc(arc, 270, 90);
        arc.Y = bounds.Bottom - diameter;
        path.AddArc(arc, 0, 90);
        arc.X = bounds.Left;
        path.AddArc(arc, 90, 90);
        path.CloseFigure();
        return path;
    }

    private sealed class RoundedButton : Control
    {
        private bool _hovered;
        private bool _pressed;

        public Color ButtonColor { get; set; } = AccentContinuous;
        public Color HoverColor { get; set; } = Color.FromArgb(19, 145, 111);
        public Color PressedColor { get; set; } = Color.FromArgb(17, 124, 96);
        public Color TextColor { get; set; } = Color.White;

        public RoundedButton()
        {
            Cursor = Cursors.Hand;
            Font = AppFonts.Create(13F, FontStyle.Bold, GraphicsUnit.Point);
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        }

        protected override void OnMouseEnter(EventArgs e)
        {
            _hovered = true;
            Invalidate();
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            _hovered = false;
            _pressed = false;
            Invalidate();
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            _pressed = true;
            Invalidate();
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            _pressed = false;
            Invalidate();
            base.OnMouseUp(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            var color = _pressed ? PressedColor : _hovered ? HoverColor : ButtonColor;
            using var path = CreateRoundRect(bounds, Height / 2);
            using var fill = new SolidBrush(color);
            e.Graphics.FillPath(fill, path);

            TextRenderer.DrawText(
                e.Graphics,
                Text,
                Font,
                bounds,
                TextColor,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        }
    }

    private sealed class StaticText : Control
    {
        public ContentAlignment TextAlign { get; set; } = ContentAlignment.MiddleLeft;
        public bool WordWrap { get; set; }
        public bool UseEllipsis { get; set; } = true;

        public StaticText()
        {
            TabStop = false;
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true);
        }

        protected override void OnTextChanged(EventArgs e)
        {
            Invalidate();
            base.OnTextChanged(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            if (BackColor.A == 255)
            {
                e.Graphics.Clear(BackColor);
            }

            var flags = CreateTextFormatFlags();
            TextRenderer.DrawText(e.Graphics, Text, Font, ClientRectangle, ForeColor, flags);
        }

        private TextFormatFlags CreateTextFormatFlags()
        {
            var flags = TextFormatFlags.NoPadding | TextFormatFlags.PreserveGraphicsClipping;
            flags |= WordWrap ? TextFormatFlags.WordBreak : TextFormatFlags.SingleLine;
            if (UseEllipsis)
            {
                flags |= TextFormatFlags.EndEllipsis;
            }

            flags |= TextAlign switch
            {
                ContentAlignment.TopCenter or ContentAlignment.MiddleCenter or ContentAlignment.BottomCenter => TextFormatFlags.HorizontalCenter,
                ContentAlignment.TopRight or ContentAlignment.MiddleRight or ContentAlignment.BottomRight => TextFormatFlags.Right,
                _ => TextFormatFlags.Left
            };

            flags |= TextAlign switch
            {
                ContentAlignment.MiddleLeft or ContentAlignment.MiddleCenter or ContentAlignment.MiddleRight => TextFormatFlags.VerticalCenter,
                ContentAlignment.BottomLeft or ContentAlignment.BottomCenter or ContentAlignment.BottomRight => TextFormatFlags.Bottom,
                _ => TextFormatFlags.Top
            };

            return flags;
        }
    }

    private sealed class CloseIconButton : Control
    {
        private bool _hovered;

        public CloseIconButton()
        {
            Cursor = Cursors.Hand;
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        }

        protected override void OnMouseEnter(EventArgs e)
        {
            _hovered = true;
            Invalidate();
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            _hovered = false;
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var fillColor = _hovered ? Color.FromArgb(235, 238, 242) : Color.FromArgb(247, 249, 250);
            using var fill = new SolidBrush(fillColor);
            e.Graphics.FillEllipse(fill, 0, 0, Width - 1, Height - 1);

            using var pen = new Pen(Color.FromArgb(102, 112, 133), 2.4F)
            {
                StartCap = LineCap.Round,
                EndCap = LineCap.Round
            };
            e.Graphics.DrawLine(pen, 12, 12, Width - 12, Height - 12);
            e.Graphics.DrawLine(pen, Width - 12, 12, 12, Height - 12);
        }
    }
}
