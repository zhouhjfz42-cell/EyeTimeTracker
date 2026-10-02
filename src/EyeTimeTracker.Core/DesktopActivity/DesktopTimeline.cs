namespace EyeTimeTracker.Core.DesktopActivity;

public enum TimelineSegmentKind
{
    /// <summary>已确认有效桌面活动。</summary>
    Active,

    /// <summary>推定桌面离开（达到阈值的中断事件）。</summary>
    InferredAway,

    /// <summary>不可观察缺口（休眠、退出、信号丢失）；不能画成休息。</summary>
    Unobserved
}

public sealed record TimelineSegment(
    TimelineSegmentKind Kind,
    DateTimeOffset StartUtc,
    DateTimeOffset EndUtc);

/// <summary>
/// 24 小时时间线分桶：有效活动 / 推定离开 / 不可观察缺口。
/// 未确认的暂存尾部不进入时间线；未回填结束时间的离开与未关闭缺口画到评估时刻。
/// </summary>
public static class DesktopTimeline
{
    public static IReadOnlyList<TimelineSegment> Build(
        DateOnly date,
        IReadOnlyList<CommittedInterval> intervals,
        IReadOnlyList<InferredBreak> breaks,
        IReadOnlyList<CoverageGapRecord> gaps,
        DateTimeOffset evaluatedAtUtc,
        TimeZoneInfo timeZone)
    {
        ArgumentNullException.ThrowIfNull(intervals);
        ArgumentNullException.ThrowIfNull(breaks);
        ArgumentNullException.ThrowIfNull(gaps);
        ArgumentNullException.ThrowIfNull(timeZone);

        var (dayStartUtc, dayEndUtc) = DailyAggregator.DayBoundsUtc(date, timeZone);
        var segments = new List<TimelineSegment>();

        foreach (var interval in intervals)
        {
            AddClipped(segments, TimelineSegmentKind.Active, interval.StartUtc, interval.EndUtc, dayStartUtc, dayEndUtc);
        }

        foreach (var breakEvent in breaks)
        {
            AddClipped(
                segments,
                TimelineSegmentKind.InferredAway,
                breakEvent.AbsenceStartedAtUtc,
                breakEvent.ReturnedAtUtc ?? evaluatedAtUtc,
                dayStartUtc,
                dayEndUtc);
        }

        foreach (var gap in gaps)
        {
            AddClipped(
                segments,
                TimelineSegmentKind.Unobserved,
                gap.FromUtc,
                gap.ToUtc ?? evaluatedAtUtc,
                dayStartUtc,
                dayEndUtc);
        }

        return segments
            .OrderBy(segment => segment.StartUtc)
            .ThenBy(segment => segment.Kind)
            .ToList();
    }

    private static void AddClipped(
        List<TimelineSegment> segments,
        TimelineSegmentKind kind,
        DateTimeOffset startUtc,
        DateTimeOffset endUtc,
        DateTimeOffset dayStartUtc,
        DateTimeOffset dayEndUtc)
    {
        var start = startUtc > dayStartUtc ? startUtc : dayStartUtc;
        var end = endUtc < dayEndUtc ? endUtc : dayEndUtc;
        if (end > start)
        {
            segments.Add(new TimelineSegment(kind, start, end));
        }
    }
}
