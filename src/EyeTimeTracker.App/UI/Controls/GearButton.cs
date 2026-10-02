using System.Drawing.Imaging;
using System.Runtime.InteropServices;

namespace EyeTimeTracker.App.UI.Controls;

/// <summary>
/// 右上角齿轮按钮：加载 Assets/gear.png（黑齿轮白底），白底抠成透明、齿轮染主题蓝 #2B69D2
/// （与主卡分钟数字同色），点击打开设置页。悬停有浅色圆底反馈。
/// </summary>
public sealed class GearButton : Control
{
    public static readonly Color GearBlue = Color.FromArgb(0x2B, 0x69, 0xD2);

    private static readonly Lazy<Bitmap> GearImage = new(LoadTintedGear);
    private bool _hovered;

    public GearButton()
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
        // 不手动调 OnClick：框架在 MouseUp 前会自行触发 Click（StandardClick），手动会双触发
        base.OnMouseUp(e);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        if (_hovered)
        {
            using var hoverBrush = new SolidBrush(Color.FromArgb(0xE8, 0xEF, 0xFB));
            e.Graphics.FillEllipse(hoverBrush, 0, 0, Width - 1, Height - 1);
        }

        var pad = 3;
        var bounds = new Rectangle(pad, pad, Width - pad * 2, Height - pad * 2);
        e.Graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
        e.Graphics.DrawImage(GearImage.Value, bounds);
    }

    /// <summary>黑齿轮白底 → 透明底主题蓝齿轮（边缘按灰度转透明度，抗锯齿平滑）。</summary>
    private static Bitmap LoadTintedGear()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "Assets", "gear.png");
        using var source = new Bitmap(path);
        var width = source.Width;
        var height = source.Height;
        var tinted = new Bitmap(width, height, PixelFormat.Format32bppArgb);

        var sourceData = source.LockBits(
            new Rectangle(0, 0, width, height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        var targetData = tinted.LockBits(
            new Rectangle(0, 0, width, height), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        try
        {
            var stride = sourceData.Stride;
            var sourceBytes = new byte[stride * height];
            var targetBytes = new byte[stride * height];
            Marshal.Copy(sourceData.Scan0, sourceBytes, 0, sourceBytes.Length);
            for (var i = 0; i < sourceBytes.Length; i += 4)
            {
                // 黑齿轮：亮度越低越不透明；白底 → 全透明
                var lum = (sourceBytes[i] + sourceBytes[i + 1] + sourceBytes[i + 2]) / 3;
                var alpha = 255 - lum;
                targetBytes[i] = GearBlue.B;
                targetBytes[i + 1] = GearBlue.G;
                targetBytes[i + 2] = GearBlue.R;
                targetBytes[i + 3] = (byte)alpha;
            }

            Marshal.Copy(targetBytes, 0, targetData.Scan0, targetBytes.Length);
        }
        finally
        {
            source.UnlockBits(sourceData);
            tinted.UnlockBits(targetData);
        }

        return tinted;
    }
}
