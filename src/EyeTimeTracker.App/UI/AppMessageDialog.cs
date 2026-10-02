using EyeTimeTracker.App.Localization;
using EyeTimeTracker.App.UI.Controls;

namespace EyeTimeTracker.App.UI;

/// <summary>简单确认/提示弹窗：标题 + 正文 + 主按钮（可选危险色）+ 可选取消。
/// 高度按正文实际换行高度自适应，正文完整显示。</summary>
public sealed class AppMessageDialog : AppPageForm
{
    private const int PageWidth = 460;
    private const int PageMargin = 28;
    private const int ContentWidth = PageWidth - PageMargin * 2;

    public AppMessageDialog(string title, string body, string primaryText, string? cancelText, bool danger, Icon? icon)
    {
        Text = title;
        SetAppIcon(icon);
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = false;
        BackColor = Color.White;

        var top = 24;
        Controls.Add(new CanvasLabel
        {
            Text = title,
            Bounds = new Rectangle(PageMargin, top, ContentWidth, 32),
            Font = AppFonts.Create(15F, FontStyle.Bold, GraphicsUnit.Point),
            ForeColor = danger ? AppPalette.Danger : AppPalette.TextPrimary
        });
        top += 32 + 8;

        using (var bodyFont = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel))
        {
            var bodyHeight = UiText.WrappedHeight(body, bodyFont, ContentWidth) + 6;
            Controls.Add(new CanvasLabel
            {
                Text = body,
                Bounds = new Rectangle(PageMargin, top, ContentWidth, bodyHeight),
                Font = AppFonts.Create(20, FontStyle.Regular, GraphicsUnit.Pixel),
                ForeColor = AppPalette.TextSecondary,
                WordWrap = true,
                TextAlign = ContentAlignment.TopLeft
            });
            top += bodyHeight + 18;
        }

        var primaryButton = new PillButton
        {
            Text = primaryText,
            Style = PillButtonStyle.Primary,
            ButtonColor = danger ? AppPalette.Danger : AppPalette.Primary,
            Bounds = cancelText is null
                ? new Rectangle((PageWidth - 200) / 2, top, 200, 48)
                : new Rectangle(PageMargin + (ContentWidth - 16) / 2 + 16, top, (ContentWidth - 16) / 2, 48)
        };
        primaryButton.Click += (_, _) =>
        {
            DialogResult = DialogResult.OK;
            Close();
        };
        Controls.Add(primaryButton);

        if (cancelText is not null)
        {
            var cancelButton = new PillButton
            {
                Text = cancelText,
                Style = PillButtonStyle.Secondary,
                Bounds = new Rectangle(PageMargin, top, (ContentWidth - 16) / 2, 48)
            };
            cancelButton.Click += (_, _) =>
            {
                DialogResult = DialogResult.Cancel;
                Close();
            };
            Controls.Add(cancelButton);
        }

        top += 48 + 24;
        ClientSize = new Size(PageWidth, top);
        CompleteLayoutScaling();
    }

    public static void Info(IWin32Window owner, string title, string body, Icon? icon = null)
    {
        using var dialog = new AppMessageDialog(title, body, AppText.Get("common.gotIt"), null, false, icon);
        dialog.ShowDialog(owner);
    }

    public static bool Confirm(IWin32Window owner, string title, string body, string confirmText, bool danger = false, Icon? icon = null)
    {
        using var dialog = new AppMessageDialog(title, body, confirmText, AppText.Get("common.cancel"), danger, icon);
        return dialog.ShowDialog(owner) == DialogResult.OK;
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

    protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
    {
        if (keyData == Keys.Escape)
        {
            DialogResult = DialogResult.Cancel;
            Close();
            return true;
        }

        return base.ProcessCmdKey(ref msg, keyData);
    }
}
