namespace EyeTimeTracker.Core.DesktopActivity;

/// <summary>按当地日期聚合的每日指标（口径见改版开发说明 §7）。</summary>
public sealed record DailyMetrics(
    DateOnly Date,
    long DesktopActiveSeconds,
    long EstimatedSedentarySeconds,
    long MaxContinuousActiveSeconds,
    int SessionsOver40mCount,
    int InferredDesktopBreakCount,
    ResponseEvaluation ResponseRate);

/// <summary>
/// 每日聚合：
/// - 桌面活动总时长 = 当日已确认活动区间的并集秒数（不含离开、锁屏、休眠和未知缺口）；
/// - 估算久坐 = 仅普通桌区间的活动秒数，不声称测量到坐姿；
/// - 跨日区间按当地日期切分；break 次数归开始离开日；>40 次数归首次越界日；
/// - 最长连续显示本日发生过活动的会话完整长度（可能大于当日本地时长）。
/// </summary>
public static class DailyAggregator
{
    public static DailyMetrics Aggregate(
        DateOnly date,
        IReadOnlyList<CommittedInterval> intervals,
        IReadOnlyList<SessionBoundary> sessions,
        IReadOnlyList<InferredBreak> breaks,
        IReadOnlyList<DeliveredOpportunity> deliveredOpportunities,
        IReadOnlyList<CoverageGapRecord> gaps,
        DateTimeOffset evaluatedAtUtc,
        TimeZoneInfo timeZone)
    {
        ArgumentNullException.ThrowIfNull(timeZone);

        var (dayStartUtc, dayEndUtc) = DayBoundsUtc(date, timeZone);

        // 当日已确认活动区间（按日切分）并集
        var clipped = new List<(DateTimeOffset Start, DateTimeOffset End, DeskType Desk, string SessionId)>();
        foreach (var interval in intervals)
        {
            var start = interval.StartUtc > dayStartUtc ? interval.StartUtc : dayStartUtc;
            var end = interval.EndUtc < dayEndUtc ? interval.EndUtc : dayEndUtc;
            if (end > start)
            {
                clipped.Add((start, end, interval.DeskType, interval.SessionId));
            }
        }

        var desktopActiveSeconds = UnionSeconds(clipped.Select(c => (c.Start, c.End)));
        var estimatedSedentarySeconds = UnionSeconds(
            clipped.Where(c => c.Desk == DeskType.Ordinary).Select(c => (c.Start, c.End)));

        // 最长连续：本日发生过活动的会话，取其完整有效长度
        var sessionIdsToday = clipped.Select(c => c.SessionId).ToHashSet(StringComparer.Ordinal);
        var maxContinuous = 0L;
        var over40Count = 0;
        foreach (var session in sessions)
        {
            var sessionSeconds = SessionAnalyzer.CommittedSeconds(
                intervals.Where(interval => interval.SessionId == session.SessionId));
            if (sessionIdsToday.Contains(session.SessionId))
            {
                maxContinuous = Math.Max(maxContinuous, sessionSeconds);
            }

            if (SessionAnalyzer.CrossedLongSessionThreshold(sessionSeconds)
                && session.StartedAtUtc >= dayStartUtc
                && session.StartedAtUtc < dayEndUtc)
            {
                over40Count++;
            }
        }

        var breakCount = breaks.Count(breakEvent =>
            breakEvent.AbsenceStartedAtUtc >= dayStartUtc && breakEvent.AbsenceStartedAtUtc < dayEndUtc);

        var responseRate = BreakResponseEvaluator.Evaluate(deliveredOpportunities, breaks, gaps, evaluatedAtUtc);

        return new DailyMetrics(
            date,
            desktopActiveSeconds,
            estimatedSedentarySeconds,
            maxContinuous,
            over40Count,
            breakCount,
            responseRate);
    }

    public static (DateTimeOffset StartUtc, DateTimeOffset EndUtc) DayBoundsUtc(DateOnly date, TimeZoneInfo timeZone)
    {
        var startLocal = date.ToDateTime(TimeOnly.MinValue);
        var endLocal = date.AddDays(1).ToDateTime(TimeOnly.MinValue);
        return (
            new DateTimeOffset(startLocal, timeZone.GetUtcOffset(startLocal)).ToUniversalTime(),
            new DateTimeOffset(endLocal, timeZone.GetUtcOffset(endLocal)).ToUniversalTime());
    }

    private static long UnionSeconds(IEnumerable<(DateTimeOffset Start, DateTimeOffset End)> intervals)
    {
        var sorted = intervals.OrderBy(interval => interval.Start).ToList();
        long total = 0;
        DateTimeOffset? currentStart = null;
        DateTimeOffset? currentEnd = null;
        foreach (var (start, end) in sorted)
        {
            if (currentStart is null)
            {
                currentStart = start;
                currentEnd = end;
                continue;
            }

            if (start <= currentEnd)
            {
                currentEnd = end > currentEnd ? end : currentEnd;
                continue;
            }

            total += (long)(currentEnd!.Value - currentStart.Value).TotalSeconds;
            currentStart = start;
            currentEnd = end;
        }

        if (currentStart is not null)
        {
            total += (long)(currentEnd!.Value - currentStart.Value).TotalSeconds;
        }

        return Math.Max(0, total);
    }
}
