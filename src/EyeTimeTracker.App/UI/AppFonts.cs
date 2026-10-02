using System.Drawing.Text;

namespace EyeTimeTracker.App.UI;

internal static class AppFonts
{
    private const string PreferredFamilyName = "Noto Sans SC";
    private const string FallbackFamilyName = "Microsoft YaHei UI";
    private static readonly PrivateFontCollection PrivateFonts = new();
    private static readonly FontFamily PreferredFamily = LoadPreferredFamily();

    /// <summary>
    /// 单位处理：Pixel 直接按像素创建（标注图给的 px 值即目标像素高度）；
    /// Point 按 96dpi 基线换算成像素（pt × 96/72），用于未改造的页面保持现有观感。
    /// 窗口尺寸按物理像素固定（不跟随 DPI），字体像素渲染清晰、不放大不溢出。
    /// </summary>
    public static Font Create(float size, FontStyle style = FontStyle.Regular, GraphicsUnit unit = GraphicsUnit.Point)
    {
        var pixels = unit == GraphicsUnit.Point ? size * 96F / 72F : size;
        return new Font(PreferredFamily, pixels, style, GraphicsUnit.Pixel);
    }

    private static FontFamily LoadPreferredFamily()
    {
        var fontPath = Path.Combine(AppContext.BaseDirectory, "Assets", "Fonts", "NotoSansSC-VF.ttf");
        if (File.Exists(fontPath))
        {
            try
            {
                PrivateFonts.AddFontFile(fontPath);
                if (PrivateFonts.Families.Length > 0)
                {
                    return PrivateFonts.Families[0];
                }
            }
            catch
            {
            }
        }

        foreach (var family in FontFamily.Families)
        {
            if (string.Equals(family.Name, PreferredFamilyName, StringComparison.OrdinalIgnoreCase))
            {
                return family;
            }
        }

        return new FontFamily(FallbackFamilyName);
    }
}
