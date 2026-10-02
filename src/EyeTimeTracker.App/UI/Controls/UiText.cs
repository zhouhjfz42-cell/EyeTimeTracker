namespace EyeTimeTracker.App.UI.Controls;

/// <summary>
/// 文字测量：以实际渲染路径（TextRenderer）量出宽高，排版按测量结果自适应，
/// 保证描述性文字完整显示、不省略号截断。
/// 字体统一为像素单位（见 AppFonts）：测量值 = 物理像素，与系统 DPI 无关。
/// </summary>
internal static class UiText
{
    private static readonly Bitmap MeasureBitmap = new(1, 1);

    /// <summary>单行文字宽度（像素）。</summary>
    public static int SingleLineWidth(string text, Font font)
    {
        if (string.IsNullOrEmpty(text))
        {
            return 0;
        }

        using var graphics = Graphics.FromImage(MeasureBitmap);
        return SingleLineWidth(graphics, text, font);
    }

    /// <summary>给定宽度内换行显示所需高度（像素；WordBreak，与 CanvasLabel 渲染一致）。</summary>
    public static int WrappedHeight(string text, Font font, int width)
    {
        if (string.IsNullOrEmpty(text) || width <= 0)
        {
            return 0;
        }

        using var graphics = Graphics.FromImage(MeasureBitmap);
        return WrappedHeight(graphics, text, font, width);
    }

    /// <summary>单行文字宽度（像素，给定 Graphics）。</summary>
    public static int SingleLineWidth(Graphics graphics, string text, Font font)
    {
        if (string.IsNullOrEmpty(text))
        {
            return 0;
        }

        return TextRenderer.MeasureText(
            graphics,
            text,
            font,
            new Size(int.MaxValue, int.MaxValue),
            TextFormatFlags.NoPadding | TextFormatFlags.SingleLine | TextFormatFlags.NoPrefix).Width;
    }

    /// <summary>换行高度（像素，给定 Graphics）。</summary>
    public static int WrappedHeight(Graphics graphics, string text, Font font, int width)
    {
        if (string.IsNullOrEmpty(text) || width <= 0)
        {
            return 0;
        }

        return TextRenderer.MeasureText(
            graphics,
            text,
            font,
            new Size(width, int.MaxValue),
            TextFormatFlags.NoPadding | TextFormatFlags.WordBreak | TextFormatFlags.NoPrefix).Height;
    }

    /// <summary>单行文字所需高度（像素）。</summary>
    public static int SingleLineHeight(string text, Font font)
    {
        using var graphics = Graphics.FromImage(MeasureBitmap);
        return TextRenderer.MeasureText(
            graphics,
            string.IsNullOrEmpty(text) ? " " : text,
            font,
            new Size(int.MaxValue, int.MaxValue),
            TextFormatFlags.NoPadding | TextFormatFlags.SingleLine).Height;
    }
}
