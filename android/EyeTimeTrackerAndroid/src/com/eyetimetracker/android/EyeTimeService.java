package com.eyetimetracker.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import java.time.LocalDate;

public final class EyeTimeService extends Service implements SensorEventListener {
    private static final String DIAG_TAG = "EyeTimeDiag";
    public static final String ACTION_STATE_CHANGED = "com.eyetimetracker.android.STATE_CHANGED";
    public static final String ACTION_REMINDER = "com.eyetimetracker.android.REMINDER";
    public static final String ACTION_START = "com.eyetimetracker.android.START";
    public static final String ACTION_STOP = "com.eyetimetracker.android.STOP";
    public static final String EXTRA_REMINDER_MINUTES = "reminder_minutes";
    public static final String EXTRA_REMINDER_REPEAT = "reminder_repeat";
    public static final String EXTRA_REMINDER_STEP = "reminder_step";

    private static final String CHANNEL_ID = "eye_time_tracker";
    private static final String REMINDER_CHANNEL_ID = ReminderNotificationProfile.CHANNEL_ID;
    private static final int FOREGROUND_ID = 1001;
    private static final int REMINDER_ID = 1002;
    private static final long TICK_MS = 10_000L;
    private static final long MOTION_THRESHOLD_MS = 180_000L;
    private static final long MAX_COUNTABLE_TICK_MS = 30_000L;
    private static final long FAMILY_BACKGROUND_UPLOAD_INTERVAL_MS = 60_000L;
    private static final long FAMILY_PENDING_UPLOAD_WINDOW_MS = 30 * 60_000L;
    private static final float MOTION_DELTA_THRESHOLD = 0.7f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private EyeTimeStore store;
    private AndroidSyncRunner syncRunner;
    private AndroidForegroundAppProvider foregroundAppProvider;
    private AndroidSyncTriggerPolicy syncPolicy;
    private FamilyStatsUploadRequestServer familyUploadRequestServer;
    private SensorManager sensorManager;
    private AudioManager audioManager;
    private PowerManager powerManager;
    private long lastMotionAt;
    private long lastTickAt;
    private LocalDate lastStatsCacheWarmDate;
    private boolean hasLastSensor;
    private float lastX;
    private float lastY;
    private float lastZ;
    private boolean counting;
    private long currentSessionStartedUnixSeconds;
    private boolean syncInFlight;
    private boolean statsCacheWarmInFlight;
    private boolean familyStatsUploadInFlight;
    private boolean pendingFamilyStatsUpload;
    private long lastFamilyStatsUploadStartedAt = Long.MIN_VALUE;
    private long pendingFamilyStatsUploadUntilAt = Long.MIN_VALUE;
    private PowerManager.WakeLock familyStatsWakeLock;
    private WifiManager.WifiLock familyStatsWifiLock;

    @Override public void onCreate() {
        super.onCreate();
        store = new EyeTimeStore(this);
        syncRunner = new AndroidSyncRunner(store, this);
        foregroundAppProvider = new AndroidForegroundAppProvider(this);
        syncPolicy = new AndroidSyncTriggerPolicy();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        lastMotionAt = System.currentTimeMillis();
        lastTickAt = lastMotionAt;
        createChannel();
        registerSensor();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(FOREGROUND_ID, buildStatusNotification(getString(R.string.sync_service_running)));
        maybeWarmPastDailyStatsCache(LocalDate.now());
        ensureFamilyStatsUploadRequestServer();
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tickRunnable);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        stopFamilyStatsUploadRequestServer();
        releaseFamilyStatsUploadLocks();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        if (hasLastSensor) {
            float delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
            if (delta >= MOTION_DELTA_THRESHOLD) {
                lastMotionAt = System.currentTimeMillis();
            }
        } else {
            hasLastSensor = true;
            lastMotionAt = System.currentTimeMillis();
        }
        lastX = x;
        lastY = y;
        lastZ = z;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTickAt;
        lastTickAt = now;
        boolean screenOn = powerManager == null || powerManager.isInteractive();
        boolean mediaActive = audioManager != null && audioManager.isMusicActive();
        ActivityDecision decision = ActivityDecision.evaluate(
                screenOn,
                now - lastMotionAt,
                mediaActive,
                elapsed,
                MOTION_THRESHOLD_MS);
        boolean nextCounting = decision.isCounting();
        boolean wasCounting = counting;
        if (nextCounting && !counting) {
            currentSessionStartedUnixSeconds = now / 1000L;
        } else if (!nextCounting) {
            currentSessionStartedUnixSeconds = 0L;
        }
        counting = nextCounting;
        store.saveLocalReminderState(counting, currentSessionStartedUnixSeconds);
        boolean countedThisTick = false;
        if (counting && elapsed > 0L && elapsed <= MAX_COUNTABLE_TICK_MS) {
            long countedSeconds = elapsed / 1000L;
            store.addSeconds(LocalDate.now(), countedSeconds, now / 1000L, "android-screen");
            AndroidForegroundAppProvider.AppSnapshot foregroundApp = foregroundAppProvider == null ? null : foregroundAppProvider.getCurrent(now);
            if (foregroundApp != null) {
                store.addAppUsage(LocalDate.now(), countedSeconds, foregroundApp.appId, foregroundApp.appName);
            }
            syncPolicy.markLocalChange(now);
            markPendingFamilyStatsUpload(now);
            countedThisTick = true;
        } else if (!counting) {
            store.finishCurrentSession(LocalDate.now());
            if (wasCounting) {
                markPendingFamilyStatsUpload(now);
            }
        }
        LocalDate todayDate = LocalDate.now();
        maybeWarmPastDailyStatsCache(todayDate);
        DailySummary today = store.getDay(todayDate);
        int reminderMinutes = store.getReminderMinutes();
        boolean repeatReminder = store.isRepeatReminderEnabled();
        if (ReminderPolicy.shouldNotify(
                today.totalSeconds,
                reminderMinutes,
                repeatReminder,
                today.reminderShown,
                today.lastReminderStep)) {
            int reminderStep = ReminderPolicy.reachedStep(today.totalSeconds, reminderMinutes);
            store.markReminderShown(todayDate, reminderStep);
            SyncSettings syncSettings = store.getSyncSettings();
            boolean peerOnline = SyncConnectionState.isPeerOnline(syncSettings, now / 1000L);
            if (ReminderDevicePolicy.shouldShowOnLocalDevice(
                    store.getLocalReminderState(),
                    syncSettings.peerReminderState,
                    peerOnline)) {
                showReminder(reminderMinutes, repeatReminder, reminderStep);
            }
        }
        updateForegroundNotification(today.totalSeconds);
        sendBroadcast(new Intent(ACTION_STATE_CHANGED));
        boolean upcomingReminderSync = syncPolicy.shouldSyncForUpcomingReminder(
                now,
                today.totalSeconds,
                reminderMinutes,
                repeatReminder,
                today.reminderShown,
                today.lastReminderStep);
        maybeRunSync(now, countedThisTick, upcomingReminderSync);
        ensureFamilyStatsUploadRequestServer();
        maybeRunFamilyStatsUpload(now, countedThisTick, wasCounting && !counting);
    }

    private void maybeWarmPastDailyStatsCache(LocalDate today) {
        if (store == null || today == null || statsCacheWarmInFlight || today.equals(lastStatsCacheWarmDate)) {
            return;
        }
        statsCacheWarmInFlight = true;
        lastStatsCacheWarmDate = today;
        new Thread(() -> {
            try {
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                store.warmPastDailyStatsCache(today);
            } finally {
                statsCacheWarmInFlight = false;
            }
        }, "EyeTimeDailyStatsCache").start();
    }

    private void maybeRunSync(long now, boolean allowLocalChangeSync, boolean allowUpcomingReminderSync) {
        SyncSettings settings = store.getSyncSettings();
        if (!settings.isPaired || syncInFlight) {
            return;
        }
        if (!syncPolicy.shouldSyncForServiceTick(now)
                && (!allowLocalChangeSync || !syncPolicy.shouldSyncForLocalChange(now))
                && !allowUpcomingReminderSync) {
            return;
        }

        syncPolicy.markSyncAttempt(now);
        syncInFlight = true;
        new Thread(() -> {
            try {
                syncRunner.syncOnce();
                store.warmPastDailyStatsCache(LocalDate.now());
            } finally {
                syncInFlight = false;
            }
        }, "EyeTimeSync").start();
    }

    private void ensureFamilyStatsUploadRequestServer() {
        if (!isChildFamilyMode()) {
            stopFamilyStatsUploadRequestServer();
            return;
        }
        if (familyUploadRequestServer != null) {
            return;
        }
        familyUploadRequestServer = new FamilyStatsUploadRequestServer(store);
        familyUploadRequestServer.start(new FamilyStatsUploadRequestServer.Listener() {
            @Override public void onStarted() {
                Log.i(DIAG_TAG, "FamilyStatsUploadRequestServer started");
            }

            @Override public void onUploadRequested() {
                Log.i(DIAG_TAG, "FamilyStatsUploadRequestServer upload requested");
                maybeRunFamilyStatsUpload(System.currentTimeMillis(), false, true);
            }

            @Override public void onError(String message) {
                Log.i(DIAG_TAG, "FamilyStatsUploadRequestServer error=" + message);
            }
        });
    }

    private void stopFamilyStatsUploadRequestServer() {
        if (familyUploadRequestServer != null) {
            familyUploadRequestServer.stop();
            familyUploadRequestServer = null;
        }
    }

    private boolean isChildFamilyMode() {
        return store != null
                && store.getProductMode() == ProductMode.FAMILY
                && store.getDeviceRole() == DeviceRole.CHILD_DEVICE;
    }

    private void maybeRunFamilyStatsUpload(long now, boolean countedThisTick, boolean force) {
        if (!isChildFamilyMode()) {
            clearPendingFamilyStatsUpload();
            return;
        }
        if (familyStatsUploadInFlight) {
            return;
        }
        if (!force) {
            if (!pendingFamilyStatsUpload) {
                return;
            }
            if (pendingFamilyStatsUploadUntilAt != Long.MIN_VALUE && now > pendingFamilyStatsUploadUntilAt) {
                clearPendingFamilyStatsUpload();
                return;
            }
            if (lastFamilyStatsUploadStartedAt != Long.MIN_VALUE
                    && now - lastFamilyStatsUploadStartedAt < FAMILY_BACKGROUND_UPLOAD_INTERVAL_MS) {
                return;
            }
        }
        familyStatsUploadInFlight = true;
        lastFamilyStatsUploadStartedAt = now;
        boolean useFullDiscovery = force || !counting || !countedThisTick;
        new Thread(() -> {
            try {
                FamilyStatsLanClient client = force
                        ? new FamilyStatsLanClient()
                        : new FamilyStatsLanClient(1200, 1800, 12000, useFullDiscovery);
                FamilyStatsLanClient.UploadResult result = client.upload(store);
                if (result.success) {
                    clearPendingFamilyStatsUpload();
                }
                Log.i(DIAG_TAG, "FamilyStats background upload success=" + result.success
                        + " skipped=" + result.skipped
                        + " changed=" + result.changedSegments
                        + " force=" + force
                        + " fullDiscovery=" + useFullDiscovery
                        + " error=" + result.error);
            } finally {
                familyStatsUploadInFlight = false;
            }
        }, force ? "FamilyStatsUploadNow" : "FamilyStatsBackgroundUpload").start();
    }

    private void markPendingFamilyStatsUpload(long now) {
        if (!isChildFamilyMode()) {
            return;
        }
        pendingFamilyStatsUpload = true;
        pendingFamilyStatsUploadUntilAt = now + FAMILY_PENDING_UPLOAD_WINDOW_MS;
        acquireFamilyStatsUploadLocks();
    }

    private void clearPendingFamilyStatsUpload() {
        pendingFamilyStatsUpload = false;
        pendingFamilyStatsUploadUntilAt = Long.MIN_VALUE;
        releaseFamilyStatsUploadLocks();
    }

    private void acquireFamilyStatsUploadLocks() {
        try {
            if (powerManager != null) {
                if (familyStatsWakeLock == null) {
                    familyStatsWakeLock = powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "EyeTimeTracker:FamilyStatsUpload");
                    familyStatsWakeLock.setReferenceCounted(false);
                }
                if (familyStatsWakeLock.isHeld()) {
                    familyStatsWakeLock.release();
                }
                familyStatsWakeLock.acquire(FAMILY_PENDING_UPLOAD_WINDOW_MS);
            }
        } catch (Exception ex) {
            Log.i(DIAG_TAG, "FamilyStats wake lock failed=" + messageOf(ex));
        }
        try {
            if (familyStatsWifiLock == null) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    familyStatsWifiLock = wifiManager.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                            "EyeTimeTracker:FamilyStatsUpload");
                    familyStatsWifiLock.setReferenceCounted(false);
                }
            }
            if (familyStatsWifiLock != null && !familyStatsWifiLock.isHeld()) {
                familyStatsWifiLock.acquire();
            }
        } catch (Exception ex) {
            Log.i(DIAG_TAG, "FamilyStats wifi lock failed=" + messageOf(ex));
        }
    }

    private void releaseFamilyStatsUploadLocks() {
        try {
            if (familyStatsWifiLock != null && familyStatsWifiLock.isHeld()) {
                familyStatsWifiLock.release();
            }
        } catch (Exception ignored) {
        }
        try {
            if (familyStatsWakeLock != null && familyStatsWakeLock.isHeld()) {
                familyStatsWakeLock.release();
            }
        } catch (Exception ignored) {
        }
    }

    private static String messageOf(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private void registerSensor() {
        if (sensorManager == null) {
            return;
        }
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.sync_service_channel_description));
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            for (String legacyChannelId : ReminderNotificationProfile.LEGACY_CHANNEL_IDS) {
                manager.deleteNotificationChannel(legacyChannelId);
            }
            manager.createNotificationChannel(channel);
            NotificationChannel reminderChannel = new NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    getString(R.string.reminder_alert_title),
                    ReminderNotificationProfile.CHANNEL_IMPORTANCE);
            reminderChannel.setDescription(getString(R.string.reminder_channel_description));
            reminderChannel.enableVibration(true);
            reminderChannel.setVibrationPattern(ReminderNotificationProfile.VIBRATION_PATTERN);
            if (ReminderNotificationProfile.ENABLE_SOUND) {
                reminderChannel.setSound(defaultReminderSound(), reminderAudioAttributes());
            }
            reminderChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(reminderChannel);
        }
    }

    private Notification buildStatusNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateForegroundNotification(long todaySeconds) {
        String status = counting ? getString(R.string.sync_status_counting) : getString(R.string.main_status_paused);
        String text = getString(R.string.sync_notification_status)
                .replace("{status}", status)
                .replace("{duration}", DurationFormatter.format(this, todaySeconds));
        Notification notification = buildStatusNotification(text);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(FOREGROUND_ID, notification);
        }
    }

    private void showReminder(int reminderMinutes, boolean repeatReminder, int reminderStep) {
        sendBroadcast(new Intent(ACTION_REMINDER)
                .putExtra(EXTRA_REMINDER_MINUTES, reminderMinutes)
                .putExtra(EXTRA_REMINDER_REPEAT, repeatReminder)
                .putExtra(EXTRA_REMINDER_STEP, reminderStep));
        if (store.isMainActivityVisible()) {
            return;
        }

        Intent alertIntent = new Intent(this, ReminderActivity.class)
                .putExtra(ReminderActivity.EXTRA_REMINDER_MINUTES, reminderMinutes)
                .putExtra(ReminderActivity.EXTRA_REMINDER_REPEAT, repeatReminder)
                .putExtra(ReminderActivity.EXTRA_REMINDER_STEP, reminderStep)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent alertPendingIntent = PendingIntent.getActivity(
                this,
                REMINDER_ID,
                alertIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, REMINDER_CHANNEL_ID)
                : new Notification.Builder(this);
        String message = ReminderAlert.message(this, reminderMinutes, repeatReminder, reminderStep);
        Notification notification = builder
                .setContentTitle(ReminderAlert.title(this))
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(alertPendingIntent)
                .setAutoCancel(true)
                .setPriority(ReminderNotificationProfile.NOTIFICATION_PRIORITY)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setFullScreenIntent(alertPendingIntent, ReminderNotificationProfile.USE_FULL_SCREEN_INTENT)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(REMINDER_ID, notification);
        }
    }

    private Uri defaultReminderSound() {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    private AudioAttributes reminderAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }
}
