using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Localization;

namespace EyeTimeTracker.App.UI;

public sealed class PcDisconnectDialog : Form
{
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);
    private static readonly Color TextSecondary = Color.FromArgb(102, 112, 133);
    private static readonly Color AccentGreen = Color.FromArgb(22, 166, 125);
    private static readonly Color BorderColor = Color.FromArgb(225, 232, 229);

    public PcDisconnectDialog(Icon? icon)
    {
        AutoScaleMode = AutoScaleMode.None;
        Text = AppText.Get("disconnect.title");
        if (icon is not null)
        {
            Icon = (Icon)icon.Clone();
        }

        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.None;
        MaximizeBox = false;
        MinimizeBox = false;
        ShowInTaskbar = false;
        ClientSize = new Size(470, 286);
        BackColor = Color.White;
        Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);

        Controls.Add(new StaticText
        {
            Text = AppText.Get("common.disconnect"),
            Bounds = new Rectangle(28, 24, 300, 58),
            Font = AppFonts.Create(19F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        var closeButton = new CloseIconButton
        {
            Bounds = new Rectangle(404, 28, 38, 38)
        };
        closeButton.Click += (_, _) =>
        {
            DialogResult = DialogResult.Cancel;
            Close();
        };
        Controls.Add(closeButton);

        Controls.Add(new StaticText
        {
            Text = AppText.Get("disconnect.pcMessage"),
            Bounds = new Rectangle(28, 94, 414, 72),
            Font = AppFonts.Create(11F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.TopLeft
        });

        var cancelButton = new RoundedButton
        {
            Text = AppText.Get("common.cancel"),
            Bounds = new Rectangle(28, 214, 190, 48),
            ButtonColor = Color.FromArgb(242, 244, 247),
            HoverColor = Color.FromArgb(232, 236, 240),
            PressedColor = Color.FromArgb(220, 226, 232),
            TextColor = Color.FromArgb(52, 64, 84)
        };
        cancelButton.Click += (_, _) =>
        {
            DialogResult = DialogResult.Cancel;
            Close();
        };
        Controls.Add(cancelButton);

        var disconnectButton = new RoundedButton
        {
            Text = AppText.Get("disconnect.title"),
            Bounds = new Rectangle(238, 214, 204, 48),
            ButtonColor = AccentGreen,
            HoverColor = Color.FromArgb(19, 145, 111),
            PressedColor = Color.FromArgb(17, 124, 96),
            TextColor = Color.White
        };
        disconnectButton.Click += (_, _) =>
        {
            DialogResult = DialogResult.OK;
            Close();
        };
        Controls.Add(disconnectButton);
    }

    protected override void OnResize(EventArgs e)
    {
        base.OnResize(e);
        using var path = CreateRoundRect(new Rectangle(0, 0, Width, Height), 26);
        Region = new Region(path);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
        using var path = CreateRoundRect(bounds, 26);
        using var fill = new SolidBrush(Color.White);
        using var border = new Pen(BorderColor);
        e.Graphics.FillPath(fill, path);
        e.Graphics.DrawPath(border, path);
        base.OnPaint(e);
    }

    protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
    {
        if (keyData == Keys.Enter)
        {
            DialogResult = DialogResult.OK;
            Close();
            return true;
        }

        if (keyData == Keys.Escape)
        {
            DialogResult = DialogResult.Cancel;
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

        public Color ButtonColor { get; set; } = AccentGreen;
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

            e.Graphics.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            using var brush = new SolidBrush(ForeColor);
            using var format = CreateStringFormat();
            e.Graphics.DrawString(Text, Font, brush, ClientRectangle, format);
        }

        private StringFormat CreateStringFormat()
        {
            var format = (StringFormat)StringFormat.GenericTypographic.Clone();
            format.Trimming = StringTrimming.None;
            format.FormatFlags &= ~StringFormatFlags.NoWrap;
            format.Alignment = TextAlign is ContentAlignment.TopRight or ContentAlignment.MiddleRight or ContentAlignment.BottomRight
                ? StringAlignment.Far
                : TextAlign is ContentAlignment.TopCenter or ContentAlignment.MiddleCenter or ContentAlignment.BottomCenter
                    ? StringAlignment.Center
                    : StringAlignment.Near;
            format.LineAlignment = TextAlign is ContentAlignment.BottomLeft or ContentAlignment.BottomCenter or ContentAlignment.BottomRight
                ? StringAlignment.Far
                : TextAlign is ContentAlignment.TopLeft or ContentAlignment.TopCenter or ContentAlignment.TopRight
                    ? StringAlignment.Near
                    : StringAlignment.Center;
            return format;
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
