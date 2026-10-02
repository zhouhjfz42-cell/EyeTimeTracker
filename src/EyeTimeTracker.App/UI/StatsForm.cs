using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.App.UI.Controls;
using EyeTimeTracker.Core.DesktopActivity;
using EyeTimeTracker.Core.DesktopReminders;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Storage;
using System.Drawing.Drawing2D;

namespace EyeTimeTracker.App.UI;

/// <summary>记录页（效果图 3）：日视图（周/月暂未实现），概览 → 时间线 → 连续段 → 提醒与中断 → 数据说明。
/// 尺寸按设计像素固定；切换日期在运行时重建内容，Bounds 统一经 Sc()（恒等）换算。
/// 排版按实际文字测量自适应，连续段很多时纵向滚动。</summary>
public sealed class StatsForm : AppPageForm
{
    private const int PageWidth = 600;
    private const int PageMargin = 24;
    private const int ContentWidth = PageWidth - PageMargin * 2;
    private const int MaxWindowHeight = 820;

    private readonly DesktopTrackingController _controller;
    private readonly Icon? _appIcon;
    private readonly Panel _root;
    private readonly CanvasLabel _dateLabel;
    private readonly PillButton _nextDayButton;
    private readonly int _contentTop;
    private DateOnly _selectedDate;

    public StatsForm(DesktopTrackingController controller, Icon? icon)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        _appIcon = icon;
        _selectedDate = DateOnly.FromDateTime(DateTime.Now);

        Text = AppText.Get("desktop.records.lead");
        SetAppIcon(icon);

        _root = new Panel
        {
            Dock = DockStyle.Fill,
            AutoScroll = true,
            BackColor = Color.Transparent
        };
        Controls.Add(_root);

        var top = 18;
        var brand = new BrandHeader { Bounds = new Rectangle(PageMargin, top, ContentWidth, 40) };
        brand.Height = brand.PreferredHeight(ContentWidth);
        _root.Controls.Add(brand);
        top = brand.Bottom + 4;

        var backButton = new PillButton
        {
            Text = AppText.Get("desktop.common.backHome"),
            Style = PillButtonStyle.Text,
            Font = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point),
            Bounds = new Rectangle(PageMargin - 8, top, 130, 24)
        };
        backButton.Click += (_, _) => Close();
        _root.Controls.Add(backButton);

        _root.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.records.lead"),
            Bounds = new Rectangle(PageMargin + 130, top + 2, ContentWidth - 130, 20),
            Font = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = AppPalette.TextSecondary,
            TextAlign = ContentAlignment.MiddleRight
        });
        top += 24 + 8;

        BuildRangeCapsules(ref top);

        var prevDayButton = new PillButton
        {
            Text = "‹",
            Style = PillButtonStyle.Secondary,
            Font = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point),
            Bounds = new Rectangle(PageMargin, top, 40, 30)
        };
        prevDayButton.Click += (_, _) => ChangeDay(-1);
        _root.Controls.Add(prevDayButton);

        _dateLabel = new CanvasLabel
        {
            Bounds = new Rectangle(PageMargin + 46, top, ContentWidth - 92, 30),
            Font = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = AppPalette.TextPrimary,
            TextAlign = ContentAlignment.MiddleCenter
        };
        _root.Controls.Add(_dateLabel);

        _nextDayButton = new PillButton
        {
            Text = "›",
            Style = PillButtonStyle.Secondary,
            Font = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point),
            Bounds = new Rectangle(PageMargin + ContentWidth - 40, top, 40, 30)
        };
        _nextDayButton.Click += (_, _) => ChangeDay(1);
        _root.Controls.Add(_nextDayButton);
        top += 30 + 10;

        _contentTop = top;
        ClientSize = new Size(PageWidth, MaxWindowHeight);
        RefreshContent();
        CompleteLayoutScaling();
        ApplyScrollMinSize();
    }

    private int _contentBottomDesign;

    private void ApplyScrollMinSize()
    {
        _root.AutoScrollMinSize = new Size(0, Sc(_contentBottomDesign));
    }

    private void BuildRangeCapsules(ref int top)
    {
        using var capsuleFont = AppFonts.Create(9.5F, FontStyle.Bold, GraphicsUnit.Point);
        var dayWidth = UiText.SingleLineWidth(AppText.Get("desktop.records.range.day"), capsuleFont) + 40;
        var weekWidth = UiText.SingleLineWidth(AppText.Get("desktop.records.range.week"), capsuleFont) + 40;
        var monthWidth = UiText.SingleLineWidth(AppText.Get("desktop.records.range.month"), capsuleFont) + 40;
        var totalWidth = dayWidth + weekWidth + monthWidth + 20;
        var left = PageMargin + (ContentWidth - totalWidth) / 2;

        var dayCapsule = new PillButton
        {
            Text = AppText.Get("desktop.records.range.day"),
            Style = PillButtonStyle.Primary,
            Font = AppFonts.Create(9.5F, FontStyle.Bold, GraphicsUnit.Point),
            Bounds = new Rectangle(left, top, dayWidth, 34)
        };
        _root.Controls.Add(dayCapsule);

        var weekCapsule = new PillButton
        {
            Text = AppText.Get("desktop.records.range.week"),
            Style = PillButtonStyle.Secondary,
            Font = AppFonts.Create(9.5F, FontStyle.Bold, GraphicsUnit.Point),
            Bounds = new Rectangle(left + dayWidth + 10, top, weekWidth, 34),
            Enabled = false
        };
        _root.Controls.Add(weekCapsule);

        var monthCapsule = new PillButton
        {
            Text = AppText.Get("desktop.records.range.month"),
            Style = PillButtonStyle.Secondary,
            Font = AppFonts.Create(9.5F, FontStyle.Bold, GraphicsUnit.Point),
            Bounds = new Rectangle(left + dayWidth + 10 + weekWidth + 10, top, monthWidth, 34),
            Enabled = false
        };
        _root.Controls.Add(monthCapsule);
        top += 34 + 2;

        _root.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.records.range.weekTodo") + " · " + AppText.Get("desktop.records.range.monthTodo"),
            Bounds = new Rectangle(PageMargin, top, ContentWidth, 14),
            Font = AppFonts.Create(8F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = AppPalette.TextSecondary,
            TextAlign = ContentAlignment.MiddleCenter
        });
        top += 14 + 6;
    }

    private void ChangeDay(int deltaDays)
    {
        var today = DateOnly.FromDateTime(DateTime.Now);
        var next = _selectedDate.AddDays(deltaDays);
        if (next > today)
        {
            return;
        }

        _selectedDate = next;
        RefreshContent();
    }

    protected override void OnDpiChanged(DpiChangedEventArgs e)
    {
        base.OnDpiChanged(e);
        // 跨显示器拖动后滚动范围按新 DPI 重算（子控件由 WinForms 自动等比重缩放）
        if (IsHandleCreated && !IsDisposed)
        {
            ApplyScrollMinSize();
        }
    }

    private void RefreshContent()
    {
        foreach (var stale in _root.Controls.OfType<Control>().Where(control => control.Tag as string == "day").ToList())
        {
            _root.Controls.Remove(stale);
            stale.Dispose();
        }

        var today = DateOnly.FromDateTime(DateTime.Now);
        _nextDayButton.Enabled = _selectedDate < today;
        _dateLabel.Text = _selectedDate == today
            ? AppText.Format(
                "desktop.main.dateLine",
                ("today", AppText.Get("desktop.main.todayLabel")),
                ("month", _selectedDate.Month),
                ("day", _selectedDate.Day),
                ("weekday", DesktopDisplayText.WeekdayLabel(_selectedDate.DayOfWeek)))
            : AppText.Format(
                "desktop.records.dateLine",
                ("month", _selectedDate.Month),
                ("day", _selectedDate.Day),
                ("weekday", DesktopDisplayText.WeekdayLabel(_selectedDate.DayOfWeek)));

        var metrics = _controller.GetDailyMetrics(_selectedDate);
        var (intervals, sessions, breaks, gaps) = _controller.GetDayDetailSnapshot();
        var instances = _controller.GetReminderInstancesSnapshot();

        var top = _contentTop;
        top = BuildMetrics(top, metrics);
        top = BuildTimeline(top, intervals, breaks, gaps);
        top = BuildSessions(top, intervals, sessions, breaks, instances);
        top = BuildReminderSummary(top, metrics, instances);
        top = BuildLegacyLink(top);
        top = BuildDataNote(top);
        _contentBottomDesign = top + 8;
        ApplyScrollMinSize();
        if (!IsHandleCreated)
        {
            // 初次布局设定窗口尺寸；运行时不再改窗口尺寸
            ClientSize = new Size(PageWidth, Math.Min(MaxWindowHeight, top + 8));
        }
    }

    private Control Track(Control control)
    {
        control.Tag = "day";
        _root.Controls.Add(control);
        return control;
    }

    private int BuildMetrics(int top, DailyMetrics metrics)
    {
        const int gap = 8;
        const int cardHeight = 74;
        var cardWidth = (ContentWidth - gap * 2) / 3;
        var thirdWidth = ContentWidth - 2 * (cardWidth + gap);
        BuildMetricCard(PageMargin, top, cardWidth, cardHeight, AppText.Get("desktop.records.metric.active"),
            DesktopDisplayText.Duration(metrics.DesktopActiveSeconds), AppPalette.TextPrimary);
        BuildMetricCard(PageMargin + cardWidth + gap, top, cardWidth, cardHeight, AppText.Get("desktop.records.metric.sedentary"),
            DesktopDisplayText.Duration(metrics.EstimatedSedentarySeconds), AppPalette.Primary);
        BuildMetricCard(PageMargin + 2 * (cardWidth + gap), top, thirdWidth, cardHeight, AppText.Get("desktop.records.metric.maxContinuous"),
            DesktopDisplayText.Duration(metrics.MaxContinuousActiveSeconds), AppPalette.Teal);
        BuildMetricCard(PageMargin, top + cardHeight + gap, cardWidth, cardHeight, AppText.Get("desktop.records.metric.over40"),
            DesktopDisplayText.Sessions(metrics.SessionsOver40mCount), AppPalette.Purple);
        BuildMetricCard(PageMargin + cardWidth + gap, top + cardHeight + gap, cardWidth, cardHeight, AppText.Get("desktop.records.metric.breaks"),
            DesktopDisplayText.Count(metrics.InferredDesktopBreakCount), AppPalette.Purple);
        BuildMetricCard(PageMargin + 2 * (cardWidth + gap), top + cardHeight + gap, thirdWidth, cardHeight, AppText.Get("desktop.records.metric.rate"),
            DesktopDisplayText.Rate(metrics.ResponseRate), AppPalette.Orange);
        return top + 2 * cardHeight + gap + 16;
    }

    private void BuildMetricCard(int left, int top, int width, int height, string label, string value, Color valueColor)
    {
        var card = new RoundedCardPanel { Bounds = Sc(left, top, width, height) };
        card.Controls.Add(new CanvasLabel
        {
            Text = label,
            Bounds = Sc(12, 10, width - 24, 16),
            Font = AppFonts.Create(8.5F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = AppPalette.TextSecondary
        });
        card.Controls.Add(new CanvasLabel
        {
            Text = value,
            Bounds = Sc(12, 32, width - 24, 28),
            Font = AppFonts.Create(12.5F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = valueColor
        });
        Track(card);
    }

    private int BuildTimeline(int top, List<CommittedInterval> intervals, List<InferredBreak> breaks, List<CoverageGapRecord> gaps)
    {
        Track(new CanvasLabel
        {
            Text = AppText.Get("desktop.records.timeline.title"),
            Bounds = Sc(PageMargin, top, 300, 20),
            Font = AppFonts.Create(11.5F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = AppPalette.TextPrimary
        });

        const int cardHeight = 108;
        var card = new RoundedCardPanel { Bounds = Sc(PageMargin, top + 26, ContentWidth, cardHeight) };
        var legend = new TimelineLegend { Bounds = Sc(18, 8, ContentWidth - 36, 20) };
        card.Controls.Add(legend);

        var segments = DesktopTimeline.Build(_selectedDate, intervals, breaks, gaps, DateTimeOffset.UtcNow, TimeZoneInfo.Local);
        var isToday = _selectedDate == DateOnly.FromDateTime(DateTime.Now);
        var bar = new TimelineBar { Bounds = Sc(18, 34, ContentWidth - 36, 38) };
        bar.SetData(_selectedDate, segments, isToday);
        card.Controls.Add(bar);

        for (var tick = 0; tick <= 4; tick++)
        {
            var hour = tick * 6;
            var labelWidth = tick == 4 ? 26 : 20;
            var labelLeft = tick == 4
                ? 18 + (ContentWidth - 36) - labelWidth + 2
                : 18 + tick * (ContentWidth - 36) / 4 - 4;
            card.Controls.Add(new CanvasLabel
            {
                Text = hour.ToString(),
                Bounds = Sc(labelLeft, 78, labelWidth, 16),
                Font = AppFonts.Create(8F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = AppPalette.TextSecondary,
                TextAlign = tick == 4 ? ContentAlignment.MiddleRight : ContentAlignment.MiddleLeft
            });
        }

        Track(card);
        return top + 26 + cardHeight + 16;
    }

    private int BuildSessions(
        int top,
        List<CommittedInterval> intervals,
        List<SessionBoundary> sessions,
        List<InferredBreak> breaks,
        List<ReminderInstance> instances)
    {
        Track(new CanvasLabel
        {
            Text = AppText.Get("desktop.records.sessions.title"),
            Bounds = Sc(PageMargin, top, 300, 20),
            Font = AppFonts.Create(11.5F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = AppPalette.TextPrimary
        });
        top += 26;

        var (dayStartUtc, dayEndUtc) = DailyAggregator.DayBoundsUtc(_selectedDate, TimeZoneInfo.Local);
        var daySessions = sessions
            .Where(session => intervals.Any(interval =>
                interval.SessionId == session.SessionId
                && interval.EndUtc > dayStartUtc
                && interval.StartUtc < dayEndUtc))
            .OrderByDescending(session => session.StartedAtUtc)
            .ToList();

        if (daySessions.Count == 0)
        {
            Track(new CanvasLabel
            {
                Text = AppText.Get("desktop.status.noData"),
                Bounds = Sc(PageMargin, top, ContentWidth, 36),
                Font = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = AppPalette.TextSecondary,
                WordWrap = true,
                TextAlign = ContentAlignment.TopLeft
            });
            return top + 42;
        }

        foreach (var session in daySessions)
        {
            var seconds = SessionAnalyzer.CommittedSeconds(intervals.Where(interval => interval.SessionId == session.SessionId));
            var eyeCount = instances.Count(instance => instance.SessionId == session.SessionId && instance.ReminderType == "eye");
            var movementCount = instances.Count(instance => instance.SessionId == session.SessionId && instance.ReminderType == "movement");
            var breakCount = breaks.Count(breakEvent => breakEvent.SessionId == session.SessionId);
            var ongoing = session.EndedAtUtc is null;

            var card = new RoundedCardPanel { Bounds = Sc(PageMargin, top, ContentWidth, 64) };
            var accent = new Control
            {
                Bounds = Sc(0, 12, 4, 40),
                BackColor = ongoing ? AppPalette.Teal : AppPalette.Primary
            };
            card.Controls.Add(accent);

            var timeRange = session.StartedAtUtc.LocalDateTime.ToString("HH:mm")
                + " – "
                + (ongoing
                    ? AppText.Get("desktop.records.sessions.ongoing")
                    : session.EndedAtUtc!.Value.LocalDateTime.ToString("HH:mm"));
            using (var timeFont = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point))
            {
                var timeWidth = UiText.SingleLineWidth(timeRange, timeFont) + 10;
                card.Controls.Add(new CanvasLabel
                {
                    Text = timeRange,
                    Bounds = Sc(16, 10, timeWidth, 44),
                    Font = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point),
                    ForeColor = AppPalette.TextPrimary
                });

                var textLeft = 16 + timeWidth + 12;
                card.Controls.Add(new CanvasLabel
                {
                    Text = DesktopDisplayText.DeskLabel(session.DeskType) + " · " + DesktopDisplayText.Duration(seconds),
                    Bounds = Sc(textLeft, 8, ContentWidth - textLeft - 14, 22),
                    Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point),
                    ForeColor = AppPalette.TextSecondary
                });
                card.Controls.Add(new CanvasLabel
                {
                    Text = AppText.Format(
                        "desktop.records.sessions.line",
                        ("eye", eyeCount),
                        ("movement", movementCount),
                        ("breaks", breakCount)),
                    Bounds = Sc(textLeft, 32, ContentWidth - textLeft - 14, 22),
                    Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point),
                    ForeColor = AppPalette.TextSecondary
                });
            }

            Track(card);
            top += 64 + 6;
        }

        return top + 10;
    }

    private int BuildReminderSummary(int top, DailyMetrics metrics, List<ReminderInstance> instances)
    {
        Track(new CanvasLabel
        {
            Text = AppText.Get("desktop.records.reminders.title"),
            Bounds = Sc(PageMargin, top, 300, 20),
            Font = AppFonts.Create(11.5F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = AppPalette.TextPrimary
        });
        top += 24;

        var textWidth = ContentWidth - 32;
        var cursor = 10;
        var card = new RoundedCardPanel { Bounds = Sc(PageMargin, top, ContentWidth, 100) };

        var eyeInstances = instances
            .Where(instance => instance.ReminderType == "eye"
                && DateOnly.FromDateTime(instance.DueAtUtc.LocalDateTime) == _selectedDate)
            .ToList();
        var deliveredCount = eyeInstances.Count(instance => instance.Status == ReminderInstance.StatusDelivered);
        using (var lineFont = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point))
        {
            var eyeLine = AppText.Format(
                "desktop.records.reminders.eyeLine",
                ("due", eyeInstances.Count),
                ("delivered", deliveredCount));
            var eyeHeight = UiText.WrappedHeight(eyeLine, lineFont, textWidth) + 2;
            card.Controls.Add(new CanvasLabel
            {
                Text = eyeLine,
                Bounds = Sc(16, cursor, textWidth, eyeHeight),
                Font = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = AppPalette.TextPrimary,
                WordWrap = true,
                TextAlign = ContentAlignment.TopLeft
            });
            cursor += eyeHeight + 3;

            var rate = metrics.ResponseRate;
            var rateText = rate.MaturedOpportunities > 0
                ? AppText.Format(
                    "desktop.records.reminders.rateLine",
                    ("matured", rate.MaturedOpportunities),
                    ("responses", rate.Responses),
                    ("percent", rate.RatePercent ?? 0),
                    ("pending", rate.Pending))
                : AppText.Get("desktop.records.reminders.rateLineNone");
            var rateHeight = UiText.WrappedHeight(rateText, lineFont, textWidth) + 2;
            card.Controls.Add(new CanvasLabel
            {
                Text = rateText,
                Bounds = Sc(16, cursor, textWidth, rateHeight),
                Font = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = AppPalette.TextPrimary,
                WordWrap = true,
                TextAlign = ContentAlignment.TopLeft
            });
            cursor += rateHeight + 3;

            if (rate.Unknown > 0)
            {
                using var unknownFont = AppFonts.Create(8.5F, FontStyle.Regular, GraphicsUnit.Point);
                var unknownText = AppText.Format("desktop.records.reminders.rateUnknown", ("unknown", rate.Unknown));
                var unknownHeight = UiText.WrappedHeight(unknownText, unknownFont, textWidth) + 2;
                card.Controls.Add(new CanvasLabel
                {
                    Text = unknownText,
                    Bounds = Sc(16, cursor, textWidth, unknownHeight),
                    Font = AppFonts.Create(8.5F, FontStyle.Regular, GraphicsUnit.Point),
                    ForeColor = AppPalette.TextSecondary,
                    WordWrap = true,
                    TextAlign = ContentAlignment.TopLeft
                });
                cursor += unknownHeight + 2;
            }

            using var footnoteFont = AppFonts.Create(8F, FontStyle.Regular, GraphicsUnit.Point);
            var footnote = AppText.Get("desktop.records.reminders.rateFootnote");
            var footnoteHeight = UiText.WrappedHeight(footnote, footnoteFont, textWidth) + 2;
            card.Controls.Add(new CanvasLabel
            {
                Text = footnote,
                Bounds = Sc(16, cursor, textWidth, footnoteHeight),
                Font = AppFonts.Create(8F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = AppPalette.TextSecondary,
                WordWrap = true,
                TextAlign = ContentAlignment.TopLeft
            });
            cursor += footnoteHeight + 10;
        }

        card.Height = Sc(cursor);
        Track(card);
        return top + cursor + 14;
    }

    private int BuildLegacyLink(int top)
    {
        using var font = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point);
        var text = AppText.Get("desktop.records.legacyLink");
        var width = UiText.SingleLineWidth(text, font) + 16;
        var link = new PillButton
        {
            Text = text,
            Style = PillButtonStyle.Text,
            Font = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point),
            Bounds = Sc(PageMargin - 8, top, width, 26)
        };
        link.Click += (_, _) => ShowLegacyRecords();
        Track(link);
        return top + 30;
    }

    private int BuildDataNote(int top)
    {
        using var font = AppFonts.Create(8.5F, FontStyle.Regular, GraphicsUnit.Point);
        var text = AppText.Get("desktop.records.dataNote");
        var height = UiText.WrappedHeight(text, font, ContentWidth) + 2;
        Track(new CanvasLabel
        {
            Text = text,
            Bounds = Sc(PageMargin, top, ContentWidth, height),
            Font = AppFonts.Create(8.5F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = AppPalette.TextSecondary,
            WordWrap = true,
            TextAlign = ContentAlignment.TopCenter
        });
        return top + height + 4;
    }

    private void ShowLegacyRecords()
    {
        using var dialog = new LegacyRecordsDialog(_appIcon);
        dialog.ShowDialog(this);
    }

    /// <summary>旧版用眼记录（升级前数据）：只读展示 legacy state.json 汇总，不计入新版指标。</summary>
    private sealed class LegacyRecordsDialog : AppPageForm
    {
        public LegacyRecordsDialog(Icon? icon)
        {
            Text = AppText.Get("desktop.records.legacyTitle");
            SetAppIcon(icon);
            ClientSize = new Size(460, 520);

            Controls.Add(new CanvasLabel
            {
                Text = AppText.Get("desktop.records.legacyTitle"),
                Bounds = new Rectangle(28, 20, 404, 26),
                Font = AppFonts.Create(13F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = AppPalette.TextPrimary
            });
            Controls.Add(new CanvasLabel
            {
                Text = AppText.Get("desktop.records.legacyNote"),
                Bounds = new Rectangle(28, 48, 404, 20),
                Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = AppPalette.TextSecondary
            });

            var listPanel = new Panel
            {
                Bounds = new Rectangle(28, 76, 404, 364),
                AutoScroll = true,
                BackColor = Color.Transparent
            };
            Controls.Add(listPanel);

            var records = LoadLegacyRecords();
            if (records.Count == 0)
            {
                listPanel.Controls.Add(new CanvasLabel
                {
                    Text = AppText.Get("desktop.records.legacyEmpty"),
                    Bounds = new Rectangle(0, 8, 404, 24),
                    Font = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point),
                    ForeColor = AppPalette.TextSecondary
                });
            }
            else
            {
                var top = 0;
                foreach (var record in records)
                {
                    listPanel.Controls.Add(new CanvasLabel
                    {
                        Text = record.Date.ToString("yyyy-MM-dd") + "：" + DesktopDisplayText.Duration(record.TotalSeconds),
                        Bounds = new Rectangle(0, top, 404, 26),
                        Font = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point),
                        ForeColor = AppPalette.TextPrimary
                    });
                    top += 28;
                }

                listPanel.AutoScrollMinSize = new Size(0, top);
            }

            var closeButton = new PillButton
            {
                Text = AppText.Get("common.gotIt"),
                Style = PillButtonStyle.Primary,
                Bounds = new Rectangle(138, 458, 184, 42)
            };
            closeButton.Click += (_, _) => Close();
            Controls.Add(closeButton);
            CompleteLayoutScaling();
        }

        private static List<DailyRecord> LoadLegacyRecords()
        {
            try
            {
                if (!File.Exists(AppPaths.StateFilePath))
                {
                    return new List<DailyRecord>();
                }

                var store = new JsonStateStore(AppPaths.StateFilePath);
                return store.Load().Records
                    .Where(record => record.TotalSeconds > 0)
                    .OrderByDescending(record => record.Date)
                    .ToList();
            }
            catch (Exception)
            {
                return new List<DailyRecord>();
            }
        }
    }
}
