using System.Drawing.Drawing2D;

namespace EyeTimeTracker.App.UI.Controls;

/// <summary>久坐会死改版视觉：浅蓝紫渐变背景、白卡片、蓝主色（见 ui-mockups）。</summary>
internal static class AppPalette
{
    public static readonly Color PageGradientTop = Color.FromArgb(233, 238, 252);
    public static readonly Color PageGradientBottom = Color.FromArgb(234, 247, 240);

    public static readonly Color Primary = Color.FromArgb(0x42, 0x85, 0xF4);
    public static readonly Color PrimaryHover = Color.FromArgb(0x3A, 0x74, 0xD6);
    public static readonly Color PrimaryPressed = Color.FromArgb(0x33, 0x66, 0xBE);
    public static readonly Color Teal = Color.FromArgb(0x20, 0xB2, 0x94);
    public static readonly Color Orange = Color.FromArgb(0xF0, 0x96, 0x3C);
    public static readonly Color Purple = Color.FromArgb(0x8B, 0x6E, 0xF6);
    public static readonly Color Danger = Color.FromArgb(0xD8, 0x5A, 0x5A);

    public static readonly Color TextPrimary = Color.FromArgb(0x1A, 0x2B, 0x4A);
    public static readonly Color TextSecondary = Color.FromArgb(0x7A, 0x8B, 0xA8);

    public static readonly Color CardFill = Color.White;
    public static readonly Color CardBorder = Color.FromArgb(0xE2, 0xE8, 0xF5);
    public static readonly Color CardShadow = Color.FromArgb(16, 0x1A, 0x2B, 0x4A);

    public static readonly Color SoftBlue = Color.FromArgb(0xE9, 0xF0, 0xFE);
    public static readonly Color SoftTeal = Color.FromArgb(0xE2, 0xF6, 0xF0);
    public static readonly Color SoftOrange = Color.FromArgb(0xFC, 0xEF, 0xE0);
    public static readonly Color SoftPurple = Color.FromArgb(0xEF, 0xEA, 0xFD);
    public static readonly Color SoftDanger = Color.FromArgb(0xFB, 0xEA, 0xEA);
    public static readonly Color DisabledFill = Color.FromArgb(0xEE, 0xF1, 0xF6);
}

internal static class UiGraphics
{
    public static GraphicsPath RoundedRect(Rectangle bounds, int radius)
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

    public static TextFormatFlags FlagsFor(ContentAlignment align, bool wordWrap = false)
    {
        var flags = TextFormatFlags.NoPadding | TextFormatFlags.PreserveGraphicsClipping | TextFormatFlags.NoPrefix;
        flags |= wordWrap ? TextFormatFlags.WordBreak : TextFormatFlags.SingleLine | TextFormatFlags.EndEllipsis;
        flags |= align switch
        {
            ContentAlignment.TopCenter or ContentAlignment.MiddleCenter or ContentAlignment.BottomCenter => TextFormatFlags.HorizontalCenter,
            ContentAlignment.TopRight or ContentAlignment.MiddleRight or ContentAlignment.BottomRight => TextFormatFlags.Right,
            _ => TextFormatFlags.Left
        };
        flags |= align switch
        {
            ContentAlignment.MiddleLeft or ContentAlignment.MiddleCenter or ContentAlignment.MiddleRight => TextFormatFlags.VerticalCenter,
            ContentAlignment.BottomLeft or ContentAlignment.BottomCenter or ContentAlignment.BottomRight => TextFormatFlags.Bottom,
            _ => TextFormatFlags.Top
        };
        return flags;
    }
}
