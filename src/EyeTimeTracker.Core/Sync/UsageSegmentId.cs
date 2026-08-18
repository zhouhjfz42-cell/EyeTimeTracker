using System.Security.Cryptography;
using System.Text;

namespace EyeTimeTracker.Core.Sync;

public static class UsageSegmentId
{
    private const string MutablePrefix = "m1_";

    public static string Create(string deviceId, string source, DateTimeOffset start, DateTimeOffset end)
    {
        var raw = $"{deviceId}:{source}:{start.ToUnixTimeSeconds()}:{end.ToUnixTimeSeconds()}";
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(raw));
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }

    public static string CreateMutable(string deviceId, string source, DateTimeOffset start)
    {
        var raw = $"mutable:{deviceId}:{source}:{start.ToUnixTimeSeconds()}";
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(raw));
        return MutablePrefix + Convert.ToHexString(bytes).ToLowerInvariant();
    }

    public static bool IsMutable(string? segmentId)
    {
        return !string.IsNullOrWhiteSpace(segmentId)
            && segmentId.StartsWith(MutablePrefix, StringComparison.Ordinal);
    }
}
