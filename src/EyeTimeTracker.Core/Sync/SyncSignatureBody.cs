namespace EyeTimeTracker.Core.Sync;

public static class SyncSignatureBody
{
    public static string ForSyncRequest(SyncRequest request)
    {
        return $"DeviceId={request.DeviceId ?? string.Empty}\n"
            + $"Platform={request.Platform ?? string.Empty}\n"
            + $"SinceUnixSeconds={request.SinceUnixSeconds}";
    }
}
