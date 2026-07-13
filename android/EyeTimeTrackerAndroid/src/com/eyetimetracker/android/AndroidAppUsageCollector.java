package com.eyetimetracker.android;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AndroidAppUsageCollector {
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
        Map<String, Long> secondsByPackage = readForegroundSeconds(manager, startMillis, endMillis);
        String deviceId = store.getDeviceId();
        long updatedAt = nowMillis / 1000L;
        for (Map.Entry<String, Long> value : secondsByPackage.entrySet()) {
            String packageName = value.getKey();
            long seconds = value.getValue() == null ? 0L : value.getValue();
            String label = installedApps.get(packageName);
            if (seconds < MIN_APP_USAGE_SECONDS || label == null || label.trim().isEmpty()) {
                continue;
            }
            entries.add(new AppUsageEntry(
                    AppUsageEntry.createId(deviceId, "android", "phone", packageName, date.toString()),
                    deviceId,
                    "android",
                    "phone",
                    packageName,
                    label,
                    date.toString(),
                    seconds,
                    updatedAt));
        }
        return entries;
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

        String activePackage = "";
        long activeStartedAt = 0L;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String packageName = event.getPackageName();
            if (packageName == null || packageName.trim().isEmpty()) {
                continue;
            }
            long timestamp = Math.max(startMillis, Math.min(endMillis, event.getTimeStamp()));
            int type = event.getEventType();
            if (isForegroundEvent(type)) {
                if (!activePackage.isEmpty()) {
                    addSeconds(secondsByPackage, activePackage, activeStartedAt, timestamp);
                }
                activePackage = packageName;
                activeStartedAt = timestamp;
            } else if (isBackgroundEvent(type) && packageName.equals(activePackage)) {
                addSeconds(secondsByPackage, activePackage, activeStartedAt, timestamp);
                activePackage = "";
                activeStartedAt = 0L;
            }
        }
        if (!activePackage.isEmpty()) {
            addSeconds(secondsByPackage, activePackage, activeStartedAt, endMillis);
        }
        return secondsByPackage;
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
