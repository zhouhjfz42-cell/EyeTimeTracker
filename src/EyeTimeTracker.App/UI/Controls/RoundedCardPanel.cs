namespace EyeTimeTracker.App.UI.Controls;

/// <summary>柔和圆角卡片：白底、浅边框、一层柔和投影。</summary>
public class RoundedCardPanel : Panel
{
    public Color FillColor { get; set; } = AppPalette.CardFill;

    public Color BorderColor { get; set; } = AppPalette.CardBorder;

    public int Radius { get; set; } = 16;

    public bool ShowShadow { get; set; } = true;

    public RoundedCardPanel()
    {
        DoubleBuffered = true;
        BackColor = Color.Transparent;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        var radius = Radius;
        var shadowOffset = 3;
        if (ShowShadow)
        {
            using var shadowPath = UiGraphics.RoundedRect(new Rectangle(0, shadowOffset, Width - 1, Height - shadowOffset + 1), radius);
            using var shadowBrush = new SolidBrush(AppPalette.CardShadow);
            e.Graphics.FillPath(shadowBrush, shadowPath);
        }

        var bounds = new Rectangle(0, 0, Width - 1, Height - shadowOffset);
        using var path = UiGraphics.RoundedRect(bounds, radius);
        using var fill = new SolidBrush(FillColor);
        using var border = new Pen(BorderColor);
        e.Graphics.FillPath(fill, path);
        e.Graphics.DrawPath(border, path);
        base.OnPaint(e);
    }
}
