namespace EyeTimeTracker.Core.DesktopActivity;

/// <summary>提醒机会：有效活动秒数到达 interval 整数倍时产生。两类同点合并为一组。</summary>
public sealed record ReminderOpportunity(
    string ReminderType,
    long DueActiveSeconds,
    int OccurrenceIndex,
    string? DeliveryGroupId,
    bool MergedIntoGroup);

/// <summary>
/// 会话纯逻辑：committed 区间 → 有效活动秒数、提醒到期点、>40 次数。
/// 提醒仅根据 committed activeSeconds 到期；40、80、120 分钟各是一条活动机会；
/// 同点到期的远望与活动合并为一个展示组，远望标记 merged；>40 指标固定 2400 秒。
/// </summary>
public static class SessionAnalyzer
{
    public static long CommittedSeconds(IEnumerable<CommittedInterval> intervals)
    {
        return intervals.Sum(interval => interval.DurationSeconds);
    }

    public static IReadOnlyList<ReminderOpportunity> Opportunities(
        long activeSeconds,
        DesktopSettings settings)
    {
        var opportunities = new List<ReminderOpportunity>();
        if (activeSeconds <= 0)
        {
            return opportunities;
        }

        var eyePoints = DuePoints(activeSeconds, settings.EyeEnabled ? settings.EyeIntervalSeconds : 0);
        var movementPoints = DuePoints(activeSeconds, settings.MovementEnabled ? settings.MovementIntervalSeconds : 0);
        var mergedPoints = eyePoints.Intersect(movementPoints).ToHashSet();
        var mergedGroupIds = mergedPoints
            .OrderBy(point => point)
            .Select((point, index) => (point, GroupId: $"group-{index + 1}"))
            .ToDictionary(pair => pair.point, pair => pair.GroupId);

        foreach (var point in eyePoints)
        {
            var merged = mergedPoints.Contains(point);
            opportunities.Add(new ReminderOpportunity(
                HealthEvents.ReminderEventPayload.TypeEye,
                point,
                OccurrenceIndex: (int)(point / settings.EyeIntervalSeconds),
                DeliveryGroupId: merged ? mergedGroupIds[point] : null,
                MergedIntoGroup: merged));
        }

        foreach (var point in movementPoints)
        {
            var merged = mergedPoints.Contains(point);
            opportunities.Add(new ReminderOpportunity(
                HealthEvents.ReminderEventPayload.TypeMovement,
                point,
                OccurrenceIndex: (int)(point / settings.MovementIntervalSeconds),
                DeliveryGroupId: merged ? mergedGroupIds[point] : null,
                MergedIntoGroup: merged));
        }

        return opportunities
            .OrderBy(opportunity => opportunity.DueActiveSeconds)
            .ThenBy(opportunity => opportunity.ReminderType, StringComparer.Ordinal)
            .ToList();
    }

    /// <summary>>40 次数：一段会话首次严格超过 2400 秒记 1 次，与提醒阈值设置独立。</summary>
    public static bool CrossedLongSessionThreshold(long activeSeconds)
    {
        return activeSeconds > DesktopSettings.LongSessionThresholdSeconds;
    }

    private static List<long> DuePoints(long activeSeconds, int intervalSeconds)
    {
        var points = new List<long>();
        if (intervalSeconds <= 0)
        {
            return points;
        }

        for (long point = intervalSeconds; point <= activeSeconds; point += intervalSeconds)
        {
            points.Add(point);
        }

        return points;
    }
}
