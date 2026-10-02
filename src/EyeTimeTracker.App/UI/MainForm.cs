using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.App.UI.Controls;
using EyeTimeTracker.Core.DesktopActivity;

namespace EyeTimeTracker.App.UI;

/// <summary>主页（主界面尺寸标注图）：窗口固定 480×890 物理像素（锁定，不随 DPI 缩放），
/// 字体全部 GraphicsUnit.Pixel 按标注 px 值创建。卡片坐标严格对齐标注：
/// 久坐卡 (26,154) 428×150 / 提醒卡 (26,375) 428×142 / 概览卡 (26,585)+(246,585) 208×104 /
/// 时间线卡 (26,750) 428×116、时间条 (48,800) 384×30；外边距 26、卡内边距 22、圆角 R18、概览卡间距 12。</summary>
public sealed class MainForm : AppPageForm
{
    // 标注图硬指标（单位 px）
    private const int PageWidth = 480;
    private const int PageHeight = 890;
    private const int PageMargin = 26;
    private const int CardPadding = 22;
    private const int CardRadius = 18;

    private const int TitlePx = 34;          // 软件标题
    private const int BodyPx = 20;           // 副标题、日期、状态、概览说明（最低字号 20px）
    private const int SectionPx = 24;        // 分区标题、卡片标题、提醒名称、分钟单位
    private const int BigNumberPx = 80;      // 久坐数字
    private const int PlanTextPx = 20;       // 提醒周期、倒计时
    private const int OverviewValuePx = 28;  // 概览数值
    private const int SmallPx = 20;          // 图例、刻度（最低字号 20px）

    private readonly DesktopTrackingController _controller;
    private readonly StartupManager _startupManager;
    private readonly Icon _appIcon;
    private readonly StatusPill _statusPill;
    private readonly CanvasLabel _dateLabel;
    private CanvasLabel _sessionTitle = null!;
    private InfoDotButton _infoButton = null!;
    private MiniTag _deskTag = null!;
    private CanvasLabel _sessionValue = null!;
    private CanvasLabel _sessionUnit = null!;
    private CanvasLabel _eyeIntervalLabel = null!;
    private CanvasLabel _eyeDueLabel = null!;
    private CanvasLabel _movementIntervalLabel = null!;
    private CanvasLabel _movementDueLabel = null!;
    private CanvasLabel _todayActiveValue = null!;
    private CanvasLabel _todayMaxValue = null!;
    private TimelineBar _timelineBar = null!;
    private readonly System.Windows.Forms.Timer _refreshTimer;
    private bool _closingForExit;

    public MainForm(DesktopTrackingController controller, StartupManager startupManager, Icon appIcon)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        _startupManager = startupManager ?? throw new ArgumentNullException(nameof(startupManager));

        Text = AppText.Get("app.name");
        _appIcon = (Icon)(appIcon ?? throw new ArgumentNullException(nameof(appIcon))).Clone();
        SetAppIcon(appIcon);
        StartPosition = FormStartPosition.CenterScreen;

        var root = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.Transparent
        };
        Controls.Add(root);

        BuildBrandRow(root);

        // 日期 + 状态胶囊
        _dateLabel = new CanvasLabel
        {
            Bounds = new Rectangle(PageMargin, 118, 300, 26),
            Font = AppFonts.Create(BodyPx, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary
        };
        root.Controls.Add(_dateLabel);

        _statusPill = new StatusPill { Bounds = new Rectangle(26 + 428 - 200, 116, 200, 30) };
        root.Controls.Add(_statusPill);

        BuildMainCard(root);
        BuildPlanSection(root);
        BuildTodaySection(root);
        BuildTimelineSection(root);

        ClientSize = new Size(PageWidth, PageHeight);
        CompleteLayoutScaling();

        _controller.Updated += OnTrackingUpdated;
        _refreshTimer = new System.Windows.Forms.Timer { Interval = 1000 };
        _refreshTimer.Tick += (_, _) => RefreshData();
        _refreshTimer.Start();
        RefreshData();
    }

    /// <summary>品牌区：蓝底白字「坐」芯片 + 34px 标题 + 18px 副标题（标题下方）+ 右上蓝色齿轮。</summary>
    private void BuildBrandRow(Control root)
    {
        root.Controls.Add(new IconChip
        {
            Glyph = AppText.Get("desktop.brand.chip"),
            ChipColor = Color.White,
            ChipBackground = AppPalette.Primary,
            Bounds = new Rectangle(26, 28, 40, 40),
            Font = AppFonts.Create(22, FontStyle.Bold, GraphicsUnit.Pixel)
        });
        root.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("app.name"),
            Bounds = new Rectangle(76, 26, 240, 42),
            Font = AppFonts.Create(TitlePx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.Primary
        });
        root.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.brand.subtitle"),
            Bounds = new Rectangle(76, 70, 340, 26),
            Font = AppFonts.Create(BodyPx, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary
        });

        var gearButton = new GearButton
        {
            Bounds = new Rectangle(424, 24, 34, 34)
        };
        gearButton.Click += (_, _) => ShowSettings();
        root.Controls.Add(gearButton);
    }

    /// <summary>久坐卡片 (26,154) 428×150：标题行（本次估算久坐 ⓘ + 桌型标签靠右）+ 80px 数字行。</summary>
    private void BuildMainCard(Control root)
    {
        var card = new RoundedCardPanel
        {
            Bounds = new Rectangle(26, 154, 428, 150),
            Radius = CardRadius
        };
        _sessionTitle = new CanvasLabel
        {
            Bounds = new Rectangle(CardPadding, 20, 200, 32),
            Font = AppFonts.Create(SectionPx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        };
        card.Controls.Add(_sessionTitle);

        _infoButton = new InfoDotButton { Bounds = new Rectangle(220, 24, 24, 24) };
        _infoButton.Click += (_, _) => AppMessageDialog.Info(this, _sessionTitle.Text, AppText.Get("desktop.main.infoTip"), _appIcon);
        card.Controls.Add(_infoButton);

        _deskTag = new MiniTag { Bounds = new Rectangle(428 - CardPadding - 100, 22, 100, 30) };
        card.Controls.Add(_deskTag);

        _sessionValue = new CanvasLabel
        {
            Bounds = new Rectangle(CardPadding, 54, 160, 88),
            Font = AppFonts.Create(BigNumberPx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = GearButton.GearBlue
        };
        card.Controls.Add(_sessionValue);
        _sessionUnit = new CanvasLabel
        {
            Bounds = new Rectangle(190, 106, 120, 32),
            Font = AppFonts.Create(SectionPx, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary
        };
        card.Controls.Add(_sessionUnit);

        root.Controls.Add(card);
    }

    /// <summary>当前提醒计划：分区标题 + 提醒卡 (26,375) 428×142，两行列「名称 左｜每 N 分钟 居中｜约 N 分钟后 右」。</summary>
    private void BuildPlanSection(Control root)
    {
        root.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.main.planTitle"),
            Bounds = new Rectangle(PageMargin, 337, 170, 32),
            Font = AppFonts.Create(SectionPx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });
        using (var noteFont = AppFonts.Create(SmallPx, FontStyle.Regular, GraphicsUnit.Pixel))
        {
            var note = AppText.Get("desktop.main.planTimingNote");
            root.Controls.Add(new RightAlignedLabel
            {
                Text = note,
                Bounds = new Rectangle(200, 341, 254, 26),
                Font = AppFonts.Create(SmallPx, FontStyle.Regular, GraphicsUnit.Pixel),
                ForeColor = AppPalette.TextSecondary
            });
        }

        var card = new RoundedCardPanel
        {
            Bounds = new Rectangle(26, 375, 428, 142),
            Radius = CardRadius
        };
        BuildPlanRow(card, AppText.Get("desktop.main.plan.eye"), AppPalette.Teal, CardPadding,
            out _eyeIntervalLabel, out _eyeDueLabel);
        var divider = new Control { Bounds = new Rectangle(CardPadding, 71, 428 - CardPadding * 2, 1), BackColor = AppPalette.CardBorder };
        card.Controls.Add(divider);
        BuildPlanRow(card, AppText.Get("desktop.main.plan.movement"), AppPalette.Orange, 71,
            out _movementIntervalLabel, out _movementDueLabel);
        root.Controls.Add(card);
    }

    private static void BuildPlanRow(Control card, string name, Color dueColor, int rowTop,
        out CanvasLabel interval, out CanvasLabel due)
    {
        var nameLabel = new CanvasLabel
        {
            Name = "name",
            Text = name,
            Font = AppFonts.Create(SectionPx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary,
            Bounds = new Rectangle(CardPadding, rowTop, 120, 49)
        };
        card.Controls.Add(nameLabel);

        interval = new CanvasLabel
        {
            Name = "interval",
            Bounds = new Rectangle(CardPadding + 120, rowTop, 160, 49),
            Font = AppFonts.Create(PlanTextPx, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary,
            TextAlign = ContentAlignment.MiddleCenter
        };
        card.Controls.Add(interval);

        due = new CanvasLabel
        {
            Name = "due",
            Bounds = new Rectangle(428 - CardPadding - 150, rowTop, 150, 49),
            Font = AppFonts.Create(PlanTextPx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = dueColor,
            TextAlign = ContentAlignment.MiddleRight
        };
        card.Controls.Add(due);
    }

    /// <summary>今日概览：标题 + 两卡 (26,585) / (246,585) 208×104，间距 12。</summary>
    private void BuildTodaySection(Control root)
    {
        root.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.main.todayTitle"),
            Bounds = new Rectangle(PageMargin, 545, 260, 32),
            Font = AppFonts.Create(SectionPx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });

        _todayActiveValue = BuildTodayCard(root, AppText.Get("desktop.main.today.active"), 26);
        _todayMaxValue = BuildTodayCard(root, AppText.Get("desktop.main.today.maxContinuous"), 246);
    }

    private static CanvasLabel BuildTodayCard(Control root, string label, int left)
    {
        var card = new RoundedCardPanel
        {
            Bounds = new Rectangle(left, 585, 208, 104),
            Radius = CardRadius
        };
        card.Controls.Add(new CanvasLabel
        {
            Text = label,
            Bounds = new Rectangle(CardPadding, 14, 208 - CardPadding * 2, 26),
            Font = AppFonts.Create(BodyPx, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary
        });
        var value = new CanvasLabel
        {
            Bounds = new Rectangle(CardPadding, 44, 208 - CardPadding * 2, 36),
            Font = AppFonts.Create(OverviewValuePx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        };
        card.Controls.Add(value);
        root.Controls.Add(card);
        return value;
    }

    /// <summary>时间线卡 (26,750) 428×116：图例行 + 时间条 (48,800) 384×30 + 刻度行。</summary>
    private void BuildTimelineSection(Control root)
    {
        root.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.records.timeline.title"),
            Bounds = new Rectangle(PageMargin, 714, 320, 32),
            Font = AppFonts.Create(SectionPx, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });

        var card = new RoundedCardPanel
        {
            Bounds = new Rectangle(26, 750, 428, 116),
            Radius = CardRadius
        };
        card.Controls.Add(new TimelineLegend { Bounds = new Rectangle(CardPadding, 14, 428 - CardPadding * 2, 26) });
        _timelineBar = new TimelineBar { Bounds = new Rectangle(48 - 26, 50, 384, 30) };
        card.Controls.Add(_timelineBar);

        for (var tick = 0; tick <= 4; tick++)
        {
            var tickWidth = 30;
            var tickLeft = tick == 4
                ? 48 - 26 + 384 - tickWidth
                : 48 - 26 + tick * 96 - 4;
            card.Controls.Add(new CanvasLabel
            {
                Text = (tick * 6).ToString(),
                Bounds = new Rectangle(tickLeft, 82, tickWidth, 26),
                Font = AppFonts.Create(SmallPx, FontStyle.Regular, GraphicsUnit.Pixel),
                ForeColor = AppPalette.TextSecondary,
                TextAlign = tick == 4 ? ContentAlignment.MiddleRight : ContentAlignment.MiddleLeft
            });
        }

        root.Controls.Add(card);
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
            _refreshTimer.Stop();
            _refreshTimer.Dispose();
            _controller.Updated -= OnTrackingUpdated;
        }

        base.Dispose(disposing);
    }

    private void OnTrackingUpdated(object? sender, TrackingUpdatedEventArgs e)
    {
        if (IsDisposed || !IsHandleCreated)
        {
            return;
        }

        try
        {
            BeginInvoke(RefreshData);
        }
        catch (InvalidOperationException)
        {
        }
    }

    protected override void OnDpiChanged(DpiChangedEventArgs e)
    {
        base.OnDpiChanged(e);
        // 尺寸不跟随 DPI；仅刷新测量驱动的布局
        if (IsHandleCreated && !IsDisposed)
        {
            RefreshData();
        }
    }

    private void RefreshData()
    {
        if (IsDisposed)
        {
            return;
        }

        if (!IsHandleCreated)
        {
            _ = Handle;
        }

        var settings = _controller.DesktopSettings;
        var state = _controller.State;
        var today = DateOnly.FromDateTime(DateTime.Now);
        var now = DateTimeOffset.UtcNow;
        using var deviceGraphics = CreateGraphics();

        var nowLocal = DateTime.Now;
        _dateLabel.Text = AppText.Format(
            "desktop.main.dateLine",
            ("today", AppText.Get("desktop.main.todayLabel")),
            ("month", nowLocal.Month),
            ("day", nowLocal.Day),
            ("weekday", DesktopDisplayText.WeekdayLabel(nowLocal.DayOfWeek)));
        var dateWidth = UiText.SingleLineWidth(deviceGraphics, _dateLabel.Text, _dateLabel.Font) + 6;

        var counting = state is ClassifierState.Active or ClassifierState.IdleCandidate;
        // 状态胶囊可用宽度 = 内容右缘 - 日期右缘 - 间隔；超长按两行显示，不与日期重叠
        var pillMaxWidth = (26 + 428) - (26 + dateWidth) - 12;
        if (state == ClassifierState.Unavailable)
        {
            _statusPill.SetStatus(AppText.Get("desktop.status.unavailable"), StatusTone.Bad, pillMaxWidth);
        }
        else if (_controller.RemindersPaused)
        {
            _statusPill.SetStatus(AppText.Get("desktop.status.remindersPaused"), StatusTone.Warn, pillMaxWidth);
        }
        else
        {
            switch (state)
            {
                case ClassifierState.Active:
                    _statusPill.SetStatus(AppText.Get("desktop.status.recording"), StatusTone.Good, pillMaxWidth);
                    break;
                case ClassifierState.IdleCandidate:
                    _statusPill.SetStatus(AppText.Get("desktop.status.idleCandidate"), StatusTone.Warn, pillMaxWidth);
                    break;
                case ClassifierState.Away:
                case ClassifierState.LockAbsence:
                    _statusPill.SetStatus(AppText.Get("desktop.status.away"), StatusTone.Neutral, pillMaxWidth);
                    break;
                default:
                    _statusPill.SetStatus(AppText.Get("desktop.status.noData"), StatusTone.Neutral, pillMaxWidth);
                    break;
            }
        }

        _dateLabel.Width = dateWidth;

        _sessionTitle.Text = settings.Desk == DeskType.Ordinary
            ? AppText.Get("desktop.main.sessionTitle.ordinary")
            : AppText.Get("desktop.main.sessionTitle.other");
        var titleWidth = UiText.SingleLineWidth(deviceGraphics, _sessionTitle.Text, _sessionTitle.Font) + 6;
        _sessionTitle.Width = titleWidth;
        _infoButton.Left = _sessionTitle.Right + 8;

        _deskTag.SetText(DesktopDisplayText.DeskLabel(settings.Desk));
        var tagWidth = _deskTag.PreferredWidth();
        _deskTag.Bounds = new Rectangle(428 - CardPadding - tagWidth, 22, tagWidth, 30);

        var (sessionSeconds, _, provisionalTailStartUtc) = _controller.CurrentSessionInfo();
        var displaySeconds = sessionSeconds;
        if (state == ClassifierState.Active && provisionalTailStartUtc is { } tailStart && tailStart < now)
        {
            // 主数字可含暂存尾部（估算口径，未确认前不进统计）
            displaySeconds += (long)(now - tailStart).TotalSeconds;
        }

        var displayMinutes = displaySeconds / 60;
        if (displayMinutes < 120)
        {
            _sessionValue.Font = AppFonts.Create(BigNumberPx, FontStyle.Bold, GraphicsUnit.Pixel);
            _sessionValue.Text = displayMinutes.ToString();
            _sessionUnit.Text = AppText.Get("desktop.main.minutesUnit");
            var numberWidth = UiText.SingleLineWidth(deviceGraphics, _sessionValue.Text, _sessionValue.Font) + 6;
            _sessionValue.Bounds = new Rectangle(CardPadding, 54, Math.Max(60, numberWidth), 88);
            _sessionUnit.Bounds = new Rectangle(_sessionValue.Right + 10, 106, 120, 32);
        }
        else
        {
            _sessionValue.Font = AppFonts.Create(56, FontStyle.Bold, GraphicsUnit.Pixel);
            _sessionValue.Text = DesktopDisplayText.Duration(displaySeconds);
            _sessionValue.Bounds = new Rectangle(CardPadding, 54, 428 - CardPadding * 2, 88);
            _sessionUnit.Text = string.Empty;
        }

        var (eyeDueSeconds, movementDueSeconds) = _controller.NextDueActiveSeconds();
        _eyeIntervalLabel.Text = AppText.Format("desktop.main.plan.everyMinutes", ("minutes", settings.EyeIntervalSeconds / 60));
        _movementIntervalLabel.Text = AppText.Format("desktop.main.plan.everyMinutes", ("minutes", settings.MovementIntervalSeconds / 60));
        _eyeDueLabel.Text = counting && settings.EyeEnabled
            ? DesktopDisplayText.DueIn(eyeDueSeconds)
            : AppText.Get("desktop.common.dash");
        _movementDueLabel.Text = counting && settings.MovementEnabled
            ? DesktopDisplayText.DueIn(movementDueSeconds)
            : AppText.Get("desktop.common.dash");
        // 计划行三段：名称左、周期居中、到期右，按实测宽度分配互不重叠的区间
        RepackPlanRow(deviceGraphics, _eyeIntervalLabel, _eyeDueLabel);
        RepackPlanRow(deviceGraphics, _movementIntervalLabel, _movementDueLabel);

        var metrics = _controller.GetDailyMetrics(today);
        _todayActiveValue.Text = DesktopDisplayText.Duration(metrics.DesktopActiveSeconds);
        _todayMaxValue.Text = DesktopDisplayText.Duration(metrics.MaxContinuousActiveSeconds);

        var (intervals, _, breaks, gaps) = _controller.GetDayDetailSnapshot();
        _timelineBar.SetData(today, DesktopTimeline.Build(today, intervals, breaks, gaps, now, TimeZoneInfo.Local), isToday: true);
    }

    private static void RepackPlanRow(Graphics graphics, CanvasLabel interval, CanvasLabel due)
    {
        // 三段按实测宽度分配互不重叠区间：名称左、周期居中、到期右
        var card = interval.Parent!;
        var name = card.Controls.OfType<CanvasLabel>().First(c => c.Name == "name" && c.Top == interval.Top);
        var nameWidth = UiText.SingleLineWidth(graphics, name.Text, name.Font) + 6;
        name.Width = nameWidth;
        var dueWidth = UiText.SingleLineWidth(graphics, due.Text, due.Font) + 6;
        var dueLeft = 428 - CardPadding - dueWidth;
        due.Bounds = new Rectangle(dueLeft, due.Top, dueWidth, due.Height);
        var intervalLeft = name.Right + 8;
        interval.Bounds = new Rectangle(intervalLeft, interval.Top, dueLeft - 8 - intervalLeft, interval.Height);
    }

    private void ShowSettings()
    {
        using var form = new SettingsForm(_controller, _startupManager, _appIcon);
        form.ShowDialog(this);
        RefreshData();
    }

    private enum StatusTone
    {
        Good,
        Warn,
        Neutral,
        Bad
    }

    /// <summary>状态胶囊：圆点 + 文字，颜色不是唯一状态线索（文字完整描述），宽度按实测。</summary>
    private sealed class StatusPill : Control
    {
        private string _text = string.Empty;
        private StatusTone _tone = StatusTone.Neutral;
        private bool _twoLines;

        public StatusPill()
        {
            SetStyle(
                ControlStyles.UserPaint
                | ControlStyles.AllPaintingInWmPaint
                | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.SupportsTransparentBackColor
                | ControlStyles.ResizeRedraw,
                true);
            BackColor = Color.Transparent;
            TabStop = false;
        }

        /// <summary>设置状态文字与色调；maxWidth 为胶囊可用最大宽度（超出则两行显示，不与左侧日期重叠）。</summary>
        public void SetStatus(string text, StatusTone tone, int maxWidth)
        {
            _text = text;
            _tone = tone;
            using var font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel);
            using var graphics = CreateGraphics();
            var needed = UiText.SingleLineWidth(graphics, text, font) + 42;
            if (needed <= maxWidth)
            {
                _twoLines = false;
                Width = Math.Max(90, needed);
                Height = 30;
            }
            else
            {
                _twoLines = true;
                Width = Math.Max(90, maxWidth);
                Height = 56;
            }

            Left = (26 + 428) - Width;
            Top = 148 - Height; // 底边对齐到久坐卡片上方
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var (background, foreground) = _tone switch
            {
                StatusTone.Good => (AppPalette.SoftTeal, AppPalette.Teal),
                StatusTone.Warn => (AppPalette.SoftOrange, Color.FromArgb(0xC9, 0x77, 0x1F)),
                StatusTone.Bad => (AppPalette.SoftDanger, AppPalette.Danger),
                _ => (Color.FromArgb(0xEC, 0xEF, 0xF5), AppPalette.TextSecondary)
            };
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using (var path = UiGraphics.RoundedRect(bounds, Height / 2))
            using (var fill = new SolidBrush(background))
            {
                e.Graphics.FillPath(fill, path);
            }

            using (var dotBrush = new SolidBrush(foreground))
            {
                e.Graphics.FillEllipse(dotBrush, 14, (Height - 8) / 2, 8, 8);
            }

            using var font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel);
            var textFlags = TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix
                | (_twoLines ? TextFormatFlags.WordBreak : TextFormatFlags.SingleLine);
            TextRenderer.DrawText(
                e.Graphics,
                _text,
                font,
                new Rectangle(30, 0, Width - 36, Height),
                foreground,
                textFlags);
        }
    }

    /// <summary>桌型小标签，宽度按实测。</summary>
    private sealed class MiniTag : Control
    {
        private string _text = string.Empty;

        public MiniTag()
        {
            SetStyle(
                ControlStyles.UserPaint
                | ControlStyles.AllPaintingInWmPaint
                | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.SupportsTransparentBackColor
                | ControlStyles.ResizeRedraw,
                true);
            BackColor = Color.Transparent;
            TabStop = false;
        }

        public void SetText(string text)
        {
            _text = text;
            Invalidate();
        }

        public int PreferredWidth()
        {
            using var font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel);
            using var graphics = CreateGraphics();
            return UiText.SingleLineWidth(graphics, _text, font) + 26;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using (var path = UiGraphics.RoundedRect(bounds, Height / 2))
            using (var fill = new SolidBrush(AppPalette.SoftBlue))
            {
                e.Graphics.FillPath(fill, path);
            }

            using var font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel);
            TextRenderer.DrawText(
                e.Graphics,
                _text,
                font,
                bounds,
                AppPalette.Primary,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
        }
    }

    /// <summary>右对齐单行文字：绘制时在真实 DC 上测量并从右缘向左排，避免右对齐裁剪首字。</summary>
    private sealed class RightAlignedLabel : Control
    {
        public RightAlignedLabel()
        {
            TabStop = false;
            SetStyle(
                ControlStyles.UserPaint
                | ControlStyles.AllPaintingInWmPaint
                | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.SupportsTransparentBackColor
                | ControlStyles.ResizeRedraw,
                true);
            BackColor = Color.Transparent;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            if (string.IsNullOrEmpty(Text) || ClientRectangle.Width <= 0)
            {
                return;
            }

            const TextFormatFlags flags = TextFormatFlags.Left | TextFormatFlags.VerticalCenter
                | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix;
            var measured = TextRenderer.MeasureText(e.Graphics, Text, Font, new Size(int.MaxValue, Height), flags);
            var left = Math.Max(0, Width - measured.Width);
            TextRenderer.DrawText(e.Graphics, Text, Font, new Rectangle(left, 0, measured.Width, Height), ForeColor, flags);
        }
    }

    /// <summary>ⓘ 圈 i 说明按钮。</summary>
    private sealed class InfoDotButton : Control
    {
        private bool _hovered;

        public InfoDotButton()
        {
            Cursor = Cursors.Hand;
            TabStop = false;
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

        protected override void OnMouseUp(MouseEventArgs e)
        {
            // 不手动调 OnClick：框架在 MouseUp 之后会自行触发 Click（StandardClick），手动会双触发
            base.OnMouseUp(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var size = Math.Min(Width, Height) - 1;
            var bounds = new Rectangle((Width - size) / 2, (Height - size) / 2, size, size);
            if (_hovered)
            {
                using var hoverBrush = new SolidBrush(AppPalette.SoftBlue);
                e.Graphics.FillEllipse(hoverBrush, bounds);
            }

            using var pen = new Pen(AppPalette.TextSecondary, Math.Max(1.2F, size / 16F));
            e.Graphics.DrawEllipse(pen, bounds);
            using var font = AppFonts.Create(14, FontStyle.Bold, GraphicsUnit.Pixel);
            TextRenderer.DrawText(
                e.Graphics,
                "i",
                font,
                bounds,
                AppPalette.TextSecondary,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
        }
    }
}
