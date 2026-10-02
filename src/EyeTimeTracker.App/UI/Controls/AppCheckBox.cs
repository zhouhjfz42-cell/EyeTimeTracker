namespace EyeTimeTracker.App.UI.Controls;

/// <summary>方形勾选框 + 文字（首次引导开机启动、免打扰启用等）。</summary>
public sealed class AppCheckBox : Control
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

    public AppCheckBox()
    {
        Cursor = Cursors.Hand;
        Font = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point);
        ForeColor = AppPalette.TextPrimary;
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

    protected override void OnMouseClick(MouseEventArgs e)
    {
        if (e.Button == MouseButtons.Left)
        {
            Checked = !Checked;
        }

        base.OnMouseClick(e);
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        if (e.KeyCode is Keys.Space or Keys.Enter)
        {
            Checked = !Checked;
            e.Handled = true;
        }

        base.OnKeyDown(e);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        var boxSize = 22;
        var box = new Rectangle(0, (Height - boxSize) / 2, boxSize - 1, boxSize - 1);
        using (var path = UiGraphics.RoundedRect(box, 6))
        {
            if (Checked)
            {
                using var fill = new SolidBrush(AppPalette.Primary);
                e.Graphics.FillPath(fill, path);
            }
            else
            {
                using var fill = new SolidBrush(Color.White);
                e.Graphics.FillPath(fill, path);
            }

            using var pen = new Pen(Checked ? AppPalette.Primary : (_hovered ? AppPalette.Primary : AppPalette.CardBorder), 1.6F);
            e.Graphics.DrawPath(pen, path);
        }

        if (Checked)
        {
            using var checkPen = new Pen(Color.White, Math.Max(1.5F, 22 / 10F))
            {
                StartCap = System.Drawing.Drawing2D.LineCap.Round,
                EndCap = System.Drawing.Drawing2D.LineCap.Round
            };
            e.Graphics.DrawLines(checkPen, new[]
            {
                new Point(box.Left + 5, box.Top + box.Height / 2 + 1),
                new Point(box.Left + box.Width / 2 - 1, box.Bottom - 6),
                new Point(box.Right - 4, box.Top + 5)
            });
        }

        if (!string.IsNullOrEmpty(Text))
        {
            var textBounds = new Rectangle(box.Right + 10, 0, Width - box.Right - 10, Height);
            TextRenderer.DrawText(
                e.Graphics,
                Text,
                Font,
                textBounds,
                ForeColor,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.WordBreak | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
        }
    }

    /// <summary>给定宽度下完整显示（文字可换行）所需高度。</summary>
    public int PreferredHeight(int width)
    {
        var textWidth = Math.Max(10, width - 22 - 10);
        return Math.Max(24, UiText.WrappedHeight(Text, Font, textWidth) + 4);
    }
}
