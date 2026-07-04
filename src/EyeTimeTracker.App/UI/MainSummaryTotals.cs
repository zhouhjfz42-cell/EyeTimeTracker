using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.App.UI;

public sealed record MainSummaryTotals(
    long TodaySeconds,
    long YesterdaySeconds,
    long WeekSeconds,
    long MonthSeconds)
{
    public static MainSummaryTotals FromRecords(DateOnly today, IEnumerable<DailyRecord> records)
    {
        var snapshot = records?.ToList() ?? new List<DailyRecord>();
        var yesterday = today.AddDays(-1);
        var weekStart = today.AddDays(-GetMondayOffset(today.DayOfWeek));
        var monthStart = new DateOnly(today.Year, today.Month, 1);

        return new MainSummaryTotals(
            RecordSeconds(snapshot, today),
            RecordSeconds(snapshot, yesterday),
            SumRecords(snapshot, weekStart, today),
            SumRecords(snapshot, monthStart, today));
    }

    private static int GetMondayOffset(DayOfWeek dayOfWeek)
    {
        return dayOfWeek == DayOfWeek.Sunday ? 6 : (int)dayOfWeek - (int)DayOfWeek.Monday;
    }

    private static long SumRecords(IEnumerable<DailyRecord> records, DateOnly start, DateOnly end)
    {
        return records
            .Where(record => record.Date >= start && record.Date <= end)
            .Sum(record => record.TotalSeconds);
    }

    private static long RecordSeconds(IEnumerable<DailyRecord> records, DateOnly date)
    {
        return records.FirstOrDefault(record => record.Date == date)?.TotalSeconds ?? 0;
    }
}
