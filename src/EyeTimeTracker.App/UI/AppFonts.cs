using System.Drawing.Text;

namespace EyeTimeTracker.App.UI;

internal static class AppFonts
{
    private const string PreferredFamilyName = "Noto Sans SC";
    private const string FallbackFamilyName = "Microsoft YaHei UI";
    private static readonly PrivateFontCollection PrivateFonts = new();
    private static readonly FontFamily PreferredFamily = LoadPreferredFamily();

    public static Font Create(float size, FontStyle style = FontStyle.Regular, GraphicsUnit unit = GraphicsUnit.Point)
    {
        return new Font(PreferredFamily, size, style, unit);
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
