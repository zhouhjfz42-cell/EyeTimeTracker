namespace EyeTimeTracker.App.UI.Controls;

/// <summary>底部标签页导航条（主页 / 设置）：白色胶囊条，选中项蓝色加粗。</summary>
public sealed class TabBarControl : Control
{
    private string[] _items = Array.Empty<string>();
    private int _selectedIndex;
    private int _hoveredIndex = -1;

    public event EventHandler<int>? TabClicked;

    public TabBarControl()
    {
        SetStyle(
            ControlStyles.UserPaint
            | ControlStyles.AllPaintingInWmPaint
            | ControlStyles.OptimizedDoubleBuffer
            | ControlStyles.SupportsTransparentBackColor
            | ControlStyles.ResizeRedraw,
            true);
        BackColor = Color.Transparent;
        Font = AppFonts.Create(11F, FontStyle.Regular, GraphicsUnit.Point);
    }

    public void SetItems(params string[] items)
    {
        _items = items ?? Array.Empty<string>();
        _selectedIndex = Math.Clamp(_selectedIndex, 0, Math.Max(0, _items.Length - 1));
        Invalidate();
    }

    public int SelectedIndex
    {
        get => _selectedIndex;
        set
        {
            if (_selectedIndex == value)
            {
                return;
            }

            _selectedIndex = Math.Clamp(value, 0, Math.Max(0, _items.Length - 1));
            Invalidate();
        }
    }

    /// <summary>以编程方式复位选中态（不触发点击）。</summary>
    public void ResetSelection(int index)
    {
        _selectedIndex = Math.Clamp(index, 0, Math.Max(0, _items.Length - 1));
        Invalidate();
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        var index = HitIndex(e.X);
        if (index != _hoveredIndex)
        {
            _hoveredIndex = index;
            Cursor = index >= 0 ? Cursors.Hand : Cursors.Default;
            Invalidate();
        }

        base.OnMouseMove(e);
    }

    protected override void OnMouseLeave(EventArgs e)
    {
        _hoveredIndex = -1;
        Invalidate();
        base.OnMouseLeave(e);
    }

    protected override void OnMouseClick(MouseEventArgs e)
    {
        var index = HitIndex(e.X);
        if (index >= 0 && index != _selectedIndex)
        {
            TabClicked?.Invoke(this, index);
        }

        base.OnMouseClick(e);
    }

    private int HitIndex(int x)
    {
        if (_items.Length == 0)
        {
            return -1;
        }

        var bar = BarBounds();
        if (x < bar.Left || x > bar.Right)
        {
            return -1;
        }

        var itemWidth = bar.Width / _items.Length;
        return Math.Clamp((x - bar.Left) / itemWidth, 0, _items.Length - 1);
    }

    private Rectangle BarBounds()
    {
        return new Rectangle(0, 4, Width - 1, Height - 9);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        var bar = BarBounds();
        using (var path = UiGraphics.RoundedRect(bar, bar.Height / 2))
        using (var fill = new SolidBrush(Color.White))
        using (var border = new Pen(AppPalette.CardBorder))
        {
            e.Graphics.FillPath(fill, path);
            e.Graphics.DrawPath(border, path);
        }

        if (_items.Length == 0)
        {
            return;
        }

        var itemWidth = bar.Width / _items.Length;
        for (var index = 0; index < _items.Length; index++)
        {
            var itemBounds = new Rectangle(bar.Left + index * itemWidth, bar.Top, itemWidth, bar.Height);
            var selected = index == _selectedIndex;
            using var font = AppFonts.Create(11F, selected ? FontStyle.Bold : FontStyle.Regular, GraphicsUnit.Point);
            TextRenderer.DrawText(
                e.Graphics,
                _items[index],
                font,
                itemBounds,
                selected ? AppPalette.Primary : AppPalette.TextSecondary,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
        }
    }
}
