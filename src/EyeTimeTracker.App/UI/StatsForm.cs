using System.Drawing.Drawing2D;
using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;
using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.App.UI;

public sealed class StatsForm : Form
{
    private static readonly Color PageBackground = Color.FromArgb(248, 251, 250);
    private static readonly Color PanelBackground = Color.FromArgb(252, 254, 253);
    private static readonly Color SoftGreen = Color.FromArgb(238, 249, 245);
    private static readonly Color AccentGreen = Color.FromArgb(22, 163, 127);
    private static readonly Color AccentBlue = Color.FromArgb(91, 92, 226);
    private static readonly Color AccentYellow = Color.FromArgb(240, 184, 58);
    private static readonly Color AccentRed = Color.FromArgb(233, 104, 104);
    private static readonly Color TextPrimary = Color.FromArgb(17, 24, 39);
    private static readonly Color TextSecondary = Color.FromArgb(101, 114, 137);
    private static readonly Color BorderColor = Color.FromArgb(217, 238, 231);

    private readonly TrackingController _controller;
    private readonly FitTextLabel _dayTotalValue;
    private readonly FitTextLabel _longestSessionValue;
    private readonly FitTextLabel _deviceShareValue;
    private readonly FitTextLabel _reminderCountValue;
    private readonly HourlyHeatChart _hourlyChart;
    private readonly FitTextLabel _summaryTitle;
    private readonly FitTextLabel _summaryLineOne;
    private readonly FitTextLabel _summaryLineTwo;
    private readonly FitTextLabel _summaryLineThree;
    private readonly FitTextLabel _summaryLineFour;
    private readonly WeekBarChart _weekChart;
    private readonly MonthTrendChart _monthChart;
    private readonly ContinuousBandsControl _continuousBands;
    private readonly FitTextLabel _weekNote;
    private readonly FitTextLabel _monthNote;
    private readonly FitTextLabel _continuousNote;
    private readonly FitTextLabel _daySelectorText;
    private readonly FitTextLabel _weekSelectorText;
    private readonly FitTextLabel _monthSelectorText;
    private readonly FitTextLabel _rangeStartSelectorText;
    private readonly FitTextLabel _rangeEndSelectorText;
    private DateOnly _selectedDay;
    private DateOnly _selectedWeekStart;
    private DateOnly _selectedMonthStart;
    private DateOnly _rangeStart;
    private DateOnly _rangeEnd;

    public StatsForm(TrackingController controller, Icon? icon)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        var today = DateOnly.FromDateTime(DateTime.Now);
        _selectedDay = today;
        _selectedWeekStart = today.AddDays(-GetMondayOffset(today.DayOfWeek));
        _selectedMonthStart = new DateOnly(today.Year, today.Month, 1);
        _rangeStart = _selectedMonthStart;
        _rangeEnd = today;

        AutoScaleMode = AutoScaleMode.None;
        Text = AppText.Get("app.statsTitle");
        if (icon is not null)
        {
            Icon = (Icon)icon.Clone();
        }

        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = true;
        ClientSize = new Size(1180, 760);
        BackColor = PageBackground;
        Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point);

        var root = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = PageBackground
        };
        Controls.Add(root);

        root.Controls.Add(new FitTextLabel
        {
            Text = AppText.Get("stats.title"),
            Bounds = new Rectangle(30, 12, 140, 76),
            MaxFontSize = 24F,
            MinFontSize = 21F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });
        root.Controls.Add(new FitTextLabel
        {
            Text = AppText.Get("stats.subtitle"),
            Bounds = new Rectangle(168, 45, 380, 30),
            MaxFontSize = 11F,
            MinFontSize = 10F,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });

        var dayPanel = CreatePanel(new Rectangle(30, 98, 1120, 290));
        root.Controls.Add(dayPanel);
        AddPanelTitle(dayPanel, AppText.Get("stats.daily.title"), new Rectangle(24, 18, 132, 36));
        _daySelectorText = AddPill(dayPanel, SelectorText(AppText.Get("common.today")), new Rectangle(170, 22, 112, 30), ShowDayMenu);
        AddLegend(dayPanel, new Point(314, 27));

        dayPanel.Controls.Add(BuildSmallMetric(AppText.Get("stats.daily.metric.total"), out _dayTotalValue, new Rectangle(24, 70, 155, 78), AccentGreen));
        dayPanel.Controls.Add(BuildSmallMetric(AppText.Get("stats.daily.metric.longest"), out _longestSessionValue, new Rectangle(194, 70, 155, 78), AccentYellow));
        dayPanel.Controls.Add(BuildSmallMetric(AppText.Get("stats.daily.metric.deviceShare"), out _deviceShareValue, new Rectangle(24, 164, 155, 78), TextPrimary));
        dayPanel.Controls.Add(BuildSmallMetric(AppText.Get("stats.daily.metric.reminders"), out _reminderCountValue, new Rectangle(194, 164, 155, 78), TextPrimary));

        _hourlyChart = new HourlyHeatChart
        {
            Bounds = new Rectangle(390, 30, 230, 230),
            BackColor = Color.Transparent
        };
        dayPanel.Controls.Add(_hourlyChart);

        _summaryTitle = AddSummaryLabel(dayPanel, AppText.Get("stats.daily.summary.yesterday"), new Rectangle(690, 18, 180, 36), 13.5F, 12F, TextPrimary, FontStyle.Bold);
        _summaryLineOne = AddSummaryLabel(dayPanel, "", new Rectangle(692, 66, 380, 34), 10.5F, 9F, TextSecondary, FontStyle.Regular);
        _summaryLineTwo = AddSummaryLabel(dayPanel, "", new Rectangle(692, 112, 380, 34), 10.5F, 9F, TextSecondary, FontStyle.Regular);
        _summaryLineThree = AddSummaryLabel(dayPanel, "", new Rectangle(692, 158, 380, 34), 10.5F, 9F, TextSecondary, FontStyle.Regular);
        _summaryLineFour = AddSummaryLabel(dayPanel, "", new Rectangle(692, 204, 380, 34), 10.5F, 9F, AccentGreen, FontStyle.Bold);

        var weekPanel = CreatePanel(new Rectangle(30, 410, 350, 300));
        root.Controls.Add(weekPanel);
        AddPanelTitle(weekPanel, AppText.Get("stats.week.title"), new Rectangle(22, 18, 150, 34));
        _weekSelectorText = AddPill(weekPanel, SelectorText(AppText.Get("common.thisWeek")), new Rectangle(232, 18, 96, 30), ShowWeekMenu);
        _weekChart = new WeekBarChart { Bounds = new Rectangle(22, 62, 306, 180), BackColor = Color.Transparent };
        weekPanel.Controls.Add(_weekChart);
        _weekNote = AddNote(weekPanel, new Rectangle(22, 250, 300, 34));

        var monthPanel = CreatePanel(new Rectangle(405, 410, 350, 300));
        root.Controls.Add(monthPanel);
        AddPanelTitle(monthPanel, AppText.Get("stats.month.title"), new Rectangle(22, 18, 150, 34));
        _monthSelectorText = AddPill(monthPanel, SelectorText(AppText.Get("common.thisMonth")), new Rectangle(232, 18, 96, 30), ShowMonthMenu);
        _monthChart = new MonthTrendChart { Bounds = new Rectangle(22, 64, 306, 170), BackColor = Color.Transparent };
        monthPanel.Controls.Add(_monthChart);
        _monthNote = AddNote(monthPanel, new Rectangle(22, 250, 300, 34));

        var continuousPanel = CreatePanel(new Rectangle(780, 410, 370, 300));
        root.Controls.Add(continuousPanel);
        AddPanelTitle(continuousPanel, AppText.Get("stats.sessions.title"), new Rectangle(22, 18, 150, 34));
        _rangeStartSelectorText = AddPill(continuousPanel, SelectorText("7/1"), new Rectangle(178, 18, 84, 30), ShowRangeStartMenu);
        _rangeEndSelectorText = AddPill(continuousPanel, SelectorText("7/1"), new Rectangle(270, 18, 84, 30), ShowRangeEndMenu);
        _continuousBands = new ContinuousBandsControl { Bounds = new Rectangle(22, 68, 326, 150), BackColor = Color.Transparent };
        continuousPanel.Controls.Add(_continuousBands);
        _continuousNote = AddNote(continuousPanel, new Rectangle(22, 235, 320, 48));

        _controller.Updated += OnTrackingUpdated;
        RefreshStats();
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _controller.Updated -= OnTrackingUpdated;
        }

        base.Dispose(disposing);
    }

    private void OnTrackingUpdated(object? sender, TrackingUpdatedEventArgs e)
    {
        if (!IsDisposed && IsHandleCreated)
        {
            BeginInvoke((MethodInvoker)RefreshStats);
        }
    }

    private void RefreshStats()
    {
        var records = _controller.GetRecordsSnapshot();
        var actualToday = DateOnly.FromDateTime(DateTime.Now);
        var todayRecord = records.FirstOrDefault(record => record.Date == _selectedDay) ?? new DailyRecord(_selectedDay);
        AppState.NormalizeRecord(todayRecord);

        var weekRecords = Enumerable.Range(0, 7)
            .Select(offset => records.FirstOrDefault(record => record.Date == _selectedWeekStart.AddDays(offset)) ?? new DailyRecord(_selectedWeekStart.AddDays(offset)))
            .ToList();
        foreach (var record in weekRecords)
        {
            AppState.NormalizeRecord(record);
        }

        var daysInMonth = _selectedMonthStart.Year == actualToday.Year && _selectedMonthStart.Month == actualToday.Month
            ? actualToday.Day
            : DateTime.DaysInMonth(_selectedMonthStart.Year, _selectedMonthStart.Month);
        var monthRecords = Enumerable.Range(0, daysInMonth)
            .Select(offset => records.FirstOrDefault(record => record.Date == _selectedMonthStart.AddDays(offset)) ?? new DailyRecord(_selectedMonthStart.AddDays(offset)))
            .ToList();
        foreach (var record in monthRecords)
        {
            AppState.NormalizeRecord(record);
        }

        var sessions = SessionValues(todayRecord).ToList();
        var longest = sessions.Count == 0 ? todayRecord.CurrentSessionSeconds : sessions.Max();
        var deviceBreakdown = _controller.GetDeviceBreakdown(_selectedDay);
        var isShowingToday = _selectedDay == actualToday;
        var summaryDate = isShowingToday ? actualToday.AddDays(-1) : _selectedDay;
        var summaryRecord = records.FirstOrDefault(record => record.Date == summaryDate) ?? new DailyRecord(summaryDate);
        AppState.NormalizeRecord(summaryRecord);
        var summarySessions = SessionValues(summaryRecord).ToList();
        var summaryLongest = summarySessions.Count == 0 ? summaryRecord.CurrentSessionSeconds : summarySessions.Max();
        var summaryDeviceBreakdown = _controller.GetDeviceBreakdown(summaryDate);
        var weekBreakdowns = Enumerable.Range(0, 7)
            .Select(offset => _controller.GetDeviceBreakdown(_selectedWeekStart.AddDays(offset)))
            .ToList();

        _dayTotalValue.Text = FormatDuration(todayRecord.TotalSeconds);
        _dayTotalValue.ForeColor = TodayColor(todayRecord.TotalSeconds);
        _longestSessionValue.Text = FormatDuration(longest);
        _deviceShareValue.Text = $"{deviceBreakdown.PcPercent}%/{deviceBreakdown.PhonePercent}%";
        _reminderCountValue.Text = CountText(ReminderDisplayCount.FromSeconds(todayRecord.TotalSeconds, _controller.Settings));
        _hourlyChart.HourlySeconds = todayRecord.HourlySeconds;
        _hourlyChart.SetSourceHourlySeconds(deviceBreakdown.PcHourlySeconds, deviceBreakdown.PhoneHourlySeconds);

        _summaryTitle.Text = isShowingToday ? AppText.Get("stats.daily.summary.yesterday") : AppText.Get("stats.daily.summary.day");
        _summaryLineOne.Text = AppText.Format("stats.daily.summary.peak", ("range", PeakHourText(summaryRecord.HourlySeconds)));
        _summaryLineTwo.Text = AppText.Format("stats.daily.summary.longest", ("duration", FormatDuration(summaryLongest)));
        _summaryLineThree.Text = SummarySourceText(summaryDeviceBreakdown);
        _summaryLineFour.Text = SummaryCareText(
            summaryLongest,
            NightSeconds(summaryRecord.HourlySeconds),
            summaryDeviceBreakdown.PhonePercent);

        _weekChart.Records = weekRecords;
        _weekChart.DeviceBreakdowns = weekBreakdowns;
        _weekNote.Text = AppText.Format("stats.week.total", ("duration", FormatDuration(weekRecords.Sum(record => record.TotalSeconds))));

        _monthChart.Records = monthRecords;
        _monthNote.Text = AppText.Format("stats.month.recordedDays", ("count", monthRecords.Count(record => record.TotalSeconds > 0)));

        var rangeStart = _rangeStart <= _rangeEnd ? _rangeStart : _rangeEnd;
        var rangeEnd = _rangeStart <= _rangeEnd ? _rangeEnd : _rangeStart;
        var rangeSessions = records
            .Where(record => record.Date >= rangeStart && record.Date <= rangeEnd)
            .SelectMany(SessionValues)
            .ToList();
        _continuousBands.Sessions = rangeSessions;
        _continuousNote.Text = rangeSessions.Count == 0
            ? AppText.Get("stats.sessions.empty")
            : AppText.Format("stats.sessions.advice", ("duration", FormatDuration(rangeSessions.Max())));
        UpdateSelectorTexts(actualToday);
    }

    private void ShowDayMenu(object? sender, EventArgs e)
    {
        ShowCalendarPicker(sender, CalendarSelectionMode.Day, _selectedDay, date =>
        {
            _selectedDay = date;
            RefreshStats();
        });
    }

    private void ShowWeekMenu(object? sender, EventArgs e)
    {
        ShowCalendarPicker(sender, CalendarSelectionMode.Week, _selectedWeekStart, weekStart =>
        {
            _selectedWeekStart = weekStart;
            RefreshStats();
        });
    }

    private void ShowMonthMenu(object? sender, EventArgs e)
    {
        ShowCalendarPicker(sender, CalendarSelectionMode.Month, _selectedMonthStart, monthStart =>
        {
            _selectedMonthStart = new DateOnly(monthStart.Year, monthStart.Month, 1);
            RefreshStats();
        });
    }

    private void ShowRangeStartMenu(object? sender, EventArgs e)
    {
        ShowRangeMenu(sender, isStart: true);
    }

    private void ShowRangeEndMenu(object? sender, EventArgs e)
    {
        ShowRangeMenu(sender, isStart: false);
    }

    private void ShowRangeMenu(object? sender, bool isStart)
    {
        var picker = new CalendarPickerForm(CalendarSelectionMode.Range, _rangeStart, _rangeStart, _rangeEnd);
        picker.RangeSelected += range =>
        {
            _rangeStart = range.Start;
            _rangeEnd = range.End;
            RefreshStats();
        };
        ShowPicker(sender, picker);
    }

    private List<DateOnly> DateCandidates()
    {
        var today = DateOnly.FromDateTime(DateTime.Now);
        var dates = _controller.GetRecordsSnapshot()
            .Select(record => record.Date)
            .Append(today)
            .Append(_selectedDay)
            .Append(_rangeStart)
            .Append(_rangeEnd)
            .Distinct()
            .ToList();
        dates.Sort();
        return dates;
    }

    private static void ShowCalendarPicker(object? sender, CalendarSelectionMode mode, DateOnly initialDate, Action<DateOnly> onSelected)
    {
        var picker = new CalendarPickerForm(mode, initialDate, initialDate, initialDate);
        picker.DateSelected += onSelected;
        ShowPicker(sender, picker);
    }

    private static void ShowPicker(object? sender, Form picker)
    {
        if (sender is Control control)
        {
            var screen = control.PointToScreen(new Point(0, control.Height + 6));
            picker.StartPosition = FormStartPosition.Manual;
            picker.Location = KeepOnScreen(screen, picker.Size);
            picker.Show(control.FindForm());
            return;
        }

        picker.StartPosition = FormStartPosition.Manual;
        picker.Location = KeepOnScreen(Cursor.Position, picker.Size);
        picker.Show();
    }

    private static Point KeepOnScreen(Point desired, Size size)
    {
        var area = Screen.FromPoint(desired).WorkingArea;
        var x = Math.Min(Math.Max(area.Left, desired.X), Math.Max(area.Left, area.Right - size.Width));
        var y = Math.Min(Math.Max(area.Top, desired.Y), Math.Max(area.Top, area.Bottom - size.Height));
        return new Point(x, y);
    }

    private void UpdateSelectorTexts(DateOnly actualToday)
    {
        _daySelectorText.Text = SelectorText(DateLabel(_selectedDay, actualToday));
        _weekSelectorText.Text = SelectorText(WeekLabel(_selectedWeekStart, actualToday));
        _monthSelectorText.Text = SelectorText(MonthLabel(_selectedMonthStart, actualToday));
        _rangeStartSelectorText.Text = SelectorText(CompactDateLabel(_rangeStart));
        _rangeEndSelectorText.Text = SelectorText(CompactDateLabel(_rangeEnd));
    }

    private static string DateLabel(DateOnly date)
    {
        return DateLabel(date, DateOnly.FromDateTime(DateTime.Now));
    }

    private static string DateLabel(DateOnly date, DateOnly today)
    {
        return date == today ? AppText.Get("common.today") : ShortDateLabel(date);
    }

    private static string WeekLabel(DateOnly weekStart, DateOnly today)
    {
        var currentWeekStart = today.AddDays(-GetMondayOffset(today.DayOfWeek));
        return weekStart == currentWeekStart ? AppText.Get("common.thisWeek") : CompactDateLabel(weekStart);
    }

    private static string MonthLabel(DateOnly monthStart, DateOnly today)
    {
        return monthStart.Year == today.Year && monthStart.Month == today.Month
            ? AppText.Get("common.thisMonth")
            : AppText.Format("calendar.monthCompact", ("month", monthStart.Month));
    }

    private static string ShortDateLabel(DateOnly date)
    {
        return $"{date.Month}/{date.Day}";
    }

    private static string CompactDateLabel(DateOnly date)
    {
        return $"{date.Month}/{date.Day}";
    }

    private static IEnumerable<long> SessionValues(DailyRecord record)
    {
        AppState.NormalizeRecord(record);
        foreach (var seconds in record.SessionSeconds.Where(seconds => seconds > 0))
        {
            yield return seconds;
        }

        if (record.CurrentSessionSeconds > 0)
        {
            yield return record.CurrentSessionSeconds;
        }
    }

    private static string PeakHourText(long[] hourlySeconds)
    {
        var max = hourlySeconds.Length == 0 ? 0 : hourlySeconds.Max();
        if (max <= 0)
        {
            return AppText.Get("common.none");
        }

        var hour = Array.IndexOf(hourlySeconds, max);
        return AppText.Format("time.hourRange", ("start:00", hour.ToString("00")), ("end:00", ((hour + 1) % 24).ToString("00")));
    }

    private static string SummarySourceText(UsageDeviceBreakdown breakdown)
    {
        if (breakdown.PcSeconds + breakdown.PhoneSeconds <= 0)
        {
            return AppText.Get("stats.source.empty");
        }

        return breakdown.PhonePercent >= breakdown.PcPercent
            ? AppText.Format("stats.source.phoneHighShort", ("percent", breakdown.PhonePercent))
            : AppText.Format("stats.source.pcHigh", ("percent", breakdown.PcPercent));
    }

    private static string SummaryCareText(long longestSessionSeconds, long nightSeconds, int phonePercent)
    {
        if (longestSessionSeconds >= 45L * 60L)
        {
            return AppText.Get("stats.care.continuousHigh");
        }

        if (nightSeconds >= 60L * 60L)
        {
            return AppText.Get("stats.care.nightHigh");
        }

        if (phonePercent >= 60)
        {
            return AppText.Get("stats.care.phoneHigh");
        }

        return longestSessionSeconds <= 0 && nightSeconds <= 0
            ? AppText.Get("stats.care.noPressure")
            : AppText.Get("stats.care.steady");
    }

    private static long NightSeconds(long[] hourlySeconds)
    {
        long total = 0;
        for (var hour = 0; hour < hourlySeconds.Length; hour++)
        {
            if (hour >= 22 || hour < 6)
            {
                total += hourlySeconds[hour];
            }
        }

        return total;
    }

    private static int GetMondayOffset(DayOfWeek dayOfWeek)
    {
        return dayOfWeek == DayOfWeek.Sunday ? 6 : (int)dayOfWeek - (int)DayOfWeek.Monday;
    }

    private static RoundedPanel CreatePanel(Rectangle bounds)
    {
        return new RoundedPanel
        {
            Bounds = bounds,
            FillColor = Color.FromArgb(252, 254, 253),
            BorderColor = BorderColor,
            Radius = 22
        };
    }

    private static Control BuildSmallMetric(string label, out FitTextLabel value, Rectangle bounds, Color valueColor)
    {
        var panel = new RoundedPanel
        {
            Bounds = bounds,
            FillColor = Color.White,
            BorderColor = BorderColor,
            Radius = 16
        };
        panel.Controls.Add(new FitTextLabel
        {
            Text = label,
            Bounds = new Rectangle(16, 12, bounds.Width - 30, 24),
            MaxFontSize = 9.5F,
            MinFontSize = 8.5F,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });
        value = new FitTextLabel
        {
            Text = AppText.Get("duration.zeroMinutes"),
            Bounds = new Rectangle(16, 38, bounds.Width - 28, 32),
            MaxFontSize = 17F,
            MinFontSize = 11F,
            FontStyle = FontStyle.Bold,
            ForeColor = valueColor,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        panel.Controls.Add(value);
        return panel;
    }

    private static void AddPanelTitle(Control parent, string title, Rectangle bounds)
    {
        parent.Controls.Add(new FitTextLabel
        {
            Text = title,
            Bounds = bounds,
            MaxFontSize = 13F,
            MinFontSize = 11F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        });
    }

    private static FitTextLabel AddPill(Control parent, string text, Rectangle bounds, EventHandler? clickHandler = null)
    {
        var pill = new RoundedPanel
        {
            Bounds = bounds,
            FillColor = SoftGreen,
            BorderColor = BorderColor,
            Radius = bounds.Height / 2
        };
        var label = new FitTextLabel
        {
            Text = text,
            Dock = DockStyle.Fill,
            MaxFontSize = 8F,
            MinFontSize = 7F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleCenter
        };
        pill.Controls.Add(label);
        if (clickHandler is not null)
        {
            pill.Cursor = Cursors.Hand;
            label.Cursor = Cursors.Hand;
            pill.Click += clickHandler;
            label.Click += clickHandler;
        }

        parent.Controls.Add(pill);
        return label;
    }

    private static void AddLegend(Control parent, Point location)
    {
        parent.Controls.Add(new LegendDot { Bounds = new Rectangle(location.X, location.Y, 14, 14), DotColor = AccentGreen });
        parent.Controls.Add(new FitTextLabel
        {
            Text = AppText.Get("common.pc"),
            Bounds = new Rectangle(location.X + 20, location.Y - 5, 44, 24),
            MaxFontSize = 9F,
            MinFontSize = 8F,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent
        });
        parent.Controls.Add(new LegendDot { Bounds = new Rectangle(location.X + 76, location.Y, 14, 14), DotColor = AccentBlue });
        parent.Controls.Add(new FitTextLabel
        {
            Text = AppText.Get("common.phone"),
            Bounds = new Rectangle(location.X + 96, location.Y - 5, 44, 24),
            MaxFontSize = 9F,
            MinFontSize = 8F,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent
        });
    }

    private static FitTextLabel AddSummaryLabel(
        Control parent,
        string text,
        Rectangle bounds,
        float maxFontSize,
        float minFontSize,
        Color color,
        FontStyle style)
    {
        var label = new FitTextLabel
        {
            Text = text,
            Bounds = bounds,
            MaxFontSize = maxFontSize,
            MinFontSize = minFontSize,
            FontStyle = style,
            ForeColor = color,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        parent.Controls.Add(label);
        return label;
    }

    private static FitTextLabel AddInsight(Control parent, string label, Rectangle bounds)
    {
        var title = new FitTextLabel
        {
            Text = label,
            Bounds = new Rectangle(bounds.X, bounds.Y, bounds.Width, 24),
            MaxFontSize = 9F,
            MinFontSize = 7.5F,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        parent.Controls.Add(title);

        var value = new FitTextLabel
        {
            Text = AppText.Get("common.none"),
            Bounds = new Rectangle(bounds.X, bounds.Y + 28, bounds.Width, 30),
            MaxFontSize = 12F,
            MinFontSize = 8.5F,
            FontStyle = FontStyle.Bold,
            ForeColor = TextPrimary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        parent.Controls.Add(value);
        return value;
    }

    private static FitTextLabel AddNote(Control parent, Rectangle bounds)
    {
        var note = new FitTextLabel
        {
            Bounds = bounds,
            MaxFontSize = 8.5F,
            MinFontSize = 7.5F,
            ForeColor = TextSecondary,
            BackColor = Color.Transparent,
            TextAlign = ContentAlignment.MiddleLeft
        };
        parent.Controls.Add(note);
        return note;
    }

    private static string FormatDuration(long totalSeconds)
    {
        var duration = TimeSpan.FromSeconds(Math.Max(0, totalSeconds));
        var totalHours = (int)duration.TotalHours;
        return totalHours > 0
            ? AppText.Format("duration.hoursMinutesPadded", ("hours", totalHours), ("minutes:00", duration.Minutes.ToString("00")))
            : AppText.Format("duration.minutes", ("minutes", duration.Minutes));
    }

    private static string FormatTooltipMinutes(long totalSeconds)
    {
        return AppText.Format("duration.minutes", ("minutes", Math.Max(0L, totalSeconds) / 60L));
    }

    private static string FormatCompactHours(long totalSeconds)
    {
        var safeSeconds = Math.Max(0L, totalSeconds);
        var halfHourUnits = (long)Math.Round(safeSeconds / 1800D, MidpointRounding.AwayFromZero);
        var wholeHours = halfHourUnits / 2L;

        return halfHourUnits % 2L == 0L
            ? AppText.Format("duration.hours", ("hours", wholeHours))
            : AppText.Format("duration.halfHours", ("hours", wholeHours));
    }

    private static string CountText(int count)
    {
        return AppText.Format("stats.sessions.count", ("count", count));
    }

    private static string[] WeekdayLabels()
    {
        return new[]
        {
            AppText.Get("calendar.weekday.mon"),
            AppText.Get("calendar.weekday.tue"),
            AppText.Get("calendar.weekday.wed"),
            AppText.Get("calendar.weekday.thu"),
            AppText.Get("calendar.weekday.fri"),
            AppText.Get("calendar.weekday.sat"),
            AppText.Get("calendar.weekday.sun")
        };
    }

    private static string SelectorText(string text)
    {
        return text + " ▾";
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

    private enum CalendarSelectionMode
    {
        Day,
        Week,
        Month,
        Range
    }

    private sealed class CalendarPickerForm : Form
    {
        private readonly CalendarSelectionMode _mode;
        private readonly CalendarMonthView _leftMonth;
        private readonly CalendarMonthView? _rightMonth;
        private readonly FitTextLabel _leftTitle;
        private readonly FitTextLabel? _rightTitle;
        private DateOnly _leftMonthStart;
        private DateOnly _selectedDate;
        private DateOnly _rangeStart;
        private DateOnly _rangeEnd;
        private bool _pickingRangeEnd = true;

        public event Action<DateOnly>? DateSelected;
        public event Action<(DateOnly Start, DateOnly End)>? RangeSelected;

        public CalendarPickerForm(CalendarSelectionMode mode, DateOnly initialDate, DateOnly rangeStart, DateOnly rangeEnd)
        {
            _mode = mode;
            _leftMonthStart = new DateOnly(initialDate.Year, initialDate.Month, 1);
            _selectedDate = initialDate;
            _rangeStart = rangeStart <= rangeEnd ? rangeStart : rangeEnd;
            _rangeEnd = rangeStart <= rangeEnd ? rangeEnd : rangeStart;

            AutoScaleMode = AutoScaleMode.None;
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            BackColor = PanelBackground;
            Font = AppFonts.Create(9F);
            Size = mode == CalendarSelectionMode.Range ? new Size(720, 418) : new Size(368, 390);

            var root = new RoundedPanel
            {
                Dock = DockStyle.Fill,
                FillColor = Color.White,
                BorderColor = Color.FromArgb(210, 226, 220),
                Radius = 24
            };
            Controls.Add(root);

            _leftTitle = AddCalendarHeader(root, new Rectangle(18, 18, mode == CalendarSelectionMode.Range ? 330 : 332, 42), true);
            _leftMonth = CreateMonthView(new Rectangle(18, 68, mode == CalendarSelectionMode.Range ? 330 : 332, 250));
            root.Controls.Add(_leftMonth);

            if (mode == CalendarSelectionMode.Range)
            {
                _rightTitle = AddCalendarHeader(root, new Rectangle(372, 18, 330, 42), false);
                _rightMonth = CreateMonthView(new Rectangle(372, 68, 330, 250));
                root.Controls.Add(_rightMonth);
            }

            if (mode == CalendarSelectionMode.Range)
            {
                var tip = new FitTextLabel
                {
                    Text = AppText.Get("calendar.pickRange"),
                    Bounds = new Rectangle(24, 330, 300, 28),
                    MaxFontSize = 9F,
                    MinFontSize = 8F,
                    ForeColor = TextSecondary,
                    BackColor = Color.Transparent
                };
                root.Controls.Add(tip);

                root.Controls.Add(CreateActionButton(AppText.Get("common.cancel"), new Rectangle(496, 330, 86, 40), Color.FromArgb(238, 242, 241), TextPrimary, (_, _) => Close()));
                root.Controls.Add(CreateActionButton(AppText.Get("common.apply"), new Rectangle(596, 330, 86, 40), AccentGreen, Color.White, (_, _) =>
                {
                    RangeSelected?.Invoke((_rangeStart, _rangeEnd));
                    Close();
                }));
            }
            else
            {
                var tipText = mode switch
                {
                    CalendarSelectionMode.Week => AppText.Get("calendar.pickWeek"),
                    CalendarSelectionMode.Month => AppText.Get("calendar.pickMonth"),
                    _ => AppText.Get("calendar.pickDay")
                };
                var tip = new FitTextLabel
                {
                    Text = tipText,
                    Bounds = new Rectangle(22, 330, 220, 28),
                    MaxFontSize = 9F,
                    MinFontSize = 8F,
                    ForeColor = TextSecondary,
                    BackColor = Color.Transparent
                };
                root.Controls.Add(tip);
                root.Controls.Add(CreateActionButton(AppText.Get("common.cancel"), new Rectangle(260, 328, 86, 40), Color.FromArgb(238, 242, 241), TextPrimary, (_, _) => Close()));
            }

            RefreshMonths();
        }

        protected override void OnDeactivate(EventArgs e)
        {
            Close();
            base.OnDeactivate(e);
        }

        private CalendarMonthView CreateMonthView(Rectangle bounds)
        {
            var view = new CalendarMonthView
            {
                Bounds = bounds,
                Mode = _mode,
                SelectedDate = _selectedDate,
                RangeStart = _rangeStart,
                RangeEnd = _rangeEnd,
                BackColor = Color.Transparent
            };
            view.DateClicked += OnDateClicked;
            return view;
        }

        private FitTextLabel AddCalendarHeader(Control parent, Rectangle bounds, bool left)
        {
            var previousYear = CreateHeaderButton("«", new Rectangle(bounds.X, bounds.Y + 4, 34, 34), (_, _) => MoveMonth(left ? -12 : 0));
            var previousMonth = CreateHeaderButton("‹", new Rectangle(bounds.X + 38, bounds.Y + 4, 34, 34), (_, _) => MoveMonth(left ? -1 : 0));
            parent.Controls.Add(previousYear);
            parent.Controls.Add(previousMonth);

            var title = new FitTextLabel
            {
                Bounds = new Rectangle(bounds.X + 76, bounds.Y, bounds.Width - 152, bounds.Height),
                MaxFontSize = 14F,
                MinFontSize = 12F,
                FontStyle = FontStyle.Bold,
                ForeColor = TextPrimary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleCenter
            };
            parent.Controls.Add(title);

            var nextMonth = CreateHeaderButton("›", new Rectangle(bounds.Right - 72, bounds.Y + 4, 34, 34), (_, _) => MoveMonth(left ? 1 : 0));
            var nextYear = CreateHeaderButton("»", new Rectangle(bounds.Right - 34, bounds.Y + 4, 34, 34), (_, _) => MoveMonth(left ? 12 : 0));
            parent.Controls.Add(nextMonth);
            parent.Controls.Add(nextYear);
            return title;
        }

        private static Control CreateHeaderButton(string text, Rectangle bounds, EventHandler click)
        {
            var label = new FitTextLabel
            {
                Text = text,
                Bounds = bounds,
                MaxFontSize = 14F,
                MinFontSize = 12F,
                FontStyle = FontStyle.Bold,
                ForeColor = TextSecondary,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleCenter,
                Cursor = Cursors.Hand
            };
            label.Click += click;
            return label;
        }

        private static Control CreateActionButton(string text, Rectangle bounds, Color fill, Color fore, EventHandler click)
        {
            var panel = new RoundedPanel
            {
                Bounds = bounds,
                FillColor = fill,
                BorderColor = fill,
                Radius = bounds.Height / 2,
                Cursor = Cursors.Hand
            };
            var label = new FitTextLabel
            {
                Text = text,
                Dock = DockStyle.Fill,
                MaxFontSize = 10F,
                MinFontSize = 9F,
                FontStyle = FontStyle.Bold,
                ForeColor = fore,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleCenter,
                Cursor = Cursors.Hand
            };
            panel.Controls.Add(label);
            panel.Click += click;
            label.Click += click;
            return panel;
        }

        private void MoveMonth(int deltaMonths)
        {
            if (deltaMonths == 0)
            {
                return;
            }

            _leftMonthStart = _leftMonthStart.AddMonths(deltaMonths);
            RefreshMonths();
        }

        private void RefreshMonths()
        {
            _leftTitle.Text = AppText.Format("calendar.monthTitle", ("year", _leftMonthStart.Year), ("month", _leftMonthStart.Month));
            _leftMonth.DisplayMonth = _leftMonthStart;
            _leftMonth.SelectedDate = _selectedDate;
            _leftMonth.RangeStart = _rangeStart;
            _leftMonth.RangeEnd = _rangeEnd;
            _leftMonth.Invalidate();

            if (_rightMonth is not null && _rightTitle is not null)
            {
                var rightStart = _leftMonthStart.AddMonths(1);
                _rightTitle.Text = AppText.Format("calendar.monthTitle", ("year", rightStart.Year), ("month", rightStart.Month));
                _rightMonth.DisplayMonth = rightStart;
                _rightMonth.SelectedDate = _selectedDate;
                _rightMonth.RangeStart = _rangeStart;
                _rightMonth.RangeEnd = _rangeEnd;
                _rightMonth.Invalidate();
            }
        }

        private void OnDateClicked(DateOnly date)
        {
            if (_mode == CalendarSelectionMode.Range)
            {
                if (!_pickingRangeEnd || date <= _rangeStart)
                {
                    _rangeStart = date;
                    _rangeEnd = date;
                    _pickingRangeEnd = true;
                }
                else
                {
                    _rangeEnd = date;
                    _pickingRangeEnd = false;
                }

                RefreshMonths();
                return;
            }

            var selected = _mode switch
            {
                CalendarSelectionMode.Week => date.AddDays(-GetMondayOffset(date.DayOfWeek)),
                CalendarSelectionMode.Month => new DateOnly(date.Year, date.Month, 1),
                _ => date
            };
            DateSelected?.Invoke(selected);
            Close();
        }
    }

    private sealed class CalendarMonthView : Control
    {
        public CalendarSelectionMode Mode { get; set; }
        public DateOnly DisplayMonth { get; set; } = new(DateTime.Now.Year, DateTime.Now.Month, 1);
        public DateOnly SelectedDate { get; set; } = DateOnly.FromDateTime(DateTime.Now);
        public DateOnly RangeStart { get; set; } = DateOnly.FromDateTime(DateTime.Now);
        public DateOnly RangeEnd { get; set; } = DateOnly.FromDateTime(DateTime.Now);

        public event Action<DateOnly>? DateClicked;

        public CalendarMonthView()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var cellWidth = Width / 7F;
            var weekdayHeight = 28F;
            var cellHeight = (Height - weekdayHeight) / 6F;
            using var weekdayFont = AppFonts.Create(9F);
            using var dayFont = AppFonts.Create(10F);
            using var selectedFont = AppFonts.Create(10F, FontStyle.Bold);
            using var weekdayBrush = new SolidBrush(TextSecondary);
            using var normalBrush = new SolidBrush(TextPrimary);
            using var mutedBrush = new SolidBrush(Color.FromArgb(174, 184, 194));
            using var selectedBrush = new SolidBrush(Color.White);
            using var softBrush = new SolidBrush(SoftGreen);
            using var activeBrush = new SolidBrush(AccentGreen);

            for (var i = 0; i < 7; i++)
            {
                DrawCenteredText(e.Graphics, WeekdayLabels()[i], weekdayFont, weekdayBrush, new RectangleF(i * cellWidth, 0, cellWidth, weekdayHeight));
            }

            foreach (var day in VisibleDays())
            {
                var index = day.Index;
                var col = index % 7;
                var row = index / 7;
                var rect = new RectangleF(col * cellWidth + 3, weekdayHeight + row * cellHeight + 4, cellWidth - 6, cellHeight - 8);
                var inMonth = day.Date.Month == DisplayMonth.Month;
                var selected = IsSelected(day.Date);
                var inRange = IsInRange(day.Date);

                if (inRange && !selected)
                {
                    e.Graphics.FillRectangle(softBrush, rect);
                }

                if (selected)
                {
                    using var path = CreateRoundRect(Rectangle.Round(rect), 10);
                    e.Graphics.FillPath(activeBrush, path);
                }

                var brush = selected ? selectedBrush : inMonth ? normalBrush : mutedBrush;
                DrawCenteredText(e.Graphics, day.Date.Day.ToString(), selected ? selectedFont : dayFont, brush, rect);

                if (day.Date == DateOnly.FromDateTime(DateTime.Now) && !selected)
                {
                    using var todayBrush = new SolidBrush(AccentBlue);
                    e.Graphics.FillEllipse(todayBrush, rect.X + rect.Width / 2F - 2.5F, rect.Bottom - 6F, 5F, 5F);
                }
            }
        }

        protected override void OnMouseClick(MouseEventArgs e)
        {
            var date = HitTest(e.Location);
            if (date is not null)
            {
                DateClicked?.Invoke(date.Value);
            }

            base.OnMouseClick(e);
        }

        private DateOnly? HitTest(Point point)
        {
            var weekdayHeight = 28F;
            if (point.Y < weekdayHeight)
            {
                return null;
            }

            var cellWidth = Width / 7F;
            var cellHeight = (Height - weekdayHeight) / 6F;
            var col = (int)(point.X / cellWidth);
            var row = (int)((point.Y - weekdayHeight) / cellHeight);
            if (col < 0 || col > 6 || row < 0 || row > 5)
            {
                return null;
            }

            var index = row * 7 + col;
            return VisibleDays().FirstOrDefault(day => day.Index == index).Date;
        }

        private bool IsSelected(DateOnly date)
        {
            if (Mode == CalendarSelectionMode.Range)
            {
                return date == RangeStart || date == RangeEnd;
            }

            if (Mode == CalendarSelectionMode.Week)
            {
                var weekStart = SelectedDate.AddDays(-GetMondayOffset(SelectedDate.DayOfWeek));
                return date >= weekStart && date <= weekStart.AddDays(6) && (date.DayOfWeek is DayOfWeek.Monday or DayOfWeek.Sunday);
            }

            if (Mode == CalendarSelectionMode.Month)
            {
                return date.Day == 1 && date.Year == SelectedDate.Year && date.Month == SelectedDate.Month;
            }

            return date == SelectedDate;
        }

        private bool IsInRange(DateOnly date)
        {
            if (Mode == CalendarSelectionMode.Range)
            {
                return date >= RangeStart && date <= RangeEnd;
            }

            if (Mode == CalendarSelectionMode.Week)
            {
                var weekStart = SelectedDate.AddDays(-GetMondayOffset(SelectedDate.DayOfWeek));
                return date >= weekStart && date <= weekStart.AddDays(6);
            }

            if (Mode == CalendarSelectionMode.Month)
            {
                return date.Year == SelectedDate.Year && date.Month == SelectedDate.Month;
            }

            return false;
        }

        private IEnumerable<(DateOnly Date, int Index)> VisibleDays()
        {
            var offset = GetMondayOffset(DisplayMonth.DayOfWeek);
            var first = DisplayMonth.AddDays(-offset);
            for (var i = 0; i < 42; i++)
            {
                yield return (first.AddDays(i), i);
            }
        }

        private static void DrawCenteredText(Graphics graphics, string text, Font font, Brush brush, RectangleF rect)
        {
            using var format = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
            graphics.DrawString(text, font, brush, rect, format);
        }
    }

    private sealed class HourlyHeatChart : Control
    {
        private long[] _hourlySeconds = new long[24];
        private long[] _pcHourlySeconds = new long[24];
        private long[] _phoneHourlySeconds = new long[24];
        private readonly ToolTip _tooltip = CreateChartToolTip();
        private readonly List<ChartHit> _hits = new();
        private string? _visibleTip;

        public HourlyHeatChart()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
        }

        public long[] HourlySeconds
        {
            get => _hourlySeconds;
            set
            {
                _hourlySeconds = value?.Length == 24 ? (long[])value.Clone() : new long[24];
                Invalidate();
            }
        }

        public void SetSourceHourlySeconds(long[] pcHourlySeconds, long[] phoneHourlySeconds)
        {
            _pcHourlySeconds = pcHourlySeconds?.Length == 24 ? (long[])pcHourlySeconds.Clone() : new long[24];
            _phoneHourlySeconds = phoneHourlySeconds?.Length == 24 ? (long[])phoneHourlySeconds.Clone() : new long[24];
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            _hits.Clear();
            var center = new PointF(Width / 2F, Height / 2F);
            var outerRadius = Math.Min(Width, Height) / 2F - 30F;
            var innerRadius = 42F;
            using var border = new Pen(BorderColor, 2F);
            e.Graphics.DrawEllipse(border, center.X - outerRadius, center.Y - outerRadius, outerRadius * 2F, outerRadius * 2F);

            const long hourSeconds = 3600L;
            var maxSeconds = hourSeconds;
            var hasSource = _pcHourlySeconds.Sum() + _phoneHourlySeconds.Sum() > 0;
            for (var hour = 0; hour < 24; hour++)
            {
                var pcSeconds = hasSource ? _pcHourlySeconds[hour] : _hourlySeconds[hour];
                var phoneSeconds = hasSource ? _phoneHourlySeconds[hour] : 0;
                (pcSeconds, phoneSeconds) = CapHourSourceSeconds(pcSeconds, phoneSeconds);
                var seconds = pcSeconds + phoneSeconds;
                if (seconds <= 0)
                {
                    continue;
                }

                var angle = -90F + hour * 15F;
                var length = 10F + (float)seconds / maxSeconds * (outerRadius - innerRadius - 14F);
                var bounds = DrawSourceHourBar(e.Graphics, center, angle, innerRadius, length, pcSeconds, phoneSeconds);
                bounds.Inflate(8F, 8F);
                _hits.Add(new ChartHit(bounds, FormatTooltipMinutes(seconds)));
            }

            DrawHourLabels(e.Graphics, center, outerRadius);

            using var centerFill = new SolidBrush(PanelBackground);
            e.Graphics.FillEllipse(centerFill, center.X - innerRadius, center.Y - innerRadius, innerRadius * 2F, innerRadius * 2F);
            e.Graphics.DrawEllipse(border, center.X - innerRadius, center.Y - innerRadius, innerRadius * 2F, innerRadius * 2F);

            using var titleFont = AppFonts.Create(16F, FontStyle.Bold);
            using var titleBrush = new SolidBrush(TextPrimary);
            using var format = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
            e.Graphics.DrawString("24H", titleFont, titleBrush, new RectangleF(center.X - 48F, center.Y - 18F, 96F, 36F), format);
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            ShowHitToolTip(e.Location);
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            HideHitToolTip();
        }

        private void ShowHitToolTip(Point point)
        {
            var text = _hits.FirstOrDefault(hit => hit.Bounds.Contains(point)).Text;
            if (string.IsNullOrEmpty(text))
            {
                HideHitToolTip();
                return;
            }

            if (_visibleTip == text)
            {
                return;
            }

            _visibleTip = text;
            _tooltip.Show(text, this, point.X + 12, point.Y + 12, 2500);
        }

        private void HideHitToolTip()
        {
            if (_visibleTip is null)
            {
                return;
            }

            _visibleTip = null;
            _tooltip.Hide(this);
        }

        private static RectangleF DrawHourBarSection(
            Graphics graphics,
            PointF center,
            float angleDegrees,
            float baseInnerRadius,
            float startOffset,
            float endOffset,
            float fullLength,
            Color color)
        {
            var angle = angleDegrees * Math.PI / 180D;
            var innerHalf = InterpolateHalfWidth(startOffset, fullLength);
            var outerHalf = InterpolateHalfWidth(endOffset, fullLength);
            var dx = (float)Math.Cos(angle);
            var dy = (float)Math.Sin(angle);
            var px = -dy;
            var py = dx;
            var innerRadius = baseInnerRadius + startOffset;
            var outerRadius = baseInnerRadius + endOffset;
            var inner = new PointF(center.X + dx * innerRadius, center.Y + dy * innerRadius);
            var outer = new PointF(center.X + dx * outerRadius, center.Y + dy * outerRadius);
            var points = new[]
            {
                new PointF(inner.X + px * innerHalf, inner.Y + py * innerHalf),
                new PointF(outer.X + px * outerHalf, outer.Y + py * outerHalf),
                new PointF(outer.X - px * outerHalf, outer.Y - py * outerHalf),
                new PointF(inner.X - px * innerHalf, inner.Y - py * innerHalf)
            };

            using var brush = new SolidBrush(color);
            graphics.FillPolygon(brush, points);
            return BoundsOf(points);
        }

        private static RectangleF DrawSourceHourBar(Graphics graphics, PointF center, float angleDegrees, float innerRadius, float length, long pcSeconds, long phoneSeconds)
        {
            var total = Math.Max(1L, pcSeconds + phoneSeconds);
            RectangleF? bounds = null;
            var cursor = innerRadius;
            if (pcSeconds > 0)
            {
                var pcLength = length * pcSeconds / total;
                bounds = DrawHourBarSection(graphics, center, angleDegrees, innerRadius, cursor - innerRadius, cursor + pcLength - innerRadius, length, AccentGreen);
                cursor += pcLength;
            }

            if (phoneSeconds > 0)
            {
                var phoneBounds = DrawHourBarSection(graphics, center, angleDegrees, innerRadius, cursor - innerRadius, length, length, AccentBlue);
                bounds = bounds.HasValue ? RectangleF.Union(bounds.Value, phoneBounds) : phoneBounds;
            }

            return bounds ?? RectangleF.Empty;
        }

        private static (long PcSeconds, long PhoneSeconds) CapHourSourceSeconds(long pcSeconds, long phoneSeconds)
        {
            const long hourSeconds = 3600L;
            pcSeconds = Math.Max(0, pcSeconds);
            phoneSeconds = Math.Max(0, phoneSeconds);
            var totalSeconds = pcSeconds + phoneSeconds;
            if (totalSeconds <= hourSeconds)
            {
                return (pcSeconds, phoneSeconds);
            }

            var scaledPcSeconds = (long)Math.Round(pcSeconds * (double)hourSeconds / totalSeconds);
            scaledPcSeconds = Math.Clamp(scaledPcSeconds, 0, hourSeconds);
            return (scaledPcSeconds, hourSeconds - scaledPcSeconds);
        }

        private static float InterpolateHalfWidth(float offset, float fullLength)
        {
            const float innerHalf = 3.5F;
            const float outerHalf = 6.2F;
            if (fullLength <= 0)
            {
                return innerHalf;
            }

            var ratio = Math.Clamp(offset / fullLength, 0F, 1F);
            return innerHalf + (outerHalf - innerHalf) * ratio;
        }

        private static void DrawHourLabels(Graphics graphics, PointF center, float outerRadius)
        {
            using var font = AppFonts.Create(8.5F, FontStyle.Regular);
            using var brush = new SolidBrush(TextSecondary);
            using var format = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
            foreach (var hour in new[] { 0, 6, 12, 18 })
            {
                var angle = (-90F + hour * 15F) * Math.PI / 180D;
                var radius = outerRadius + 18F;
                var x = center.X + (float)Math.Cos(angle) * radius;
                var y = center.Y + (float)Math.Sin(angle) * radius;
                graphics.DrawString(hour.ToString(), font, brush, new RectangleF(x - 24F, y - 11F, 48F, 22F), format);
            }
        }
    }

    private sealed class WeekBarChart : Control
    {
        private IReadOnlyList<DailyRecord> _records = Array.Empty<DailyRecord>();
        private IReadOnlyList<UsageDeviceBreakdown> _deviceBreakdowns = Array.Empty<UsageDeviceBreakdown>();
        private readonly ToolTip _tooltip = CreateChartToolTip();
        private readonly List<ChartHit> _hits = new();
        private string? _visibleTip;

        public WeekBarChart()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
        }

        public IReadOnlyList<DailyRecord> Records
        {
            get => _records;
            set
            {
                _records = value ?? Array.Empty<DailyRecord>();
                Invalidate();
            }
        }

        public IReadOnlyList<UsageDeviceBreakdown> DeviceBreakdowns
        {
            get => _deviceBreakdowns;
            set
            {
                _deviceBreakdowns = value ?? Array.Empty<UsageDeviceBreakdown>();
                Invalidate();
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            _hits.Clear();
            var records = _records.Count == 7 ? _records : Enumerable.Range(0, 7).Select(_ => new DailyRecord()).ToList();
            var breakdowns = _deviceBreakdowns.Count == 7
                ? _deviceBreakdowns
                : Enumerable.Range(0, 7).Select(_ => new UsageDeviceBreakdown(0, 0)).ToList();
            var maxSeconds = Math.Max(1L, records.Max(record => record.TotalSeconds));
            var labels = WeekdayLabels();
            using var textBrush = new SolidBrush(TextSecondary);
            using var font = AppFonts.Create(7.5F);
            using var labelFormat = new StringFormat
            {
                Alignment = StringAlignment.Center,
                LineAlignment = StringAlignment.Center,
                Trimming = StringTrimming.None
            };

            for (var i = 0; i < 7; i++)
            {
                var barHeight = (int)Math.Max(4, records[i].TotalSeconds * (Height - 34) / (double)maxSeconds);
                var x = 12 + i * ((Width - 24) / 7);
                var width = 24;
                var y = Height - 26 - barHeight;
                var breakdown = breakdowns[i];
                var sourceSeconds = breakdown.PcSeconds + breakdown.PhoneSeconds;
                if (sourceSeconds > 0)
                {
                    var phoneHeight = (int)Math.Round(barHeight * breakdown.PhoneSeconds / (double)sourceSeconds);
                    var pcHeight = barHeight - phoneHeight;
                    using var pcBrush = new SolidBrush(AccentGreen);
                    using var phoneBrush = new SolidBrush(AccentBlue);
                    e.Graphics.FillRectangle(phoneBrush, x, y, width, phoneHeight);
                    e.Graphics.FillRectangle(pcBrush, x, y + phoneHeight, width, pcHeight);
                }
                else
                {
                    using var brush = new SolidBrush(AccentGreen);
                    e.Graphics.FillRectangle(brush, x, y, width, barHeight);
                }

                var bounds = new RectangleF(x - 6, y - 6, width + 12, barHeight + 12);
                _hits.Add(new ChartHit(bounds, FormatCompactHours(records[i].TotalSeconds)));
                e.Graphics.DrawString(labels[i], font, textBrush, new RectangleF(x - 8, Height - 22, width + 16, 20), labelFormat);
            }
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            ShowHitToolTip(e.Location);
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            HideHitToolTip();
        }

        private void ShowHitToolTip(Point point)
        {
            var text = _hits.FirstOrDefault(hit => hit.Bounds.Contains(point)).Text;
            if (string.IsNullOrEmpty(text))
            {
                HideHitToolTip();
                return;
            }

            if (_visibleTip == text)
            {
                return;
            }

            _visibleTip = text;
            _tooltip.Show(text, this, point.X + 12, point.Y + 12, 2500);
        }

        private void HideHitToolTip()
        {
            if (_visibleTip is null)
            {
                return;
            }

            _visibleTip = null;
            _tooltip.Hide(this);
        }
    }

    private sealed class MonthTrendChart : Control
    {
        private IReadOnlyList<DailyRecord> _records = Array.Empty<DailyRecord>();
        private readonly ToolTip _tooltip = CreateChartToolTip();
        private readonly List<ChartHit> _hits = new();
        private string? _visibleTip;

        public MonthTrendChart()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
        }

        public IReadOnlyList<DailyRecord> Records
        {
            get => _records;
            set
            {
                _records = value ?? Array.Empty<DailyRecord>();
                Invalidate();
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            _hits.Clear();
            using var warn = new Pen(AccentYellow, 1.5F) { DashPattern = new float[] { 4F, 5F } };
            var warnY = Height * 0.32F;
            e.Graphics.DrawLine(warn, 8, warnY, Width - 8, warnY);

            if (_records.Count == 0)
            {
                return;
            }

            var maxSeconds = Math.Max(1L, Math.Max(6L * 3600L, _records.Max(record => record.TotalSeconds)));
            var points = _records.Select((record, index) =>
            {
                var x = _records.Count == 1 ? Width / 2F : 12F + index * ((Width - 24F) / (_records.Count - 1));
                var y = Height - 20F - (float)(record.TotalSeconds / (double)maxSeconds * (Height - 40F));
                return new PointF(x, y);
            }).ToArray();

            using var pen = new Pen(AccentGreen, 4F) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round };
            if (points.Length > 1)
            {
                e.Graphics.DrawLines(pen, points);
            }

            using var brush = new SolidBrush(AccentGreen);
            for (var i = 0; i < points.Length; i++)
            {
                var point = points[i];
                e.Graphics.FillEllipse(brush, point.X - 3F, point.Y - 3F, 6F, 6F);
                _hits.Add(new ChartHit(new RectangleF(point.X - 12F, point.Y - 12F, 24F, 24F), FormatCompactHours(_records[i].TotalSeconds)));
            }
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            ShowHitToolTip(e.Location);
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            HideHitToolTip();
        }

        private void ShowHitToolTip(Point point)
        {
            var text = _hits.FirstOrDefault(hit => hit.Bounds.Contains(point)).Text;
            if (string.IsNullOrEmpty(text))
            {
                HideHitToolTip();
                return;
            }

            if (_visibleTip == text)
            {
                return;
            }

            _visibleTip = text;
            _tooltip.Show(text, this, point.X + 12, point.Y + 12, 2500);
        }

        private void HideHitToolTip()
        {
            if (_visibleTip is null)
            {
                return;
            }

            _visibleTip = null;
            _tooltip.Hide(this);
        }
    }

    private readonly record struct ChartHit(RectangleF Bounds, string Text);

    private static ToolTip CreateChartToolTip()
    {
        return new ToolTip
        {
            InitialDelay = 0,
            ReshowDelay = 0,
            AutoPopDelay = 2500,
            ShowAlways = true
        };
    }

    private static RectangleF BoundsOf(IReadOnlyList<PointF> points)
    {
        var minX = points.Min(point => point.X);
        var maxX = points.Max(point => point.X);
        var minY = points.Min(point => point.Y);
        var maxY = points.Max(point => point.Y);
        return new RectangleF(minX, minY, maxX - minX, maxY - minY);
    }

    private sealed class ContinuousBandsControl : Control
    {
        private IReadOnlyList<long> _sessions = Array.Empty<long>();

        public ContinuousBandsControl()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
        }

        public IReadOnlyList<long> Sessions
        {
            get => _sessions;
            set
            {
                _sessions = value ?? Array.Empty<long>();
                Invalidate();
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var counts = new[]
            {
                _sessions.Count(seconds => seconds < 30 * 60),
                _sessions.Count(seconds => seconds >= 30 * 60 && seconds < 45 * 60),
                _sessions.Count(seconds => seconds >= 45 * 60)
            };
            var labels = new[] { AppText.Get("stats.sessions.band.under30"), AppText.Get("stats.sessions.band.30to45"), AppText.Get("stats.sessions.band.over45") };
            var colors = new[] { AccentGreen, AccentYellow, AccentRed };
            var max = Math.Max(1, counts.Max());

            using var labelFont = AppFonts.Create(7.5F);
            using var valueFont = AppFonts.Create(7.5F, FontStyle.Bold);
            using var textBrush = new SolidBrush(TextSecondary);
            using var labelFormat = new StringFormat { Alignment = StringAlignment.Far, LineAlignment = StringAlignment.Center };
            using var valueFormat = new StringFormat { Alignment = StringAlignment.Near, LineAlignment = StringAlignment.Center };
            var labelBounds = new RectangleF(0, 0, 86, 22);
            var valueBounds = new RectangleF(Width - 72, 0, 70, 22);
            var trackLeft = 104;
            var trackWidth = Math.Max(20, Width - trackLeft - 92);

            for (var i = 0; i < 3; i++)
            {
                var y = 8 + i * 45;
                labelBounds.Y = y;
                valueBounds.Y = y;
                e.Graphics.DrawString(labels[i], labelFont, textBrush, labelBounds, labelFormat);
                var track = new Rectangle(trackLeft, y + 2, trackWidth, 12);
                using (var trackBrush = new SolidBrush(Color.FromArgb(232, 243, 239)))
                {
                    e.Graphics.FillRectangle(trackBrush, track);
                }

                var fill = new Rectangle(track.X, track.Y, (int)(track.Width * counts[i] / (float)max), track.Height);
                using (var fillBrush = new SolidBrush(colors[i]))
                {
                    e.Graphics.FillRectangle(fillBrush, fill);
                }

                using var valueBrush = new SolidBrush(TextPrimary);
                e.Graphics.DrawString(CountText(counts[i]), valueFont, valueBrush, valueBounds, valueFormat);
            }
        }
    }

    private sealed class DeviceShareControl : Control
    {
        private long _pcSeconds;
        private long _phoneSeconds;

        public DeviceShareControl()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
        }

        public void SetShares(long pcSeconds, long phoneSeconds)
        {
            _pcSeconds = Math.Max(0, pcSeconds);
            _phoneSeconds = Math.Max(0, phoneSeconds);
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            using var titleFont = AppFonts.Create(9F, FontStyle.Bold);
            TextRenderer.DrawText(
                e.Graphics,
                AppText.Get("stats.source.title"),
                titleFont,
                new Rectangle(0, 0, Width, 24),
                TextPrimary,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPadding);

            DrawSourceRow(e.Graphics, 36, AppText.Get("common.pc"), _pcSeconds, AccentGreen);
            DrawSourceRow(e.Graphics, 88, AppText.Get("common.phone"), _phoneSeconds, AccentBlue);
        }

        private void DrawSourceRow(Graphics graphics, int y, string label, long seconds, Color color)
        {
            var total = Math.Max(1, _pcSeconds + _phoneSeconds);
            var percent = seconds / (float)total;
            using var trackBrush = new SolidBrush(Color.FromArgb(232, 243, 239));
            using var fillBrush = new SolidBrush(color);
            var track = new Rectangle(0, y + 32, Width - 6, 12);
            graphics.FillRectangle(trackBrush, track);
            graphics.FillRectangle(fillBrush, track.X, track.Y, (int)(track.Width * percent), track.Height);

            var text = $"{label} {(int)Math.Round(percent * 100)}%";
            using var rowFont = AppFonts.Create(9.25F, FontStyle.Regular);
            TextRenderer.DrawText(
                graphics,
                text,
                rowFont,
                new Rectangle(0, y, Width - 6, 28),
                TextSecondary,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter);
        }
    }

    private sealed class RoundedPanel : Panel
    {
        public Color FillColor { get; set; } = Color.White;
        public Color BorderColor { get; set; } = StatsForm.BorderColor;
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

    private sealed class LegendDot : Control
    {
        public Color DotColor { get; set; } = AccentGreen;

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            using var brush = new SolidBrush(DotColor);
            e.Graphics.FillEllipse(brush, 0, 0, Width - 1, Height - 1);
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
