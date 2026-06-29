using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.App.UI;

public sealed class MainForm : Form
{
    private static readonly Color PageBackground = Color.FromArgb(245, 248, 247);
    private static readonly Color CardBackground = Color.White;
    private static readonly Color SoftGreen = Color.FromArgb(239, 250, 246);
    private static readonly Color AccentGreen = Color.FromArgb(22, 166, 125);
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);
    private static readonly Color TextSecondary = Color.FromArgb(102, 112, 133);
    private static readonly Color BorderColor = Color.FromArgb(229, 234, 231);

    private readonly TrackingController _controller;
    private readonly StartupManager _startupManager;
    private readonly Label _todayValue;
    private readonly Label _yesterdayValue;
    private readonly Label _statusValue;
    private readonly StatusDot _statusDot;
    private readonly Label _weekValue;
    private readonly Label _monthValue;
    private readonly NumericUpDown _idleThresholdMinutes;
    private readonly CheckBox _countAudio;
    private readonly CheckBox _startWithWindows;
    private DateOnly? _displayResetDate;
    private long _todayDisplayBaseline;
    private long _yesterdayDisplayBaseline;
    private long _weekDisplayBaseline;
    private long _monthDisplayBaseline;
    private bool _loadingSettings;
    private bool _closingForExit;

    public MainForm(TrackingController controller, StartupManager startupManager, Icon appIcon)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        _startupManager = startupManager ?? throw new ArgumentNullException(nameof(startupManager));

        AutoScaleMode = AutoScaleMode.Dpi;
        Text = "\u7528\u773c\u65f6\u95f4\u8bb0\u5f55";
        Icon = (Icon)(appIcon ?? throw new ArgumentNullException(nameof(appIcon))).Clone();
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = true;
        ClientSize = new Size(430, 512);
        BackColor = PageBackground;
        Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point);

        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            Padding = new Padding(22, 20, 22, 18),
            ColumnCount = 1,
            RowCount = 5
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 58));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 132));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 100));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 158));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));

        var todayCard = BuildTodayCard(out var todayValue);
        var metricsGrid = BuildMetricsGrid(out var yesterdayValue, out var weekValue, out var monthValue);
        var settingsCard = BuildSettingsCard(out var idleThresholdMinutes, out var countAudio, out var startWithWindows);
        var footer = BuildFooter(out var statusDot, out var statusValue);

        _todayValue = todayValue;
        _yesterdayValue = yesterdayValue;
        _weekValue = weekValue;
        _monthValue = monthValue;
        _idleThresholdMinutes = idleThresholdMinutes;
        _countAudio = countAudio;
        _startWithWindows = startWithWindows;
        _statusDot = statusDot;
        _statusValue = statusValue;

        root.Controls.Add(BuildHeader(), 0, 0);
        root.Controls.Add(todayCard, 0, 1);
        root.Controls.Add(metricsGrid, 0, 2);
        root.Controls.Add(settingsCard, 0, 3);
        root.Controls.Add(footer, 0, 4);
        Controls.Add(root);

        _idleThresholdMinutes.ValueChanged += (_, _) => SaveSettingsFromControls();
        _countAudio.CheckedChanged += (_, _) => SaveSettingsFromControls();
        _startWithWindows.CheckedChanged += (_, _) => SaveSettingsFromControls();
        _controller.Updated += OnTrackingUpdated;

        LoadSettings();
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

    private static Control BuildHeader()
    {
        var header = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            ColumnCount = 1,
            RowCount = 2,
            Margin = Padding.Empty
        };
        header.RowStyles.Add(new RowStyle(SizeType.Absolute, 30));
        header.RowStyles.Add(new RowStyle(SizeType.Absolute, 22));

        header.Controls.Add(new Label
        {
            Text = "\u7528\u773c\u65f6\u95f4",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 18F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            TextAlign = ContentAlignment.MiddleLeft
        }, 0, 0);
        header.Controls.Add(new Label
        {
            Text = "\u952e\u9f20\u6d3b\u52a8\u6216\u97f3\u9891\u64ad\u653e\u65f6\u8ba1\u65f6",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            TextAlign = ContentAlignment.MiddleLeft
        }, 0, 1);
        return header;
    }

    private static Control BuildTodayCard(out Label todayValue)
    {
        var card = new RoundedPanel
        {
            Dock = DockStyle.Fill,
            FillColor = SoftGreen,
            BorderColor = Color.FromArgb(213, 241, 231),
            Radius = 22,
            Margin = new Padding(0, 4, 0, 12),
            Padding = new Padding(18, 15, 18, 15)
        };

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.Transparent,
            ColumnCount = 1,
            RowCount = 3
        };
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 24));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 24));

        layout.Controls.Add(new Label
        {
            Text = "\u4eca\u5929",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            TextAlign = ContentAlignment.MiddleLeft
        }, 0, 0);

        todayValue = new Label
        {
            Text = "0\u5206\u949f",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 26F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = AccentGreen,
            TextAlign = ContentAlignment.MiddleLeft,
            AutoEllipsis = true
        };
        layout.Controls.Add(todayValue, 0, 1);
        layout.Controls.Add(new Label
        {
            Text = "\u63a5\u8fd1 5\u5c0f\u65f630\u5206\u65f6\u63d0\u9192",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            TextAlign = ContentAlignment.MiddleLeft
        }, 0, 2);

        card.Controls.Add(layout);
        return card;
    }

    private static Control BuildMetricsGrid(out Label yesterdayValue, out Label weekValue, out Label monthValue)
    {
        var grid = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            ColumnCount = 3,
            RowCount = 1,
            Margin = new Padding(0, 0, 0, 12)
        };
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 33.33F));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 33.33F));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 33.34F));

        grid.Controls.Add(BuildMetricCard("\u6628\u5929", out yesterdayValue), 0, 0);
        grid.Controls.Add(BuildMetricCard("\u672c\u5468", out weekValue), 1, 0);
        grid.Controls.Add(BuildMetricCard("\u672c\u6708", out monthValue), 2, 0);
        return grid;
    }

    private static Control BuildMetricCard(string title, out Label value)
    {
        var card = new RoundedPanel
        {
            Dock = DockStyle.Fill,
            FillColor = CardBackground,
            BorderColor = BorderColor,
            Radius = 18,
            Margin = new Padding(0, 0, 9, 0),
            Padding = new Padding(12, 10, 12, 10)
        };

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.Transparent,
            ColumnCount = 1,
            RowCount = 2
        };
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 24));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.Controls.Add(new Label
        {
            Text = title,
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            TextAlign = ContentAlignment.MiddleLeft
        }, 0, 0);

        value = new Label
        {
            Text = "0\u5206\u949f",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            TextAlign = ContentAlignment.MiddleLeft,
            AutoEllipsis = true
        };
        layout.Controls.Add(value, 0, 1);
        card.Controls.Add(layout);
        return card;
    }

    private Control BuildSettingsCard(out NumericUpDown idleThresholdMinutes, out CheckBox countAudio, out CheckBox startWithWindows)
    {
        var card = new RoundedPanel
        {
            Dock = DockStyle.Fill,
            FillColor = CardBackground,
            BorderColor = BorderColor,
            Radius = 22,
            Margin = new Padding(0, 0, 0, 12),
            Padding = new Padding(16, 14, 16, 14)
        };

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.Transparent,
            ColumnCount = 2,
            RowCount = 4
        };
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 88));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 32));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 32));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));

        layout.Controls.Add(CreateSmallLabel("\u952e\u9f20\u7a7a\u95f2\u9608\u503c\uff08\u5206\u949f\uff09"), 0, 0);
        idleThresholdMinutes = new NumericUpDown
        {
            Dock = DockStyle.Fill,
            Minimum = 1,
            Maximum = 120,
            TextAlign = HorizontalAlignment.Right,
            BorderStyle = BorderStyle.FixedSingle,
            BackColor = Color.White,
            ForeColor = TextPrimary,
            Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Regular, GraphicsUnit.Point)
        };
        layout.Controls.Add(idleThresholdMinutes, 1, 0);

        countAudio = CreateCheckBox("\u97f3\u9891\u8ba1\u65f6");
        layout.Controls.Add(countAudio, 0, 1);
        layout.SetColumnSpan(countAudio, 2);

        startWithWindows = CreateCheckBox("\u5f00\u673a\u81ea\u542f");
        layout.Controls.Add(startWithWindows, 0, 2);
        layout.SetColumnSpan(startWithWindows, 2);

        var buttons = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.RightToLeft,
            BackColor = Color.Transparent,
            Padding = new Padding(0, 8, 0, 0),
            Margin = Padding.Empty
        };
        var resetDisplay = new RoundedButton
        {
            Text = "\u91cd\u7f6e\u663e\u793a",
            Width = 112,
            Height = 34,
            ButtonColor = Color.FromArgb(239, 242, 244),
            HoverColor = Color.FromArgb(229, 235, 238),
            PressedColor = Color.FromArgb(218, 226, 229),
            TextColor = TextPrimary
        };
        resetDisplay.Click += (_, _) => ResetDisplayedStatistics();
        buttons.Controls.Add(resetDisplay);
        layout.Controls.Add(buttons, 0, 3);
        layout.SetColumnSpan(buttons, 2);

        card.Controls.Add(layout);
        return card;
    }

    private static Control BuildFooter(out StatusDot statusDot, out Label statusValue)
    {
        var footer = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground,
            ColumnCount = 3,
            RowCount = 1,
            Margin = Padding.Empty
        };
        footer.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 18));
        footer.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        footer.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 0));

        statusDot = new StatusDot
        {
            Dock = DockStyle.Fill,
            Margin = new Padding(0, 7, 8, 0)
        };
        footer.Controls.Add(statusDot, 0, 0);

        statusValue = new Label
        {
            Text = "",
            Dock = DockStyle.Fill,
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            TextAlign = ContentAlignment.MiddleLeft
        };
        footer.Controls.Add(statusValue, 1, 0);
        return footer;
    }

    private static Label CreateSmallLabel(string text)
    {
        return new Label
        {
            Text = text,
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            ForeColor = TextSecondary,
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point),
            AutoEllipsis = true
        };
    }

    private static CheckBox CreateCheckBox(string text)
    {
        return new CheckBox
        {
            Text = text,
            Dock = DockStyle.Fill,
            AutoSize = true,
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            Font = new Font("Microsoft YaHei UI", 9.5F, FontStyle.Regular, GraphicsUnit.Point),
            FlatStyle = FlatStyle.System
        };
    }

    private void LoadSettings()
    {
        _loadingSettings = true;
        try
        {
            var settings = _controller.Settings;
            _idleThresholdMinutes.Value = Math.Clamp(settings.IdleThresholdSeconds / 60, 1, 120);
            _countAudio.Checked = settings.CountAudio;
            _startWithWindows.Checked = settings.StartWithWindows;
        }
        finally
        {
            _loadingSettings = false;
        }
    }

    private void SaveSettingsFromControls()
    {
        if (_loadingSettings)
        {
            return;
        }

        var settings = _controller.Settings with
        {
            IdleThresholdSeconds = (int)_idleThresholdMinutes.Value * 60,
            CountAudio = _countAudio.Checked,
            StartWithWindows = _startWithWindows.Checked
        };

        _controller.Settings = settings;
        _controller.SaveNow();
        ApplyStartupSetting(settings.StartWithWindows);
        UpdateSummary(_controller.Current);
    }

    private void ApplyStartupSetting(bool enabled)
    {
        try
        {
            _startupManager.SetEnabled(enabled);
        }
        catch (Exception)
        {
        }
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
            ? string.Format("{0}\u5c0f\u65f6 {1:00}\u5206\u949f", totalHours, duration.Minutes)
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
        public Color FillColor { get; set; } = CardBackground;

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
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Bold, GraphicsUnit.Point);
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
            var size = Math.Min(10, Math.Min(Width, Height));
            var x = (Width - size) / 2;
            var y = (Height - size) / 2;
            using var brush = new SolidBrush(IsActive ? AccentGreen : Color.FromArgb(152, 162, 179));
            e.Graphics.FillEllipse(brush, x, y, size, size);
        }
    }
}
