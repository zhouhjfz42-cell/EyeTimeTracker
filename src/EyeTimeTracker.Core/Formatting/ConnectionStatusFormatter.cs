namespace EyeTimeTracker.Core.Formatting;

public static class ConnectionStatusFormatter
{
    public static string Format(string baseStatus, bool isConnected, string peerName)
    {
        if (!isConnected || string.IsNullOrWhiteSpace(peerName))
        {
            return baseStatus;
        }

        return $"{baseStatus}\uff08\u5df2\u8fde\u63a5{peerName}\uff09";
    }

    public static string Format(string baseStatus, bool isPaired, bool isOnline, string peerName)
    {
        if (!isPaired || string.IsNullOrWhiteSpace(peerName))
        {
            return baseStatus;
        }

        return isOnline
            ? $"{baseStatus}\uff08\u5df2\u8fde\u63a5{peerName}\uff09"
            : $"{baseStatus}\uff08{peerName}\u6682\u65f6\u79bb\u7ebf\uff09";
    }
}
