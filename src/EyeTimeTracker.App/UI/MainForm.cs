using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;

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
    private readonly FitTextLabel _reminderValue;
    private readonly FitTextLabel _statusValue;
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

        BuildDashboard(root, out var todayValue, out var yesterdayValue, out var weekValue, out var monthValue, out var reminderValue, out var statusDot, out var statusValue);

        _todayValue = todayValue;
        _yesterdayValue = yesterdayValue;
        _weekValue = weekValue;
        _monthValue = monthValue;
        _reminderValue = reminderValue;
        _statusDot = statusDot;
        _statusValue = statusValue;

        Controls.Add(root);

        _controller.Updated += OnTrackingUpdated;
        UpdateReminderDisplay();
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
        out FitTextLabel reminderValue,
        out StatusDot statusDot,
        out FitTextLabel statusValue)
    {
        root.Controls.Add(new FitTextLabel
        {
            Text = "\u7528\u773c\u65f6\u95f4",
            Bounds = new Rectangle(34, 30, 270, 76),
            MaxFontSize = 22F,
            MinFontSize = 20F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        root.Controls.Add(new FitTextLabel
        {
            Text = "\u952e\u9f20\u52a8\u4f5c\u3001\u5a92\u4f53\u64ad\u653e\u65f6\u8ba1\u5165\u7edf\u8ba1",
            Bounds = new Rectangle(34, 96, 600, 42),
            MaxFontSize = 11F,
            MinFontSize = 10F,
            FontStyle = FontStyle.Regular,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        root.Controls.Add(new FitTextLabel
        {
            Text = "\u4eca\u5929",
            Bounds = new Rectangle(34, 148, 104, 46),
            MaxFontSize = 14F,
            MinFontSize = 14F,
            FontStyle = FontStyle.Regular,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        statusDot = new StatusDot
        {
            Bounds = new Rectangle(154, 163, 16, 16)
        };
        root.Controls.Add(statusDot);
        statusDot.BringToFront();

        statusValue = new FitTextLabel
        {
            Text = "\u7edf\u8ba1\u4e2d",
            Bounds = new Rectangle(178, 148, 160, 46),
            MaxFontSize = 14F,
            MinFontSize = 12F,
            FontStyle = FontStyle.Regular,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        root.Controls.Add(statusValue);
        statusValue.BringToFront();

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
        root.Controls.Add(BuildReminderCard(new Rectangle(344, 484, 282, 136), out reminderValue));

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

    private Control BuildReminderCard(Rectangle bounds, out FitTextLabel value)
    {
        var card = CreateMetricShell(bounds);
        AddMetricTitle(card, "\u63d0\u9192 (\u70b9\u51fb\u4fee\u6539)");
        value = new FitTextLabel
        {
            Text = ReminderThreshold.Format(_controller.Settings.ReminderThresholdSeconds),
            Bounds = new Rectangle(24, 58, bounds.Width - 40, 66),
            MaxFontSize = 22F,
            MinFontSize = 14F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            TextAlign = ContentAlignment.MiddleLeft,
            BackColor = Color.Transparent
        };
        card.Controls.Add(value);
        WireClick(card, (_, _) => ShowReminderDialog());
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
        card.Controls.Add(new FitTextLabel
        {
            Text = title,
            Bounds = new Rectangle(24, 20, 220, 36),
            MaxFontSize = 12F,
            MinFontSize = 11F,
            FontStyle = FontStyle.Regular,
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

    private void ShowReminderDialog()
    {
        using var dialog = new ReminderThresholdDialog(
            ReminderThreshold.ToMinutes(_controller.Settings.ReminderThresholdSeconds),
            Icon);

        if (dialog.ShowDialog(this) != DialogResult.OK)
        {
            return;
        }

        _controller.Settings = _controller.Settings with
        {
            ReminderThresholdSeconds = ReminderThreshold.FromMinutes(dialog.ReminderMinutes)
        };
        _controller.SaveNow();
        UpdateReminderDisplay();
    }

    private void UpdateReminderDisplay()
    {
        _reminderValue.Text = ReminderThreshold.Format(_controller.Settings.ReminderThresholdSeconds);
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

    private static void WireClick(Control control, EventHandler handler)
    {
        control.Cursor = Cursors.Hand;
        control.Click += handler;

        foreach (Control child in control.Controls)
        {
            WireClick(child, handler);
        }
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

    private sealed class CloseIconButton : Control
    {
        private bool _hovered;
        private bool _pressed;

        public CloseIconButton()
        {
            Cursor = Cursors.Hand;
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

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var fillColor = _pressed
                ? Color.FromArgb(220, 226, 232)
                : _hovered
                    ? Color.FromArgb(232, 236, 240)
                    : Color.FromArgb(242, 244, 247);

            using (var fill = new SolidBrush(fillColor))
            {
                e.Graphics.FillEllipse(fill, 0, 0, Width - 1, Height - 1);
            }

            using var pen = new Pen(TextSecondary, 2.2F)
            {
                StartCap = LineCap.Round,
                EndCap = LineCap.Round
            };
            var centerX = Width / 2F;
            var centerY = Height / 2F;
            const float half = 6.4F;
            e.Graphics.DrawLine(pen, centerX - half, centerY - half, centerX + half, centerY + half);
            e.Graphics.DrawLine(pen, centerX + half, centerY - half, centerX - half, centerY + half);
        }
    }

    private sealed class ReminderThresholdDialog : Form
    {
        private readonly TextBox _minutesInput;
        private readonly FitTextLabel _hintLabel;

        public int ReminderMinutes { get; private set; }

        public ReminderThresholdDialog(int currentMinutes, Icon? icon)
        {
            ReminderMinutes = Math.Clamp(currentMinutes, ReminderThreshold.MinMinutes, ReminderThreshold.MaxMinutes);
            AutoScaleMode = AutoScaleMode.None;
            Text = "\u4fee\u6539\u63d0\u9192\u65f6\u95f4";
            if (icon is not null)
            {
                Icon = (Icon)icon.Clone();
            }

            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.None;
            MaximizeBox = false;
            MinimizeBox = false;
            ShowInTaskbar = false;
            ClientSize = new Size(392, 282);
            BackColor = Color.White;
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point);
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);

            Controls.Add(new FitTextLabel
            {
                Text = "\u63d0\u9192\u65f6\u95f4",
                Bounds = new Rectangle(28, 20, 220, 48),
                MaxFontSize = 20F,
                MinFontSize = 18F,
                FontStyle = FontStyle.Bold,
                ForeColor = TextPrimary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleLeft
            });

            var closeButton = new CloseIconButton
            {
                Bounds = new Rectangle(332, 22, 38, 38)
            };
            closeButton.Click += (_, _) =>
            {
                DialogResult = DialogResult.Cancel;
                Close();
            };
            Controls.Add(closeButton);

            _hintLabel = new FitTextLabel
            {
                Bounds = new Rectangle(28, 74, 330, 34),
                MaxFontSize = 12F,
                MinFontSize = 10F,
                FontStyle = FontStyle.Regular,
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleLeft
            };
            Controls.Add(_hintLabel);

            var inputShell = new RoundedPanel
            {
                Bounds = new Rectangle(28, 114, 336, 80),
                FillColor = Color.FromArgb(249, 253, 251),
                BorderColor = Color.FromArgb(206, 226, 218),
                Radius = 18
            };

            _minutesInput = new TextBox
            {
                Text = ReminderMinutes.ToString(),
                Bounds = new Rectangle(22, 14, 160, 52),
                BorderStyle = BorderStyle.None,
                Font = new Font("Microsoft YaHei UI", 20F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = TextPrimary,
                BackColor = Color.FromArgb(249, 253, 251),
                MaxLength = 5,
                Multiline = true,
                WordWrap = false,
                ScrollBars = ScrollBars.None
            };
            _minutesInput.KeyPress += OnMinutesKeyPress;
            _minutesInput.TextChanged += (_, _) => UpdateHint();
            inputShell.Controls.Add(_minutesInput);

            inputShell.Controls.Add(new FitTextLabel
            {
                Text = "\u5206\u949f",
                Bounds = new Rectangle(184, 15, 92, 50),
                MaxFontSize = 15F,
                MinFontSize = 14F,
                FontStyle = FontStyle.Regular,
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleLeft
            });
            Controls.Add(inputShell);

            var cancelButton = new RoundedButton
            {
                Text = "\u53d6\u6d88",
                Bounds = new Rectangle(28, 212, 154, 48),
                ButtonColor = Color.FromArgb(242, 244, 247),
                HoverColor = Color.FromArgb(232, 236, 240),
                PressedColor = Color.FromArgb(220, 226, 232),
                TextColor = Color.FromArgb(52, 64, 84),
                Font = new Font("Microsoft YaHei UI", 13F, FontStyle.Bold, GraphicsUnit.Point)
            };
            cancelButton.Click += (_, _) =>
            {
                DialogResult = DialogResult.Cancel;
                Close();
            };
            Controls.Add(cancelButton);

            var okButton = new RoundedButton
            {
                Text = "\u4fdd\u5b58",
                Bounds = new Rectangle(202, 212, 162, 48),
                ButtonColor = AccentGreen,
                HoverColor = Color.FromArgb(19, 145, 111),
                PressedColor = Color.FromArgb(17, 124, 96),
                TextColor = Color.White,
                Font = new Font("Microsoft YaHei UI", 13F, FontStyle.Bold, GraphicsUnit.Point)
            };
            okButton.Click += (_, _) => SaveAndClose();
            Controls.Add(okButton);

            UpdateHint();
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            _minutesInput.Focus();
            _minutesInput.SelectAll();
        }

        protected override void OnResize(EventArgs e)
        {
            base.OnResize(e);
            using var path = CreateRoundRect(new Rectangle(0, 0, Width, Height), 26);
            Region = new Region(path);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using var path = CreateRoundRect(bounds, 26);
            using var fill = new SolidBrush(Color.White);
            using var border = new Pen(BorderColor);
            e.Graphics.FillPath(fill, path);
            e.Graphics.DrawPath(border, path);
            base.OnPaint(e);
        }

        protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
        {
            if (keyData == Keys.Enter)
            {
                SaveAndClose();
                return true;
            }

            if (keyData == Keys.Escape)
            {
                DialogResult = DialogResult.Cancel;
                Close();
                return true;
            }

            return base.ProcessCmdKey(ref msg, keyData);
        }

        private void OnMinutesKeyPress(object? sender, KeyPressEventArgs e)
        {
            if (!char.IsControl(e.KeyChar) && !char.IsDigit(e.KeyChar))
            {
                e.Handled = true;
            }
        }

        private void SaveAndClose()
        {
            if (!TryReadMinutes(out var minutes))
            {
                return;
            }

            ReminderMinutes = minutes;
            DialogResult = DialogResult.OK;
            Close();
        }

        private void UpdateHint()
        {
            if (TryReadMinutes(out var minutes))
            {
                _hintLabel.ForeColor = TextSecondary;
                _hintLabel.Text = string.Format(
                    "\u5355\u4f4d\uff1a\u5206\u949f{0}",
                    ReminderThreshold.FormatEquivalent(ReminderThreshold.FromMinutes(minutes)));
                return;
            }

            _hintLabel.ForeColor = Color.FromArgb(190, 80, 68);
            _hintLabel.Text = string.Format(
                "\u8bf7\u8f93\u5165 {0}-{1} \u4e4b\u95f4\u7684\u5206\u949f\u6570",
                ReminderThreshold.MinMinutes,
                ReminderThreshold.MaxMinutes);
        }

        private bool TryReadMinutes(out int minutes)
        {
            return int.TryParse(_minutesInput.Text.Trim(), out minutes)
                && minutes >= ReminderThreshold.MinMinutes
                && minutes <= ReminderThreshold.MaxMinutes;
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
