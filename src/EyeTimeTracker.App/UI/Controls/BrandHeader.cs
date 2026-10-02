using EyeTimeTracker.App.Localization;

namespace EyeTimeTracker.App.UI.Controls;

/// <summary>品牌区：「坐」圆形芯片 + 久坐会死 + 浅色副标题（空间不足时副标题落到第二行，不截断）。
/// OnPaint 用实际 Graphics 测量，尺寸固定像素渲染。</summary>
public sealed class BrandHeader : Control
{
    public BrandHeader()
    {
        SetStyle(
            ControlStyles.UserPaint
            | ControlStyles.AllPaintingInWmPaint
            | ControlStyles.OptimizedDoubleBuffer
            | ControlStyles.SupportsTransparentBackColor
            | ControlStyles.ResizeRedraw,
            true);
        BackColor = Color.Transparent;
        TabStop = false;
    }

    /// <summary>给定宽度（设计像素）下完整显示所需高度（单行或标题+副标题两行）。</summary>
    public int PreferredHeight(int width)
    {
        ComputeLayout(null, width, out _, out var twoLines);
        return twoLines ? 58 : 40;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        ComputeLayout(e.Graphics, Width, out var layout, out _);

        var chipSize = Sc(32);
        var chipBounds = new Rectangle(0, layout.TwoLines ? Sc(2) : (Height - chipSize) / 2, chipSize, chipSize);
        using (var chipBrush = new SolidBrush(AppPalette.SoftBlue))
        {
            e.Graphics.FillEllipse(chipBrush, chipBounds);
        }

        TextRenderer.DrawText(
            e.Graphics,
            AppText.Get("desktop.brand.chip"),
            layout.ChipFont,
            chipBounds,
            AppPalette.Primary,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);

        TextRenderer.DrawText(
            e.Graphics,
            AppText.Get("app.name"),
            layout.TitleFont,
            layout.TitleBounds,
            AppPalette.Primary,
            TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);

        TextRenderer.DrawText(
            e.Graphics,
            AppText.Get("desktop.brand.subtitle"),
            layout.SubtitleFont,
            layout.SubtitleBounds,
            AppPalette.TextSecondary,
            TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);

        layout.Dispose();
    }

    private void ComputeLayout(Graphics? graphics, int width, out HeaderLayout layout, out bool twoLines)
    {
        var chipFont = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point);
        var titleFont = AppFonts.Create(18F, FontStyle.Bold, GraphicsUnit.Point);
        var subtitleFont = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point);
        // OnPaint 时用设备 DPI 的 Graphics 测量；构造期排版用 96dpi 设计测量
        var titleWidth = graphics is null
            ? UiText.SingleLineWidth(AppText.Get("app.name"), titleFont)
            : UiText.SingleLineWidth(graphics, AppText.Get("app.name"), titleFont);
        var subtitleWidth = graphics is null
            ? UiText.SingleLineWidth(AppText.Get("desktop.brand.subtitle"), subtitleFont)
            : UiText.SingleLineWidth(graphics, AppText.Get("desktop.brand.subtitle"), subtitleFont);
        var chipSize = graphics is null ? 32 : Sc(32);
        var textLeft = chipSize + (graphics is null ? 12 : Sc(12));
        twoLines = textLeft + titleWidth + Sc(10) + subtitleWidth > width;
        Rectangle titleBounds;
        Rectangle subtitleBounds;
        if (twoLines)
        {
            var lineTop = graphics is null ? 2 : Sc(2);
            var titleHeight = graphics is null ? 28 : Sc(28);
            var subtitleTop = graphics is null ? 30 : Sc(30);
            var subtitleHeight = graphics is null ? 22 : Sc(22);
            titleBounds = new Rectangle(textLeft, lineTop, Math.Max(0, width - textLeft), titleHeight);
            subtitleBounds = new Rectangle(textLeft, subtitleTop, Math.Max(0, width - textLeft), subtitleHeight);
        }
        else
        {
            var gap = graphics is null ? 10 : Sc(10);
            titleBounds = new Rectangle(textLeft, 0, titleWidth + 4, Height);
            subtitleBounds = new Rectangle(titleBounds.Right + gap, 0, Math.Max(0, width - titleBounds.Right - gap), Height);
        }

        layout = new HeaderLayout(chipFont, titleFont, subtitleFont, titleBounds, subtitleBounds, twoLines);
    }

    private int Sc(int value)
    {
        return value;
    }

    private sealed class HeaderLayout : IDisposable
    {
        public HeaderLayout(Font chipFont, Font titleFont, Font subtitleFont, Rectangle titleBounds, Rectangle subtitleBounds, bool twoLines)
        {
            ChipFont = chipFont;
            TitleFont = titleFont;
            SubtitleFont = subtitleFont;
            TitleBounds = titleBounds;
            SubtitleBounds = subtitleBounds;
            TwoLines = twoLines;
        }

        public Font ChipFont { get; }
        public Font TitleFont { get; }
        public Font SubtitleFont { get; }
        public Rectangle TitleBounds { get; }
        public Rectangle SubtitleBounds { get; }
        public bool TwoLines { get; }

        public void Dispose()
        {
            ChipFont.Dispose();
            TitleFont.Dispose();
            SubtitleFont.Dispose();
        }
    }
}
