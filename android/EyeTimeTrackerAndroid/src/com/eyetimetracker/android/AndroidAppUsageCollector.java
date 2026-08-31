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
import java.util.Set;
import java.util.HashSet;

public final class AndroidAppUsageCollector {
    private static final String DIAG_TAG = "EyeTimeDiag";
    private static final long MIN_APP_USAGE_SECONDS = 30L;
    private final Context context;

    public AndroidAppUsageCollector(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    public static final class CollectResult {
        public final List<AppUsageEntry> entries;
        // 窗口结束时仍在前台的包（start 记为窗口末尾），下一窗口以此为种子继续
        public final Map<String, Long> openSessions;

        CollectResult(List<AppUsageEntry> entries, Map<String, Long> openSessions) {
            this.entries = entries;
            this.openSessions = openSessions;
        }
    }

    public CollectResult collectDailyUsage(EyeTimeStore store, LocalDate date, long nowMillis) {
        List<AppUsageEntry> entries = new ArrayList<>();
        Map<String, Long> openSessions = new HashMap<>();
        CollectResult empty = new CollectResult(entries, openSessions);
        if (context == null || store == null || date == null) {
            return empty;
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return empty;
        }

        long dayStartMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = Math.min(
                date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                Math.max(dayStartMillis, nowMillis));
        if (endMillis <= dayStartMillis) {
            return empty;
        }

        // MIUI 只向应用返回最近约 2 小时的事件，全天重算会丢掉更早的时长。
        // 改为增量累加：每次只计算 [上次游标, 现在] 的窗口，时长累加到当日条目上。
        long cursorMillis = store.getAppUsageCursorMillis(date);
        long startMillis = Math.max(dayStartMillis, cursorMillis);
        Map<String, Long> seededSessions = cursorMillis > dayStartMillis
                ? store.getAppUsageOpenSessions(date)
                : new HashMap<>();

        PackageManager packageManager = context.getPackageManager();
        Map<String, String> installedApps = readInstalledLaunchableApps(packageManager);
        ForegroundWindow window = readForegroundSeconds(manager, startMillis, endMillis, seededSessions);
        openSessions.putAll(window.openSessions);
        Map<String, Long> secondsByPackage = window.secondsByPackage;
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
            if (seconds <= 0L) {
                skippedShort++;
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
        Log.i(DIAG_TAG, "AndroidAppUsageCollector date=" + date
                + " windowSec=" + ((endMillis - startMillis) / 1000L)
                + " launchable=" + installedApps.size()
                + " eventPackages=" + secondsByPackage.size()
                + " entries=" + entries.size()
                + " skippedSystem=" + skippedSystem
                + " skippedNotLaunchable=" + skippedNotLaunchable
                + " skippedNoLabel=" + skippedNoLabel
                + " openSessions=" + openSessions.size()
                + " samples=" + samples);
        return new CollectResult(entries, openSessions);
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

    private static final class EventRecord {
        final int eventType;
        final long timestampMillis;
        final String packageName;
        final String className;

        EventRecord(int eventType, long timestampMillis, String packageName, String className) {
            this.eventType = eventType;
            this.timestampMillis = timestampMillis;
            this.packageName = packageName;
            this.className = className;
        }
    }

    private static final class ForegroundWindow {
        final Map<String, Long> secondsByPackage;
        final Map<String, Long> openSessions;

        ForegroundWindow(Map<String, Long> secondsByPackage, Map<String, Long> openSessions) {
            this.secondsByPackage = secondsByPackage;
            this.openSessions = openSessions;
        }
    }

    private ForegroundWindow readForegroundSeconds(
            UsageStatsManager manager,
            long startMillis,
            long endMillis,
            Map<String, Long> seededSessions) {
        Map<String, Long> secondsByPackage = new HashMap<>();
        Map<String, Long> openSessions = new HashMap<>();
        UsageEvents events;
        try {
            events = manager.queryEvents(startMillis, endMillis);
        } catch (Exception ex) {
            return new ForegroundWindow(secondsByPackage, openSessions);
        }
        if (events == null) {
            return new ForegroundWindow(secondsByPackage, openSessions);
        }

        // 按 (包名, 类名) 跟踪处于 resumed 状态的 activity；公开 API 拿不到 instanceId，
        // 同类名多实例会合并计算，误差可接受。包内 resumed activity 计数 0->1 开始计时，1->0 结束。
        // 两个兜底缺一不可：
        // 1) 不能用单个"活跃包"模型：同包导航时旧 activity 的 STOPPED 晚于新 activity 的 RESUMED
        //    到达，单活跃模型会误判"应用退到后台"，丢掉之后整段使用时间；
        // 2) 必须有"新包前台则其它包截止"：MIUI 会丢 PAUSED/STOPPED 事件，纯计数器模型
        //    会把丢失关闭事件的 App 时长一直挂到当前时刻，造成虚高。
        // MIUI 返回的事件不保证时间顺序（按任务分组），必须先按时间戳排序再处理。
        List<EventRecord> sortedEvents = new ArrayList<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            sortedEvents.add(new EventRecord(
                    event.getEventType(),
                    event.getTimeStamp(),
                    event.getPackageName(),
                    event.getClassName()));
        }
        sortedEvents.sort((left, right) -> Long.compare(left.timestampMillis, right.timestampMillis));

        Map<String, Long> openActivityKeys = new HashMap<>();
        Map<String, Integer> resumedCountByPackage = new HashMap<>();
        Map<String, Long> sessionStartByPackage = new HashMap<>();
        Set<String> seededOpenPackages = new HashSet<>();
        if (seededSessions != null) {
            for (Map.Entry<String, Long> seed : seededSessions.entrySet()) {
                // 上一窗口遗留的前台会话：从窗口起点继续计时，遇到该包任意后台事件时结束
                sessionStartByPackage.put(seed.getKey(), startMillis);
                resumedCountByPackage.put(seed.getKey(), 1);
                seededOpenPackages.add(seed.getKey());
            }
        }

        for (EventRecord item : sortedEvents) {
            String packageName = item.packageName;
            if (packageName == null || packageName.trim().isEmpty()) {
                continue;
            }
            long timestamp = Math.max(startMillis, Math.min(endMillis, item.timestampMillis));
            int type = item.eventType;
            if (isBackgroundEvent(type) && seededOpenPackages.contains(packageName)) {
                Long seedStart = sessionStartByPackage.remove(packageName);
                addSeconds(secondsByPackage, packageName,
                        seedStart == null ? startMillis : seedStart, timestamp);
                resumedCountByPackage.put(packageName, 0);
                seededOpenPackages.remove(packageName);
                continue;
            }
            if (isForegroundEvent(type) && seededOpenPackages.contains(packageName)) {
                // 遗留会话仍在继续，忽略该包的前台事件直到它出现后台事件
                continue;
            }
            String className = item.className;
            String activityKey = packageName + "" + (className == null ? "" : className);
            if (isForegroundEvent(type)) {
                if (openActivityKeys.put(activityKey, timestamp) == null) {
                    int resumedCount = resumedCountByPackage.getOrDefault(packageName, 0);
                    if (resumedCount == 0) {
                        sessionStartByPackage.put(packageName, timestamp);
                    }
                    resumedCountByPackage.put(packageName, resumedCount + 1);
                }
                closeOtherPackageSessions(
                        secondsByPackage,
                        openActivityKeys,
                        resumedCountByPackage,
                        sessionStartByPackage,
                        packageName,
                        timestamp);
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
            // 会话起点记为窗口末尾，下一窗口从该点继续，避免重复累计
            openSessions.put(session.getKey(), endMillis);
        }
        return new ForegroundWindow(secondsByPackage, openSessions);
    }

    private static void closeOtherPackageSessions(
            Map<String, Long> secondsByPackage,
            Map<String, Long> openActivityKeys,
            Map<String, Integer> resumedCountByPackage,
            Map<String, Long> sessionStartByPackage,
            String foregroundPackage,
            long timestamp) {
        List<String> closingPackages = new ArrayList<>();
        for (Map.Entry<String, Long> session : sessionStartByPackage.entrySet()) {
            if (!session.getKey().equals(foregroundPackage)) {
                closingPackages.add(session.getKey());
            }
        }
        for (String packageName : closingPackages) {
            Long sessionStart = sessionStartByPackage.remove(packageName);
            addSeconds(secondsByPackage, packageName,
                    sessionStart == null ? timestamp : sessionStart, timestamp);
            resumedCountByPackage.put(packageName, 0);
            List<String> keysToRemove = new ArrayList<>();
            for (String key : openActivityKeys.keySet()) {
                if (key.startsWith(packageName + "")) {
                    keysToRemove.add(key);
                }
            }
            for (String key : keysToRemove) {
                openActivityKeys.remove(key);
            }
        }
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

    private static void addSample(List<String> samples, String reason, String packageName, long seconds) {
        if (samples == null || samples.size() >= 6) {
            return;
        }
        samples.add(reason + ":" + packageName + "=" + seconds + "s");
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
