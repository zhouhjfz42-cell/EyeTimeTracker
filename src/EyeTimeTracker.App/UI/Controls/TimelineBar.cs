using EyeTimeTracker.App.Localization;
using EyeTimeTracker.Core.DesktopActivity;
using System.Drawing.Drawing2D;

namespace EyeTimeTracker.App.UI.Controls;

/// <summary>桌面活动时间线横条：有效活动（蓝，进行中段为青）/ 推定离开（斜纹）/ 不可观察（灰）。</summary>
internal sealed class TimelineBar : Control
{
    private IReadOnlyList<TimelineSegment> _segments = Array.Empty<TimelineSegment>();
    private DateOnly _date;
    private bool _isToday;

    public TimelineBar()
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

    public void SetData(DateOnly date, IReadOnlyList<TimelineSegment> segments, bool isToday)
    {
        _date = date;
        _segments = segments;
        _isToday = isToday;
        Invalidate();
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
        using var path = UiGraphics.RoundedRect(bounds, Height / 2);
        using (var background = new SolidBrush(Color.FromArgb(0xEE, 0xF1, 0xF6)))
        {
            e.Graphics.FillPath(background, path);
        }

        var (dayStartUtc, dayEndUtc) = DailyAggregator.DayBoundsUtc(_date, TimeZoneInfo.Local);
        var totalSeconds = Math.Max(1.0, (dayEndUtc - dayStartUtc).TotalSeconds);
        var nowUtc = DateTimeOffset.UtcNow;
        e.Graphics.SetClip(path);
        foreach (var segment in _segments)
        {
            var left = (int)((segment.StartUtc - dayStartUtc).TotalSeconds / totalSeconds * Width);
            var right = (int)Math.Ceiling((segment.EndUtc - dayStartUtc).TotalSeconds / totalSeconds * Width);
            var rect = new Rectangle(left, 0, Math.Max(2, right - left), Height);
            if (segment.Kind == TimelineSegmentKind.Active)
            {
                var ongoing = _isToday && segment.EndUtc >= nowUtc.AddSeconds(-30);
                using var brush = new SolidBrush(ongoing ? AppPalette.Teal : AppPalette.Primary);
                e.Graphics.FillRectangle(brush, rect);
            }
            else if (segment.Kind == TimelineSegmentKind.InferredAway)
            {
                using var brush = new HatchBrush(HatchStyle.ForwardDiagonal, Color.FromArgb(0xAF, 0xBC, 0xD4), Color.FromArgb(0xE6, 0xEA, 0xF3));
                e.Graphics.FillRectangle(brush, rect);
            }
            else
            {
                using var brush = new SolidBrush(Color.FromArgb(0xC9, 0xD2, 0xE0));
                e.Graphics.FillRectangle(brush, rect);
            }
        }

        e.Graphics.ResetClip();
    }
}

/// <summary>时间线图例：有效活动 / 推定离开 / 不可观察，宽度按文字测量。</summary>
internal sealed class TimelineLegend : Control
{
    public TimelineLegend()
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

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        using var font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel);
        var x = 0;
        x = DrawLegendItem(e.Graphics, font, x, (g, s) =>
        {
            using var brush = new SolidBrush(AppPalette.Primary);
            g.FillEllipse(brush, 0, s(7), s(10), s(10));
        }, AppText.Get("desktop.records.timeline.legendActive"));
        x = DrawLegendItem(e.Graphics, font, x, (g, s) =>
        {
            using var brush = new HatchBrush(HatchStyle.ForwardDiagonal, Color.FromArgb(0xAF, 0xBC, 0xD4), Color.FromArgb(0xE6, 0xEA, 0xF3));
            g.FillRectangle(brush, 0, s(5), s(16), s(15));
        }, AppText.Get("desktop.records.timeline.legendAway"));
        DrawLegendItem(e.Graphics, font, x, (g, s) =>
        {
            using var brush = new SolidBrush(Color.FromArgb(0xC9, 0xD2, 0xE0));
            g.FillRectangle(brush, 0, s(5), s(12), s(15));
        }, AppText.Get("desktop.records.timeline.legendGap"));
    }

    private int DrawLegendItem(Graphics graphics, Font font, int x, Action<Graphics, Func<int, int>> drawIcon, string text)
    {
        Func<int, int> s = value => value;
        graphics.TranslateTransform(x, 0);
        drawIcon(graphics, s);
        graphics.ResetTransform();
        var textX = x + s(22);
        var textWidth = UiText.SingleLineWidth(graphics, text, font) + 4;
        TextRenderer.DrawText(graphics, text, font, new Rectangle(textX, 0, textWidth, Height), AppPalette.TextSecondary,
            TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
        return textX + textWidth + s(18);
    }
}
