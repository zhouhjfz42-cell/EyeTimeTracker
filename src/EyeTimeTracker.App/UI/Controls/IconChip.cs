namespace EyeTimeTracker.App.UI.Controls;

/// <summary>图标芯片：圆形浅色底 + 单字（如「坐」「远」「动」）。</summary>
public sealed class IconChip : Control
{
    private string _glyph = string.Empty;

    public Color ChipColor { get; set; } = AppPalette.Primary;

    public Color ChipBackground { get; set; } = AppPalette.SoftBlue;

    public string Glyph
    {
        get => _glyph;
        set
        {
            _glyph = value ?? string.Empty;
            Invalidate();
        }
    }

    public IconChip()
    {
        TabStop = false;
        SetStyle(
            ControlStyles.UserPaint
            | ControlStyles.AllPaintingInWmPaint
            | ControlStyles.OptimizedDoubleBuffer
            | ControlStyles.SupportsTransparentBackColor
            | ControlStyles.ResizeRedraw,
            true);
        BackColor = Color.Transparent;
        Font = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        var size = Math.Min(Width, Height) - 1;
        var bounds = new Rectangle((Width - size) / 2, (Height - size) / 2, size, size);
        using var brush = new SolidBrush(ChipBackground);
        e.Graphics.FillEllipse(brush, bounds);
        if (!string.IsNullOrEmpty(_glyph))
        {
            TextRenderer.DrawText(
                e.Graphics,
                _glyph,
                Font,
                bounds,
                ChipColor,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
        }
    }
}
