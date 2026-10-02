namespace EyeTimeTracker.App.UI.Controls;

public enum PillButtonStyle
{
    /// <summary>蓝底白字。</summary>
    Primary,

    /// <summary>白底蓝描边蓝字。</summary>
    Secondary,

    /// <summary>无底色蓝字。</summary>
    Text
}

/// <summary>胶囊按钮：主 / 次 / 文字三种，可键盘访问。</summary>
public class PillButton : Control
{
    private bool _hovered;
    private bool _pressed;

    public PillButtonStyle Style { get; set; } = PillButtonStyle.Primary;

    public Color ButtonColor { get; set; } = AppPalette.Primary;

    public Color TextColor { get; set; } = Color.White;

    public PillButton()
    {
        Cursor = Cursors.Hand;
        Font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel);
        SetStyle(
            ControlStyles.UserPaint
            | ControlStyles.AllPaintingInWmPaint
            | ControlStyles.OptimizedDoubleBuffer
            | ControlStyles.SupportsTransparentBackColor
            | ControlStyles.ResizeRedraw,
            true);
        BackColor = Color.Transparent;
    }

    protected override void OnEnabledChanged(EventArgs e)
    {
        Cursor = Enabled ? Cursors.Hand : Cursors.Default;
        Invalidate();
        base.OnEnabledChanged(e);
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
        if (e.Button == MouseButtons.Left)
        {
            _pressed = true;
            Focus();
            Invalidate();
        }

        base.OnMouseDown(e);
    }

    protected override void OnMouseUp(MouseEventArgs e)
    {
        _pressed = false;
        Invalidate();
        // 不手动调 OnClick：框架在 MouseUp 前会自行触发 Click（StandardClick），手动会双触发
        base.OnMouseUp(e);
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        if (e.KeyCode is Keys.Enter or Keys.Space)
        {
            OnClick(EventArgs.Empty);
            e.Handled = true;
        }

        base.OnKeyDown(e);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
        var textColor = ResolveTextColor();
        if (Style != PillButtonStyle.Text)
        {
            using var path = UiGraphics.RoundedRect(bounds, Height / 2);
            using var fill = new SolidBrush(ResolveFillColor());
            e.Graphics.FillPath(fill, path);
            if (Style == PillButtonStyle.Secondary && Enabled)
            {
                using var pen = new Pen(ButtonColor, 1.6F);
                e.Graphics.DrawPath(pen, path);
            }
        }

        TextRenderer.DrawText(
            e.Graphics,
            Text,
            Font,
            new Rectangle(4, 0, Math.Max(0, Width - 8), Height),
            textColor,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
    }

    private Color ResolveFillColor()
    {
        if (!Enabled)
        {
            return AppPalette.DisabledFill;
        }

        if (Style == PillButtonStyle.Secondary)
        {
            return _pressed ? AppPalette.SoftBlue : Color.White;
        }

        return _pressed
            ? ControlPaint.Dark(ButtonColor, 0.12F)
            : _hovered
                ? ControlPaint.Dark(ButtonColor, 0.06F)
                : ButtonColor;
    }

    private Color ResolveTextColor()
    {
        if (!Enabled)
        {
            return AppPalette.TextSecondary;
        }

        if (Style == PillButtonStyle.Primary)
        {
            return TextColor;
        }

        return _pressed ? ControlPaint.Dark(ButtonColor, 0.12F) : ButtonColor;
    }
}
