using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

/// <summary>
/// 把存储中的零散 segment 按天按来源合并成连续时间区间段，用于压缩 state.json 体积。
/// 只在首尾精确相接（next.Start == prev.End）时合并，不取整、不增减任何一秒，
/// 压缩前后按天统计结果完全一致。只压缩更新时间早于阈值的稳定段：最近几天的原始段保持原样，
/// 避免增量同步按 ID 比对时产生重复；可延长（mutable）段保持原样，保证进行中的会话能继续延长。
/// </summary>
public static class SegmentStorageCompactor
{
    public static List<UsageSegment> CompactForStorage(IEnumerable<UsageSegment>? segments, long compactBeforeUnixSeconds)
    {
        var result = new List<UsageSegment>();
        var groups = new Dictionary<GroupKey, List<UsageSegment>>();
        if (segments is not null)
        {
            foreach (var segment in segments)
            {
                if (!IsCompactable(segment)
                    || segment.UpdatedAtUnixSeconds >= compactBeforeUnixSeconds
                    || UsageSegmentId.IsMutable(segment.SegmentId))
                {
                    result.Add(segment);
                    continue;
                }

                var key = new GroupKey(segment.DeviceId, segment.Platform, segment.Source, segment.LocalDate);
                if (!groups.TryGetValue(key, out var group))
                {
                    group = new List<UsageSegment>();
                    groups[key] = group;
                }

                group.Add(segment);
            }
        }

        foreach (var (key, group) in groups)
        {
            group.Sort(static (left, right) =>
            {
                var byStart = left.StartUnixSeconds.CompareTo(right.StartUnixSeconds);
                return byStart != 0 ? byStart : left.EndUnixSeconds.CompareTo(right.EndUnixSeconds);
            });

            var rangeStart = group[0].StartUnixSeconds;
            var rangeEnd = group[0].EndUnixSeconds;
            for (var index = 1; index < group.Count; index++)
            {
                var segment = group[index];
                if (segment.StartUnixSeconds == rangeEnd)
                {
                    // 首尾精确相接才延长，重叠或有间隔都另起一段，保证总覆盖一秒不差
                    rangeEnd = segment.EndUnixSeconds;
                    continue;
                }

                result.Add(CreateRangeSegment(key, rangeStart, rangeEnd));
                rangeStart = segment.StartUnixSeconds;
                rangeEnd = segment.EndUnixSeconds;
            }

            result.Add(CreateRangeSegment(key, rangeStart, rangeEnd));
        }

        return result;
    }

    private static bool IsCompactable(UsageSegment? segment)
    {
        return segment is not null
            && !string.IsNullOrWhiteSpace(segment.DeviceId)
            && !string.IsNullOrWhiteSpace(segment.Platform)
            && !string.IsNullOrWhiteSpace(segment.Source)
            && segment.LocalDate != default
            && segment.EndUnixSeconds > segment.StartUnixSeconds;
    }

    private static UsageSegment CreateRangeSegment(GroupKey key, long startUnixSeconds, long endUnixSeconds)
    {
        var start = DateTimeOffset.FromUnixTimeSeconds(startUnixSeconds);
        var end = DateTimeOffset.FromUnixTimeSeconds(endUnixSeconds);
        return new UsageSegment
        {
            SegmentId = UsageSegmentId.Create(key.DeviceId, key.Source, start, end),
            DeviceId = key.DeviceId,
            Platform = key.Platform,
            Source = key.Source,
            StartUnixSeconds = startUnixSeconds,
            EndUnixSeconds = endUnixSeconds,
            LocalDate = key.LocalDate,
            CreatedAtUnixSeconds = startUnixSeconds,
            UpdatedAtUnixSeconds = endUnixSeconds
        };
    }

    private readonly record struct GroupKey(string DeviceId, string Platform, string Source, DateOnly LocalDate);
}
