using System.Text;
using System.Text.Json;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Sync;

if (args.Length is < 1 or > 2)
{
    Console.Error.WriteLine("Usage: StatsEngineComparison <state.json> [report.md]");
    return 2;
}

var statePath = Path.GetFullPath(args[0]);
var reportPath = args.Length == 2
    ? Path.GetFullPath(args[1])
    : Path.Combine(Environment.CurrentDirectory, "outputs", "stats-engine-comparison.md");

if (!File.Exists(statePath))
{
    Console.Error.WriteLine($"State file not found: {statePath}");
    return 2;
}

try
{
    using var document = JsonDocument.Parse(File.ReadAllText(statePath));
}
catch (JsonException)
{
    Console.Error.WriteLine("State file is being updated or is invalid. Close the desktop app and run again.");
    return 1;
}

var state = new JsonStateStore(statePath).Load();
var segments = LegacyUsageSegments.NormalizeEffectiveSegments(
    state.Segments,
    state.Records,
    state.DeviceId,
    state.Platform);
var candidateDates = CollectDates(state, segments);

var rows = candidateDates
    .Select(date => CompareDay(date, SegmentsForDate(date, segments)))
    .ToList();

Directory.CreateDirectory(Path.GetDirectoryName(reportPath)!);
File.WriteAllText(reportPath, BuildReport(statePath, segments.Count, rows), new UTF8Encoding(false));
Console.WriteLine($"Report written: {reportPath}");
Console.WriteLine($"Compared {rows.Count} days from {segments.Count} effective segments.");

return 0;

static IReadOnlyList<DateOnly> CollectDates(AppState state, IEnumerable<UsageSegment> segments)
{
    var dates = state.Records
        .Where(record => record.Date != default)
        .Select(record => record.Date)
        .ToHashSet();

    foreach (var segment in segments)
    {
        if (segment.EndUnixSeconds <= segment.StartUnixSeconds)
        {
            continue;
        }

        var start = DateOnly.FromDateTime(DateTimeOffset.FromUnixTimeSeconds(segment.StartUnixSeconds).LocalDateTime);
        var end = DateOnly.FromDateTime(DateTimeOffset.FromUnixTimeSeconds(segment.EndUnixSeconds - 1).LocalDateTime);
        for (var date = start; date <= end; date = date.AddDays(1))
        {
            dates.Add(date);
        }
    }

    return dates.OrderBy(date => date).ToList();
}

static IEnumerable<UsageSegment> SegmentsForDate(DateOnly date, IEnumerable<UsageSegment> segments)
{
    var start = ToUnixSeconds(date, 0);
    var end = ToUnixSeconds(date.AddDays(1), 0);
    return segments.Where(segment => segment.StartUnixSeconds < end && segment.EndUnixSeconds > start);
}

static ComparisonRow CompareDay(DateOnly date, IEnumerable<UsageSegment> segments)
{
    var daySegments = segments.ToList();
    var bucket = UsageSegmentMerger.BuildDailyRecord(date, daySegments);
    var exact = UsageIntervalMerger.BuildDailyRecord(date, daySegments);

    return new ComparisonRow(
        date,
        bucket.TotalSeconds,
        exact.TotalSeconds,
        exact.TotalSeconds - bucket.TotalSeconds,
        MaxHourlyDifference(bucket.HourlySeconds, exact.HourlySeconds),
        LongestSession(bucket.SessionSeconds),
        LongestSession(exact.SessionSeconds));
}

static long ToUnixSeconds(DateOnly date, int hour)
{
    var local = date.ToDateTime(new TimeOnly(hour, 0));
    return new DateTimeOffset(local, TimeZoneInfo.Local.GetUtcOffset(local)).ToUnixTimeSeconds();
}

static long MaxHourlyDifference(long[] bucket, long[] exact)
{
    return Enumerable.Range(0, 24)
        .Select(hour => Math.Abs((bucket.ElementAtOrDefault(hour)) - exact.ElementAtOrDefault(hour)))
        .DefaultIfEmpty(0)
        .Max();
}

static long LongestSession(IEnumerable<long> sessions) => sessions.DefaultIfEmpty(0).Max();

static string BuildReport(string statePath, int segmentCount, IReadOnlyList<ComparisonRow> rows)
{
    var changed = rows.Where(row => row.DeltaSeconds != 0 || row.MaxHourlyDifferenceSeconds != 0 || row.BucketLongestSessionSeconds != row.ExactLongestSessionSeconds).ToList();
    var builder = new StringBuilder();
    builder.AppendLine("# 统计引擎对照报告");
    builder.AppendLine();
    builder.AppendLine($"- 状态文件：`{Path.GetFileName(statePath)}`");
    builder.AppendLine($"- 有效片段：{segmentCount}");
    builder.AppendLine($"- 对照日期：{rows.Count}");
    builder.AppendLine($"- 存在差异的日期：{changed.Count}");
    builder.AppendLine();
    builder.AppendLine("新引擎只按真实片段计算秒数；旧引擎按十秒格向上对齐。因此新引擎通常会更小，差异不代表数据丢失。");
    builder.AppendLine();
    builder.AppendLine("| 日期 | 旧十秒格 | 新精确区间 | 总时长差异 | 最大小时差异 | 旧最长连续 | 新最长连续 |");
    builder.AppendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: |");
    foreach (var row in rows)
    {
        builder.AppendLine($"| {row.Date:yyyy-MM-dd} | {Format(row.BucketSeconds)} | {Format(row.ExactSeconds)} | {FormatSigned(row.DeltaSeconds)} | {Format(row.MaxHourlyDifferenceSeconds)} | {Format(row.BucketLongestSessionSeconds)} | {Format(row.ExactLongestSessionSeconds)} |");
    }

    return builder.ToString();
}

static string Format(long seconds)
{
    var span = TimeSpan.FromSeconds(Math.Max(0, seconds));
    return $"{(int)span.TotalHours}:{span.Minutes:D2}:{span.Seconds:D2}";
}

static string FormatSigned(long seconds) => seconds == 0 ? "0:00:00" : $"{(seconds > 0 ? "+" : "-")}{Format(Math.Abs(seconds))}";

sealed record ComparisonRow(
    DateOnly Date,
    long BucketSeconds,
    long ExactSeconds,
    long DeltaSeconds,
    long MaxHourlyDifferenceSeconds,
    long BucketLongestSessionSeconds,
    long ExactLongestSessionSeconds);
