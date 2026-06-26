using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.App.UI;

public sealed class MainForm : Form
{
    private readonly TrackingController _controller;
    private readonly StartupManager _startupManager;
    private readonly Label _todayValue;
    private readonly Label _statusValue;
    private readonly Label _weekValue;
    private readonly Label _monthValue;
    private readonly NumericUpDown _idleThresholdMinutes;
    private readonly CheckBox _countAudio;
    private readonly CheckBox _startWithWindows;
    private bool _loadingSettings;
    private bool _closingForExit;

    public MainForm(TrackingController controller, StartupManager startupManager)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        _startupManager = startupManager ?? throw new ArgumentNullException(nameof(startupManager));

        Text = "\u7528\u773c\u65f6\u95f4\u8bb0\u5f55";
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = true;
        ClientSize = new Size(360, 300);

        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(16),
            ColumnCount = 1,
            RowCount = 3
        };
        root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 12));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));

        var summary = new TableLayoutPanel
        {
            Dock = DockStyle.Top,
            AutoSize = true,
            ColumnCount = 2
        };
        summary.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 88));
        summary.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));

        _todayValue = AddSummaryRow(summary, 0, "\u4eca\u5929");
        _statusValue = AddSummaryRow(summary, 1, "\u72b6\u6001");
        _weekValue = AddSummaryRow(summary, 2, "\u672c\u5468");
        _monthValue = AddSummaryRow(summary, 3, "\u672c\u6708");

        var settings = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 2,
            RowCount = 3
        };
        settings.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        settings.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 124));
        settings.RowStyles.Add(new RowStyle(SizeType.Absolute, 40));
        settings.RowStyles.Add(new RowStyle(SizeType.Absolute, 40));
        settings.RowStyles.Add(new RowStyle(SizeType.Absolute, 40));

        settings.Controls.Add(CreateLabel("\u952e\u9f20\u7a7a\u95f2\u9608\u503c\uff08\u5206\u949f\uff09"), 0, 0);
        _idleThresholdMinutes = new NumericUpDown
        {
            Dock = DockStyle.Fill,
            Minimum = 1,
            Maximum = 120,
            TextAlign = HorizontalAlignment.Right
        };
        settings.Controls.Add(_idleThresholdMinutes, 1, 0);

        _countAudio = new CheckBox
        {
            Text = "\u97f3\u9891\u8ba1\u65f6",
            Dock = DockStyle.Fill,
            AutoSize = true
        };
        settings.Controls.Add(_countAudio, 0, 1);
        settings.SetColumnSpan(_countAudio, 2);

        _startWithWindows = new CheckBox
        {
            Text = "\u5f00\u673a\u81ea\u542f",
            Dock = DockStyle.Fill,
            AutoSize = true
        };
        settings.Controls.Add(_startWithWindows, 0, 2);
        settings.SetColumnSpan(_startWithWindows, 2);

        root.Controls.Add(summary, 0, 0);
        root.Controls.Add(new Panel(), 0, 1);
        root.Controls.Add(settings, 0, 2);
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

    private static Label AddSummaryRow(TableLayoutPanel panel, int row, string caption)
    {
        panel.RowStyles.Add(new RowStyle(SizeType.Absolute, 32));
        panel.Controls.Add(CreateLabel(caption), 0, row);

        var value = CreateLabel("");
        value.Font = new Font(value.Font, FontStyle.Bold);
        panel.Controls.Add(value, 1, row);
        return value;
    }

    private static Label CreateLabel(string text)
    {
        return new Label
        {
            Text = text,
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            AutoEllipsis = true
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
        var weekStart = today.AddDays(-GetMondayOffset(today.DayOfWeek));
        var monthStart = new DateOnly(today.Year, today.Month, 1);

        _todayValue.Text = FormatDuration(current.TotalSeconds);
        _statusValue.Text = current.IsCounting ? "\u8ba1\u65f6\u4e2d" : "\u6682\u505c";
        _weekValue.Text = FormatDuration(SumRecords(records, weekStart, today));
        _monthValue.Text = FormatDuration(SumRecords(records, monthStart, today));
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

    private static string FormatDuration(long totalSeconds)
    {
        var duration = TimeSpan.FromSeconds(Math.Max(0, totalSeconds));
        var totalHours = (int)duration.TotalHours;
        return totalHours > 0
            ? string.Format("{0}\u5c0f\u65f6 {1:00}\u5206\u949f", totalHours, duration.Minutes)
            : string.Format("{0}\u5206\u949f", duration.Minutes);
    }
}
