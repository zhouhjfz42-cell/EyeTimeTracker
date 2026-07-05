package com.eyetimetracker.android;

public final class EyeCareSummaryFormatter {
    private static final long CONTINUOUS_PRESSURE_SECONDS = 45L * 60L;
    private static final long NIGHT_PRESSURE_SECONDS = 60L * 60L;
    private static final int HIGH_PHONE_PERCENT = 60;

    private EyeCareSummaryFormatter() {
    }

    public static String sourceText(DeviceUsageBreakdown breakdown) {
        if (breakdown.pcSeconds + breakdown.phoneSeconds <= 0L) {
            return "暂无设备来源数据";
        }

        if (breakdown.phonePercent() >= breakdown.pcPercent()) {
            return "手机占比 " + breakdown.phonePercent() + "%，建议用大屏或拉远";
        }

        return "电脑占比 " + breakdown.pcPercent() + "%，注意定时休息";
    }

    public static String careText(long longestSessionSeconds, long nightSeconds, int phonePercent) {
        if (longestSessionSeconds >= CONTINUOUS_PRESSURE_SECONDS) {
            return "护眼表现：连续用眼偏多";
        }

        if (nightSeconds >= NIGHT_PRESSURE_SECONDS) {
            return "护眼表现：夜间用眼偏多";
        }

        if (phonePercent >= HIGH_PHONE_PERCENT) {
            return "护眼表现：手机占比较高";
        }

        return longestSessionSeconds <= 0L && nightSeconds <= 0L
                ? "护眼表现：暂无明显压力"
                : "护眼表现：节奏较平稳";
    }
}
