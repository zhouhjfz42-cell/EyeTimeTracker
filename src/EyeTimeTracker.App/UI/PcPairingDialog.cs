using System.Drawing.Drawing2D;

namespace EyeTimeTracker.App.UI;

public sealed class PcPairingDialog : Form
{
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);
    private static readonly Color TextSecondary = Color.FromArgb(102, 112, 133);
    private static readonly Color AccentGreen = Color.FromArgb(22, 166, 125);
    private static readonly Color BorderColor = Color.FromArgb(225, 232, 229);
    private static readonly Color InputFill = Color.FromArgb(249, 253, 251);

    private readonly PairingCodeInput _codeInput;
    private readonly Label _hintLabel;

    public string PairingCode { get; private set; } = string.Empty;

    public PcPairingDialog(Icon? icon)
    {
        AutoScaleMode = AutoScaleMode.None;
        Text = "\u624b\u673a\u914d\u5bf9";
        if (icon is not null)
        {
            Icon = (Icon)icon.Clone();
        }

        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.None;
        MaximizeBox = false;
        MinimizeBox = false;
        ShowInTaskbar = false;
        ClientSize = new Size(470, 344);
        BackColor = Color.White;
        Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point);
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);

        Controls.Add(new StaticText
        {
            Text = "\u624b\u673a\u914d\u5bf9",
            Bounds = new Rectangle(28, 24, 300, 58),
            Font = new Font("Microsoft YaHei UI", 19F, FontStyle.Bold, GraphicsUnit.Point),
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
            Text = "\u8f93\u5165\u624b\u673a\u4e0a\u663e\u793a\u7684\u914d\u5bf9\u7801",
            Bounds = new Rectangle(28, 92, 414, 42),
            Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent
        });

        _codeInput = new PairingCodeInput
        {
            Bounds = new Rectangle(28, 154, 414, 76),
            Font = new Font("Microsoft YaHei UI", 22F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            FillColor = InputFill,
            BorderColor = Color.FromArgb(206, 226, 218)
        };
        _codeInput.TextChanged += (_, _) => ClearHint();
        Controls.Add(_codeInput);

        _hintLabel = new Label
        {
            Bounds = new Rectangle(28, 240, 414, 26),
            Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = Color.FromArgb(190, 80, 68),
            BackColor = Color.Transparent
        };
        Controls.Add(_hintLabel);

        var cancelButton = new RoundedButton
        {
            Text = "\u53d6\u6d88",
            Bounds = new Rectangle(28, 284, 190, 48),
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

        var okButton = new RoundedButton
        {
            Text = "\u786e\u8ba4\u914d\u5bf9\u7801",
            Bounds = new Rectangle(238, 284, 204, 48),
            ButtonColor = AccentGreen,
            HoverColor = Color.FromArgb(19, 145, 111),
            PressedColor = Color.FromArgb(17, 124, 96),
            TextColor = Color.White
        };
        okButton.Click += (_, _) => SaveAndClose();
        Controls.Add(okButton);
    }

    protected override void OnShown(EventArgs e)
    {
        base.OnShown(e);
        _codeInput.Focus();
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
            SaveAndClose();
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

    private void SaveAndClose()
    {
        var code = _codeInput.Text.Trim();
        if (code.Length != 6)
        {
            _hintLabel.Text = "\u8bf7\u8f93\u5165 6 \u4f4d\u6570\u5b57\u914d\u5bf9\u7801\u3002";
            return;
        }

        PairingCode = code;
        DialogResult = DialogResult.OK;
        Close();
    }

    private void ClearHint()
    {
        if (!string.IsNullOrEmpty(_hintLabel.Text))
        {
            _hintLabel.Text = string.Empty;
        }
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

    private sealed class RoundedPanel : Panel
    {
        public Color FillColor { get; set; } = Color.White;
        public Color BorderColor { get; set; } = PcPairingDialog.BorderColor;
        public int Radius { get; set; } = 18;

        public RoundedPanel()
        {
            DoubleBuffered = true;
            BackColor = Color.Transparent;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using var path = CreateRoundRect(bounds, Radius);
            using var fill = new SolidBrush(FillColor);
            using var border = new Pen(BorderColor);
            e.Graphics.FillPath(fill, path);
            e.Graphics.DrawPath(border, path);
            base.OnPaint(e);
        }
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
            Font = new Font("Microsoft YaHei UI", 13F, FontStyle.Bold, GraphicsUnit.Point);
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

    private sealed class PairingCodeInput : Control
    {
        private const int MaxCodeLength = 6;

        private bool _hovered;

        public Color FillColor { get; set; } = InputFill;

        public Color BorderColor { get; set; } = Color.FromArgb(206, 226, 218);

        public PairingCodeInput()
        {
            Cursor = Cursors.IBeam;
            TabStop = true;
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.Selectable, true);
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

        protected override void OnMouseDown(MouseEventArgs e)
        {
            Focus();
            base.OnMouseDown(e);
        }

        protected override void OnGotFocus(EventArgs e)
        {
            Invalidate();
            base.OnGotFocus(e);
        }

        protected override void OnLostFocus(EventArgs e)
        {
            Invalidate();
            base.OnLostFocus(e);
        }

        protected override bool IsInputKey(Keys keyData)
        {
            return keyData == Keys.Back || keyData == Keys.Delete || base.IsInputKey(keyData);
        }

        protected override void OnKeyDown(KeyEventArgs e)
        {
            if ((e.KeyCode == Keys.Back || e.KeyCode == Keys.Delete) && Text.Length > 0)
            {
                Text = Text[..^1];
                e.Handled = true;
                return;
            }

            base.OnKeyDown(e);
        }

        protected override void OnKeyPress(KeyPressEventArgs e)
        {
            if (char.IsDigit(e.KeyChar))
            {
                if (Text.Length < MaxCodeLength)
                {
                    Text += e.KeyChar;
                }

                e.Handled = true;
                return;
            }

            if (!char.IsControl(e.KeyChar))
            {
                e.Handled = true;
                return;
            }

            base.OnKeyPress(e);
        }

        protected override void OnTextChanged(EventArgs e)
        {
            Invalidate();
            base.OnTextChanged(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using (var path = CreateRoundRect(bounds, 18))
            using (var fill = new SolidBrush(FillColor))
            using (var border = new Pen(Focused ? AccentGreen : _hovered ? Color.FromArgb(172, 211, 196) : BorderColor, Focused ? 1.6F : 1F))
            {
                e.Graphics.FillPath(fill, path);
                e.Graphics.DrawPath(border, path);
            }

            var textBounds = new Rectangle(20, 0, Width - 40, Height);
            TextRenderer.DrawText(
                e.Graphics,
                Text,
                Font,
                textBounds,
                ForeColor,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPadding);

            if (Focused)
            {
                DrawCaret(e.Graphics, textBounds);
            }
        }

        private void DrawCaret(Graphics graphics, Rectangle textBounds)
        {
            var measured = string.IsNullOrEmpty(Text)
                ? Size.Empty
                : TextRenderer.MeasureText(graphics, Text, Font, textBounds.Size, TextFormatFlags.NoPadding);
            var caretX = textBounds.Left + (textBounds.Width / 2);
            if (!string.IsNullOrEmpty(Text))
            {
                caretX += measured.Width / 2 + 3;
            }

            var caretTop = textBounds.Top + 18;
            var caretBottom = textBounds.Bottom - 18;
            using var pen = new Pen(TextPrimary, 1.4F);
            graphics.DrawLine(pen, caretX, caretTop, caretX, caretBottom);
        }
    }

    private sealed class CloseIconButton : Control
    {
        private bool _hovered;
        private bool _pressed;

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
            if (ClientRectangle.Contains(e.Location))
            {
                OnClick(EventArgs.Empty);
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var fillColor = _pressed
                ? Color.FromArgb(220, 226, 232)
                : _hovered
                    ? Color.FromArgb(232, 236, 240)
                    : Color.FromArgb(242, 244, 247);

            using (var fill = new SolidBrush(fillColor))
            {
                e.Graphics.FillEllipse(fill, 0, 0, Width - 1, Height - 1);
            }

            using var pen = new Pen(TextSecondary, 2.2F)
            {
                StartCap = LineCap.Round,
                EndCap = LineCap.Round
            };
            var centerX = Width / 2F;
            var centerY = Height / 2F;
            const float half = 6.4F;
            e.Graphics.DrawLine(pen, centerX - half, centerY - half, centerX + half, centerY + half);
            e.Graphics.DrawLine(pen, centerX + half, centerY - half, centerX - half, centerY + half);
        }
    }
}
