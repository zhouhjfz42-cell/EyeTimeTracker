using EyeTimeTracker.Core.DesktopActivity;
using EyeTimeTracker.Core.DesktopReminders;

/// <summary>P0-3 提醒调度纯逻辑测试（§6 状态机与冲突规则）。</summary>
static class DesktopReminderSchedulerTests
{
    private static readonly DateTimeOffset T0 = new(2026, 9, 29, 12, 0, 0, TimeSpan.Zero);

    private static void Check<T>(T expected, T actual, string name)
    {
        if (!EqualityComparer<T>.Default.Equals(expected, actual))
        {
            throw new Exception($"{name}: expected {expected}, actual {actual}");
        }
    }

    public static void RunAll()
    {
        EyeDueAtIntervalThenIdempotent();
        MergedWhenBothDueAtSameEvaluation();
        SuppressedWhenNotActiveAndNoBackfill();
        PausedSuppressedAndResumeContinues();
        QuietHoursAcrossMidnight();
        PolicyChangeRebasesNextDue();
        NewSessionResetsCycles();
        CrashRecoveryMarksInterrupted();
        Console.WriteLine("  desktop reminder scheduler tests passed.");
    }

    private static void EyeDueAtIntervalThenIdempotent()
    {
        var name = nameof(EyeDueAtIntervalThenIdempotent);
        var scheduler = new ReminderScheduler("device-1");
        var state = new ReminderSchedulerState();
        var settings = DesktopSettings.Default;

        var none = scheduler.Evaluate(ClassifierState.Active, "s1", 1199, state, settings, T0, T0);
        Check(0, none.Count, name + " nothing before 1200");

        var due = scheduler.Evaluate(ClassifierState.Active, "s1", 1200, state, settings, T0, T0);
        Check(1, due.Count, name + " one eye due");
        Check("eye", due[0].ReminderType, name + " type");
        Check(1, due[0].OccurrenceIndex, name + " occurrence 1");
        Check(ReminderInstance.StatusDispatching, due[0].Status, name + " dispatching");
        Check(1200L, state.EyeCycleBaseActiveSeconds, name + " base advanced");

        var again = scheduler.Evaluate(ClassifierState.Active, "s1", 1210, state, settings, T0, T0);
        Check(0, again.Count, name + " idempotent at same cycle");

        var next = scheduler.Evaluate(ClassifierState.Active, "s1", 2400, state, settings, T0, T0);
        Check(true, next.Any(instance => instance.ReminderType == "eye"), name + " next eye at 2400 cycle");
    }

    private static void MergedWhenBothDueAtSameEvaluation()
    {
        var name = nameof(MergedWhenBothDueAtSameEvaluation);
        var scheduler = new ReminderScheduler("device-1");
        var state = new ReminderSchedulerState();
        var settings = DesktopSettings.Default;

        var due = scheduler.Evaluate(ClassifierState.Active, "s1", 2400, state, settings, T0, T0);
        Check(2, due.Count, name + " two instances");
        var eye = due.Single(instance => instance.ReminderType == "eye");
        var movement = due.Single(instance => instance.ReminderType == "movement");
        Check(true, eye.MergedIntoGroup, name + " eye merged");
        Check(false, movement.MergedIntoGroup, name + " movement primary");
        Check(eye.DeliveryGroupId ?? "", movement.DeliveryGroupId ?? "", name + " same delivery group");

        var again = scheduler.Evaluate(ClassifierState.Active, "s1", 2405, state, settings, T0, T0);
        Check(0, again.Count, name + " no repeat after merged dispatch");
    }

    private static void SuppressedWhenNotActiveAndNoBackfill()
    {
        var name = nameof(SuppressedWhenNotActiveAndNoBackfill);
        var scheduler = new ReminderScheduler("device-1");
        var state = new ReminderSchedulerState();
        var settings = DesktopSettings.Default;

        var suppressed = scheduler.Evaluate(ClassifierState.IdleCandidate, "s1", 1200, state, settings, T0, T0);
        Check(1, suppressed.Count, name + " one instance recorded");
        Check(ReminderInstance.StatusSuppressed, suppressed[0].Status, name + " suppressed");
        Check(ReminderInstance.ReasonNotActive, suppressed[0].SuppressionReason ?? "", name + " reason not active");
        Check(1200L, state.EyeCycleBaseActiveSeconds, name + " base still advanced");

        var noBackfill = scheduler.Evaluate(ClassifierState.Active, "s1", 1210, state, settings, T0, T0);
        Check(0, noBackfill.Count, name + " no backfill after resume");
    }

    private static void PausedSuppressedAndResumeContinues()
    {
        var name = nameof(PausedSuppressedAndResumeContinues);
        var scheduler = new ReminderScheduler("device-1");
        var state = new ReminderSchedulerState
        {
            PausedUntilUtc = T0.AddMinutes(30)
        };
        var settings = DesktopSettings.Default;

        var suppressed = scheduler.Evaluate(ClassifierState.Active, "s1", 1200, state, settings, T0, T0);
        Check(ReminderInstance.ReasonPaused, suppressed[0].SuppressionReason ?? "", name + " reason paused");

        state.PausedUntilUtc = null;
        var next = scheduler.Evaluate(ClassifierState.Active, "s1", 1210, state, settings, T0.AddMinutes(31), T0.AddMinutes(31));
        Check(0, next.Count, name + " no historical queue after resume");
        var scheduled = scheduler.Evaluate(ClassifierState.Active, "s1", 2400, state, settings, T0.AddMinutes(31), T0.AddMinutes(31));
        Check(2, scheduled.Count, name + " next valid schedule point fires merged pair");
        Check(
            scheduled[0].DeliveryGroupId ?? "",
            scheduled[1].DeliveryGroupId ?? "",
            name + " resumed pair shares group");
    }

    private static void QuietHoursAcrossMidnight()
    {
        var name = nameof(QuietHoursAcrossMidnight);
        var settings = DesktopSettings.Default with
        {
            QuietHoursStartMinuteOfDay = 22 * 60 + 30,
            QuietHoursEndMinuteOfDay = 7 * 60
        };
        Check(true, ReminderScheduler.IsInQuietHours(settings, T0.AddHours(11)), name + " 23:00 in quiet hours");
        Check(true, ReminderScheduler.IsInQuietHours(settings, T0.AddHours(-6)), name + " 06:00 in quiet hours");
        Check(false, ReminderScheduler.IsInQuietHours(settings, T0), name + " 12:00 not in quiet hours");

        var scheduler = new ReminderScheduler("device-1");
        var state = new ReminderSchedulerState();
        var atNight = T0.AddHours(11);
        var suppressed = scheduler.Evaluate(ClassifierState.Active, "s1", 1200, state, settings, atNight, atNight);
        Check(ReminderInstance.ReasonQuietHours, suppressed[0].SuppressionReason ?? "", name + " reason quiet hours");
    }

    private static void PolicyChangeRebasesNextDue()
    {
        var name = nameof(PolicyChangeRebasesNextDue);
        var scheduler = new ReminderScheduler("device-1");
        var state = new ReminderSchedulerState();
        var settings = DesktopSettings.Default;

        scheduler.Evaluate(ClassifierState.Active, "s1", 2400, state, settings, T0, T0);

        // 改成 25 分钟：从修改时刻（当前 2400 秒）起算下一间隔，不追补旧点
        var changed = settings with { EyeIntervalSeconds = 25 * 60, PolicyVersion = "desktop-v1-t1" };
        var atChange = scheduler.Evaluate(ClassifierState.Active, "s1", 2400, state, changed, T0, T0);
        Check(0, atChange.Count, name + " nothing fired at change point");
        Check(2400L, state.EyeCycleBaseActiveSeconds, name + " rebased at change point");
        var none = scheduler.Evaluate(ClassifierState.Active, "s1", 2440, state, changed, T0, T0);
        Check(0, none.Count, name + " not due yet after rebase");
        var due = scheduler.Evaluate(ClassifierState.Active, "s1", 2400 + 1500, state, changed, T0, T0);
        Check(1, due.Count, name + " due at change + new interval");
        Check("desktop-v1-t1", due[0].PolicyVersion, name + " new policy version on instance");
    }

    private static void NewSessionResetsCycles()
    {
        var name = nameof(NewSessionResetsCycles);
        var scheduler = new ReminderScheduler("device-1");
        var state = new ReminderSchedulerState();
        var settings = DesktopSettings.Default;

        scheduler.Evaluate(ClassifierState.Active, "s1", 2400, state, settings, T0, T0);

        // 新会话：两个计时重置
        var fresh = scheduler.Evaluate(ClassifierState.Active, "s2", 1199, state, settings, T0, T0);
        Check(0, fresh.Count, name + " new session does not inherit old progress");
        Check(0L, state.EyeCycleBaseActiveSeconds, name + " eye cycle reset");
        Check(0L, state.MovementCycleBaseActiveSeconds, name + " movement cycle reset");
        var due = scheduler.Evaluate(ClassifierState.Active, "s2", 1200, state, settings, T0, T0);
        Check(1, due.Count, name + " first eye of new session");
        Check(1, due[0].OccurrenceIndex, name + " occurrence restarts at 1");
        Check(
            ReminderInstance.BuildIdempotencyKey("device-1", "s2", "eye", 1, "desktop-v1"),
            due[0].IdempotencyKey,
            name + " idempotency key");
    }

    private static void CrashRecoveryMarksInterrupted()
    {
        var name = nameof(CrashRecoveryMarksInterrupted);
        var instances = new List<ReminderInstance>
        {
            new()
            {
                IdempotencyKey = "k1",
                ReminderId = "r1",
                SessionId = "s1",
                ReminderType = "eye",
                PolicyVersion = "desktop-v1",
                DueActiveSeconds = 1200,
                DueAtUtc = T0,
                Status = ReminderInstance.StatusDispatching
            },
            new()
            {
                IdempotencyKey = "k2",
                ReminderId = "r2",
                SessionId = "s1",
                ReminderType = "eye",
                PolicyVersion = "desktop-v1",
                DueActiveSeconds = 2400,
                DueAtUtc = T0,
                Status = ReminderInstance.StatusDelivered
            }
        };

        var marked = ReminderScheduler.MarkInterruptedDispatches(instances, T0.AddMinutes(1));
        Check(1, marked, name + " one interrupted");
        Check(ReminderInstance.StatusDeliveryFailed, instances[0].Status, name + " dispatching marked failed");
        Check(ReminderInstance.ReasonDispatchInterrupted, instances[0].SuppressionReason ?? "", name + " interrupted reason");
        Check(ReminderInstance.StatusDelivered, instances[1].Status, name + " delivered untouched");
    }
}
