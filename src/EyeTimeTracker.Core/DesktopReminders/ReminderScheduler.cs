using EyeTimeTracker.Core.DesktopActivity;

namespace EyeTimeTracker.Core.DesktopReminders;

/// <summary>
/// 提醒调度纯逻辑：按 committed 有效活动秒数到期（周期基准 = 上次调度或策略修改时的秒数），
/// 两类同一评估到期合并为一个展示组；抑制（未 Active／暂停／免打扰）记录后不补发；
/// 新会话两个计时重置；策略修改从修改时刻起算下一间隔，不追补旧点。
/// 本类不触碰平台投递，只产出应落盘的实例；同次更新先活动变化后评估提醒（由调用方保证顺序）。
/// </summary>
public sealed class ReminderScheduler
{
    private readonly string _deviceId;

    public ReminderScheduler(string deviceId)
    {
        _deviceId = string.IsNullOrWhiteSpace(deviceId) ? "desktop-local" : deviceId;
    }

    /// <summary>
    /// 评估一次。返回本次应落盘的新实例（待发或已抑制）；state 就地推进。
    /// sessionId 为当前会话（无会话时传 null 或空），sessionActiveSeconds 只取 committed。
    /// </summary>
    public IReadOnlyList<ReminderInstance> Evaluate(
        ClassifierState classifierState,
        string? sessionId,
        long sessionActiveSeconds,
        ReminderSchedulerState state,
        DesktopSettings settings,
        DateTimeOffset evaluatedAtUtc,
        DateTimeOffset localNow)
    {
        ArgumentNullException.ThrowIfNull(state);
        ArgumentNullException.ThrowIfNull(settings);

        // 有效离开后两个计时重置
        var currentSessionId = sessionId ?? string.Empty;
        if (!string.Equals(state.ActiveSessionId, currentSessionId, StringComparison.Ordinal))
        {
            state.ActiveSessionId = currentSessionId;
            state.EyeCycleBaseActiveSeconds = 0;
            state.MovementCycleBaseActiveSeconds = 0;
            state.EyeSequence = 0;
            state.MovementSequence = 0;
        }

        // 修改提醒配置：新 policyVersion 从修改时刻开始算下一提醒间隔，不追补旧点。
        // 首次使用（空版本）只记录版本，不重设基准，否则会吃掉首次到期。
        if (string.IsNullOrEmpty(state.PolicyVersion))
        {
            state.PolicyVersion = settings.PolicyVersion;
        }
        else if (!string.Equals(state.PolicyVersion, settings.PolicyVersion, StringComparison.Ordinal))
        {
            state.EyeCycleBaseActiveSeconds = sessionActiveSeconds;
            state.MovementCycleBaseActiveSeconds = sessionActiveSeconds;
            state.PolicyVersion = settings.PolicyVersion;
        }

        var hasSession = !string.IsNullOrEmpty(currentSessionId);
        var eyeDue = settings.EyeEnabled
            && settings.EyeIntervalSeconds > 0
            && hasSession
            && sessionActiveSeconds - state.EyeCycleBaseActiveSeconds >= settings.EyeIntervalSeconds;
        var movementDue = settings.MovementEnabled
            && settings.MovementIntervalSeconds > 0
            && hasSession
            && sessionActiveSeconds - state.MovementCycleBaseActiveSeconds >= settings.MovementIntervalSeconds;
        if (!eyeDue && !movementDue)
        {
            return Array.Empty<ReminderInstance>();
        }

        var suppressionReason = ResolveSuppressionReason(classifierState, state, settings, evaluatedAtUtc, localNow);
        var results = new List<ReminderInstance>();

        // 两类同一评估到期：合并为一个展示组，远望标记 merged，只算一次实际展示
        var merged = eyeDue && movementDue;
        string? deliveryGroupId = merged ? $"group-{++state.DeliveryGroupSequence}" : null;

        if (eyeDue)
        {
            results.Add(CreateInstance(
                ReminderEventPayloadType.Eye,
                ++state.EyeSequence,
                currentSessionId,
                sessionActiveSeconds,
                settings.PolicyVersion,
                evaluatedAtUtc,
                suppressionReason,
                deliveryGroupId,
                mergedIntoGroup: merged));
            state.EyeCycleBaseActiveSeconds = sessionActiveSeconds;
        }

        if (movementDue)
        {
            results.Add(CreateInstance(
                ReminderEventPayloadType.Movement,
                ++state.MovementSequence,
                currentSessionId,
                sessionActiveSeconds,
                settings.PolicyVersion,
                evaluatedAtUtc,
                suppressionReason,
                deliveryGroupId,
                mergedIntoGroup: false));
            state.MovementCycleBaseActiveSeconds = sessionActiveSeconds;
        }

        return results;
    }

    /// <summary>崩溃恢复：Dispatching 中的实例记为投递失败（中断），不重复播音。</summary>
    public static int MarkInterruptedDispatches(IEnumerable<ReminderInstance> instances, DateTimeOffset closedAtUtc)
    {
        var marked = 0;
        foreach (var instance in instances)
        {
            if (instance.Status == ReminderInstance.StatusDispatching)
            {
                instance.Status = ReminderInstance.StatusDeliveryFailed;
                instance.SuppressionReason = ReminderInstance.ReasonDispatchInterrupted;
                instance.ClosedAtUtc = closedAtUtc;
                marked++;
            }
        }

        return marked;
    }

    private static string? ResolveSuppressionReason(
        ClassifierState classifierState,
        ReminderSchedulerState state,
        DesktopSettings settings,
        DateTimeOffset evaluatedAtUtc,
        DateTimeOffset localNow)
    {
        // 同次状态更新先处理活动变化，再评估提醒；未 Active 一律不投递
        if (classifierState != ClassifierState.Active)
        {
            return ReminderInstance.ReasonNotActive;
        }

        if (state.PausedUntilUtc is { } pausedUntil && evaluatedAtUtc < pausedUntil)
        {
            return ReminderInstance.ReasonPaused;
        }

        if (IsInQuietHours(settings, localNow))
        {
            return ReminderInstance.ReasonQuietHours;
        }

        return null;
    }

    public static bool IsInQuietHours(DesktopSettings settings, DateTimeOffset localNow)
    {
        if (settings.QuietHoursStartMinuteOfDay is not { } start
            || settings.QuietHoursEndMinuteOfDay is not { } end
            || start == end)
        {
            return false;
        }

        var minuteOfDay = localNow.Hour * 60 + localNow.Minute;
        return start < end
            ? minuteOfDay >= start && minuteOfDay < end
            : minuteOfDay >= start || minuteOfDay < end;
    }

    private ReminderInstance CreateInstance(
        string reminderType,
        int occurrenceIndex,
        string sessionId,
        long dueActiveSeconds,
        string policyVersion,
        DateTimeOffset dueAtUtc,
        string? suppressionReason,
        string? deliveryGroupId,
        bool mergedIntoGroup)
    {
        return new ReminderInstance
        {
            IdempotencyKey = ReminderInstance.BuildIdempotencyKey(
                _deviceId,
                sessionId,
                reminderType,
                occurrenceIndex,
                policyVersion),
            ReminderId = $"reminder-{Guid.NewGuid():N}",
            SessionId = sessionId,
            ReminderType = reminderType,
            OccurrenceIndex = occurrenceIndex,
            PolicyVersion = policyVersion,
            DueActiveSeconds = dueActiveSeconds,
            DueAtUtc = dueAtUtc,
            Status = suppressionReason is null
                ? ReminderInstance.StatusDispatching
                : ReminderInstance.StatusSuppressed,
            SuppressionReason = suppressionReason,
            DeliveryGroupId = deliveryGroupId,
            MergedIntoGroup = mergedIntoGroup
        };
    }

    private static class ReminderEventPayloadType
    {
        public const string Eye = "eye";
        public const string Movement = "movement";
    }
}
