using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.App.UI;
using EyeTimeTracker.Core.DesktopActivity;
using EyeTimeTracker.Core.DesktopReminders;
using EyeTimeTracker.Core.Storage;

/// <summary>
/// 页面渲染自检：用带种子数据的控制器实例化每个页面，DrawToBitmap 导出 PNG，
/// 供人工逐张检查截断/覆盖/遮挡。入口：dotnet run -- render-dump &lt;输出目录&gt;。
/// 多语言渲染：先设 EYETIMETRACKER_LOCALE 环境变量再运行。
/// 渲染前把进程切到 PerMonitorV2：窗体按真实屏幕 DPI（如 175%）走 AutoScaleMode.Dpi，
/// 位图分辨率同步设置，导出的 PNG 即真机等效布局。
/// </summary>
internal static class DesktopPageRenderDump
{
    // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2
    private static readonly IntPtr PerMonitorV2 = new(-4);

    [DllImport("user32.dll")]
    private static extern bool SetProcessDpiAwarenessContext(IntPtr value);

    public static void RunAll(string outputDir)
    {
        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                var dpiAware = SetProcessDpiAwarenessContext(PerMonitorV2);
                Console.WriteLine("dpi awareness PerMonitorV2: " + (dpiAware ? "applied" : "fallback (already set or unavailable)"));
                RenderAll(outputDir);
            }
            catch (Exception exception)
            {
                failure = exception;
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure is not null)
        {
            throw failure;
        }
    }

    private static void RenderAll(string outputDir)
    {
        Directory.CreateDirectory(outputDir);
        var storePath = Path.Combine(Path.GetTempPath(), "eyetime-render-dump", Guid.NewGuid() + ".json");
        var store = new DesktopStateStore(storePath);
        store.Save(BuildSeedSnapshot());
        var controller = new DesktopTrackingController(new IdleTimeProvider(), store);
        try
        {
            var startup = new StartupManager();
            var icon = SystemIcons.Application;

            Render(new DeskOnboardingForm(controller, startup, icon), outputDir, "1-onboarding.png");
            Render(new MainForm(controller, startup, icon), outputDir, "2-main.png");
            Render(new SettingsForm(controller, startup, icon), outputDir, "5-settings.png");
            Render(new TrayMenuForm(icon, () => { }, () => { }, () => { }, () => { }), outputDir, "6-traymenu.png");
            Render(new DesktopReminderToast(
                isMovement: false,
                EyeTimeTracker.App.Localization.AppText.Get("desktop.eye.toastTitle"),
                EyeTimeTracker.App.Localization.AppText.Format("desktop.eye.toastBody", ("seconds", 20)),
                icon), outputDir, "9-toast-eye.png");
            Render(new DesktopReminderToast(
                isMovement: true,
                EyeTimeTracker.App.Localization.AppText.Format("desktop.movement.toastTitle.ordinary", ("minutes", 2)),
                EyeTimeTracker.App.Localization.AppText.Get("desktop.movement.toastBody"),
                icon), outputDir, "10-toast-movement.png");
            Render(new AppMessageDialog(
                EyeTimeTracker.App.Localization.AppText.Get("desktop.main.sessionTitle.ordinary"),
                EyeTimeTracker.App.Localization.AppText.Get("desktop.main.infoTip"),
                EyeTimeTracker.App.Localization.AppText.Get("common.gotIt"),
                null,
                danger: false,
                icon), outputDir, "7-messagedialog.png");

            // 设置页桌型选择弹窗（私有嵌套类，用反射实例化）
            var pickerType = typeof(SettingsForm).GetNestedType("DeskPickerDialog", System.Reflection.BindingFlags.NonPublic);
            if (pickerType is not null
                && Activator.CreateInstance(pickerType, DeskType.Ordinary, icon) is Form picker)
            {
                Render(picker, outputDir, "8-deskpicker.png");
            }
        }
        finally
        {
            controller.Dispose();
            try
            {
                File.Delete(storePath);
            }
            catch (Exception)
            {
            }
        }
    }

    private static void Render(Form form, string outputDir, string fileName)
    {
        try
        {
            // 展开滚动区：让窗体高度容纳全部内容再导出（锁了尺寸的窗体跳过——锁定本身即验收点）
            var scroll = form.Controls.OfType<Panel>().FirstOrDefault(panel => panel.AutoScroll);
            if (form.MaximumSize.IsEmpty
                && scroll is not null && scroll.AutoScrollMinSize.Height > scroll.Height)
            {
                var chrome = form.ClientSize.Height - scroll.Height;
                form.ClientSize = new Size(
                    form.ClientSize.Width,
                    Math.Min(1600, scroll.AutoScrollMinSize.Height + chrome));
            }

            form.StartPosition = FormStartPosition.Manual;
            // 放在主显示器上渲染：离屏坐标会被 Windows 指派到最近显示器（可能是别的 DPI），
            // 测量与绘制 DPI 不一致会产生假象；自检工具短暂闪现可接受
            var primary = Screen.PrimaryScreen!.WorkingArea;
            form.Location = new Point(primary.Right - form.Width - 8, primary.Top + 8);

            if (form is MainForm mainForm)
            {
                // 锁尺寸证明：编程拉宽 400px 应被 MaximumSize 钳制，内容不跟随拉伸
                Console.WriteLine($"  [lock] border={mainForm.FormBorderStyle} size={mainForm.Size} min={mainForm.MinimumSize} max={mainForm.MaximumSize}");
                var lockedWidth = mainForm.Width;
                mainForm.Width = lockedWidth + 400;
                Console.WriteLine($"  [lock] widen +400 attempt -> width={mainForm.Width} (clamped={mainForm.Width == lockedWidth})");
                if (mainForm.Width != lockedWidth)
                {
                    throw new Exception("MainForm width is not locked");
                }

                // 设计基准证明：尺寸按物理像素固定，锁定值 = 设计宽 480（与 DPI 无关）
                Console.WriteLine($"  [lock] clientWidth={mainForm.ClientSize.Width} designBase=480 dpi={mainForm.DeviceDpi} expected=480");
                if (mainForm.ClientSize.Width != 480
                    || mainForm.ClientSize.Height != 890
                    || mainForm.MinimumSize != mainForm.Size
                    || mainForm.MaximumSize != mainForm.Size)
                {
                    throw new Exception("MainForm locked size does not equal 480x890 physical pixels");
                }
            }
            // DrawToBitmap 对原生 TextBox 只在可见状态下才渲染文字，保持显示并离屏截取
            form.Show();
            form.ActiveControl = null;
            form.Refresh();
            Console.WriteLine($"  [diag] {fileName}: client={form.ClientSize.Width}x{form.ClientSize.Height} dpi={form.DeviceDpi} window={form.Width}x{form.Height}");

            using var bitmap = new Bitmap(form.Width, form.Height);
            // 位图分辨率对齐窗体设备 DPI：Point 字号按真实缩放渲染，所见即真机
            bitmap.SetResolution(form.DeviceDpi, form.DeviceDpi);
            form.DrawToBitmap(bitmap, new Rectangle(0, 0, form.Width, form.Height));
            bitmap.Save(Path.Combine(outputDir, fileName), ImageFormat.Png);
            Console.WriteLine($"{fileName}: {form.GetType().Name} {form.Width}x{form.Height} @ {form.DeviceDpi}dpi");
        }
        finally
        {
            form.Dispose();
        }
    }

    /// <summary>造一份「今天有两段会话、一次中断响应、一次等待观察」的真实感数据。</summary>
    private static DesktopStateSnapshot BuildSeedSnapshot()
    {
        var today = DateTime.Today;
        DateTimeOffset Local(int hour, int minute) => new(today.AddHours(hour).AddMinutes(minute), TimeZoneInfo.Local.GetUtcOffset(today.AddHours(hour).AddMinutes(minute)));

        var s1 = new SessionBoundary("seed-s1", DeskType.Ordinary, Local(8, 2))
        {
            EndedAtUtc = Local(9, 12),
            EndReason = SessionBoundary.ReasonInferredAway
        };
        var s2 = new SessionBoundary("seed-s2", DeskType.Ordinary, Local(9, 15))
        {
            EndedAtUtc = Local(10, 7),
            EndReason = SessionBoundary.ReasonInferredAway
        };
        var intervals = new List<CommittedInterval>
        {
            new("seed-s1", Local(8, 2), Local(8, 32), DeskType.Ordinary),
            new("seed-s1", Local(8, 33), Local(9, 0), DeskType.Ordinary),
            new("seed-s1", Local(9, 1), Local(9, 12), DeskType.Ordinary),
            new("seed-s2", Local(9, 15), Local(9, 50), DeskType.Ordinary),
            new("seed-s2", Local(9, 52), Local(10, 7), DeskType.Ordinary)
        };
        var breaks = new List<InferredBreak>
        {
            new("seed-b1", "seed-s1", DeskType.Ordinary, Local(9, 12), Local(9, 14), "inferred_away")
            {
                ReturnedAtUtc = Local(9, 15)
            },
            new("seed-b2", "seed-s2", DeskType.Ordinary, Local(10, 7), Local(10, 9), "inferred_away")
            {
                ReturnedAtUtc = Local(10, 10)
            }
        };
        var gaps = new List<CoverageGapRecord>
        {
            new(Local(7, 0), "app_closed") { ToUtc = Local(8, 0) }
        };

        ReminderInstance Reminder(string id, string sessionId, string type, DateTimeOffset dueAt, string status, DateTimeOffset? dispatchAt)
        {
            return new ReminderInstance
            {
                IdempotencyKey = $"desktop-local:{sessionId}:{type}:1:desktop-v1:{id}",
                ReminderId = id,
                SessionId = sessionId,
                ReminderType = type,
                OccurrenceIndex = 1,
                PolicyVersion = "desktop-v1",
                DueActiveSeconds = 1200,
                DueAtUtc = dueAt,
                Status = status,
                DispatchAtUtc = dispatchAt,
                ClosedAtUtc = dispatchAt
            };
        }

        var now = DateTimeOffset.UtcNow;
        var instances = new List<ReminderInstance>
        {
            Reminder("seed-eye-1", "seed-s1", "eye", Local(8, 22), ReminderInstance.StatusDelivered, Local(8, 22)),
            Reminder("seed-eye-2", "seed-s1", "eye", Local(8, 42), ReminderInstance.StatusDelivered, Local(8, 42)),
            Reminder("seed-move-1", "seed-s1", "movement", Local(9, 5), ReminderInstance.StatusDelivered, Local(9, 5)),
            Reminder("seed-move-2", "seed-s2", "movement", now.AddMinutes(-3), ReminderInstance.StatusDelivered, now.AddMinutes(-3))
        };

        return new DesktopStateSnapshot
        {
            HasCompletedOnboarding = true,
            Settings = DesktopSettings.Default with { Desk = DeskType.Ordinary },
            StartWithWindows = true,
            StartedTrackingAtUtc = Local(8, 2),
            SavedAtUtc = now,
            DeviceId = "desktop-local",
            Sessions = new List<SessionBoundary> { s1, s2 },
            Intervals = intervals,
            Breaks = breaks,
            Gaps = gaps,
            LastClassifierState = ClassifierState.Away,
            ReminderState = new ReminderSchedulerState { PolicyVersion = "desktop-v1" },
            ReminderInstances = instances
        };
    }
}
