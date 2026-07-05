using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.Core.Formatting;

public static class EyeCareSummaryFormatter
{
    private const long ContinuousPressureSeconds = 45L * 60L;
    private const long NightPressureSeconds = 60L * 60L;
    private const int HighPhonePercent = 60;

    public static string SourceText(UsageDeviceBreakdown breakdown)
    {
        if (breakdown.PcSeconds + breakdown.PhoneSeconds <= 0)
        {
            return "暂无设备来源数据";
        }

        return breakdown.PhonePercent >= breakdown.PcPercent
            ? $"手机占比 {breakdown.PhonePercent}%，建议用大屏或拉远"
            : $"电脑占比 {breakdown.PcPercent}%，注意定时休息";
    }

    public static string CareText(long longestSessionSeconds, long nightSeconds, int phonePercent)
    {
        if (longestSessionSeconds >= ContinuousPressureSeconds)
        {
            return "护眼表现：连续用眼偏多";
        }

        if (nightSeconds >= NightPressureSeconds)
        {
            return "护眼表现：夜间用眼偏多";
        }

        if (phonePercent >= HighPhonePercent)
        {
            return "护眼表现：手机占比较高";
        }

        return longestSessionSeconds <= 0 && nightSeconds <= 0
            ? "护眼表现：暂无明显压力"
            : "护眼表现：节奏较平稳";
    }
}
