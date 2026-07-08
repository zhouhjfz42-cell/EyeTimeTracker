using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.Core.Formatting;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;

namespace EyeTimeTracker.App.UI;

public sealed class MainForm : Form
{
    private static readonly Color PageBackground = Color.FromArgb(248, 251, 250);
    private static readonly Color SoftGreen = Color.FromArgb(238, 249, 245);
    private static readonly Color AccentGreen = Color.FromArgb(22, 166, 125);
    private static readonly Color AccentYellow = Color.FromArgb(224, 162, 42);
    private static readonly Color AccentRed = Color.FromArgb(222, 82, 72);
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
    private readonly StartupManager _startupManager;
    private readonly Icon _appIcon;
    private readonly Action? _showPairingDialog;
    private readonly Action? _disconnectPairing;
    private ToggleSwitch? _startupSwitch;
    private RoundedButton? _pairingButton;
    private RoundedButton? _statsButton;
    private DateOnly? _displayResetDate;
    private long _todayDisplayBaseline;
    private long _yesterdayDisplayBaseline;
    private long _weekDisplayBaseline;
    private long _monthDisplayBaseline;
    private bool _closingForExit;

    public MainForm(
        TrackingController controller,
        StartupManager startupManager,
        Icon appIcon,
        Action? showPairingDialog = null,
        Action? disconnectPairing = null)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        _startupManager = startupManager ?? throw new ArgumentNullException(nameof(startupManager));
        _showPairingDialog = showPairingDialog;
        _disconnectPairing = disconnectPairing;

        AutoScaleMode = AutoScaleMode.None;
        Text = AppText.Get("app.name");
        _appIcon = (Icon)(appIcon ?? throw new ArgumentNullException(nameof(appIcon))).Clone();
        Icon = (Icon)_appIcon.Clone();
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = true;
        ClientSize = new Size(660, 760);
        BackColor = PageBackground;
        Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);

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
            Text = AppText.Get("app.mainTitle"),
            Bounds = MainFormLayout.TitleBounds,
            MaxFontSize = 22F,
            MinFontSize = 20F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        root.Controls.Add(new FitTextLabel
        {
            Text = AppText.Get("main.subtitle.pc"),
            Bounds = MainFormLayout.SubtitleBounds,
            MaxFontSize = 11F,
            MinFontSize = 10F,
            FontStyle = FontStyle.Regular,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        var startupLabel = new FitTextLabel
        {
            Text = AppText.Get("main.autostart"),
            Bounds = MainFormLayout.StartupLabelBounds,
            MaxFontSize = 10.5F,
            MinFontSize = 9F,
            FontStyle = FontStyle.Regular,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleRight
        };
        root.Controls.Add(startupLabel);

        _startupSwitch = new ToggleSwitch
        {
            Bounds = MainFormLayout.StartupSwitchBounds,
            Checked = _controller.Settings.StartWithWindows
        };
        _startupSwitch.Click += (_, _) => UpdateStartupSetting(_startupSwitch.Checked);
        root.Controls.Add(_startupSwitch);
        startupLabel.BringToFront();
        _startupSwitch.BringToFront();

        root.Controls.Add(new FitTextLabel
        {
            Text = AppText.Get("common.today"),
            Bounds = new Rectangle(34, 144, 104, 52),
            MaxFontSize = 14F,
            MinFontSize = 14F,
            FontStyle = FontStyle.Regular,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        statusDot = new StatusDot
        {
            Bounds = new Rectangle(154, 162, 16, 16)
        };
        root.Controls.Add(statusDot);
        statusDot.BringToFront();

        statusValue = new FitTextLabel
        {
            Text = AppText.Get("main.status.tracking"),
            Bounds = new Rectangle(178, 144, 310, 52),
            MaxFontSize = 14F,
            MinFontSize = 10F,
            FontStyle = FontStyle.Regular,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        root.Controls.Add(statusValue);
        statusValue.BringToFront();

        todayValue = new FitTextLabel
        {
            Text = AppText.Get("duration.zeroMinutes"),
            Bounds = new Rectangle(34, 194, 600, 104),
            MaxFontSize = 44F,
            MinFontSize = 24F,
            FontStyle = FontStyle.Bold,
            ForeColor = AccentGreen,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        root.Controls.Add(todayValue);

        root.Controls.Add(BuildMetricCard(AppText.Get("main.card.yesterday"), new Rectangle(34, 320, 282, 136), out yesterdayValue));
        root.Controls.Add(BuildMetricCard(AppText.Get("main.card.week"), new Rectangle(344, 320, 282, 136), out weekValue));
        root.Controls.Add(BuildMetricCard(AppText.Get("main.card.month"), new Rectangle(34, 484, 282, 136), out monthValue));
        root.Controls.Add(BuildReminderCard(new Rectangle(344, 484, 282, 136), out reminderValue));

        if (_showPairingDialog is not null)
        {
            _pairingButton = new RoundedButton
            {
                Text = AppText.Get("pair.pc.title"),
                Bounds = MainFormLayout.PairingButtonBounds,
                ButtonColor = AccentGreen,
                HoverColor = Color.FromArgb(19, 145, 111),
                PressedColor = Color.FromArgb(17, 124, 96),
                TextColor = Color.White,
                Font = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point)
            };
            _pairingButton.Click += (_, _) => HandlePairingButtonClick();
            root.Controls.Add(_pairingButton);
            _pairingButton.BringToFront();
        }

        _statsButton = new RoundedButton
        {
            Text = AppText.Get("common.statsPage"),
            Bounds = new Rectangle(200, 668, 260, 58),
            ButtonColor = AccentGreen,
            HoverColor = Color.FromArgb(19, 145, 111),
            PressedColor = Color.FromArgb(17, 124, 96),
            TextColor = Color.White,
            Font = AppFonts.Create(13F, FontStyle.Bold, GraphicsUnit.Point)
        };
        _statsButton.Click += (_, _) => ShowStats();
        root.Controls.Add(_statsButton);
    }

    private void ShowStats()
    {
        using var statsForm = new StatsForm(_controller, _appIcon);
        statsForm.ShowDialog(this);
    }

    private static Control BuildMetricCard(string title, Rectangle bounds, out FitTextLabel value)
    {
        var card = CreateMetricShell(bounds);
        AddMetricTitle(card, title);
        value = new FitTextLabel
        {
            Text = AppText.Get("duration.zeroMinutes"),
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
        AddMetricTitle(card, AppText.Get("main.card.reminderClickable"));
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
            Bounds = new Rectangle(24, 14, 228, 44),
            MaxFontSize = 11.5F,
            MinFontSize = 9.5F,
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
        var totals = MainSummaryTotals.FromRecords(today, records);
        var todayTotal = totals.TodaySeconds;
        var yesterdayTotal = totals.YesterdaySeconds;
        var weekTotal = totals.WeekSeconds;
        var monthTotal = totals.MonthSeconds;

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
        _todayValue.ForeColor = TodayColor(todayTotal);
        _yesterdayValue.Text = FormatDuration(yesterdayTotal);
        _weekValue.Text = FormatDuration(weekTotal);
        _monthValue.Text = FormatDuration(monthTotal);
        UpdateReminderDisplay();
        _statusValue.Text = FormatConnectionStatus(
            current.IsCounting ? AppText.Get("main.status.tracking") : AppText.Get("main.status.paused"),
            _controller.IsPaired,
            _controller.IsPeerOnline,
            AppText.Get("common.phone"));
        _statusDot.IsActive = current.IsCounting;
        if (_pairingButton is not null)
        {
            _pairingButton.Text = _controller.IsPaired ? AppText.Get("common.disconnect") : AppText.Get("pair.pc.title");
        }
    }

    private void HandlePairingButtonClick()
    {
        if (_controller.IsPaired)
        {
            _disconnectPairing?.Invoke();
            return;
        }

        _showPairingDialog?.Invoke();
    }

    private static string FormatConnectionStatus(string baseStatus, bool isPaired, bool isOnline, string peerName)
    {
        if (!isPaired || string.IsNullOrWhiteSpace(peerName))
        {
            return baseStatus;
        }

        return AppText.Format(
            isOnline ? "main.status.connected" : "main.status.offline",
            ("status", baseStatus),
            ("device", peerName));
    }

    private void ShowReminderDialog()
    {
        using var dialog = new ReminderThresholdDialog(
            ReminderThreshold.ToMinutes(_controller.Settings.ReminderThresholdSeconds),
            _controller.Settings.RepeatReminder,
            !_controller.IsPaired,
            Icon);

        if (dialog.ShowDialog(this) != DialogResult.OK)
        {
            return;
        }

        _controller.Settings = _controller.Settings with
        {
            ReminderThresholdSeconds = ReminderThreshold.FromMinutes(dialog.ReminderMinutes),
            RepeatReminder = dialog.RepeatReminder
        };
        _controller.SaveNow();
        UpdateReminderDisplay();
    }

    private void UpdateReminderDisplay()
    {
        _reminderValue.Text = ReminderThreshold.Format(_controller.Settings.ReminderThresholdSeconds);
    }

    private void UpdateStartupSetting(bool enabled)
    {
        _controller.Settings = _controller.Settings with
        {
            StartWithWindows = enabled
        };
        _controller.SaveNow();

        try
        {
            _startupManager.SetEnabled(enabled);
        }
        catch (Exception)
        {
        }
    }

    private void ResetDisplayedStatistics()
    {
        var current = _controller.Current;
        var records = _controller.GetRecordsSnapshot();
        var today = current.Date;
        var totals = MainSummaryTotals.FromRecords(today, records);

        _displayResetDate = today;
        _todayDisplayBaseline = totals.TodaySeconds;
        _yesterdayDisplayBaseline = totals.YesterdaySeconds;
        _weekDisplayBaseline = totals.WeekSeconds;
        _monthDisplayBaseline = totals.MonthSeconds;

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
            ? AppText.Format("duration.hoursMinutesPadded", ("hours", totalHours), ("minutes:00", duration.Minutes.ToString("00")))
            : AppText.Format("duration.minutes", ("minutes", duration.Minutes));
    }

    private static Color TodayColor(long totalSeconds)
    {
        return TodayTonePolicy.FromSeconds(totalSeconds) switch
        {
            TodayTone.Warn => AccentYellow,
            TodayTone.Danger => AccentRed,
            _ => AccentGreen
        };
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
            Font = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point);
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

    private sealed class ToggleSwitch : Control
    {
        private bool _checked;
        private bool _hovered;
        private bool _pressed;

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
            }
        }

        public ToggleSwitch()
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
            if (ClientRectangle.Contains(e.Location))
            {
                Checked = !Checked;
                OnClick(EventArgs.Empty);
            }

            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var track = new Rectangle(0, 0, Width - 1, Height - 1);
            var offColor = _hovered || _pressed
                ? Color.FromArgb(226, 231, 235)
                : Color.FromArgb(236, 240, 244);
            var trackColor = Checked ? AccentGreen : offColor;
            using (var path = CreateRoundRect(track, Height / 2))
            using (var brush = new SolidBrush(trackColor))
            {
                e.Graphics.FillPath(brush, path);
            }

            var knobSize = Math.Max(4, Height - 8);
            var knobX = Checked ? Width - knobSize - 4 : 4;
            var knob = new Rectangle(knobX, 4, knobSize, knobSize);
            using var knobBrush = new SolidBrush(Color.White);
            e.Graphics.FillEllipse(knobBrush, knob);
        }
    }

    private sealed class ReminderThresholdDialog : Form
    {
        private readonly TextBox _minutesInput;
        private readonly FitTextLabel _hintLabel;
        private readonly ToggleSwitch _repeatSwitch;
        private readonly FitTextLabel _repeatLabel;
        private readonly bool _canEdit;

        public int ReminderMinutes { get; private set; }
        public bool RepeatReminder { get; private set; }

        public ReminderThresholdDialog(int currentMinutes, bool repeatReminder, bool canEdit, Icon? icon)
        {
            ReminderMinutes = Math.Clamp(currentMinutes, ReminderThreshold.MinMinutes, ReminderThreshold.MaxMinutes);
            RepeatReminder = repeatReminder;
            _canEdit = canEdit;
            AutoScaleMode = AutoScaleMode.None;
            Text = AppText.Get("reminder.title");
            if (icon is not null)
            {
                Icon = (Icon)icon.Clone();
            }

            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.None;
            MaximizeBox = false;
            MinimizeBox = false;
            ShowInTaskbar = false;
            ClientSize = new Size(470, 340);
            BackColor = Color.White;
            Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);

            Controls.Add(new FitTextLabel
            {
                Text = AppText.Get("reminder.title"),
                Bounds = new Rectangle(28, 20, 240, 60),
                MaxFontSize = 19F,
                MinFontSize = 17F,
                FontStyle = FontStyle.Bold,
                ForeColor = TextPrimary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleLeft
            });

            var closeButton = new CloseIconButton
            {
                Bounds = new Rectangle(404, 24, 38, 38)
            };
            closeButton.Click += (_, _) =>
            {
                DialogResult = DialogResult.Cancel;
                Close();
            };
            Controls.Add(closeButton);

            _hintLabel = new FitTextLabel
            {
                Bounds = new Rectangle(28, 82, 414, 34),
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
                Bounds = new Rectangle(28, 122, 414, 76),
                FillColor = Color.FromArgb(249, 253, 251),
                BorderColor = Color.FromArgb(206, 226, 218),
                Radius = 18
            };

            _minutesInput = new TextBox
            {
                Text = ReminderMinutes.ToString(),
                Bounds = new Rectangle(24, 7, 150, 58),
                BorderStyle = BorderStyle.None,
                Font = AppFonts.Create(20F, FontStyle.Bold, GraphicsUnit.Point),
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
                Text = AppText.Get("reminder.unitMinute"),
                Bounds = new Rectangle(306, 8, 84, 58),
                MaxFontSize = 14F,
                MinFontSize = 13F,
                FontStyle = FontStyle.Regular,
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleRight
            });
            Controls.Add(inputShell);

            _repeatSwitch = new ToggleSwitch
            {
                Bounds = new Rectangle(30, 216, 48, 28),
                Checked = RepeatReminder
            };
            _repeatSwitch.Click += (_, _) => RepeatReminder = _repeatSwitch.Checked;
            Controls.Add(_repeatSwitch);

            _repeatLabel = new FitTextLabel
            {
                Bounds = new Rectangle(84, 203, 372, 52),
                MaxFontSize = 8F,
                MinFontSize = 7.2F,
                FontStyle = FontStyle.Regular,
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleLeft
            };
            _repeatLabel.Click += (_, _) =>
            {
                _repeatSwitch.Checked = !_repeatSwitch.Checked;
                RepeatReminder = _repeatSwitch.Checked;
            };
            Controls.Add(_repeatLabel);

            var cancelButton = new RoundedButton
            {
                Text = AppText.Get("common.cancel"),
                Bounds = new Rectangle(28, 270, 190, 48),
                ButtonColor = Color.FromArgb(242, 244, 247),
                HoverColor = Color.FromArgb(232, 236, 240),
                PressedColor = Color.FromArgb(220, 226, 232),
                TextColor = Color.FromArgb(52, 64, 84),
                Font = AppFonts.Create(13F, FontStyle.Bold, GraphicsUnit.Point)
            };
            cancelButton.Click += (_, _) =>
            {
                DialogResult = DialogResult.Cancel;
                Close();
            };
            Controls.Add(cancelButton);

            var okButton = new RoundedButton
            {
                Text = _canEdit ? AppText.Get("common.save") : AppText.Get("common.ok"),
                Bounds = new Rectangle(238, 270, 204, 48),
                ButtonColor = AccentGreen,
                HoverColor = Color.FromArgb(19, 145, 111),
                PressedColor = Color.FromArgb(17, 124, 96),
                TextColor = Color.White,
                Font = AppFonts.Create(13F, FontStyle.Bold, GraphicsUnit.Point)
            };
            okButton.Click += (_, _) => SaveAndClose();
            Controls.Add(okButton);

            if (!_canEdit)
            {
                _minutesInput.ReadOnly = true;
                _minutesInput.ForeColor = TextSecondary;
                _repeatSwitch.Enabled = false;
            }

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

            if (!_canEdit)
            {
                DialogResult = DialogResult.Cancel;
                Close();
                return;
            }

            ReminderMinutes = minutes;
            RepeatReminder = _repeatSwitch.Checked;
            DialogResult = DialogResult.OK;
            Close();
        }

        private void UpdateHint()
        {
            if (TryReadMinutes(out var minutes))
            {
                _hintLabel.ForeColor = TextSecondary;
                _hintLabel.Text = _canEdit
                    ? AppText.Format(
                        "reminder.unitHint",
                        ("equivalent", ReminderThreshold.FormatEquivalent(ReminderThreshold.FromMinutes(minutes))))
                    : AppText.Get("reminder.pcConnectedReadonly");
                _repeatLabel.Text = AppText.Get("reminder.repeatLabel");
                return;
            }

            _hintLabel.ForeColor = Color.FromArgb(190, 80, 68);
            _hintLabel.Text = AppText.Format(
                "reminder.validation.minutesRange",
                ("min", ReminderThreshold.MinMinutes),
                ("max", ReminderThreshold.MaxMinutes));
            _repeatLabel.Text = AppText.Get("reminder.repeatShort");
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

            if (ClientRectangle.Width <= 0 || ClientRectangle.Height <= 0 || string.IsNullOrEmpty(Text))
            {
                return;
            }

            using var font = CreateFittingFont(e.Graphics, ClientRectangle);
            TextRenderer.DrawText(e.Graphics, Text, font, ClientRectangle, ForeColor, CreateTextFormatFlags());
        }

        private Font CreateFittingFont(Graphics graphics, Rectangle bounds)
        {
            for (var size = MaxFontSize; size >= MinFontSize; size -= 0.5F)
            {
                var font = AppFonts.Create(size, FontStyle, GraphicsUnit.Point);
                var measured = TextRenderer.MeasureText(
                    graphics,
                    Text,
                    font,
                    new Size(bounds.Width, int.MaxValue),
                    CreateMeasureTextFormatFlags());
                if (measured.Width <= bounds.Width && measured.Height <= bounds.Height)
                {
                    return font;
                }

                font.Dispose();
            }

            return AppFonts.Create(MinFontSize, FontStyle, GraphicsUnit.Point);
        }

        private TextFormatFlags CreateTextFormatFlags()
        {
            var flags = TextFormatFlags.NoPadding | TextFormatFlags.WordBreak | TextFormatFlags.TextBoxControl;

            flags |= TextAlign is ContentAlignment.TopRight or ContentAlignment.MiddleRight or ContentAlignment.BottomRight
                ? TextFormatFlags.Right
                : TextAlign is ContentAlignment.TopCenter or ContentAlignment.MiddleCenter or ContentAlignment.BottomCenter
                    ? TextFormatFlags.HorizontalCenter
                    : TextFormatFlags.Left;

            flags |= TextAlign is ContentAlignment.BottomLeft or ContentAlignment.BottomCenter or ContentAlignment.BottomRight
                ? TextFormatFlags.Bottom
                : TextAlign is ContentAlignment.TopLeft or ContentAlignment.TopCenter or ContentAlignment.TopRight
                    ? TextFormatFlags.Top
                    : TextFormatFlags.VerticalCenter;

            return flags;
        }

        private static TextFormatFlags CreateMeasureTextFormatFlags()
        {
            return TextFormatFlags.NoPadding | TextFormatFlags.WordBreak | TextFormatFlags.TextBoxControl;
        }
    }

}
