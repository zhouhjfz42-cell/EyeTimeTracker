package com.eyetimetracker.android;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AndroidAppUsageCollector {
    private static final String DIAG_TAG = "EyeTimeDiag";
    private static final long MIN_APP_USAGE_SECONDS = 30L;
    private final Context context;

    public AndroidAppUsageCollector(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    public List<AppUsageEntry> collectDailyUsage(EyeTimeStore store, LocalDate date, long nowMillis) {
        List<AppUsageEntry> entries = new ArrayList<>();
        if (context == null || store == null || date == null) {
            return entries;
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return entries;
        }

        long startMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = Math.min(
                date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                Math.max(startMillis, nowMillis));
        if (endMillis <= startMillis) {
            return entries;
        }

        PackageManager packageManager = context.getPackageManager();
        Map<String, String> installedApps = readInstalledLaunchableApps(packageManager);
        Map<String, Long> eventSecondsByPackage = readForegroundSeconds(manager, startMillis, endMillis);
        Map<String, Long> statsSecondsByPackage = eventSecondsByPackage.isEmpty()
                ? readAggregatedUsageSeconds(manager, startMillis, endMillis)
                : new HashMap<>();
        Map<String, Long> secondsByPackage = eventSecondsByPackage.isEmpty()
                ? statsSecondsByPackage
                : eventSecondsByPackage;
        String deviceId = store.getDeviceId();
        long updatedAt = nowMillis / 1000L;
        int skippedShort = 0;
        int skippedSystem = 0;
        int skippedNotLaunchable = 0;
        int skippedNoLabel = 0;
        List<String> samples = new ArrayList<>();
        for (Map.Entry<String, Long> value : secondsByPackage.entrySet()) {
            String packageName = value.getKey();
            long seconds = value.getValue() == null ? 0L : value.getValue();
            if (seconds < MIN_APP_USAGE_SECONDS) {
                skippedShort++;
                addSample(samples, "short", packageName, seconds);
                continue;
            }
            if (shouldSkipPackage(packageManager, packageName)) {
                skippedSystem++;
                addSample(samples, "skip", packageName, seconds);
                continue;
            }
            if (!installedApps.containsKey(packageName)) {
                skippedNotLaunchable++;
                addSample(samples, "hidden", packageName, seconds);
                continue;
            }
            String label = resolveAppLabel(packageManager, installedApps, packageName);
            if (label == null || label.trim().isEmpty()) {
                skippedNoLabel++;
                addSample(samples, "label", packageName, seconds);
                continue;
            }
            entries.add(new AppUsageEntry(
                    AppUsageEntry.createId(deviceId, "android", "phone", packageName, date.toString()),
                    deviceId,
                    "android",
                    "phone",
                    packageName,
                    label,
                    "",
                    date.toString(),
                    seconds,
                    updatedAt));
        }
        List<AppUsageEntry> dedupedEntries = capToPhoneUsageSeconds(dedupeSameNamedApps(entries), store, date);
        int duplicateNames = entries.size() - dedupedEntries.size();
        Log.i(DIAG_TAG, "AndroidAppUsageCollector date=" + date
                + " launchable=" + installedApps.size()
                + " eventPackages=" + eventSecondsByPackage.size()
                + " statsPackages=" + statsSecondsByPackage.size()
                + " mergedPackages=" + secondsByPackage.size()
                + " entries=" + dedupedEntries.size()
                + " duplicateNames=" + duplicateNames
                + " skippedShort=" + skippedShort
                + " skippedSystem=" + skippedSystem
                + " skippedNotLaunchable=" + skippedNotLaunchable
                + " skippedNoLabel=" + skippedNoLabel
                + " samples=" + samples);
        return dedupedEntries;
    }

    private Map<String, String> readInstalledLaunchableApps(PackageManager packageManager) {
        Map<String, String> apps = new HashMap<>();
        if (packageManager == null) {
            return apps;
        }
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchable = packageManager.queryIntentActivities(launcherIntent, 0);
        if (launchable == null) {
            return apps;
        }
        for (ResolveInfo resolveInfo : launchable) {
            if (resolveInfo == null || resolveInfo.activityInfo == null) {
                continue;
            }
            String packageName = resolveInfo.activityInfo.packageName;
            if (shouldSkipPackage(packageManager, packageName)) {
                continue;
            }
            CharSequence label = resolveInfo.loadLabel(packageManager);
            String appName = label == null ? "" : label.toString().trim();
            if (!appName.isEmpty() && !appName.equals(packageName)) {
                apps.put(packageName, appName);
            }
        }
        return apps;
    }

    private Map<String, Long> readForegroundSeconds(UsageStatsManager manager, long startMillis, long endMillis) {
        Map<String, Long> secondsByPackage = new HashMap<>();
        UsageEvents events;
        try {
            events = manager.queryEvents(startMillis, endMillis);
        } catch (Exception ex) {
            return secondsByPackage;
        }
        if (events == null) {
            return secondsByPackage;
        }

        // 按 (包名, 类名) 跟踪处于 resumed 状态的 activity；公开 API 拿不到 instanceId，
        // 同类名多实例会合并计算，误差可接受。包内 resumed activity 计数 0->1 开始计时，1->0 结束。
        // 不能用单个"活跃包"模型：同包导航时旧 activity 的 STOPPED 晚于新 activity 的 RESUMED 到达，
        // 单活跃模型会把它误判为"应用退到后台"，丢掉之后整段使用时间。
        Map<String, Long> openActivityKeys = new HashMap<>();
        Map<String, Integer> resumedCountByPackage = new HashMap<>();
        Map<String, Long> sessionStartByPackage = new HashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String packageName = event.getPackageName();
            if (packageName == null || packageName.trim().isEmpty()) {
                continue;
            }
            long timestamp = Math.max(startMillis, Math.min(endMillis, event.getTimeStamp()));
            int type = event.getEventType();
            String className = event.getClassName();
            String activityKey = packageName + "" + (className == null ? "" : className);
            if (isForegroundEvent(type)) {
                if (openActivityKeys.put(activityKey, timestamp) == null) {
                    int resumedCount = resumedCountByPackage.getOrDefault(packageName, 0);
                    if (resumedCount == 0) {
                        sessionStartByPackage.put(packageName, timestamp);
                    }
                    resumedCountByPackage.put(packageName, resumedCount + 1);
                }
            } else if (isBackgroundEvent(type) && openActivityKeys.remove(activityKey) != null) {
                int resumedCount = resumedCountByPackage.getOrDefault(packageName, 1) - 1;
                resumedCountByPackage.put(packageName, resumedCount);
                if (resumedCount == 0) {
                    Long sessionStart = sessionStartByPackage.remove(packageName);
                    addSeconds(secondsByPackage, packageName,
                            sessionStart == null ? timestamp : sessionStart, timestamp);
                }
            }
        }
        for (Map.Entry<String, Long> session : sessionStartByPackage.entrySet()) {
            addSeconds(secondsByPackage, session.getKey(), session.getValue(), endMillis);
        }
        return secondsByPackage;
    }

    private String resolveAppLabel(PackageManager packageManager, Map<String, String> launchableApps, String packageName) {
        if (packageName == null || packageName.trim().isEmpty() || packageManager == null) {
            return "";
        }
        String cached = launchableApps == null ? "" : launchableApps.get(packageName);
        if (cached != null && !cached.trim().isEmpty()) {
            return cached.trim();
        }
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(info);
            String appName = label == null ? "" : label.toString().trim();
            if (appName.isEmpty() || appName.equals(packageName)) {
                return "";
            }
            return appName;
        } catch (Exception ex) {
            return "";
        }
    }

    private Map<String, Long> readAggregatedUsageSeconds(UsageStatsManager manager, long startMillis, long endMillis) {
        Map<String, Long> secondsByPackage = new HashMap<>();
        Map<String, UsageStats> statsByPackage;
        try {
            statsByPackage = manager.queryAndAggregateUsageStats(startMillis, endMillis);
        } catch (Exception ex) {
            return secondsByPackage;
        }
        if (statsByPackage == null || statsByPackage.isEmpty()) {
            return secondsByPackage;
        }
        for (Map.Entry<String, UsageStats> value : statsByPackage.entrySet()) {
            String packageName = value.getKey();
            UsageStats stats = value.getValue();
            if (packageName == null || packageName.trim().isEmpty() || stats == null) {
                continue;
            }
            long millis = Math.max(0L, stats.getTotalTimeInForeground());
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                millis = Math.max(millis, stats.getTotalTimeVisible());
            }
            long seconds = millis / 1000L;
            if (seconds > 0L) {
                secondsByPackage.put(packageName, seconds);
            }
        }
        return secondsByPackage;
    }

    private static void addSample(List<String> samples, String reason, String packageName, long seconds) {
        if (samples == null || samples.size() >= 6) {
            return;
        }
        samples.add(reason + ":" + packageName + "=" + seconds + "s");
    }

    private static List<AppUsageEntry> dedupeSameNamedApps(List<AppUsageEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, AppUsageEntry> bestByName = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (AppUsageEntry entry : entries) {
            if (entry == null || entry.durationSeconds <= 0L) {
                continue;
            }
            String key = appDisplayKey(entry);
            AppUsageEntry existing = bestByName.get(key);
            if (existing == null) {
                bestByName.put(key, entry);
                order.add(key);
            } else if (entry.durationSeconds > existing.durationSeconds
                    || (entry.durationSeconds == existing.durationSeconds
                    && entry.updatedAtUnixSeconds > existing.updatedAtUnixSeconds)) {
                bestByName.put(key, entry);
            }
        }
        List<AppUsageEntry> values = new ArrayList<>();
        for (String key : order) {
            AppUsageEntry entry = bestByName.get(key);
            if (entry != null) {
                values.add(entry);
            }
        }
        return values;
    }

    private static List<AppUsageEntry> capToPhoneUsageSeconds(List<AppUsageEntry> entries, EyeTimeStore store, LocalDate date) {
        if (entries == null || entries.isEmpty() || store == null || date == null) {
            return entries == null ? new ArrayList<>() : entries;
        }
        long totalUsageSeconds;
        try {
            totalUsageSeconds = date.equals(LocalDate.now())
                    ? store.displayHomeStats(date).todaySeconds
                    : store.getDay(date).totalSeconds;
        } catch (Exception ex) {
            return entries;
        }
        if (totalUsageSeconds < MIN_APP_USAGE_SECONDS) {
            return entries;
        }
        long total = 0L;
        for (AppUsageEntry entry : entries) {
            if (entry != null) {
                total += Math.max(0L, entry.durationSeconds);
            }
        }
        if (total <= totalUsageSeconds || total <= 0L) {
            return entries;
        }

        List<AppUsageEntry> capped = new ArrayList<>();
        long cappedTotal = 0L;
        for (AppUsageEntry entry : entries) {
            if (entry == null || entry.durationSeconds <= 0L) {
                continue;
            }
            long scaledSeconds = Math.max(1L, (entry.durationSeconds * totalUsageSeconds) / total);
            if (scaledSeconds < MIN_APP_USAGE_SECONDS) {
                continue;
            }
            cappedTotal += scaledSeconds;
            capped.add(new AppUsageEntry(
                    entry.entryId,
                    entry.deviceId,
                    entry.platform,
                    entry.source,
                    entry.appId,
                    entry.appName,
                    "",
                    entry.localDate,
                    scaledSeconds,
                    entry.updatedAtUnixSeconds));
        }
        Log.i(DIAG_TAG, "AndroidAppUsageCollector capped app total raw=" + total
                + " visibleTotal=" + totalUsageSeconds
                + " capped=" + cappedTotal
                + " entries=" + capped.size());
        return capped;
    }

    private static String appDisplayKey(AppUsageEntry entry) {
        String appName = entry.appName == null || entry.appName.trim().isEmpty()
                ? entry.appId
                : entry.appName;
        return safeKey(entry.source) + ":" + safeKey(appName);
    }

    private static String safeKey(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return safe.isEmpty() ? "_" : safe;
    }

    private static boolean isForegroundEvent(int type) {
        return type == UsageEvents.Event.MOVE_TO_FOREGROUND
                || (android.os.Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED);
    }

    private static boolean isBackgroundEvent(int type) {
        return type == UsageEvents.Event.MOVE_TO_BACKGROUND
                || (android.os.Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_PAUSED)
                || (android.os.Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_STOPPED);
    }

    private static void addSeconds(Map<String, Long> secondsByPackage, String packageName, long startMillis, long endMillis) {
        long seconds = Math.max(0L, (endMillis - startMillis) / 1000L);
        if (seconds <= 0L) {
            return;
        }
        secondsByPackage.put(packageName, secondsByPackage.getOrDefault(packageName, 0L) + seconds);
    }

    private boolean shouldSkipPackage(PackageManager packageManager, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return true;
        }
        String normalized = packageName.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(context.getPackageName())
                || normalized.equals("android")
                || normalized.equals("com.android.systemui")
                || normalized.equals("com.miui.home")
                || normalized.equals("com.miui.securitycenter")
                || normalized.equals("com.xiaomi.mirror")
                || normalized.contains("launcher")) {
            return true;
        }
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return !info.enabled;
        } catch (Exception ex) {
            return true;
        }
    }
}
