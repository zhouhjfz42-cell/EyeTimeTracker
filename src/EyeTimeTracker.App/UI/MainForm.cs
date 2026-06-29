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
    private readonly Label _todayValue;
    private readonly Label _yesterdayValue;
    private readonly Label _weekValue;
    private readonly Label _monthValue;
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

        AutoScaleMode = AutoScaleMode.Dpi;
        Text = "\u7528\u773c\u65f6\u95f4\u8bb0\u5f55";
        Icon = (Icon)(appIcon ?? throw new ArgumentNullException(nameof(appIcon))).Clone();
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = true;
        ClientSize = new Size(540, 650);
        BackColor = PageBackground;
        Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point);

        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            Padding = new Padding(28, 28, 28, 24),
            ColumnCount = 1,
            RowCount = 5
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 112));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 128));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 246));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 86));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));

        var header = BuildHeader(out var statusDot, out var statusValue);
        var today = BuildTodayBlock(out var todayValue);
        var metrics = BuildMetricsGrid(out var yesterdayValue, out var weekValue, out var monthValue);
        var actions = BuildActions();

        _todayValue = todayValue;
        _yesterdayValue = yesterdayValue;
        _weekValue = weekValue;
        _monthValue = monthValue;
        _statusDot = statusDot;
        _statusValue = statusValue;

        root.Controls.Add(header, 0, 0);
        root.Controls.Add(today, 0, 1);
        root.Controls.Add(metrics, 0, 2);
        root.Controls.Add(actions, 0, 3);
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

    private static Control BuildHeader(out StatusDot statusDot, out Label statusValue)
    {
        var header = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            ColumnCount = 2,
            RowCount = 2,
            Margin = Padding.Empty
        };
        header.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        header.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 76));
        header.RowStyles.Add(new RowStyle(SizeType.Absolute, 64));
        header.RowStyles.Add(new RowStyle(SizeType.Absolute, 34));

        var titleLine = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            Margin = Padding.Empty,
            Padding = new Padding(0, 16, 0, 0)
        };
        titleLine.Controls.Add(new Label
        {
            Text = "\u7528\u773c\u65f6\u95f4",
            AutoSize = true,
            Font = new Font("Microsoft YaHei UI", 22F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            Margin = new Padding(0, 0, 14, 0)
        });

        var statusPill = new RoundedPanel
        {
            Width = 96,
            Height = 32,
            FillColor = Color.White,
            BorderColor = BorderColor,
            Radius = 16,
            Margin = new Padding(0, 11, 0, 0),
            Padding = new Padding(10, 0, 10, 0)
        };
        var statusLayout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.Transparent,
            ColumnCount = 2,
            RowCount = 1,
            Margin = Padding.Empty
        };
        statusLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 18));
        statusLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));

        statusDot = new StatusDot
        {
            Dock = DockStyle.Fill,
            Margin = new Padding(0, 0, 6, 0)
        };
        statusValue = new Label
        {
            Text = "\u7edf\u8ba1\u4e2d",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            TextAlign = ContentAlignment.MiddleLeft
        };
        statusLayout.Controls.Add(statusDot, 0, 0);
        statusLayout.Controls.Add(statusValue, 1, 0);
        statusPill.Controls.Add(statusLayout);
        titleLine.Controls.Add(statusPill);

        var avatar = new AvatarPlaceholder
        {
            Dock = DockStyle.Fill,
            Margin = new Padding(10, 10, 0, 8)
        };

        var subtitle = new Label
        {
            Text = "\u4eae\u5c4f + \u52a8\u4f5c\u6216\u5a92\u4f53\u64ad\u653e\u65f6\u8ba1\u65f6",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            TextAlign = ContentAlignment.TopLeft,
            AutoEllipsis = false
        };

        header.Controls.Add(titleLine, 0, 0);
        header.Controls.Add(avatar, 1, 0);
        header.SetRowSpan(avatar, 2);
        header.Controls.Add(subtitle, 0, 1);
        return header;
    }

    private static Control BuildTodayBlock(out Label todayValue)
    {
        var block = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            ColumnCount = 1,
            RowCount = 2,
            Margin = Padding.Empty
        };
        block.RowStyles.Add(new RowStyle(SizeType.Absolute, 34));
        block.RowStyles.Add(new RowStyle(SizeType.Absolute, 86));

        block.Controls.Add(new Label
        {
            Text = "\u4eca\u5929",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 14F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            TextAlign = ContentAlignment.BottomLeft
        }, 0, 0);

        todayValue = new Label
        {
            Text = "0\u5206\u949f",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 40F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = AccentGreen,
            TextAlign = ContentAlignment.BottomLeft,
            AutoEllipsis = false
        };
        block.Controls.Add(todayValue, 0, 1);
        return block;
    }

    private static Control BuildMetricsGrid(out Label yesterdayValue, out Label weekValue, out Label monthValue)
    {
        var grid = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            ColumnCount = 2,
            RowCount = 2,
            Margin = Padding.Empty
        };
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
        grid.RowStyles.Add(new RowStyle(SizeType.Percent, 50));
        grid.RowStyles.Add(new RowStyle(SizeType.Percent, 50));

        grid.Controls.Add(BuildMetricCard("\u6628\u5929", out yesterdayValue), 0, 0);
        grid.Controls.Add(BuildMetricCard("\u672c\u5468", out weekValue), 1, 0);
        grid.Controls.Add(BuildMetricCard("\u672c\u6708", out monthValue), 0, 1);
        grid.Controls.Add(BuildStaticMetricCard("\u63d0\u9192", "5\u5c0f\u65f630\u5206"), 1, 1);
        return grid;
    }

    private static Control BuildMetricCard(string title, out Label value)
    {
        var card = CreateMetricShell();
        var layout = CreateMetricLayout(title);
        value = new Label
        {
            Text = "0\u5206\u949f",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 20F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            TextAlign = ContentAlignment.MiddleLeft,
            AutoEllipsis = false
        };
        layout.Controls.Add(value, 0, 1);
        card.Controls.Add(layout);
        return card;
    }

    private static Control BuildStaticMetricCard(string title, string value)
    {
        var card = CreateMetricShell();
        var layout = CreateMetricLayout(title);
        layout.Controls.Add(new Label
        {
            Text = value,
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 20F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            TextAlign = ContentAlignment.MiddleLeft,
            AutoEllipsis = false
        }, 0, 1);
        card.Controls.Add(layout);
        return card;
    }

    private static RoundedPanel CreateMetricShell()
    {
        return new RoundedPanel
        {
            Dock = DockStyle.Fill,
            FillColor = SoftGreen,
            BorderColor = Color.FromArgb(226, 245, 238),
            Radius = 24,
            Margin = new Padding(0, 0, 16, 16),
            Padding = new Padding(18, 14, 18, 12)
        };
    }

    private static TableLayoutPanel CreateMetricLayout(string title)
    {
        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.Transparent,
            ColumnCount = 1,
            RowCount = 2
        };
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 34));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.Controls.Add(new Label
        {
            Text = title,
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            TextAlign = ContentAlignment.MiddleLeft
        }, 0, 0);
        return layout;
    }

    private Control BuildActions()
    {
        var actions = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            ColumnCount = 3,
            RowCount = 1,
            Margin = Padding.Empty,
            Padding = new Padding(0, 14, 0, 0)
        };
        actions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 25));
        actions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
        actions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 25));

        var resetDisplay = new RoundedButton
        {
            Text = "\u91cd\u7f6e\u663e\u793a",
            Dock = DockStyle.Fill,
            Height = 52,
            ButtonColor = AccentGreen,
            HoverColor = Color.FromArgb(19, 145, 111),
            PressedColor = Color.FromArgb(17, 124, 96),
            TextColor = Color.White,
            Font = new Font("Microsoft YaHei UI", 13F, FontStyle.Bold, GraphicsUnit.Point)
        };
        resetDisplay.Click += (_, _) => ResetDisplayedStatistics();
        actions.Controls.Add(resetDisplay, 1, 0);
        return actions;
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

    private sealed class RoundedButton : Button
    {
        private bool _hovered;
        private bool _pressed;

        public Color ButtonColor { get; set; } = AccentGreen;

        public Color HoverColor { get; set; } = Color.FromArgb(19, 145, 111);

        public Color PressedColor { get; set; } = Color.FromArgb(17, 124, 96);

        public Color TextColor { get; set; } = Color.White;

        public RoundedButton()
        {
            FlatStyle = FlatStyle.Flat;
            FlatAppearance.BorderSize = 0;
            UseVisualStyleBackColor = false;
            Cursor = Cursors.Hand;
            Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold, GraphicsUnit.Point);
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        }

        protected override void OnMouseEnter(EventArgs e)
        {
            _hovered = true;
            Invalidate();
            base.OnMouseEnter(e);
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            _hovered = false;
            _pressed = false;
            Invalidate();
            base.OnMouseLeave(e);
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            _pressed = true;
            Invalidate();
            base.OnMouseDown(e);
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            _pressed = false;
            Invalidate();
            base.OnMouseUp(e);
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

    private sealed class AvatarPlaceholder : Control
    {
        public AvatarPlaceholder()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var size = Math.Min(58, Math.Min(Width, Height));
            var x = (Width - size) / 2;
            var y = (Height - size) / 2;
            using var fill = new SolidBrush(Color.White);
            using var pen = new Pen(Color.FromArgb(255, 77, 86), 5);
            e.Graphics.FillEllipse(fill, x, y, size, size);
            e.Graphics.DrawEllipse(pen, x + 2, y + 2, size - 4, size - 4);
        }
    }
}
