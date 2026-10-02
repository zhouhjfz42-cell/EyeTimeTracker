using EyeTimeTracker.Core.DesktopActivity;

/// <summary>桌面领域规则测试（非夹具部分）：响应率、跨日聚合、桌型变更、时钟跳变。</summary>
static class DesktopDomainTests
{
    private static void Check<T>(T expected, T actual, string name)
    {
        if (!EqualityComparer<T>.Default.Equals(expected, actual))
        {
            throw new Exception($"{name}: expected {expected}, actual {actual}");
        }
    }

    public static void RunAll()
    {
        foreach (var file in Directory.GetFiles(DesktopFixtureRunner.FindFixtureDir(), "*.json").OrderBy(name => name, StringComparer.Ordinal))
        {
            DesktopFixtureRunner.Run(file);
        }

        ResponseRateAndMaturity();
        ResponseWindowEdges();
        DailyAggregatorCrossMidnight();
        DeskProfileChangedKeepsSession();
        ClockJumpBackwardsProducesNoNegative();
        Console.WriteLine("  desktop domain tests passed.");
    }

    private static void ResponseRateAndMaturity()
    {
        var name = nameof(ResponseRateAndMaturity);
        var t0 = new DateTimeOffset(2026, 9, 29, 9, 0, 0, TimeSpan.Zero);
        var opportunities = new List<DeliveredOpportunity>
        {
            new("op-1", t0),
            new("op-2", t0.AddMinutes(30)),
            new("op-3", t0.AddMinutes(60)),
            new("op-4", t0.AddMinutes(90)),
            new("op-5", t0.AddMinutes(120)),
            new("op-6", t0.AddMinutes(155))
        };
        var breaks = Enumerable.Range(0, 4)
            .Select(i => new InferredBreak(
                $"break-{i + 1}",
                "s1",
                DeskType.Ordinary,
                t0.AddMinutes(i * 30).AddMinutes(5),
                t0.AddMinutes(i * 30).AddMinutes(7),
                "idle_timeout"))
            .ToList();

        var evaluation = BreakResponseEvaluator.Evaluate(
            opportunities,
            breaks,
            new List<CoverageGapRecord>(),
            t0.AddMinutes(163));

        Check(5, evaluation.MaturedOpportunities, name + " matured");
        Check(4, evaluation.Responses, name + " responses");
        Check(1, evaluation.Pending, name + " pending");
        Check(0, evaluation.Unknown, name + " unknown");
        Check(80, evaluation.RatePercent ?? -1, name + " rate 80%");

        var empty = BreakResponseEvaluator.Evaluate(
            new List<DeliveredOpportunity>(),
            breaks,
            new List<CoverageGapRecord>(),
            t0);
        Check(true, empty.RatePercent is null, name + " no denominator shows dash");
    }

    private static void ResponseWindowEdges()
    {
        var name = nameof(ResponseWindowEdges);
        var t0 = new DateTimeOffset(2026, 9, 29, 9, 0, 0, TimeSpan.Zero);

        // 9:59 内开始离开算响应；10:01 开始不匹配
        var edge = BreakResponseEvaluator.Evaluate(
            new List<DeliveredOpportunity>
            {
                new("op-1", t0),
                new("op-2", t0.AddMinutes(30))
            },
            new List<InferredBreak>
            {
                new("b-hit", "s1", DeskType.Ordinary, t0.AddMinutes(9).AddSeconds(59), t0.AddMinutes(11).AddSeconds(59), "idle_timeout"),
                new("b-miss", "s1", DeskType.Ordinary, t0.AddMinutes(40).AddSeconds(1), t0.AddMinutes(42).AddSeconds(1), "idle_timeout")
            },
            new List<CoverageGapRecord>(),
            t0.AddHours(1));
        Check(1, edge.Responses, name + " only in-window break counts");

        // 同窗口有两个机会时，一条 break 只匹配最近一条未匹配机会
        var nearest = BreakResponseEvaluator.Evaluate(
            new List<DeliveredOpportunity>
            {
                new("op-early", t0),
                new("op-late", t0.AddMinutes(5))
            },
            new List<InferredBreak>
            {
                new("b-one", "s1", DeskType.Ordinary, t0.AddMinutes(9), t0.AddMinutes(11), "idle_timeout")
            },
            new List<CoverageGapRecord>(),
            t0.AddHours(1));
        Check(1, nearest.Responses, name + " one break matches one opportunity");

        // 休眠使窗口不可观察：记 unknown 并排除分母
        var unobservable = BreakResponseEvaluator.Evaluate(
            new List<DeliveredOpportunity> { new("op-gap", t0) },
            new List<InferredBreak>(),
            new List<CoverageGapRecord> { new(t0.AddMinutes(3), "sleep") },
            t0.AddHours(1));
        Check(1, unobservable.Unknown, name + " unknown excluded");
        Check(true, unobservable.RatePercent is null, name + " unknown leaves no denominator");
    }

    private static void DailyAggregatorCrossMidnight()
    {
        var name = nameof(DailyAggregatorCrossMidnight);
        var timeZone = TimeZoneInfo.CreateCustomTimeZone("utc+8", TimeSpan.FromHours(8), "utc+8", "utc+8");
        var start = new DateTimeOffset(2026, 9, 29, 23, 50, 0, TimeSpan.FromHours(8));
        var classifier = new ActivityClassifier(DesktopSettings.Default with { Desk = DeskType.Ordinary });
        for (var i = 0; i <= 30; i++)
        {
            var at = start.AddMinutes(i);
            classifier.Apply(new ActivitySignal(ActivitySignalKind.Input, at, i * 60_000L));
        }

        classifier.AdvanceTo(start.AddMinutes(31), 31 * 60_000L);
        Check(1, classifier.Sessions.Count, name + " same session across midnight");

        DailyMetrics Aggregate(DateOnly date) => DailyAggregator.Aggregate(
            date,
            classifier.Intervals.ToList(),
            classifier.Sessions.ToList(),
            classifier.Breaks.ToList(),
            new List<DeliveredOpportunity>(),
            classifier.Gaps.ToList(),
            start.AddMinutes(31),
            timeZone);

        var day1 = Aggregate(new DateOnly(2026, 9, 29));
        var day2 = Aggregate(new DateOnly(2026, 9, 30));
        Check(600L, day1.DesktopActiveSeconds, name + " day1 active");
        Check(1200L, day2.DesktopActiveSeconds, name + " day2 active");
        Check(1800L, day1.MaxContinuousActiveSeconds, name + " day1 full session length");
        Check(1800L, day2.MaxContinuousActiveSeconds, name + " day2 full session length");
    }

    private static void DeskProfileChangedKeepsSession()
    {
        var name = nameof(DeskProfileChangedKeepsSession);
        var timeZone = TimeZoneInfo.CreateCustomTimeZone("utc+8", TimeSpan.FromHours(8), "utc+8", "utc+8");
        var start = new DateTimeOffset(2026, 9, 29, 9, 0, 0, TimeSpan.FromHours(8));
        var classifier = new ActivityClassifier(DesktopSettings.Default with { Desk = DeskType.Ordinary });
        for (var i = 0; i <= 10; i++)
        {
            var at = start.AddMinutes(i);
            classifier.Apply(new ActivitySignal(ActivitySignalKind.Input, at, i * 60_000L));
            if (i == 5)
            {
                classifier.Apply(new ActivitySignal(ActivitySignalKind.DeskProfileChanged, at, i * 60_000L, DeskType.Adjustable));
            }
        }

        Check(1, classifier.Sessions.Count, name + " same session");
        Check(600L, SessionAnalyzer.CommittedSeconds(classifier.Intervals), name + " continuous seconds kept");

        var metrics = DailyAggregator.Aggregate(
            new DateOnly(2026, 9, 29),
            classifier.Intervals.ToList(),
            classifier.Sessions.ToList(),
            classifier.Breaks.ToList(),
            new List<DeliveredOpportunity>(),
            classifier.Gaps.ToList(),
            start.AddMinutes(10),
            timeZone);
        Check(600L, metrics.DesktopActiveSeconds, name + " desktop active");
        Check(300L, metrics.EstimatedSedentarySeconds, name + " sedentary only ordinary part");
    }

    private static void ClockJumpBackwardsProducesNoNegative()
    {
        var name = nameof(ClockJumpBackwardsProducesNoNegative);
        var start = new DateTimeOffset(2026, 9, 29, 9, 0, 0, TimeSpan.Zero);
        var classifier = new ActivityClassifier(DesktopSettings.Default);
        classifier.Apply(new ActivitySignal(ActivitySignalKind.Input, start, 0));
        classifier.Apply(new ActivitySignal(ActivitySignalKind.Input, start.AddSeconds(30), 30_000));

        // 时钟回拨：不产生负时长区间，也不误触发离开
        classifier.AdvanceTo(start.AddSeconds(10), 10_000);
        classifier.Apply(new ActivitySignal(ActivitySignalKind.Input, start.AddSeconds(20), 20_000));
        Check(ClassifierState.Active, classifier.State, name + " stays active");
        Check(30L, SessionAnalyzer.CommittedSeconds(classifier.Intervals), name + " no negative intervals");
        Check(0, classifier.Breaks.Count, name + " no phantom break");
    }
}
