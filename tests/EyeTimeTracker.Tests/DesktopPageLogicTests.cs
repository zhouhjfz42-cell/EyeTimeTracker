using EyeTimeTracker.App.UI;
using EyeTimeTracker.Core.DesktopActivity;

/// <summary>P0-4 页面纯逻辑：24 小时时间线分桶、时长与中断率展示格式化。</summary>
internal static class DesktopPageLogicTests
{
    private static readonly TimeZoneInfo Utc = TimeZoneInfo.Utc;

    private static void AssertEqual<T>(T expected, T actual, string name)
    {
        if (!EqualityComparer<T>.Default.Equals(expected, actual))
        {
            throw new Exception($"{name}: expected {expected}, actual {actual}");
        }
    }

    private static void TestTimelineClipsIntervalsToDay()
    {
        var date = new DateOnly(2026, 9, 29);
        var intervals = new List<CommittedInterval>
        {
            new("s1", new DateTimeOffset(2026, 9, 29, 1, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 29, 3, 0, 0, TimeSpan.Zero), DeskType.Ordinary),
            new("s1", new DateTimeOffset(2026, 9, 28, 22, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 28, 23, 0, 0, TimeSpan.Zero), DeskType.Ordinary)
        };
        var segments = DesktopTimeline.Build(date, intervals, new List<InferredBreak>(), new List<CoverageGapRecord>(),
            new DateTimeOffset(2026, 9, 29, 12, 0, 0, TimeSpan.Zero), Utc);

        AssertEqual(1, segments.Count, nameof(TestTimelineClipsIntervalsToDay) + " count");
        AssertEqual(TimelineSegmentKind.Active, segments[0].Kind, nameof(TestTimelineClipsIntervalsToDay) + " kind");
        AssertEqual(new DateTimeOffset(2026, 9, 29, 1, 0, 0, TimeSpan.Zero), segments[0].StartUtc, nameof(TestTimelineClipsIntervalsToDay) + " start");
    }

    private static void TestTimelineSplitsCrossMidnightInterval()
    {
        var date = new DateOnly(2026, 9, 29);
        var intervals = new List<CommittedInterval>
        {
            new("s1", new DateTimeOffset(2026, 9, 28, 23, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 29, 1, 0, 0, TimeSpan.Zero), DeskType.Ordinary)
        };
        var segments = DesktopTimeline.Build(date, intervals, new List<InferredBreak>(), new List<CoverageGapRecord>(),
            new DateTimeOffset(2026, 9, 29, 12, 0, 0, TimeSpan.Zero), Utc);

        AssertEqual(1, segments.Count, nameof(TestTimelineSplitsCrossMidnightInterval) + " count");
        AssertEqual(new DateTimeOffset(2026, 9, 29, 0, 0, 0, TimeSpan.Zero), segments[0].StartUtc, nameof(TestTimelineSplitsCrossMidnightInterval) + " clipped start");
        AssertEqual(new DateTimeOffset(2026, 9, 29, 1, 0, 0, TimeSpan.Zero), segments[0].EndUtc, nameof(TestTimelineSplitsCrossMidnightInterval) + " clipped end");
    }

    private static void TestTimelineOpenBreakAndGapExtendToEvaluationTime()
    {
        var date = new DateOnly(2026, 9, 29);
        var now = new DateTimeOffset(2026, 9, 29, 12, 0, 0, TimeSpan.Zero);
        var breaks = new List<InferredBreak>
        {
            new("b1", "s1", DeskType.Ordinary, new DateTimeOffset(2026, 9, 29, 10, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 29, 10, 2, 0, TimeSpan.Zero), "inferred_away")
        };
        var gaps = new List<CoverageGapRecord>
        {
            new(new DateTimeOffset(2026, 9, 29, 11, 0, 0, TimeSpan.Zero), "suspend")
        };
        var segments = DesktopTimeline.Build(date, new List<CommittedInterval>(), breaks, gaps, now, Utc);

        AssertEqual(2, segments.Count, nameof(TestTimelineOpenBreakAndGapExtendToEvaluationTime) + " count");
        var away = segments[0];
        AssertEqual(TimelineSegmentKind.InferredAway, away.Kind, nameof(TestTimelineOpenBreakAndGapExtendToEvaluationTime) + " away kind");
        AssertEqual(now, away.EndUtc, nameof(TestTimelineOpenBreakAndGapExtendToEvaluationTime) + " away open end");
        var gap = segments[1];
        AssertEqual(TimelineSegmentKind.Unobserved, gap.Kind, nameof(TestTimelineOpenBreakAndGapExtendToEvaluationTime) + " gap kind");
        AssertEqual(now, gap.EndUtc, nameof(TestTimelineOpenBreakAndGapExtendToEvaluationTime) + " gap open end");
    }

    private static void TestTimelineGapDoesNotBecomeRest()
    {
        // 不可观察缺口必须单独成类，不能并入推定离开
        var date = new DateOnly(2026, 9, 29);
        var gaps = new List<CoverageGapRecord>
        {
            new(new DateTimeOffset(2026, 9, 29, 2, 0, 0, TimeSpan.Zero), "app_closed")
            {
                ToUtc = new DateTimeOffset(2026, 9, 29, 4, 0, 0, TimeSpan.Zero)
            }
        };
        var segments = DesktopTimeline.Build(date, new List<CommittedInterval>(), new List<InferredBreak>(), gaps,
            new DateTimeOffset(2026, 9, 29, 12, 0, 0, TimeSpan.Zero), Utc);

        AssertEqual(1, segments.Count, nameof(TestTimelineGapDoesNotBecomeRest) + " count");
        AssertEqual(TimelineSegmentKind.Unobserved, segments[0].Kind, nameof(TestTimelineGapDoesNotBecomeRest) + " kind");
        AssertEqual(new DateTimeOffset(2026, 9, 29, 4, 0, 0, TimeSpan.Zero), segments[0].EndUtc, nameof(TestTimelineGapDoesNotBecomeRest) + " end");
    }

    private static void TestDurationFormatting()
    {
        AssertEqual("0分钟", DesktopDisplayText.Duration(0), nameof(TestDurationFormatting) + " zero");
        AssertEqual("52分钟", DesktopDisplayText.Duration(52 * 60), nameof(TestDurationFormatting) + " minutes");
        AssertEqual("4小时18分", DesktopDisplayText.Duration(4 * 3600 + 18 * 60), nameof(TestDurationFormatting) + " hours");
    }

    private static void TestResponseRateDisplay()
    {
        AssertEqual("—", DesktopDisplayText.Rate(new ResponseEvaluation(0, 0, 0, 0)), nameof(TestResponseRateDisplay) + " no denominator");
        AssertEqual("80%", DesktopDisplayText.Rate(new ResponseEvaluation(5, 4, 1, 0)), nameof(TestResponseRateDisplay) + " percent");
    }

    public static void RunAll()
    {
        TestTimelineClipsIntervalsToDay();
        TestTimelineSplitsCrossMidnightInterval();
        TestTimelineOpenBreakAndGapExtendToEvaluationTime();
        TestTimelineGapDoesNotBecomeRest();
        TestDurationFormatting();
        TestResponseRateDisplay();
    }
}
