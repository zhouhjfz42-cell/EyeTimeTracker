using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.App.UI.Controls;
using EyeTimeTracker.Core.DesktopActivity;

namespace EyeTimeTracker.App.UI;

/// <summary>P1 单页设置：提醒设置 + 应用设置合并页，窗口固定 480×932 物理像素（锁定 Min=Max，不随 DPI）。
/// 字体一律 GraphicsUnit.Pixel、最低 20px；左右边距 26、卡片 428 宽、内边距 22、圆角 R18、卡片间距 12。
/// 试听/暂停即时生效；间隔/时长/开关/渠道/免打扰在「保存」时经 UpdateDesktopSettings 一次性保存。</summary>
public sealed class SettingsForm : AppPageForm
{
    private const int PageWidth = 480;
    private const int PageHeight = 932;
    private const int PageMargin = 26;
    private const int CardWidth = 428;
    private const int CardPadding = 22;
    private const int CardRadius = 18;

    private const int StandardEyeMinutes = 20;
    private const int StandardMovementMinutes = 40;
    private const int LightEyeMinutes = 30;
    private const int LightMovementMinutes = 60;

    private readonly DesktopTrackingController _controller;
    private readonly StartupManager _startupManager;
    private readonly Icon? _appIcon;
    private PillButton _presetStandard = null!;
    private PillButton _presetLight = null!;
    private PillButton _presetCustom = null!;
    private AppSwitch _eyeEnabledSwitch = null!;
    private AppSwitch _movementEnabledSwitch = null!;
    private TextBox _eyeIntervalInput = null!;
    private TextBox _eyeActionInput = null!;
    private TextBox _movementIntervalInput = null!;
    private TextBox _movementActionInput = null!;
    private AppCheckBox _eyeSoundCheck = null!;
    private AppCheckBox _eyeNotificationCheck = null!;
    private AppCheckBox _movementSoundCheck = null!;
    private AppCheckBox _movementNotificationCheck = null!;
    private AppSwitch _quietEnabledSwitch = null!;
    private DateTimePicker _quietStartPicker = null!;
    private DateTimePicker _quietEndPicker = null!;
    private PillButton _pauseButton = null!;
    private CanvasLabel _deskValue = null!;
    private AppSwitch _startupSwitch = null!;

    public SettingsForm(DesktopTrackingController controller, StartupManager startupManager, Icon? icon)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        _startupManager = startupManager ?? throw new ArgumentNullException(nameof(startupManager));
        _appIcon = icon;

        Text = AppText.Get("desktop.settings.title");
        SetAppIcon(icon);

        var root = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.Transparent
        };
        Controls.Add(root);

        BuildTitleRow(root);
        BuildPresetRow(root);
        BuildReminderCard(root, isEye: true, top: 116);
        BuildReminderCard(root, isEye: false, top: 318);
        BuildQuietPauseCard(root, top: 520);
        BuildAppSection(root);

        var saveButton = new PillButton
        {
            Text = AppText.Get("desktop.reminders.save"),
            Style = PillButtonStyle.Primary,
            Font = AppFonts.Create(22, FontStyle.Bold, GraphicsUnit.Pixel),
            Bounds = new Rectangle(PageMargin, 872, CardWidth, 44)
        };
        saveButton.Click += (_, _) => Save();
        root.Controls.Add(saveButton);

        ClientSize = new Size(PageWidth, PageHeight);
        CompleteLayoutScaling();
        LoadFromSettings();
    }

    /// <summary>标题行：左「提醒设置」28px Bold，右「返回主页 ›」20px 蓝色文字按钮。</summary>
    private void BuildTitleRow(Control root)
    {
        var title = AppText.Get("desktop.settings.title");
        using (var titleFont = AppFonts.Create(28, FontStyle.Bold, GraphicsUnit.Pixel))
        {
            root.Controls.Add(new CanvasLabel
            {
                Text = title,
                Bounds = new Rectangle(PageMargin, 20, UiText.SingleLineWidth(title, titleFont) + 6, 36),
                Font = AppFonts.Create(28, FontStyle.Bold, GraphicsUnit.Pixel),
                ForeColor = AppPalette.TextPrimary
            });
        }

        var back = new PillButton
        {
            Text = AppText.Get("desktop.settings.backHome"),
            Style = PillButtonStyle.Text,
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel)
        };
        using (var backFont = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel))
        {
            var backWidth = UiText.SingleLineWidth(back.Text, backFont) + 8;
            back.Bounds = new Rectangle(PageMargin + CardWidth - backWidth, 24, backWidth, 32);
        }

        back.Click += (_, _) => Close();
        root.Controls.Add(back);
    }

    /// <summary>预设行：标准 20/40、轻提醒 30/60、自定义；与当前值匹配的高亮，否则高亮自定义。</summary>
    private void BuildPresetRow(Control root)
    {
        var left = PageMargin;
        _presetStandard = CreatePresetCapsule(AppText.Get("desktop.reminders.preset.standard"), ref left);
        _presetStandard.Click += (_, _) => ApplyPreset(StandardEyeMinutes, StandardMovementMinutes);
        root.Controls.Add(_presetStandard);
        _presetLight = CreatePresetCapsule(AppText.Get("desktop.reminders.preset.light"), ref left);
        _presetLight.Click += (_, _) => ApplyPreset(LightEyeMinutes, LightMovementMinutes);
        root.Controls.Add(_presetLight);
        _presetCustom = CreatePresetCapsule(AppText.Get("desktop.reminders.preset.custom"), ref left);
        root.Controls.Add(_presetCustom);
    }

    private PillButton CreatePresetCapsule(string text, ref int left)
    {
        using var font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel);
        var width = UiText.SingleLineWidth(text, font) + 26;
        var capsule = new PillButton
        {
            Text = text,
            Style = PillButtonStyle.Secondary,
            Font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel),
            Bounds = new Rectangle(left, 64, width, 40)
        };
        left += width + 10;
        return capsule;
    }

    /// <summary>远望/活动提醒卡 428×190：标题+开关 / 两个输入框 186×40 / 渠道勾选+试听。</summary>
    private void BuildReminderCard(Control root, bool isEye, int top)
    {
        var accent = isEye ? AppPalette.Teal : AppPalette.Orange;
        var card = new RoundedCardPanel
        {
            Bounds = new Rectangle(PageMargin, top, CardWidth, 190),
            Radius = CardRadius
        };

        var titleText = isEye ? AppText.Get("desktop.reminders.eyeTitle") : AppText.Get("desktop.reminders.movementTitle");
        using (var titleFont = AppFonts.Create(24, FontStyle.Bold, GraphicsUnit.Pixel))
        {
            card.Controls.Add(new CanvasLabel
            {
                Text = titleText,
                Bounds = new Rectangle(CardPadding, 14, UiText.SingleLineWidth(titleText, titleFont) + 6, 32),
                Font = AppFonts.Create(24, FontStyle.Bold, GraphicsUnit.Pixel),
                ForeColor = accent
            });
        }

        var enabledSwitch = new AppSwitch
        {
            Bounds = new Rectangle(CardWidth - CardPadding - 50, 16, 50, 28)
        };
        card.Controls.Add(enabledSwitch);

        var intervalLabel = AppText.Get("desktop.settings.field.interval");
        var actionLabel = AppText.Get(isEye ? "desktop.settings.field.eyeAction" : "desktop.settings.field.movementAction");
        card.Controls.Add(new CanvasLabel
        {
            Text = intervalLabel,
            Bounds = new Rectangle(CardPadding, 56, 186, 24),
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary
        });
        card.Controls.Add(new CanvasLabel
        {
            Text = actionLabel,
            Bounds = new Rectangle(CardPadding + 186 + 12, 56, 186, 24),
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary
        });

        var intervalInput = CreateInput(card, new Rectangle(CardPadding, 82, 186, 40));
        var actionInput = CreateInput(card, new Rectangle(CardPadding + 186 + 12, 82, 186, 40));
        intervalInput.TextChanged += (_, _) => UpdatePresetSelection();
        actionInput.TextChanged += (_, _) => { };

        var soundCheck = CreateChannelCheck(card, "desktop.reminders.channel.sound", CardPadding, 136);
        // 第二个勾选框紧跟第一个（实测宽度），不与右侧试听按钮重叠
        var notificationCheck = CreateChannelCheck(card, "desktop.reminders.channel.notification", soundCheck.Right + 16, 136);

        var listenButton = new PillButton
        {
            Text = AppText.Get("desktop.onboarding.listen"),
            Style = PillButtonStyle.Primary,
            Font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel),
            Bounds = new Rectangle(CardWidth - CardPadding - 100, 130, 100, 40)
        };
        listenButton.Click += (_, _) =>
        {
            // 试听即时播放，不依赖保存
            try
            {
                if (isEye)
                {
                    ReminderTones.PlayEye();
                }
                else
                {
                    ReminderTones.PlayMovement();
                }
            }
            catch (Exception)
            {
            }
        };
        card.Controls.Add(listenButton);

        if (isEye)
        {
            _eyeEnabledSwitch = enabledSwitch;
            _eyeIntervalInput = intervalInput;
            _eyeActionInput = actionInput;
            _eyeSoundCheck = soundCheck;
            _eyeNotificationCheck = notificationCheck;
        }
        else
        {
            _movementEnabledSwitch = enabledSwitch;
            _movementIntervalInput = intervalInput;
            _movementActionInput = actionInput;
            _movementSoundCheck = soundCheck;
            _movementNotificationCheck = notificationCheck;
        }

        root.Controls.Add(card);
    }

    private AppCheckBox CreateChannelCheck(Control card, string textKey, int left, int top)
    {
        var text = AppText.Get(textKey);
        var check = new AppCheckBox
        {
            Text = text,
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        };
        using (var font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel))
        {
            check.Bounds = new Rectangle(left, top, UiText.SingleLineWidth(text, font) + 40, 32);
        }

        card.Controls.Add(check);
        return check;
    }

    private static TextBox CreateInput(Control parent, Rectangle bounds)
    {
        var shell = new RoundedCardPanel
        {
            Bounds = bounds,
            Radius = 10,
            ShowShadow = false,
            FillColor = Color.FromArgb(0xFA, 0xFC, 0xFE),
            BorderColor = Color.FromArgb(0xD5, 0xDE, 0xEE)
        };
        var input = new TextBox
        {
            BorderStyle = BorderStyle.None,
            Font = AppFonts.Create(22, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary,
            BackColor = shell.FillColor,
            MaxLength = 3,
            Bounds = new Rectangle(12, 7, bounds.Width - 24, bounds.Height - 14)
        };
        input.KeyPress += (_, e) =>
        {
            if (!char.IsControl(e.KeyChar) && !char.IsDigit(e.KeyChar))
            {
                e.Handled = true;
            }
        };
        shell.Controls.Add(input);
        parent.Controls.Add(shell);
        return input;
    }

    /// <summary>免打扰/暂停卡 428×168：免打扰+开关 / 时段两个时间框 / 分隔线 / 临时暂停+按钮。</summary>
    private void BuildQuietPauseCard(Control root, int top)
    {
        var card = new RoundedCardPanel
        {
            Bounds = new Rectangle(PageMargin, top, CardWidth, 168),
            Radius = CardRadius
        };

        card.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.settings.quiet"),
            Bounds = new Rectangle(CardPadding, 14, 160, 32),
            Font = AppFonts.Create(24, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });
        _quietEnabledSwitch = new AppSwitch
        {
            Bounds = new Rectangle(CardWidth - CardPadding - 50, 16, 50, 28)
        };
        _quietEnabledSwitch.CheckedChanged += (_, _) => UpdateQuietPickersEnabled();
        card.Controls.Add(_quietEnabledSwitch);

        // 时段行：标签宽度实测，两个时间框与「至」顺排
        var periodLabel = AppText.Get("desktop.settings.quietPeriod");
        int periodWidth;
        using (var periodFont = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel))
        {
            periodWidth = UiText.SingleLineWidth(periodLabel, periodFont) + 6;
        }

        card.Controls.Add(new CanvasLabel
        {
            Text = periodLabel,
            Bounds = new Rectangle(CardPadding, 60, periodWidth, 30),
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });
        var pickerLeft = CardPadding + periodWidth + 8;
        _quietStartPicker = CreateTimePicker(new Rectangle(pickerLeft, 54, 110, 40));
        card.Controls.Add(_quietStartPicker);
        card.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.settings.quietTo"),
            Bounds = new Rectangle(pickerLeft + 114, 60, 30, 30),
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary,
            TextAlign = ContentAlignment.MiddleCenter
        });
        _quietEndPicker = CreateTimePicker(new Rectangle(pickerLeft + 148, 54, 110, 40));
        card.Controls.Add(_quietEndPicker);

        var divider = new Control { Bounds = new Rectangle(CardPadding, 104, CardWidth - CardPadding * 2, 1), BackColor = AppPalette.CardBorder };
        card.Controls.Add(divider);

        card.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.settings.pauseTemp"),
            Bounds = new Rectangle(CardPadding, 116, 140, 30),
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });

        _pauseButton = new PillButton
        {
            Style = PillButtonStyle.Secondary,
            Font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel)
        };
        using (var pauseFont = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel))
        {
            var pauseWidth = Math.Max(
                UiText.SingleLineWidth(AppText.Get("desktop.settings.pause30"), pauseFont),
                UiText.SingleLineWidth(AppText.Get("tray.resumeReminders"), pauseFont)) + 32;
            _pauseButton.Bounds = new Rectangle(CardWidth - CardPadding - pauseWidth, 112, pauseWidth, 40);
        }

        _pauseButton.Click += (_, _) => TogglePause();
        card.Controls.Add(_pauseButton);

        root.Controls.Add(card);
    }

    private static DateTimePicker CreateTimePicker(Rectangle bounds)
    {
        return new DateTimePicker
        {
            Bounds = bounds,
            Format = DateTimePickerFormat.Custom,
            CustomFormat = "HH:mm",
            ShowUpDown = true,
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel)
        };
    }

    /// <summary>应用设置分区标题 + 卡 428×120：桌型 / 开机启动 / 语言三行各 40。</summary>
    private void BuildAppSection(Control root)
    {
        root.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.settings.section.appSettings"),
            Bounds = new Rectangle(PageMargin, 700, 240, 32),
            Font = AppFonts.Create(24, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });

        var card = new RoundedCardPanel
        {
            Bounds = new Rectangle(PageMargin, 740, CardWidth, 120),
            Radius = CardRadius
        };

        // 桌型行
        card.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.settings.desk"),
            Bounds = new Rectangle(CardPadding, 0, 120, 40),
            Font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });
        _deskValue = new CanvasLabel
        {
            Bounds = new Rectangle(180, 0, CardWidth - 180 - CardPadding, 40),
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.Primary,
            TextAlign = ContentAlignment.MiddleRight
        };
        card.Controls.Add(_deskValue);
        WireClick(card.Controls[card.Controls.Count - 1], (_, _) => PickDesk());
        WireClick(card.Controls[0], (_, _) => PickDesk());
        card.Controls.Add(new Control { Bounds = new Rectangle(CardPadding, 40, CardWidth - CardPadding * 2, 1), BackColor = AppPalette.CardBorder });

        // 开机启动行（标签宽度实测，延伸到开关左侧）
        var startupLabel = AppText.Get("desktop.settings.startup");
        using (var startupFont = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel))
        {
            card.Controls.Add(new CanvasLabel
            {
                Text = startupLabel,
                Bounds = new Rectangle(CardPadding, 40, UiText.SingleLineWidth(startupLabel, startupFont) + 6, 40),
                Font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel),
                ForeColor = AppPalette.TextPrimary
            });
        }
        _startupSwitch = new AppSwitch
        {
            Bounds = new Rectangle(CardWidth - CardPadding - 50, 46, 50, 28)
        };
        _startupSwitch.CheckedChanged += (_, _) => ApplyStartup(_startupSwitch.Checked);
        card.Controls.Add(_startupSwitch);
        card.Controls.Add(new Control { Bounds = new Rectangle(CardPadding, 80, CardWidth - CardPadding * 2, 1), BackColor = AppPalette.CardBorder });

        // 语言行（只读）
        card.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.settings.language"),
            Bounds = new Rectangle(CardPadding, 80, 120, 40),
            Font = AppFonts.Create(20, FontStyle.Bold, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextPrimary
        });
        card.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.settings.languageValue"),
            Bounds = new Rectangle(180, 80, CardWidth - 180 - CardPadding, 40),
            Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
            ForeColor = AppPalette.TextSecondary,
            TextAlign = ContentAlignment.MiddleRight
        });

        root.Controls.Add(card);
    }

    private void LoadFromSettings()
    {
        var settings = _controller.DesktopSettings;
        _eyeEnabledSwitch.Checked = settings.EyeEnabled;
        _eyeIntervalInput.Text = (settings.EyeIntervalSeconds / 60).ToString();
        _eyeActionInput.Text = settings.EyeSuggestedSeconds.ToString();
        _eyeSoundCheck.Checked = settings.EyeSoundEnabled;
        _eyeNotificationCheck.Checked = settings.EyeNotificationEnabled;
        _movementEnabledSwitch.Checked = settings.MovementEnabled;
        _movementIntervalInput.Text = (settings.MovementIntervalSeconds / 60).ToString();
        _movementActionInput.Text = Math.Max(1, settings.MovementSuggestedSeconds / 60).ToString();
        _movementSoundCheck.Checked = settings.MovementSoundEnabled;
        _movementNotificationCheck.Checked = settings.MovementNotificationEnabled;

        if (settings.QuietHoursStartMinuteOfDay is { } start && settings.QuietHoursEndMinuteOfDay is { } end)
        {
            _quietEnabledSwitch.Checked = true;
            _quietStartPicker.Value = DateTime.Today.AddMinutes(start);
            _quietEndPicker.Value = DateTime.Today.AddMinutes(end);
        }

        UpdateQuietPickersEnabled();
        UpdatePresetSelection();
        UpdatePauseButton();
        _deskValue.Text = DesktopDisplayText.DeskLabel(settings.Desk) + " ›";
        _startupSwitch.Checked = _controller.Settings.StartWithWindows;
    }

    private void ApplyPreset(int eyeMinutes, int movementMinutes)
    {
        _eyeIntervalInput.Text = eyeMinutes.ToString();
        _movementIntervalInput.Text = movementMinutes.ToString();
        UpdatePresetSelection();
    }

    private void UpdatePresetSelection()
    {
        var eye = ReadInt(_eyeIntervalInput, -1);
        var movement = ReadInt(_movementIntervalInput, -1);
        SelectCapsule(_presetStandard, eye == StandardEyeMinutes && movement == StandardMovementMinutes);
        SelectCapsule(_presetLight, eye == LightEyeMinutes && movement == LightMovementMinutes);
        SelectCapsule(_presetCustom,
            !(eye == StandardEyeMinutes && movement == StandardMovementMinutes)
            && !(eye == LightEyeMinutes && movement == LightMovementMinutes));
    }

    private static void SelectCapsule(PillButton capsule, bool selected)
    {
        capsule.Style = selected ? PillButtonStyle.Primary : PillButtonStyle.Secondary;
        capsule.Invalidate();
    }

    private void UpdateQuietPickersEnabled()
    {
        _quietStartPicker.Enabled = _quietEnabledSwitch.Checked;
        _quietEndPicker.Enabled = _quietEnabledSwitch.Checked;
    }

    private void TogglePause()
    {
        // 暂停/恢复即时生效，不依赖保存
        if (_controller.RemindersPaused)
        {
            _controller.ResumeReminders();
        }
        else
        {
            _controller.PauseReminders();
        }

        UpdatePauseButton();
    }

    private void UpdatePauseButton()
    {
        _pauseButton.Text = AppText.Get(_controller.RemindersPaused
            ? "tray.resumeReminders"
            : "desktop.settings.pause30");
    }

    private void PickDesk()
    {
        using var dialog = new DeskPickerDialog(_controller.DesktopSettings.Desk, _appIcon);
        if (dialog.ShowDialog(this) != DialogResult.OK || dialog.SelectedDesk is not { } desk)
        {
            return;
        }

        _controller.SetDesk(desk);
        _deskValue.Text = DesktopDisplayText.DeskLabel(desk) + " ›";
    }

    private void ApplyStartup(bool enabled)
    {
        _controller.Settings = _controller.Settings with { StartWithWindows = enabled };
        _controller.SaveNow();
        try
        {
            _startupManager.SetEnabled(enabled);
        }
        catch (Exception)
        {
        }
    }

    private void Save()
    {
        if (!TryRead(_eyeIntervalInput, 1, 180, "desktop.reminders.validation.intervalRange")
            || !TryRead(_eyeActionInput, 5, 120, "desktop.reminders.validation.eyeSuggestedRange")
            || !TryRead(_movementIntervalInput, 1, 180, "desktop.reminders.validation.intervalRange")
            || !TryRead(_movementActionInput, 1, 15, "desktop.reminders.validation.movementSuggestedRange"))
        {
            return;
        }

        var eyeInterval = int.Parse(_eyeIntervalInput.Text.Trim());
        var eyeAction = int.Parse(_eyeActionInput.Text.Trim());
        var movementInterval = int.Parse(_movementIntervalInput.Text.Trim());
        var movementAction = int.Parse(_movementActionInput.Text.Trim());

        if (eyeAction > eyeInterval * 60 || movementAction > movementInterval)
        {
            ShowValidationError("desktop.reminders.validation.suggestedExceedsInterval");
            return;
        }

        if (!_eyeSoundCheck.Checked && !_eyeNotificationCheck.Checked
            || !_movementSoundCheck.Checked && !_movementNotificationCheck.Checked)
        {
            ShowValidationError("desktop.channels.keepOne");
            return;
        }

        int? quietStart = null;
        int? quietEnd = null;
        if (_quietEnabledSwitch.Checked)
        {
            quietStart = (int)_quietStartPicker.Value.TimeOfDay.TotalMinutes;
            quietEnd = (int)_quietEndPicker.Value.TimeOfDay.TotalMinutes;
        }

        _controller.UpdateDesktopSettings(settings => settings with
        {
            EyeEnabled = _eyeEnabledSwitch.Checked,
            MovementEnabled = _movementEnabledSwitch.Checked,
            EyeIntervalSeconds = eyeInterval * 60,
            EyeSuggestedSeconds = eyeAction,
            MovementIntervalSeconds = movementInterval * 60,
            MovementSuggestedSeconds = movementAction * 60,
            EyeSoundEnabled = _eyeSoundCheck.Checked,
            EyeNotificationEnabled = _eyeNotificationCheck.Checked,
            MovementSoundEnabled = _movementSoundCheck.Checked,
            MovementNotificationEnabled = _movementNotificationCheck.Checked,
            QuietHoursStartMinuteOfDay = quietStart,
            QuietHoursEndMinuteOfDay = quietEnd
        });
        DialogResult = DialogResult.OK;
        Close();
    }

    private bool TryRead(TextBox input, int min, int max, string messageKey)
    {
        var value = ReadInt(input, -1);
        if (value >= min && value <= max)
        {
            return true;
        }

        ShowValidationError(messageKey);
        return false;
    }

    private void ShowValidationError(string messageKey)
    {
        AppMessageDialog.Info(this, AppText.Get("desktop.settings.title"), AppText.Get(messageKey), _appIcon);
    }

    private static int ReadInt(TextBox input, int fallback)
    {
        return int.TryParse(input.Text.Trim(), out var value) ? value : fallback;
    }

    private static void WireClick(Control control, EventHandler handler)
    {
        control.Cursor = Cursors.Hand;
        control.Click += handler;
    }

    /// <summary>桌型选择弹窗：两个单选行（普通桌 / 升降桌），选中描边加粗，行高按描述测量。</summary>
    private sealed class DeskPickerDialog : AppPageForm
    {
        private readonly List<DeskRow> _rows = new();

        public DeskType? SelectedDesk { get; private set; }

        public DeskPickerDialog(DeskType current, Icon? icon)
        {
            Text = AppText.Get("desktop.settings.desk");
            SetAppIcon(icon);
            StartPosition = FormStartPosition.CenterParent;
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            BackColor = Color.White;

            const int width = 440;
            const int margin = 28;
            var contentWidth = width - margin * 2;

            var top = 22;
            Controls.Add(new CanvasLabel
            {
                Text = AppText.Get("desktop.settings.desk"),
                Bounds = new Rectangle(margin, top, contentWidth, 30),
                Font = AppFonts.Create(15F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = AppPalette.TextPrimary
            });
            top += 30 + 12;

            top = AddRow(DeskType.Ordinary, AppText.Get("desktop.desk.ordinary"), AppText.Get("desktop.onboarding.desk.ordinaryDesc"), current == DeskType.Ordinary, top, margin, contentWidth);
            top = AddRow(DeskType.Adjustable, AppText.Get("desktop.desk.adjustable"), AppText.Get("desktop.onboarding.desk.adjustableDesc"), current == DeskType.Adjustable, top, margin, contentWidth);

            var doneButton = new PillButton
            {
                Text = AppText.Get("desktop.channels.done"),
                Style = PillButtonStyle.Primary,
                Bounds = new Rectangle((width - 184) / 2, top + 8, 184, 46)
            };
            doneButton.Click += (_, _) =>
            {
                DialogResult = DialogResult.OK;
                Close();
            };
            Controls.Add(doneButton);
            top += 8 + 46 + 22;

            ClientSize = new Size(width, top);
            CompleteLayoutScaling();
        }

        private int AddRow(DeskType desk, string title, string description, bool selected, int top, int margin, int contentWidth)
        {
            var row = new DeskRow(desk, title, description, selected)
            {
                Bounds = new Rectangle(margin, top, contentWidth, 68)
            };
            row.Height = row.PreferredHeight(contentWidth);
            row.Click += (_, _) => Select(desk);
            _rows.Add(row);
            Controls.Add(row);
            return row.Bottom + 12;
        }

        private void Select(DeskType desk)
        {
            SelectedDesk = desk;
            foreach (var row in _rows)
            {
                row.Selected = row.Desk == desk;
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
            using var path = UiGraphics.RoundedRect(bounds, 22);
            using var fill = new SolidBrush(Color.White);
            using var border = new Pen(AppPalette.CardBorder);
            e.Graphics.FillPath(fill, path);
            e.Graphics.DrawPath(border, path);
        }

        protected override void OnResize(EventArgs e)
        {
            using var path = UiGraphics.RoundedRect(new Rectangle(0, 0, Width, Height), 22);
            Region = new Region(path);
            base.OnResize(e);
        }

        private sealed class DeskRow : Control
        {
            private readonly string _title;
            private readonly string _description;
            private bool _selected;

            public DeskRow(DeskType desk, string title, string description, bool selected)
            {
                Desk = desk;
                _title = title;
                _description = description;
                _selected = selected;
                Cursor = Cursors.Hand;
                SetStyle(
                    ControlStyles.UserPaint
                    | ControlStyles.AllPaintingInWmPaint
                    | ControlStyles.OptimizedDoubleBuffer
                    | ControlStyles.SupportsTransparentBackColor
                    | ControlStyles.ResizeRedraw,
                    true);
                BackColor = Color.Transparent;
            }

            public DeskType Desk { get; }

            public bool Selected
            {
                get => _selected;
                set
                {
                    _selected = value;
                    Invalidate();
                }
            }

            public int PreferredHeight(int width)
            {
                using var descFont = AppFonts.Create(8.5F, FontStyle.Regular, GraphicsUnit.Point);
                var descHeight = UiText.WrappedHeight(_description, descFont, width - 36);
                return Math.Max(64, 12 + 24 + 2 + descHeight + 12);
            }

            protected override void OnMouseClick(MouseEventArgs e)
            {
                // Click 由框架在 MouseClick 之后统一触发，这里不手动调 OnClick，否则 Select 会执行两次
                base.OnMouseClick(e);
            }

            protected override void OnPaint(PaintEventArgs e)
            {
                e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                var bounds = new Rectangle(0, 0, Width - 1, Height - 1);
                using (var path = UiGraphics.RoundedRect(bounds, 14))
                {
                    using var fill = new SolidBrush(_selected ? AppPalette.SoftBlue : Color.White);
                    e.Graphics.FillPath(fill, path);
                    using var pen = new Pen(_selected ? AppPalette.Primary : AppPalette.CardBorder, _selected ? 2F : 1F);
                    e.Graphics.DrawPath(pen, path);
                }

                var padX = 16;
                using (var titleFont = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point))
                {
                    TextRenderer.DrawText(e.Graphics, _title, titleFont, new Rectangle(padX, 10, Width - padX * 2, 22), AppPalette.TextPrimary,
                        TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
                }

                using (var descFont = AppFonts.Create(8.5F, FontStyle.Regular, GraphicsUnit.Point))
                {
                    var descTop = 34;
                    TextRenderer.DrawText(e.Graphics, _description, descFont, new Rectangle(padX, descTop, Width - padX * 2, Height - descTop - 8), AppPalette.TextSecondary,
                        TextFormatFlags.Left | TextFormatFlags.Top | TextFormatFlags.WordBreak | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
                }
            }
        }
    }
}
