namespace EyeTimeTracker.App.UI.Controls;

/// <summary>只画文字的透明安全标签（渐变/卡片背景上不留底色块）。</summary>
public sealed class CanvasLabel : Control
{
    public ContentAlignment TextAlign { get; set; } = ContentAlignment.MiddleLeft;

    public bool WordWrap { get; set; }

    public CanvasLabel()
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
    }

    protected override void OnTextChanged(EventArgs e)
    {
        Invalidate();
        base.OnTextChanged(e);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        if (ClientRectangle.Width <= 0 || ClientRectangle.Height <= 0 || string.IsNullOrEmpty(Text))
        {
            return;
        }

        TextRenderer.DrawText(
            e.Graphics,
            Text,
            Font,
            ClientRectangle,
            ForeColor,
            UiGraphics.FlagsFor(TextAlign, WordWrap));
    }
}
