using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Localization;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;

namespace EyeTimeTracker.App.UI;

public sealed class EyeCareRemindersForm : Form
{
    private static readonly Color PageBackground = Color.FromArgb(248, 251, 250);
    private static readonly Color SoftGreen = Color.FromArgb(238, 249, 245);
    private static readonly Color AccentGreen = Color.FromArgb(22, 166, 125);
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);
    private static readonly Color TextSecondary = Color.FromArgb(102, 112, 133);
    private static readonly Color BorderColor = Color.FromArgb(226, 245, 238);

    private readonly bool _canEdit;
    private readonly Icon? _icon;
    private readonly ValueText _cumulativeValue;
    private readonly ValueText _continuousValue;
    private bool _settingsChanged;

    public TrackerSettings Settings { get; private set; }

    public EyeCareRemindersForm(TrackerSettings settings, bool canEdit, Icon? icon)
    {
        Settings = settings ?? throw new ArgumentNullException(nameof(settings));
        _canEdit = canEdit;
        _icon = icon is null ? null : (Icon)icon.Clone();

        AutoScaleMode = AutoScaleMode.None;
        Text = AppText.Get("eyeCareReminders.dialogTitle");
        if (_icon is not null)
        {
            Icon = (Icon)_icon.Clone();
        }

        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(560, 560);
        BackColor = PageBackground;
        Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);

        Controls.Add(new StaticText
        {
            Text = AppText.Get("eyeCareReminders.dialogTitle"),
            Bounds = new Rectangle(34, 34, 360, 56),
            Font = AppFonts.Create(24F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        Controls.Add(new StaticText
        {
            Text = AppText.Get("eyeCareReminders.dialogLead"),
            Bounds = new Rectangle(34, 100, 492, 58),
            Font = AppFonts.Create(11F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.TopLeft,
            WordWrap = true,
            UseEllipsis = false
        });

        Controls.Add(CreateSettingCard(
            new Rectangle(34, 180, 492, 110),
            AppText.Get("eyeCareReminders.cumulativeTitle"),
            AppText.Get("eyeCareReminders.cumulativeDesc"),
            out _cumulativeValue,
            ShowCumulativeDialog));

        Controls.Add(CreateSettingCard(
            new Rectangle(34, 310, 492, 110),
            AppText.Get("eyeCareReminders.continuousTitle"),
            AppText.Get("eyeCareReminders.continuousDesc"),
            out _continuousValue,
            ShowContinuousDialog));

        Controls.Add(new StaticText
        {
            Text = AppText.Get("eyeCareReminders.footer"),
            Bounds = new Rectangle(34, 438, 492, 78),
            Font = AppFonts.Create(10.5F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.TopLeft,
            WordWrap = true,
            UseEllipsis = false
        });

        RefreshValues();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (_settingsChanged && DialogResult == DialogResult.None)
        {
            DialogResult = DialogResult.OK;
        }

        base.OnFormClosing(e);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _icon?.Dispose();
        }

        base.Dispose(disposing);
    }

    private Control CreateSettingCard(Rectangle bounds, string title, string desc, out ValueText value, Action onClick)
    {
        var card = new RoundedPanel
        {
            Bounds = bounds,
            FillColor = SoftGreen,
            BorderColor = BorderColor,
            Radius = 22,
            Cursor = Cursors.Hand
        };

        card.Controls.Add(new StaticText
        {
            Text = title,
            Bounds = new Rectangle(24, 18, 280, 38),
            Font = AppFonts.Create(16F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        card.Controls.Add(new StaticText
        {
            Text = desc,
            Bounds = new Rectangle(24, 60, 286, 34),
            Font = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft,
            WordWrap = true,
            UseEllipsis = false
        });

        value = new ValueText
        {
            Bounds = new Rectangle(326, 26, 140, 58),
            Font = AppFonts.Create(15F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleRight
        };
        card.Controls.Add(value);

        WireClick(card, (_, _) => onClick());
        return card;
    }

    private void ShowCumulativeDialog()
    {
        using var dialog = new MinutesDialog(
            AppText.Get("eyeCareReminders.cumulativeTitle"),
            AppText.Get("eyeCareReminders.cumulativeDesc"),
            ReminderThreshold.ToMinutes(Settings.ReminderThresholdSeconds),
            enabled: true,
            showSwitch: false,
            canEdit: _canEdit,
            _icon);

        if (dialog.ShowDialog(this) != DialogResult.OK)
        {
            return;
        }

        Settings = Settings with
        {
            ReminderThresholdSeconds = ReminderThreshold.FromMinutes(dialog.ReminderMinutes),
            RepeatReminder = true
        };
        _settingsChanged = true;
        RefreshValues();
    }

    private void ShowContinuousDialog()
    {
        using var dialog = new ContinuousReminderDialog(
            AppText.Get("eyeCareReminders.continuousTitle"),
            AppText.Get("eyeCareReminders.continuousDesc"),
            ReminderThreshold.ToMinutes(Settings.ContinuousReminderThresholdSeconds),
            Settings.ContinuousReminderEnabled,
            canEdit: _canEdit,
            Settings.ContinuousReminderExemptionPeriods,
            _icon);

        if (dialog.ShowDialog(this) != DialogResult.OK)
        {
            return;
        }

        Settings = Settings with
        {
            ContinuousReminderEnabled = dialog.ReminderEnabled,
            ContinuousReminderThresholdSeconds = ReminderThreshold.FromMinutes(dialog.ReminderMinutes),
            ContinuousReminderExemptionPeriods = dialog.ExemptionPeriods
        };
        _settingsChanged = true;
        RefreshValues();
    }

    private void RefreshValues()
    {
        _cumulativeValue.Text = ReminderThreshold.Format(Settings.ReminderThresholdSeconds);
        _continuousValue.Text = Settings.ContinuousReminderEnabled
            ? ReminderThreshold.Format(Settings.ContinuousReminderThresholdSeconds)
            : AppText.Get("eyeCareReminders.disabled");
    }

    private static void WireClick(Control control, EventHandler handler)
    {
        control.Click += handler;
        foreach (Control child in control.Controls)
        {
            WireClick(child, handler);
        }
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

    private sealed class ContinuousReminderDialog : Form
    {
        private const int CsDropShadow = 0x00020000;
        private readonly TextBox _minutesInput;
        private readonly CheckBox _enabledInput;
        private readonly CheckBox _exemptionInput;
        private readonly Panel _periodsPanel;
        private readonly List<ReminderExemptionPeriod> _periods;
        private readonly bool _canEdit;

        public int ReminderMinutes { get; private set; }
        public bool ReminderEnabled { get; private set; }
        public List<ReminderExemptionPeriod> ExemptionPeriods { get; private set; }

        public ContinuousReminderDialog(
            string title,
            string desc,
            int currentMinutes,
            bool enabled,
            bool canEdit,
            IReadOnlyList<ReminderExemptionPeriod> periods,
            Icon? icon)
        {
            ReminderMinutes = Math.Clamp(currentMinutes, ReminderThreshold.MinMinutes, ReminderThreshold.MaxMinutes);
            ReminderEnabled = enabled;
            _canEdit = canEdit;
            _periods = periods.Where(period => period.IsValid).Distinct().ToList();
            ExemptionPeriods = new List<ReminderExemptionPeriod>(_periods);

            AutoScaleMode = AutoScaleMode.None;
            Text = title;
            if (icon is not null)
            {
                Icon = (Icon)icon.Clone();
            }
            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            ClientSize = new Size(470, 578);
            BackColor = Color.White;
            Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);

            Controls.Add(new StaticText
            {
                Text = title,
                Bounds = new Rectangle(28, 22, 414, 48),
                Font = AppFonts.Create(19F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = TextPrimary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleLeft
            });
            Controls.Add(new StaticText
            {
                Text = desc,
                Bounds = new Rectangle(28, 76, 414, 44),
                Font = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.TopLeft,
                WordWrap = true,
                UseEllipsis = false
            });

            _enabledInput = new CheckBox
            {
                Text = AppText.Get("eyeCareReminders.enableReminder"),
                Checked = enabled,
                Bounds = new Rectangle(28, 132, 414, 42),
                Font = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = TextPrimary,
                BackColor = SoftGreen,
                Enabled = _canEdit
            };
            Controls.Add(_enabledInput);

            var inputShell = new RoundedPanel
            {
                Bounds = new Rectangle(28, 190, 414, 68),
                FillColor = Color.FromArgb(249, 253, 251),
                BorderColor = Color.FromArgb(206, 226, 218),
                Radius = 18
            };
            _minutesInput = new TextBox
            {
                Text = ReminderMinutes.ToString(),
                Bounds = new Rectangle(24, 6, 150, 54),
                BorderStyle = BorderStyle.None,
                Font = AppFonts.Create(20F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = _canEdit ? TextPrimary : TextSecondary,
                BackColor = Color.FromArgb(249, 253, 251),
                MaxLength = 5,
                Multiline = true,
                WordWrap = false,
                ReadOnly = !_canEdit
            };
            _minutesInput.KeyPress += (_, e) =>
            {
                if (!char.IsControl(e.KeyChar) && !char.IsDigit(e.KeyChar))
                {
                    e.Handled = true;
                }
            };
            inputShell.Controls.Add(_minutesInput);
            inputShell.Controls.Add(new StaticText
            {
                Text = AppText.Get("eyeCareReminders.minutesInputHint"),
                Bounds = new Rectangle(306, 6, 84, 54),
                Font = AppFonts.Create(14F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleRight
            });
            Controls.Add(inputShell);

            _exemptionInput = new CheckBox
            {
                Text = AppText.Get("eyeCareReminders.exemptionTitle"),
                Checked = _periods.Count > 0,
                Bounds = new Rectangle(28, 276, 414, 42),
                Font = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = TextPrimary,
                BackColor = SoftGreen,
                Enabled = _canEdit
            };
            _exemptionInput.CheckedChanged += (_, _) => RefreshPeriods();
            Controls.Add(_exemptionInput);

            _periodsPanel = new Panel
            {
                Bounds = new Rectangle(28, 334, 414, 112),
                BackColor = Color.White,
                AutoScroll = true,
                Enabled = _canEdit
            };
            Controls.Add(_periodsPanel);

            var addButton = new RoundedButton
            {
                Text = AppText.Get("eyeCareReminders.exemptionAdd"),
                Bounds = new Rectangle(28, 454, 414, 38),
                ButtonColor = Color.FromArgb(237, 248, 244),
                HoverColor = Color.FromArgb(226, 242, 236),
                PressedColor = Color.FromArgb(214, 236, 228),
                TextColor = AccentGreen,
                Enabled = _canEdit
            };
            addButton.Click += (_, _) => AddOrEditPeriod(null);
            Controls.Add(addButton);

            var cancelButton = new RoundedButton
            {
                Text = AppText.Get("common.cancel"),
                Bounds = new Rectangle(28, 516, 190, 48),
                ButtonColor = Color.FromArgb(242, 244, 247),
                HoverColor = Color.FromArgb(232, 236, 240),
                PressedColor = Color.FromArgb(220, 226, 232),
                TextColor = Color.FromArgb(52, 64, 84)
            };
            cancelButton.Click += (_, _) => { DialogResult = DialogResult.Cancel; Close(); };
            Controls.Add(cancelButton);

            var saveButton = new RoundedButton
            {
                Text = _canEdit ? AppText.Get("common.save") : AppText.Get("common.ok"),
                Bounds = new Rectangle(238, 516, 204, 48),
                ButtonColor = AccentGreen,
                HoverColor = Color.FromArgb(19, 145, 111),
                PressedColor = Color.FromArgb(17, 124, 96),
                TextColor = Color.White
            };
            saveButton.Click += (_, _) => SaveAndClose();
            Controls.Add(saveButton);
            RefreshPeriods();
        }

        protected override CreateParams CreateParams
        {
            get
            {
                var parameters = base.CreateParams;
                parameters.ClassStyle |= CsDropShadow;
                return parameters;
            }
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
            using var border = new Pen(Color.FromArgb(190, 211, 202));
            e.Graphics.FillPath(fill, path);
            e.Graphics.DrawPath(border, path);
            base.OnPaint(e);
        }

        private void RefreshPeriods()
        {
            _periodsPanel.Controls.Clear();
            _periodsPanel.Enabled = _canEdit && _exemptionInput.Checked;
            if (!_exemptionInput.Checked)
            {
                return;
            }

            if (_periods.Count == 0)
            {
                _periodsPanel.Controls.Add(new StaticText
                {
                    Text = AppText.Get("eyeCareReminders.exemptionEmpty"),
                    Bounds = new Rectangle(4, 8, 390, 30),
                    Font = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point),
                    ForeColor = TextSecondary,
                    BackColor = Color.Transparent,
                    TextAlign = ContentAlignment.MiddleLeft
                });
                return;
            }

            for (var index = 0; index < _periods.Count; index++)
            {
                var period = _periods[index];
                var row = new Panel { Bounds = new Rectangle(0, index * 36, 398, 32), BackColor = Color.Transparent, Cursor = Cursors.Hand };
                row.Click += (_, _) => AddOrEditPeriod(index);
                row.Controls.Add(new StaticText
                {
                    Text = FormatPeriod(period),
                    Bounds = new Rectangle(4, 0, 320, 32),
                    Font = AppFonts.Create(10.5F, FontStyle.Regular, GraphicsUnit.Point),
                    ForeColor = TextPrimary,
                    BackColor = Color.Transparent,
                    TextAlign = ContentAlignment.MiddleLeft
                });
                var remove = new RoundedButton
                {
                    Text = "×",
                    Bounds = new Rectangle(350, 2, 30, 28),
                    ButtonColor = Color.FromArgb(242, 244, 247),
                    HoverColor = Color.FromArgb(232, 236, 240),
                    PressedColor = Color.FromArgb(220, 226, 232),
                    TextColor = TextSecondary,
                    Font = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point)
                };
                var capturedIndex = index;
                remove.Click += (_, _) => { _periods.RemoveAt(capturedIndex); RefreshPeriods(); };
                row.Controls.Add(remove);
                _periodsPanel.Controls.Add(row);
            }
        }

        private void AddOrEditPeriod(int? index)
        {
            var current = index.HasValue ? _periods[index.Value] : ReminderExemptionPeriod.Default;
            using var dialog = new TimeRangeDialog(current, null);
            if (dialog.ShowDialog(this) != DialogResult.OK || dialog.Period is null)
            {
                return;
            }

            if (index.HasValue)
            {
                _periods[index.Value] = dialog.Period;
            }
            else
            {
                _periods.Add(dialog.Period);
                _exemptionInput.Checked = true;
            }
            RefreshPeriods();
        }

        private void SaveAndClose()
        {
            if (!_canEdit)
            {
                DialogResult = DialogResult.Cancel;
                Close();
                return;
            }

            ReminderMinutes = Math.Clamp(ParseMinutes(_minutesInput.Text, ReminderMinutes), ReminderThreshold.MinMinutes, ReminderThreshold.MaxMinutes);
            ReminderEnabled = _enabledInput.Checked;
            ExemptionPeriods = _exemptionInput.Checked
                ? _periods.Where(period => period.IsValid).Distinct().ToList()
                : new List<ReminderExemptionPeriod>();
            DialogResult = DialogResult.OK;
            Close();
        }

        private static int ParseMinutes(string text, int fallback)
        {
            return int.TryParse(text.Trim(), out var value) ? value : fallback;
        }

        private static string FormatPeriod(ReminderExemptionPeriod period)
        {
            var end = period.StartMinuteOfDay > period.EndMinuteOfDay
                ? "次日 " + ReminderExemptionPeriod.FormatMinuteOfDay(period.EndMinuteOfDay)
                : ReminderExemptionPeriod.FormatMinuteOfDay(period.EndMinuteOfDay);
            return ReminderExemptionPeriod.FormatMinuteOfDay(period.StartMinuteOfDay) + " - " + end;
        }

        private sealed class TimeRangeDialog : Form
        {
            private readonly NumericUpDown _startHour = CreateNumber(0, 23);
            private readonly NumericUpDown _startMinute = CreateNumber(0, 59);
            private readonly NumericUpDown _endHour = CreateNumber(0, 23);
            private readonly NumericUpDown _endMinute = CreateNumber(0, 59);

            public ReminderExemptionPeriod? Period { get; private set; }

            public TimeRangeDialog(ReminderExemptionPeriod current, Icon? icon)
            {
                Text = AppText.Get("eyeCareReminders.exemptionTitle");
                if (icon is not null) Icon = (Icon)icon.Clone();
                AutoScaleMode = AutoScaleMode.None;
                StartPosition = FormStartPosition.CenterParent;
                FormBorderStyle = FormBorderStyle.FixedSingle;
                MaximizeBox = false;
                MinimizeBox = false;
                ClientSize = new Size(430, 248);
                BackColor = Color.White;

                Controls.Add(new StaticText
                {
                    Text = AppText.Get("eyeCareReminders.exemptionTitle"),
                    Bounds = new Rectangle(24, 18, 380, 38),
                    Font = AppFonts.Create(17F, FontStyle.Bold, GraphicsUnit.Point),
                    ForeColor = TextPrimary,
                    BackColor = Color.Transparent,
                    TextAlign = ContentAlignment.MiddleLeft
                });

                AddTimeRow("从", current.StartMinuteOfDay, 72, _startHour, _startMinute);
                AddTimeRow("到", current.EndMinuteOfDay, 126, _endHour, _endMinute);

                var cancel = new RoundedButton
                {
                    Text = AppText.Get("common.cancel"),
                    Bounds = new Rectangle(24, 188, 174, 44),
                    ButtonColor = Color.FromArgb(237, 248, 244),
                    HoverColor = Color.FromArgb(226, 242, 236),
                    PressedColor = Color.FromArgb(214, 236, 228),
                    TextColor = AccentGreen
                };
                cancel.Click += (_, _) => { DialogResult = DialogResult.Cancel; Close(); };
                Controls.Add(cancel);
                var confirm = new RoundedButton
                {
                    Text = AppText.Get("common.confirm"),
                    Bounds = new Rectangle(214, 188, 192, 44),
                    ButtonColor = AccentGreen,
                    HoverColor = Color.FromArgb(19, 145, 111),
                    PressedColor = Color.FromArgb(17, 124, 96),
                    TextColor = Color.White
                };
                confirm.Click += (_, _) => SaveAndClose();
                Controls.Add(confirm);
            }

            private void AddTimeRow(string label, int minutes, int y, NumericUpDown hour, NumericUpDown minute)
            {
                hour.Value = minutes / 60;
                minute.Value = minutes % 60;
                Controls.Add(new StaticText
                {
                    Text = label,
                    Bounds = new Rectangle(26, y, 42, 32),
                    Font = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point),
                    ForeColor = TextPrimary,
                    BackColor = Color.Transparent,
                    TextAlign = ContentAlignment.MiddleLeft
                });
                hour.Bounds = new Rectangle(78, y, 90, 32);
                minute.Bounds = new Rectangle(180, y, 90, 32);
                Controls.Add(hour);
                Controls.Add(minute);
            }

            private void SaveAndClose()
            {
                var period = new ReminderExemptionPeriod(
                    (int)_startHour.Value * 60 + (int)_startMinute.Value,
                    (int)_endHour.Value * 60 + (int)_endMinute.Value);
                if (!period.IsValid)
                {
                    MessageBox.Show(this, AppText.Get("eyeCareReminders.exemptionInvalid"), Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
                    return;
                }
                Period = period;
                DialogResult = DialogResult.OK;
                Close();
            }

            private static NumericUpDown CreateNumber(int min, int max)
            {
                return new NumericUpDown
                {
                    Minimum = min,
                    Maximum = max,
                    Font = AppFonts.Create(12F, FontStyle.Regular, GraphicsUnit.Point),
                    TextAlign = HorizontalAlignment.Center,
                    Increment = 1,
                    BorderStyle = BorderStyle.FixedSingle
                };
            }
        }
    }

    private sealed class MinutesDialog : Form
    {
        private const int CsDropShadow = 0x00020000;
        private readonly TextBox _minutesInput;
        private readonly CheckBox? _enabledInput;
        private readonly StaticText _hintLabel;
        private readonly bool _canEdit;

        public int ReminderMinutes { get; private set; }
        public bool ReminderEnabled { get; private set; }

        public MinutesDialog(string title, string desc, int currentMinutes, bool enabled, bool showSwitch, bool canEdit, Icon? icon)
        {
            ReminderMinutes = Math.Clamp(currentMinutes, ReminderThreshold.MinMinutes, ReminderThreshold.MaxMinutes);
            ReminderEnabled = enabled;
            _canEdit = canEdit;

            AutoScaleMode = AutoScaleMode.None;
            Text = title;
            if (icon is not null)
            {
                Icon = (Icon)icon.Clone();
            }

            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            ClientSize = new Size(470, showSwitch ? 386 : 328);
            BackColor = Color.White;
            Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);

            Controls.Add(new StaticText
            {
                Text = title,
                Bounds = new Rectangle(28, 22, 330, 48),
                Font = AppFonts.Create(19F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = TextPrimary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleLeft
            });

            Controls.Add(new StaticText
            {
                Text = desc,
                Bounds = new Rectangle(28, 76, 414, 44),
                Font = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.TopLeft,
                WordWrap = true,
                UseEllipsis = false
            });

            var y = 132;
            if (showSwitch)
            {
                _enabledInput = new CheckBox
                {
                    Text = AppText.Get("eyeCareReminders.enableReminder"),
                    Checked = Enabled,
                    Bounds = new Rectangle(28, y, 414, 42),
                    Font = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point),
                    ForeColor = TextPrimary,
                    BackColor = SoftGreen,
                    Enabled = _canEdit
                };
                Controls.Add(_enabledInput);
                y += 58;
            }

            var inputShell = new RoundedPanel
            {
                Bounds = new Rectangle(28, y, 414, 76),
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
                ForeColor = _canEdit ? TextPrimary : TextSecondary,
                BackColor = Color.FromArgb(249, 253, 251),
                MaxLength = 5,
                Multiline = true,
                WordWrap = false,
                ScrollBars = ScrollBars.None,
                ReadOnly = !_canEdit
            };
            _minutesInput.KeyPress += (_, e) =>
            {
                if (!char.IsControl(e.KeyChar) && !char.IsDigit(e.KeyChar))
                {
                    e.Handled = true;
                }
            };
            _minutesInput.TextChanged += (_, _) => UpdateHint();
            inputShell.Controls.Add(_minutesInput);

            inputShell.Controls.Add(new StaticText
            {
                Text = AppText.Get("eyeCareReminders.minutesInputHint"),
                Bounds = new Rectangle(306, 8, 84, 58),
                Font = AppFonts.Create(14F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleRight
            });
            Controls.Add(inputShell);
            y += 86;

            _hintLabel = new StaticText
            {
                Bounds = new Rectangle(28, y, 414, 30),
                Font = AppFonts.Create(10.5F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleLeft
            };
            Controls.Add(_hintLabel);
            y += 42;

            var cancelButton = new RoundedButton
            {
                Text = AppText.Get("common.cancel"),
                Bounds = new Rectangle(28, y, 190, 48),
                ButtonColor = Color.FromArgb(242, 244, 247),
                HoverColor = Color.FromArgb(232, 236, 240),
                PressedColor = Color.FromArgb(220, 226, 232),
                TextColor = Color.FromArgb(52, 64, 84)
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
                Bounds = new Rectangle(238, y, 204, 48),
                ButtonColor = AccentGreen,
                HoverColor = Color.FromArgb(19, 145, 111),
                PressedColor = Color.FromArgb(17, 124, 96),
                TextColor = Color.White
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

        protected override CreateParams CreateParams
        {
            get
            {
                var parameters = base.CreateParams;
                parameters.ClassStyle |= CsDropShadow;
                return parameters;
            }
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
            using var border = new Pen(Color.FromArgb(190, 211, 202));
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
            ReminderEnabled = _enabledInput?.Checked ?? true;
            DialogResult = DialogResult.OK;
            Close();
        }

        private void UpdateHint()
        {
            if (TryReadMinutes(out var minutes))
            {
                _hintLabel.ForeColor = TextSecondary;
                _hintLabel.Text = _canEdit
                    ? ReminderThreshold.FormatEquivalent(ReminderThreshold.FromMinutes(minutes))
                    : AppText.Get("reminder.pcConnectedReadonly");
                return;
            }

            _hintLabel.ForeColor = Color.FromArgb(190, 80, 68);
            _hintLabel.Text = AppText.Format(
                "reminder.validation.minutesRange",
                ("min", ReminderThreshold.MinMinutes),
                ("max", ReminderThreshold.MaxMinutes));
        }

        private bool TryReadMinutes(out int minutes)
        {
            return int.TryParse(_minutesInput.Text.Trim(), out minutes)
                && minutes >= ReminderThreshold.MinMinutes
                && minutes <= ReminderThreshold.MaxMinutes;
        }
    }

    private sealed class RoundedPanel : Panel
    {
        public Color FillColor { get; init; } = Color.White;
        public Color BorderColor { get; init; } = EyeCareRemindersForm.BorderColor;
        public int Radius { get; init; } = 18;

        public RoundedPanel()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
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
            Font = AppFonts.Create(13F, FontStyle.Bold, GraphicsUnit.Point);
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

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            var color = _pressed ? PressedColor : _hovered ? HoverColor : ButtonColor;
            using var path = CreateRoundRect(bounds, Height / 2);
            using var fill = new SolidBrush(color);
            e.Graphics.FillPath(fill, path);

            TextRenderer.DrawText(
                e.Graphics,
                Text,
                Font,
                bounds,
                TextColor,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        }
    }

    private class StaticText : Control
    {
        public ContentAlignment TextAlign { get; set; } = ContentAlignment.MiddleLeft;
        public bool WordWrap { get; set; }
        public bool UseEllipsis { get; set; } = true;

        public StaticText()
        {
            TabStop = false;
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true);
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

            TextRenderer.DrawText(e.Graphics, Text, Font, ClientRectangle, ForeColor, CreateTextFormatFlags());
        }

        private TextFormatFlags CreateTextFormatFlags()
        {
            var flags = TextFormatFlags.NoPadding | TextFormatFlags.PreserveGraphicsClipping;
            flags |= WordWrap ? TextFormatFlags.WordBreak : TextFormatFlags.SingleLine;
            if (UseEllipsis)
            {
                flags |= TextFormatFlags.EndEllipsis;
            }

            flags |= TextAlign switch
            {
                ContentAlignment.TopCenter or ContentAlignment.MiddleCenter or ContentAlignment.BottomCenter => TextFormatFlags.HorizontalCenter,
                ContentAlignment.TopRight or ContentAlignment.MiddleRight or ContentAlignment.BottomRight => TextFormatFlags.Right,
                _ => TextFormatFlags.Left
            };

            flags |= TextAlign switch
            {
                ContentAlignment.MiddleLeft or ContentAlignment.MiddleCenter or ContentAlignment.MiddleRight => TextFormatFlags.VerticalCenter,
                ContentAlignment.BottomLeft or ContentAlignment.BottomCenter or ContentAlignment.BottomRight => TextFormatFlags.Bottom,
                _ => TextFormatFlags.Top
            };

            return flags;
        }
    }

    private sealed class ValueText : StaticText
    {
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

            using var font = CreateFittingFont(e.Graphics);
            TextRenderer.DrawText(
                e.Graphics,
                Text,
                font,
                ClientRectangle,
                ForeColor,
                TextFormatFlags.NoPadding
                | TextFormatFlags.SingleLine
                | TextFormatFlags.Right
                | TextFormatFlags.VerticalCenter);
        }

        private Font CreateFittingFont(Graphics graphics)
        {
            for (var size = 15F; size >= 11F; size -= 0.5F)
            {
                var font = AppFonts.Create(size, FontStyle.Bold, GraphicsUnit.Point);
                var measured = TextRenderer.MeasureText(
                    graphics,
                    Text,
                    font,
                    ClientSize,
                    TextFormatFlags.NoPadding | TextFormatFlags.SingleLine);
                if (measured.Width <= ClientSize.Width && measured.Height <= ClientSize.Height)
                {
                    return font;
                }

                font.Dispose();
            }

            return AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point);
        }
    }
}
