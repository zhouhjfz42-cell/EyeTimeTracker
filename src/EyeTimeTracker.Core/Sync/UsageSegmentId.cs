using System.Security.Cryptography;
using System.Text;

namespace EyeTimeTracker.Core.Sync;

public static class UsageSegmentId
{
    public static string Create(string deviceId, string source, DateTimeOffset start, DateTimeOffset end)
    {
        var raw = $"{deviceId}:{source}:{start.ToUnixTimeSeconds()}:{end.ToUnixTimeSeconds()}";
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(raw));
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }
}
