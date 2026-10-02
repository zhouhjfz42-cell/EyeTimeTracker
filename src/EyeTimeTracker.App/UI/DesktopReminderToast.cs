using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.UI.Controls;

namespace EyeTimeTracker.App.UI;

/// <summary>
/// 轻通知：应用自绘小窗（不依赖系统气球/通知中心，系统通知设置吞不掉）。
/// 屏幕主工作区右下角弹出，TopMost 但不抢焦点（ShowWithoutActivation），
/// 5000ms 自动消失并 Dispose；点击任意处关闭；连续两条时新条替换旧条（不叠一摞）。
/// 样式按效果图 6-轻通知：圆角 tinted 卡片 + 左侧圆形芯片（远望青/活动橙）+ 标题 + 正文 + 右上 ✕。
/// </summary>
public sealed class DesktopReminderToast : AppPageForm
{
    private const int AutoCloseMs = 5000;
    private const int ScreenMargin = 16;

    private static DesktopReminderToast? _current;

    private readonly System.Windows.Forms.Timer _autoCloseTimer;
    private bool _closing;

    public DesktopReminderToast(bool isMovement, string title, string body, Icon? icon)
    {
        var accent = isMovement ? AppPalette.Orange : AppPalette.Teal;
        var cardFill = isMovement ? AppPalette.SoftOrange : AppPalette.SoftTeal;
        var cardBorder = isMovement ? Color.FromArgb(0xF2, 0xD9, 0xBC) : Color.FromArgb(0xC2, 0xEA, 0xDE);

        Text = title;
        SetAppIcon(icon);
        StartPosition = FormStartPosition.Manual;
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = false;
        TopMost = true;
        BackColor = cardFill;
        _cardBorder = cardBorder;

        using (var titleFont = AppFonts.Create(24, FontStyle.Bold, GraphicsUnit.Pixel))
        using (var bodyFont = AppFonts.Create(22, FontStyle.Regular, GraphicsUnit.Pixel))
        {
            // 尺寸贴合文字内容（字体最低 20px），四周留白一致
            var maxTextWidth = 1120 - 136 - 32 - 56;
            var titleWidth = UiText.SingleLineWidth(title, titleFont);
            var bodyWidth = UiText.SingleLineWidth(body, bodyFont);
            var textWidth = Math.Min(maxTextWidth, Math.Max(titleWidth, bodyWidth) + 12);
            var bodyHeight = UiText.WrappedHeight(body, bodyFont, textWidth) + 4;
            var contentHeight = 32 + 8 + bodyHeight;
            var width = Math.Max(420, 136 + textWidth + 56);
            var height = Math.Max(120, 24 + contentHeight + 24);

            Controls.Add(new IconChip
            {
                Glyph = isMovement ? "动" : "远",
                ChipColor = accent,
                ChipBackground = Color.White,
                Bounds = new Rectangle(28, (height - 80) / 2, 80, 80),
                Font = AppFonts.Create(40, FontStyle.Bold, GraphicsUnit.Pixel)
            });
            Controls.Add(new CanvasLabel
            {
                Text = title,
                Bounds = new Rectangle(136, 24, textWidth, 32),
                Font = AppFonts.Create(24, FontStyle.Bold, GraphicsUnit.Pixel),
                ForeColor = isMovement ? Color.FromArgb(0xC9, 0x77, 0x1F) : Color.FromArgb(0x14, 0x7A, 0x64)
            });
            Controls.Add(new CanvasLabel
            {
                Text = body,
                Bounds = new Rectangle(136, 64, textWidth, bodyHeight),
                Font = AppFonts.Create(22, FontStyle.Regular, GraphicsUnit.Pixel),
                ForeColor = AppPalette.TextSecondary,
                WordWrap = true,
                TextAlign = ContentAlignment.TopLeft
            });

            var closeButton = new CloseDot { Bounds = new Rectangle(width - 56, 24, 32, 32) };
            closeButton.Click += (_, _) => CloseToast();
            Controls.Add(closeButton);

            ClientSize = new Size(width, height);
        }

        CompleteLayoutScaling();

        _autoCloseTimer = new System.Windows.Forms.Timer { Interval = AutoCloseMs };
        _autoCloseTimer.Tick += (_, _) => CloseToast();

        WireClick(this, (_, _) => CloseToast());
    }

    private readonly Color _cardBorder;

    /// <summary>右下角弹出（主屏工作区留边）。连续提醒：新条替换旧条。</summary>
    public static void ShowToast(bool isMovement, string title, string body, Icon? icon)
    {
        _current?.CloseToast();

        var toast = new DesktopReminderToast(isMovement, title, body, icon);
        var area = Screen.PrimaryScreen?.WorkingArea ?? Screen.FromPoint(Cursor.Position).WorkingArea;
        toast.Location = new Point(
            Math.Max(area.Left, area.Right - toast.Width - ScreenMargin),
            Math.Max(area.Top, area.Bottom - toast.Height - ScreenMargin));
        _current = toast;
        toast.Show();
        toast._autoCloseTimer.Start();
    }



    private void CloseToast()
    {
        if (_closing)
        {
            return;
        }

        _closing = true;
        if (ReferenceEquals(_current, this))
        {
            _current = null;
        }

        _autoCloseTimer.Stop();
        Close();
        Dispose();
    }

    protected override bool ShowWithoutActivation => true;

    /// <summary>不抢焦点（NOACTIVATE）、不进任务栏/Alt-Tab（TOOLWINDOW）。</summary>
    protected override CreateParams CreateParams
    {
        get
        {
            const int WS_EX_NOACTIVATE = 0x08000000;
            const int WS_EX_TOOLWINDOW = 0x00000080;
            var cp = base.CreateParams;
            cp.ExStyle |= WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW;
            return cp;
        }
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        var radius = 16;
        var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
        using var path = UiGraphics.RoundedRect(bounds, radius);
        using var fill = new SolidBrush(BackColor);
        using var border = new Pen(_cardBorder);
        e.Graphics.FillPath(fill, path);
        e.Graphics.DrawPath(border, path);
    }

    protected override void OnResize(EventArgs e)
    {
        using var path = UiGraphics.RoundedRect(new Rectangle(0, 0, Width, Height), 16);
        Region = new Region(path);
        base.OnResize(e);
    }

    private static void WireClick(Control control, EventHandler handler)
    {
        control.Click += handler;
        foreach (Control child in control.Controls)
        {
            WireClick(child, handler);
        }
    }

    /// <summary>右上 ✕ 关闭钮。</summary>
    private sealed class CloseDot : Control
    {
        private bool _hovered;

        public CloseDot()
        {
            Cursor = Cursors.Hand;
            TabStop = false;
            SetStyle(
                ControlStyles.UserPaint
                | ControlStyles.AllPaintingInWmPaint
                | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.SupportsTransparentBackColor
                | ControlStyles.ResizeRedraw,
                true);
            BackColor = Color.Transparent;
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

        protected override void OnMouseUp(MouseEventArgs e)
        {
            // 不手动调 OnClick：框架在 MouseUp 之后会自行触发 Click（StandardClick），手动会双触发
            base.OnMouseUp(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            if (_hovered)
            {
                using var hoverBrush = new SolidBrush(Color.FromArgb(0x55, 0xFF, 0xFF, 0xFF));
                e.Graphics.FillEllipse(hoverBrush, 0, 0, Width - 1, Height - 1);
            }

            var inset = 7;
            using var pen = new Pen(AppPalette.TextSecondary, Math.Max(1.4F, Width / 16F))
            {
                StartCap = System.Drawing.Drawing2D.LineCap.Round,
                EndCap = System.Drawing.Drawing2D.LineCap.Round
            };
            e.Graphics.DrawLine(pen, inset, inset, Width - inset, Height - inset);
            e.Graphics.DrawLine(pen, Width - inset, inset, inset, Height - inset);
        }
    }
}
