namespace EyeTimeTracker.Core.DesktopActivity;

/// <summary>已投递的活动提醒机会（只有实际投递成功才创建机会）。</summary>
public sealed record DeliveredOpportunity(
    string OpportunityId,
    DateTimeOffset DeliveredAtUtc);

public sealed record ResponseEvaluation(
    int MaturedOpportunities,
    int Responses,
    int Pending,
    int Unknown)
{
    /// <summary>无分母返回 null，界面显示"— / 暂无可评估提醒"，不能显示 0%。</summary>
    public int? RatePercent =>
        MaturedOpportunities > 0 ? (int)Math.Round(100.0 * Responses / MaturedOpportunities) : null;
}

/// <summary>
/// 提醒后中断率（估算）匹配规则（见改版开发说明 §7）：
/// - 只对实际投递成功的活动提醒创建机会；
/// - 投递后 10 分钟内开始无输入并持续达到阈值，关联为响应；
/// - 窗口终点按 absenceStartedAt 判断；一条 break 只匹配最近一条未匹配机会；
/// - 成功机会立即成熟；未响应机会到投递后 12 分钟成熟；未成熟显示"等待观察"；
/// - 休眠、崩溃或缺口使窗口不可观察时记 unknown 并排除分母，同时显示未知数。
/// </summary>
public static class BreakResponseEvaluator
{
    public static readonly TimeSpan ResponseWindow = TimeSpan.FromMinutes(10);
    public static readonly TimeSpan MaturityWindow = TimeSpan.FromMinutes(12);

    public static ResponseEvaluation Evaluate(
        IReadOnlyList<DeliveredOpportunity> opportunities,
        IReadOnlyList<InferredBreak> breaks,
        IReadOnlyList<CoverageGapRecord> gaps,
        DateTimeOffset evaluatedAtUtc)
    {
        ArgumentNullException.ThrowIfNull(opportunities);
        ArgumentNullException.ThrowIfNull(breaks);
        ArgumentNullException.ThrowIfNull(gaps);

        var sortedOpportunities = opportunities
            .OrderBy(opportunity => opportunity.DeliveredAtUtc)
            .ToList();
        var sortedBreaks = breaks
            .OrderBy(breakEvent => breakEvent.AbsenceStartedAtUtc)
            .ToList();

        var matchedOpportunityIds = new HashSet<string>(StringComparer.Ordinal);
        var matchedBreakIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var breakEvent in sortedBreaks)
        {
            // 一条 break 只匹配最近一条未匹配机会
            DeliveredOpportunity? candidate = null;
            foreach (var opportunity in sortedOpportunities)
            {
                if (matchedOpportunityIds.Contains(opportunity.OpportunityId))
                {
                    continue;
                }

                var windowEnd = opportunity.DeliveredAtUtc + ResponseWindow;
                if (breakEvent.AbsenceStartedAtUtc > opportunity.DeliveredAtUtc
                    && breakEvent.AbsenceStartedAtUtc <= windowEnd)
                {
                    candidate = opportunity;
                }
            }

            if (candidate is not null)
            {
                matchedOpportunityIds.Add(candidate.OpportunityId);
                matchedBreakIds.Add(breakEvent.BreakId);
            }
        }

        var responses = matchedOpportunityIds.Count;
        var matured = responses;
        var pending = 0;
        var unknown = 0;
        foreach (var opportunity in sortedOpportunities)
        {
            if (matchedOpportunityIds.Contains(opportunity.OpportunityId))
            {
                continue;
            }

            if (WindowIsUnobservable(opportunity.DeliveredAtUtc, gaps))
            {
                unknown++;
                continue;
            }

            if (evaluatedAtUtc >= opportunity.DeliveredAtUtc + MaturityWindow)
            {
                matured++;
            }
            else
            {
                pending++;
            }
        }

        return new ResponseEvaluation(matured, responses, pending, unknown);
    }

    /// <summary>休眠、崩溃或缺口使机会窗口不可观察时记 unknown。</summary>
    private static bool WindowIsUnobservable(DateTimeOffset deliveredAtUtc, IReadOnlyList<CoverageGapRecord> gaps)
    {
        var windowEnd = deliveredAtUtc + MaturityWindow;
        foreach (var gap in gaps)
        {
            var gapEnd = gap.ToUtc ?? DateTimeOffset.MaxValue;
            if (gap.FromUtc < windowEnd && gapEnd > deliveredAtUtc)
            {
                return true;
            }
        }

        return false;
    }
}
