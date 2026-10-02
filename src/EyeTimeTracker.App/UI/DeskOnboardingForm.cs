using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.App.UI.Controls;
using EyeTimeTracker.Core.DesktopActivity;

namespace EyeTimeTracker.App.UI;

/// <summary>首次引导：选择桌型（普通桌 / 升降桌，不预选、必须选一项），参考效果图 1。
/// DeskType.Unknown 仅为数据兼容保留，新版 UI 不再提供入口。
/// 排版按实际文字测量自适应：描述文字完整换行，控件高度随内容变化。</summary>
public sealed class DeskOnboardingForm : AppPageForm
{
    private const int PageWidth = 600;
    private const int PageMargin = 28;
    private const int ContentWidth = PageWidth - PageMargin * 2;

    private readonly DesktopTrackingController _controller;
    private readonly StartupManager _startupManager;
    private readonly Icon? _appIcon;
    private readonly List<DeskOptionCard> _cards = new();
    private readonly PillButton _startButton;
    private readonly AppCheckBox _autostartCheck;
    private DeskType? _selected;

    public DeskOnboardingForm(DesktopTrackingController controller, StartupManager startupManager, Icon? icon)
    {
        _controller = controller ?? throw new ArgumentNullException(nameof(controller));
        _startupManager = startupManager ?? throw new ArgumentNullException(nameof(startupManager));
        _appIcon = icon;

        Text = AppText.Get("app.name");
        SetAppIcon(icon);

        var top = 18;
        var brand = new BrandHeader { Bounds = new Rectangle(PageMargin, top, ContentWidth, 42) };
        brand.Height = brand.PreferredHeight(ContentWidth);
        Controls.Add(brand);
        top = brand.Bottom + 10;

        using (var titleFont = AppFonts.Create(20F, FontStyle.Bold, GraphicsUnit.Point))
        {
            var title = AppText.Get("desktop.onboarding.title");
            Controls.Add(new CanvasLabel
            {
                Text = title,
                Bounds = new Rectangle(PageMargin, top, ContentWidth, UiText.SingleLineHeight(title, titleFont) + 8),
                Font = AppFonts.Create(20F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = AppPalette.TextPrimary
            });
            top += UiText.SingleLineHeight(title, titleFont) + 8 + 4;
        }

        using (var leadFont = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point))
        {
            var lead = AppText.Get("desktop.onboarding.lead");
            var leadHeight = UiText.WrappedHeight(lead, leadFont, ContentWidth) + 4;
            Controls.Add(new CanvasLabel
            {
                Text = lead,
                Bounds = new Rectangle(PageMargin, top, ContentWidth, leadHeight),
                Font = AppFonts.Create(10F, FontStyle.Regular, GraphicsUnit.Point),
                ForeColor = AppPalette.TextSecondary,
                WordWrap = true,
                TextAlign = ContentAlignment.TopLeft
            });
            top += leadHeight + 10;
        }

        top = AddDeskCard(DeskType.Ordinary, "坐", AppPalette.Teal, AppPalette.SoftTeal,
            AppText.Get("desktop.desk.ordinary"), AppText.Get("desktop.onboarding.desk.ordinaryDesc"), top);
        top = AddDeskCard(DeskType.Adjustable, "站", AppPalette.Primary, AppPalette.SoftBlue,
            AppText.Get("desktop.desk.adjustable"), AppText.Get("desktop.onboarding.desk.adjustableDesc"), top);

        top = BuildPlanCard(top);

        _autostartCheck = new AppCheckBox
        {
            Text = AppText.Get("desktop.onboarding.autostart"),
            Checked = _controller.Settings.StartWithWindows,
            Bounds = new Rectangle(PageMargin, top, ContentWidth, 30)
        };
        _autostartCheck.Height = _autostartCheck.PreferredHeight(ContentWidth);
        _autostartCheck.CheckedChanged += (_, _) => ApplyStartup(_autostartCheck.Checked);
        Controls.Add(_autostartCheck);
        top = _autostartCheck.Bottom + 12;

        _startButton = new PillButton
        {
            Text = AppText.Get("desktop.onboarding.start"),
            Style = PillButtonStyle.Primary,
            Enabled = false,
            Bounds = new Rectangle(PageMargin, top, ContentWidth, 48)
        };
        _startButton.Click += (_, _) =>
        {
            if (_selected is { } desk)
            {
                Complete(desk);
            }
        };
        Controls.Add(_startButton);
        top += 48 + 10;

        Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.onboarding.footer"),
            Bounds = new Rectangle(PageMargin, top, ContentWidth, 18),
            Font = AppFonts.Create(9F, FontStyle.Regular, GraphicsUnit.Point),
            ForeColor = AppPalette.TextSecondary,
            TextAlign = ContentAlignment.MiddleCenter
        });
        top += 18 + 14;

        ClientSize = new Size(PageWidth, top);
        CompleteLayoutScaling();
    }

    private int AddDeskCard(DeskType desk, string glyph, Color chipColor, Color chipBackground, string title, string description, int top)
    {
        var card = new DeskOptionCard(desk, glyph, chipColor, chipBackground, title, description)
        {
            Bounds = new Rectangle(PageMargin, top, ContentWidth, 98)
        };
        card.Height = card.PreferredHeight(ContentWidth);
        card.Click += (_, _) => Select(desk);
        _cards.Add(card);
        Controls.Add(card);
        return card.Bottom + 10;
    }

    private int BuildPlanCard(int top)
    {
        var card = new RoundedCardPanel
        {
            Bounds = new Rectangle(PageMargin, top, ContentWidth, 100),
            FillColor = AppPalette.SoftBlue,
            BorderColor = Color.FromArgb(0xD8, 0xE4, 0xFA),
            ShowShadow = false
        };

        using (var buttonFont = AppFonts.Create(9.5F, FontStyle.Bold, GraphicsUnit.Point))
        {
            var listenText = AppText.Get("desktop.onboarding.listen");
            var adjustText = AppText.Get("desktop.onboarding.adjust");
            var adjustWidth = UiText.SingleLineWidth(adjustText, buttonFont) + 28;
            var listenWidth = UiText.SingleLineWidth(listenText, buttonFont) + 28;
            var adjustButton = new PillButton
            {
                Text = adjustText,
                Style = PillButtonStyle.Secondary,
                Font = AppFonts.Create(9.5F, FontStyle.Bold, GraphicsUnit.Point),
                Bounds = new Rectangle(ContentWidth - 18 - adjustWidth, 12, adjustWidth, 32)
            };
            adjustButton.Click += (_, _) =>
            {
                using var settings = new SettingsForm(_controller, _startupManager, _appIcon);
                settings.ShowDialog(this);
            };
            var listenButton = new PillButton
            {
                Text = listenText,
                Style = PillButtonStyle.Secondary,
                Font = AppFonts.Create(9.5F, FontStyle.Bold, GraphicsUnit.Point),
                Bounds = new Rectangle(adjustButton.Left - 10 - listenWidth, 12, listenWidth, 32)
            };
            listenButton.Click += (_, _) =>
            {
                try
                {
                    ReminderTones.PlayEye();
                }
                catch (Exception)
                {
                }
            };
            card.Controls.Add(listenButton);
            card.Controls.Add(adjustButton);
        }

        card.Controls.Add(new CanvasLabel
        {
            Text = AppText.Get("desktop.onboarding.planTitle"),
            Bounds = new Rectangle(20, 12, 220, 32),
            Font = AppFonts.Create(11F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = AppPalette.TextPrimary
        });

        using (var planFont = AppFonts.Create(10.5F, FontStyle.Bold, GraphicsUnit.Point))
        {
            var planLine = AppText.Get("desktop.onboarding.planLine");
            var lineWidth = ContentWidth - 40;
            var lineHeight = UiText.WrappedHeight(planLine, planFont, lineWidth) + 4;
            card.Controls.Add(new CanvasLabel
            {
                Text = planLine,
                Bounds = new Rectangle(20, 50, lineWidth, lineHeight),
                Font = AppFonts.Create(10.5F, FontStyle.Bold, GraphicsUnit.Point),
                ForeColor = AppPalette.Primary,
                WordWrap = true,
                TextAlign = ContentAlignment.TopLeft
            });
            card.Height = 50 + lineHeight + 12;
        }

        Controls.Add(card);
        return card.Bottom + 12;
    }

    private void Select(DeskType desk)
    {
        _selected = desk;
        foreach (var card in _cards)
        {
            card.Selected = card.Desk == desk;
        }

        _startButton.Enabled = true;
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

    private void Complete(DeskType desk)
    {
        _controller.CompleteOnboarding(desk);
        DialogResult = DialogResult.OK;
        Close();
    }

    private sealed class DeskOptionCard : Control
    {
        private readonly string _glyph;
        private readonly Color _chipColor;
        private readonly Color _chipBackground;
        private readonly string _title;
        private readonly string _description;
        private bool _selected;
        private bool _hovered;

        public DeskOptionCard(DeskType desk, string glyph, Color chipColor, Color chipBackground, string title, string description)
        {
            Desk = desk;
            _glyph = glyph;
            _chipColor = chipColor;
            _chipBackground = chipBackground;
            _title = title;
            _description = description;
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

        /// <summary>给定宽度（设计像素）下完整显示标题与描述（描述可换行）所需高度。</summary>
        public int PreferredHeight(int width)
        {
            using var descFont = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point);
            var descHeight = UiText.WrappedHeight(_description, descFont, DescriptionWidth(width));
            return Math.Max(86, 16 + 26 + 4 + descHeight + 14);
        }

        private int DescriptionWidth(int width)
        {
            return width - Dev(136);
        }

        private int Dev(int value)
        {
            // 设计像素即物理像素（不随 DPI 缩放）
            return IsHandleCreated ? value : value;
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

        protected override void OnMouseClick(MouseEventArgs e)
        {
            // Click 由框架在 MouseClick 之后统一触发，这里不手动调 OnClick，否则处理器会执行两次
            base.OnMouseClick(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var bounds = new Rectangle(0, 0, Width - 1, Height - Dev(3));
            using (var path = UiGraphics.RoundedRect(bounds, Dev(16)))
            {
                using var fill = new SolidBrush(Color.White);
                e.Graphics.FillPath(fill, path);
                using var border = new Pen(_selected ? AppPalette.Primary : (_hovered ? Color.FromArgb(0xC5, 0xD3, 0xEE) : AppPalette.CardBorder), _selected ? 2F : 1F);
                e.Graphics.DrawPath(border, path);
            }

            var chipSize = Dev(40);
            var chipBounds = new Rectangle(Dev(18), Dev(16), chipSize, chipSize);
            using (var chipBrush = new SolidBrush(_chipBackground))
            {
                e.Graphics.FillEllipse(chipBrush, chipBounds);
            }

            using (var chipFont = AppFonts.Create(13F, FontStyle.Bold, GraphicsUnit.Point))
            {
                TextRenderer.DrawText(e.Graphics, _glyph, chipFont, chipBounds, _chipColor,
                    TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
            }

            var textLeft = chipBounds.Right + Dev(16);
            using (var titleFont = AppFonts.Create(12F, FontStyle.Bold, GraphicsUnit.Point))
            {
                TextRenderer.DrawText(e.Graphics, _title, titleFont, new Rectangle(textLeft, Dev(16), Width - textLeft - Dev(56), Dev(26)), AppPalette.TextPrimary,
                    TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
            }

            using (var descFont = AppFonts.Create(9.5F, FontStyle.Regular, GraphicsUnit.Point))
            {
                var descTop = Dev(44);
                TextRenderer.DrawText(e.Graphics, _description, descFont, new Rectangle(textLeft, descTop, DescriptionWidth(Width), Height - descTop - Dev(12)), AppPalette.TextSecondary,
                    TextFormatFlags.Left | TextFormatFlags.Top | TextFormatFlags.WordBreak | TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix);
            }

            if (_selected)
            {
                var markSize = Dev(26);
                var mark = new Rectangle(Width - markSize - Dev(18), Dev(18), markSize, markSize);
                using var markBrush = new SolidBrush(AppPalette.Primary);
                e.Graphics.FillEllipse(markBrush, mark);
                using var checkPen = new Pen(Color.White, Math.Max(1.6F, markSize / 11F))
                {
                    StartCap = System.Drawing.Drawing2D.LineCap.Round,
                    EndCap = System.Drawing.Drawing2D.LineCap.Round
                };
                e.Graphics.DrawLines(checkPen, new[]
                {
                    new Point(mark.Left + markSize / 4, mark.Top + markSize / 2 + 1),
                    new Point(mark.Left + markSize / 2 - 1, mark.Bottom - markSize / 4 - 1),
                    new Point(mark.Right - markSize / 4 - 1, mark.Top + markSize / 4 + 1)
                });
            }
        }
    }
}
