namespace EyeTimeTracker.App.UI.Controls;

/// <summary>开关：轨道 + 滑块，点击切换。</summary>
public sealed class AppSwitch : Control
{
    private bool _checked;
    private bool _hovered;

    public event EventHandler? CheckedChanged;

    public bool Checked
    {
        get => _checked;
        set
        {
            if (_checked == value)
            {
                return;
            }

            _checked = value;
            Invalidate();
            CheckedChanged?.Invoke(this, EventArgs.Empty);
        }
    }

    public AppSwitch()
    {
        Cursor = Cursors.Hand;
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
        if (e.Button == MouseButtons.Left && ClientRectangle.Contains(e.Location))
        {
            Checked = !Checked;
        }

        base.OnMouseUp(e);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        var track = new Rectangle(0, 0, Width - 1, Height - 1);
        var offColor = _hovered ? Color.FromArgb(0xDC, 0xE3, 0xEE) : Color.FromArgb(0xE6, 0xEB, 0xF3);
        var trackColor = Checked ? AppPalette.Teal : offColor;
        using (var path = UiGraphics.RoundedRect(track, Height / 2))
        using (var brush = new SolidBrush(trackColor))
        {
            e.Graphics.FillPath(brush, path);
        }

        var pad = 4;
        var knobSize = Math.Max(4, Height - pad * 2);
        var knobX = Checked ? Width - knobSize - pad : pad;
        using var knobBrush = new SolidBrush(Color.White);
        e.Graphics.FillEllipse(knobBrush, knobX, pad, knobSize, knobSize);
    }
}
