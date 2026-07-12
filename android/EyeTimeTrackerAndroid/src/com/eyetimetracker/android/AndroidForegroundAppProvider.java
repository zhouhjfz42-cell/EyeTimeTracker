package com.eyetimetracker.android;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public final class AndroidForegroundAppProvider {
    private final Context context;

    public AndroidForegroundAppProvider(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    public AppSnapshot getCurrent(long nowMillis) {
        if (context == null) {
            return null;
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return null;
        }

        UsageEvents events;
        try {
            events = manager.queryEvents(Math.max(0L, nowMillis - 120_000L), nowMillis);
        } catch (Exception ex) {
            return null;
        }
        if (events == null) {
            return null;
        }

        String packageName = "";
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || (android.os.Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED)) {
                packageName = event.getPackageName();
            }
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            return null;
        }
        return new AppSnapshot(packageName, appLabel(packageName));
    }

    private String appLabel(String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(info);
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Exception ignored) {
        }
        return packageName;
    }

    public static final class AppSnapshot {
        public final String appId;
        public final String appName;

        AppSnapshot(String appId, String appName) {
            this.appId = appId == null ? "" : appId;
            this.appName = appName == null ? "" : appName;
        }
    }
}
