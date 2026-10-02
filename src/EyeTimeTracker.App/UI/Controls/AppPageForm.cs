using System.Drawing.Drawing2D;

namespace EyeTimeTracker.App.UI.Controls;

/// <summary>改版页面统一外壳：浅蓝紫渐变背景、居中、固定边框、统一字体。
/// 窗口尺寸按设计像素固定（物理像素，不跟随系统 DPI 缩放），
/// 并锁定 Min=Max=Size，杜绝任何路径下拉出空白；跨屏拖动尺寸不变。</summary>
public abstract class AppPageForm : Form
{
    protected AppPageForm()
    {
        AutoScaleMode = AutoScaleMode.None;
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = true;
        BackColor = AppPalette.PageGradientTop;
        Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);
        DoubleBuffered = true;
    }

    /// <summary>
    /// 在派生类构造函数末尾（所有子控件与 ClientSize 就绪后）调用：
    /// 创建句柄并锁定窗口尺寸（Min=Max=当前 Size）。
    /// 尺寸按设计像素固定，不做 DPI 放大。
    /// </summary>
    protected void CompleteLayoutScaling()
    {
        _ = Handle;
        MinimumSize = Size;
        MaximumSize = Size;
    }

    /// <summary>设计像素 = 物理像素（尺寸不跟随 DPI，恒等换算）。</summary>
    protected int Sc(int value)
    {
        return value;
    }

    protected Rectangle Sc(int x, int y, int width, int height)
    {
        return new Rectangle(x, y, width, height);
    }

    protected void SetAppIcon(Icon? icon)
    {
        if (icon is not null)
        {
            Icon = (Icon)icon.Clone();
        }
    }

    protected override void OnPaintBackground(PaintEventArgs e)
    {
        var bounds = ClientRectangle;
        if (bounds.Width <= 0 || bounds.Height <= 0)
        {
            base.OnPaintBackground(e);
            return;
        }

        using var brush = new LinearGradientBrush(
            bounds,
            AppPalette.PageGradientTop,
            AppPalette.PageGradientBottom,
            LinearGradientMode.Vertical);
        e.Graphics.FillRectangle(brush, bounds);
    }
}
