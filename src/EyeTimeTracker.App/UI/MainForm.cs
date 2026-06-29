using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.App.UI;

public sealed class MainForm : Form
{
    private static readonly Color PageBackground = Color.FromArgb(248, 251, 250);
    private static readonly Color SoftGreen = Color.FromArgb(238, 249, 245);
    private static readonly Color AccentGreen = Color.FromArgb(22, 166, 125);
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);
    private static readonly Color TextSecondary = Color.FromArgb(102, 112, 133);
    private static readonly Color BorderColor = Color.FromArgb(225, 232, 229);

    private readonly TrackingController _controller;
    private readonly FitTextLabel _todayValue;
    private readonly FitTextLabel _yesterdayValue;
    private readonly FitTextLabel _weekValue;
    private readonly FitTextLabel _monthValue;
    private readonly Label _statusValue;
    private readonly StatusDot _statusDot;
    private DateOnly? _displayResetDate;
    private long _todayDisplayBaseline;
    private long _yesterdayDisplayBaseline;
    private long _weekDisplayBaseline;
    private long _monthDisplayBaseline;
    private bool _closingForExit;

    public MainForm(TrackingController controller, StartupManager startupManager, Icon appIcon)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        _ = startupManager ?? throw new ArgumentNullException(nameof(startupManager));

        AutoScaleMode = AutoScaleMode.None;
        Text = "\u7528\u773c\u65f6\u95f4\u8bb0\u5f55";
        Icon = (Icon)(appIcon ?? throw new ArgumentNullException(nameof(appIcon))).Clone();
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = true;
        ClientSize = new Size(660, 760);
        BackColor = PageBackground;
        Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point);

        var root = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground
        };

        BuildDashboard(root, out var todayValue, out var yesterdayValue, out var weekValue, out var monthValue, out var statusDot, out var statusValue);

        _todayValue = todayValue;
        _yesterdayValue = yesterdayValue;
        _weekValue = weekValue;
        _monthValue = monthValue;
        _statusDot = statusDot;
        _statusValue = statusValue;

        Controls.Add(root);

        _controller.Updated += OnTrackingUpdated;
        UpdateSummary(_controller.Current);
    }

    public void CloseForExit()
    {
        _closingForExit = true;
        Close();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (!_closingForExit && e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            Hide();
            return;
        }

        base.OnFormClosing(e);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _controller.Updated -= OnTrackingUpdated;
        }

        base.Dispose(disposing);
    }

    private void BuildDashboard(
        Control root,
        out FitTextLabel todayValue,
        out FitTextLabel yesterdayValue,
        out FitTextLabel weekValue,
        out FitTextLabel monthValue,
        out StatusDot statusDot,
        out Label statusValue)
    {
        root.Controls.Add(new Label
        {
            Text = "\u7528\u773c\u65f6\u95f4",
            Bounds = new Rectangle(34, 38, 250, 58),
            Font = new Font("Microsoft YaHei UI", 22F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        statusDot = new StatusDot
        {
            Bounds = new Rectangle(296, 58, 18, 18)
        };
        root.Controls.Add(statusDot);

        statusValue = new Label
        {
            Text = "\u7edf\u8ba1\u4e2d",
            Bounds = new Rectangle(318, 50, 130, 34),
            Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        root.Controls.Add(statusValue);

        root.Controls.Add(new Label
        {
            Text = "\u4eae\u5c4f + \u52a8\u4f5c\u6216\u5a92\u4f53\u64ad\u653e\u65f6\u8ba1\u65f6",
            Bounds = new Rectangle(34, 96, 570, 34),
            Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        root.Controls.Add(new Label
        {
            Text = "\u4eca\u5929",
            Bounds = new Rectangle(34, 148, 160, 46),
            Font = new Font("Microsoft YaHei UI", 14F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        todayValue = new FitTextLabel
        {
            Text = "0\u5206\u949f",
            Bounds = new Rectangle(34, 194, 600, 104),
            MaxFontSize = 44F,
            MinFontSize = 24F,
            FontStyle = FontStyle.Bold,
            ForeColor = AccentGreen,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        root.Controls.Add(todayValue);

        root.Controls.Add(BuildMetricCard("\u6628\u5929", new Rectangle(34, 320, 282, 136), out yesterdayValue));
        root.Controls.Add(BuildMetricCard("\u672c\u5468", new Rectangle(344, 320, 282, 136), out weekValue));
        root.Controls.Add(BuildMetricCard("\u672c\u6708", new Rectangle(34, 484, 282, 136), out monthValue));
        root.Controls.Add(BuildStaticMetricCard("\u63d0\u9192", "5\u5c0f\u65f630\u5206", new Rectangle(344, 484, 282, 136)));

        var resetDisplay = new RoundedButton
        {
            Text = "\u91cd\u7f6e\u663e\u793a",
            Bounds = new Rectangle(200, 668, 260, 58),
            ButtonColor = AccentGreen,
            HoverColor = Color.FromArgb(19, 145, 111),
            PressedColor = Color.FromArgb(17, 124, 96),
            TextColor = Color.White,
            Font = new Font("Microsoft YaHei UI", 13F, FontStyle.Bold, GraphicsUnit.Point)
        };
        resetDisplay.Click += (_, _) => ResetDisplayedStatistics();
        root.Controls.Add(resetDisplay);
    }

    private static Control BuildMetricCard(string title, Rectangle bounds, out FitTextLabel value)
    {
        var card = CreateMetricShell(bounds);
        AddMetricTitle(card, title);
        value = new FitTextLabel
        {
            Text = "0\u5206\u949f",
            Bounds = new Rectangle(24, 58, bounds.Width - 40, 66),
            MaxFontSize = 22F,
            MinFontSize = 14F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            TextAlign = ContentAlignment.MiddleLeft,
            BackColor = Color.Transparent
        };
        card.Controls.Add(value);
        return card;
    }

    private static Control BuildStaticMetricCard(string title, string value, Rectangle bounds)
    {
        var card = CreateMetricShell(bounds);
        AddMetricTitle(card, title);
        card.Controls.Add(new FitTextLabel
        {
            Text = value,
            Bounds = new Rectangle(24, 58, bounds.Width - 40, 66),
            MaxFontSize = 22F,
            MinFontSize = 14F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            TextAlign = ContentAlignment.MiddleLeft,
            BackColor = Color.Transparent
        });
        return card;
    }

    private static RoundedPanel CreateMetricShell(Rectangle bounds)
    {
        return new RoundedPanel
        {
            Bounds = bounds,
            FillColor = SoftGreen,
            BorderColor = Color.FromArgb(226, 245, 238),
            Radius = 24,
            Padding = new Padding(20, 16, 20, 16)
        };
    }

    private static void AddMetricTitle(Control card, string title)
    {
        card.Controls.Add(new Label
        {
            Text = title,
            Bounds = new Rectangle(24, 20, 180, 34),
            Font = new Font("Microsoft YaHei UI", 12F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });
    }

    private void OnTrackingUpdated(object? sender, TrackingUpdatedEventArgs e)
    {
        if (IsDisposed || !IsHandleCreated)
        {
            return;
        }

        try
        {
            if (InvokeRequired)
            {
                BeginInvoke(() => UpdateSummary(e));
            }
            else
            {
                UpdateSummary(e);
            }
        }
        catch (InvalidOperationException)
        {
        }
    }

    private void UpdateSummary(TrackingUpdatedEventArgs current)
    {
        var records = _controller.GetRecordsSnapshot();
        var today = current.Date;
        var yesterday = today.AddDays(-1);
        var weekStart = today.AddDays(-GetMondayOffset(today.DayOfWeek));
        var monthStart = new DateOnly(today.Year, today.Month, 1);
        var todayTotal = current.TotalSeconds;
        var yesterdayTotal = RecordSeconds(records, yesterday);
        var weekTotal = SumRecords(records, weekStart, today);
        var monthTotal = SumRecords(records, monthStart, today);

        if (_displayResetDate is not null && _displayResetDate != today)
        {
            ClearDisplayReset();
        }

        if (_displayResetDate == today)
        {
            todayTotal -= _todayDisplayBaseline;
            yesterdayTotal -= _yesterdayDisplayBaseline;
            weekTotal -= _weekDisplayBaseline;
            monthTotal -= _monthDisplayBaseline;
        }

        _todayValue.Text = FormatDuration(todayTotal);
        _yesterdayValue.Text = FormatDuration(yesterdayTotal);
        _weekValue.Text = FormatDuration(weekTotal);
        _monthValue.Text = FormatDuration(monthTotal);
        _statusValue.Text = current.IsCounting ? "\u7edf\u8ba1\u4e2d" : "\u6682\u505c";
        _statusDot.IsActive = current.IsCounting;
    }

    private void ResetDisplayedStatistics()
    {
        var current = _controller.Current;
        var records = _controller.GetRecordsSnapshot();
        var today = current.Date;
        var yesterday = today.AddDays(-1);
        var weekStart = today.AddDays(-GetMondayOffset(today.DayOfWeek));
        var monthStart = new DateOnly(today.Year, today.Month, 1);

        _displayResetDate = today;
        _todayDisplayBaseline = current.TotalSeconds;
        _yesterdayDisplayBaseline = RecordSeconds(records, yesterday);
        _weekDisplayBaseline = SumRecords(records, weekStart, today);
        _monthDisplayBaseline = SumRecords(records, monthStart, today);

        UpdateSummary(current);
    }

    private void ClearDisplayReset()
    {
        _displayResetDate = null;
        _todayDisplayBaseline = 0;
        _yesterdayDisplayBaseline = 0;
        _weekDisplayBaseline = 0;
        _monthDisplayBaseline = 0;
    }

    private static int GetMondayOffset(DayOfWeek dayOfWeek)
    {
        return dayOfWeek == DayOfWeek.Sunday ? 6 : (int)dayOfWeek - (int)DayOfWeek.Monday;
    }

    private static long SumRecords(IEnumerable<DailyRecord> records, DateOnly start, DateOnly end)
    {
        return records
            .Where(record => record.Date >= start && record.Date <= end)
            .Sum(record => record.TotalSeconds);
    }

    private static long RecordSeconds(IEnumerable<DailyRecord> records, DateOnly date)
    {
        return records.FirstOrDefault(record => record.Date == date)?.TotalSeconds ?? 0;
    }

    private static string FormatDuration(long totalSeconds)
    {
        var duration = TimeSpan.FromSeconds(Math.Max(0, totalSeconds));
        var totalHours = (int)duration.TotalHours;
        return totalHours > 0
            ? string.Format("{0}\u5c0f\u65f6 {1:00}\u5206", totalHours, duration.Minutes)
            : string.Format("{0}\u5206\u949f", duration.Minutes);
    }

    private static GraphicsPath CreateRoundRect(Rectangle bounds, int radius)
    {
        var path = new GraphicsPath();
        var diameter = Math.Max(1, radius * 2);
        var arc = new Rectangle(bounds.Location, new Size(diameter, diameter));

        path.AddArc(arc, 180, 90);
        arc.X = bounds.Right - diameter;
        path.AddArc(arc, 270, 90);
        arc.Y = bounds.Bottom - diameter;
        path.AddArc(arc, 0, 90);
        arc.X = bounds.Left;
        path.AddArc(arc, 90, 90);
        path.CloseFigure();
        return path;
    }

    private sealed class RoundedPanel : Panel
    {
        public Color FillColor { get; set; } = Color.White;

        public Color BorderColor { get; set; } = MainForm.BorderColor;

        public int Radius { get; set; } = 18;

        public RoundedPanel()
        {
            DoubleBuffered = true;
            BackColor = Color.Transparent;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using var path = CreateRoundRect(bounds, Radius);
            using var fill = new SolidBrush(FillColor);
            using var border = new Pen(BorderColor);
            e.Graphics.FillPath(fill, path);
            e.Graphics.DrawPath(border, path);
            base.OnPaint(e);
        }
    }

    private sealed class RoundedButton : Control
    {
        private bool _hovered;
        private bool _pressed;

        public Color ButtonColor { get; set; } = AccentGreen;

        public Color HoverColor { get; set; } = Color.FromArgb(19, 145, 111);

        public Color PressedColor { get; set; } = Color.FromArgb(17, 124, 96);

        public Color TextColor { get; set; } = Color.White;

        public RoundedButton()
        {
            Cursor = Cursors.Hand;
            Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold, GraphicsUnit.Point);
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        }

        protected override void OnMouseEnter(EventArgs e)
        {
            _hovered = true;
            Invalidate();
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            _hovered = false;
            _pressed = false;
            Invalidate();
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            _pressed = true;
            Invalidate();
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            _pressed = false;
            Invalidate();
            if (ClientRectangle.Contains(e.Location))
            {
                OnClick(EventArgs.Empty);
            }
        }

        protected override void OnPaint(PaintEventArgs pevent)
        {
            pevent.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            var color = _pressed ? PressedColor : _hovered ? HoverColor : ButtonColor;
            using var path = CreateRoundRect(bounds, Height / 2);
            using var brush = new SolidBrush(Enabled ? color : Color.FromArgb(230, 234, 238));
            pevent.Graphics.FillPath(brush, path);

            TextRenderer.DrawText(
                pevent.Graphics,
                Text,
                Font,
                bounds,
                Enabled ? TextColor : TextSecondary,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        }
    }

    private sealed class StatusDot : Control
    {
        private bool _isActive;

        public bool IsActive
        {
            get => _isActive;
            set
            {
                if (_isActive == value)
                {
                    return;
                }

                _isActive = value;
                Invalidate();
            }
        }

        public StatusDot()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var size = Math.Min(12, Math.Min(Width, Height));
            var x = (Width - size) / 2;
            var y = (Height - size) / 2;
            using var brush = new SolidBrush(IsActive ? AccentGreen : Color.FromArgb(152, 162, 179));
            e.Graphics.FillEllipse(brush, x, y, size, size);
        }
    }

    private sealed class FitTextLabel : Control
    {
        public float MaxFontSize { get; set; } = 20F;

        public float MinFontSize { get; set; } = 10F;

        public FontStyle FontStyle { get; set; } = FontStyle.Regular;

        public ContentAlignment TextAlign { get; set; } = ContentAlignment.MiddleLeft;

        public FitTextLabel()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            SetStyle(ControlStyles.SupportsTransparentBackColor, true);
        }

        protected override void OnTextChanged(EventArgs e)
        {
            Invalidate();
            base.OnTextChanged(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            if (BackColor.A == 255)
            {
                e.Graphics.Clear(BackColor);
            }

            e.Graphics.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            var bounds = ClientRectangle;
            if (bounds.Width <= 0 || bounds.Height <= 0 || string.IsNullOrEmpty(Text))
            {
                return;
            }

            using var font = CreateFittingFont(e.Graphics, bounds);
            using var brush = new SolidBrush(ForeColor);
            using var format = CreateStringFormat();
            e.Graphics.DrawString(Text, font, brush, bounds, format);
        }

        private Font CreateFittingFont(Graphics graphics, Rectangle bounds)
        {
            for (var size = MaxFontSize; size >= MinFontSize; size -= 0.5F)
            {
                var font = new Font("Microsoft YaHei UI", size, FontStyle, GraphicsUnit.Point);
                var measured = graphics.MeasureString(Text, font, bounds.Width, StringFormat.GenericTypographic);
                if (measured.Width <= bounds.Width && measured.Height <= bounds.Height)
                {
                    return font;
                }

                font.Dispose();
            }

            return new Font("Microsoft YaHei UI", MinFontSize, FontStyle, GraphicsUnit.Point);
        }

        private StringFormat CreateStringFormat()
        {
            var format = (StringFormat)StringFormat.GenericTypographic.Clone();
            format.FormatFlags |= StringFormatFlags.NoClip;
            format.Trimming = StringTrimming.None;
            format.Alignment = TextAlign is ContentAlignment.TopRight or ContentAlignment.MiddleRight or ContentAlignment.BottomRight
                ? StringAlignment.Far
                : TextAlign is ContentAlignment.TopCenter or ContentAlignment.MiddleCenter or ContentAlignment.BottomCenter
                    ? StringAlignment.Center
                    : StringAlignment.Near;
            format.LineAlignment = TextAlign is ContentAlignment.BottomLeft or ContentAlignment.BottomCenter or ContentAlignment.BottomRight
                ? StringAlignment.Far
                : TextAlign is ContentAlignment.TopLeft or ContentAlignment.TopCenter or ContentAlignment.TopRight
                    ? StringAlignment.Near
                    : StringAlignment.Center;
            return format;
        }
    }

}
