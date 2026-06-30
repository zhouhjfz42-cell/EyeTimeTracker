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
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.time.LocalDate;

public final class EyeTimeService extends Service implements SensorEventListener {
    public static final String ACTION_STATE_CHANGED = "com.eyetimetracker.android.STATE_CHANGED";
    public static final String ACTION_START = "com.eyetimetracker.android.START";
    public static final String ACTION_STOP = "com.eyetimetracker.android.STOP";

    private static final String CHANNEL_ID = "eye_time_tracker";
    private static final int FOREGROUND_ID = 1001;
    private static final int REMINDER_ID = 1002;
    private static final long TICK_MS = 10_000L;
    private static final long MOTION_THRESHOLD_MS = 180_000L;
    private static final long MAX_COUNTABLE_TICK_MS = 30_000L;
    private static final float MOTION_DELTA_THRESHOLD = 0.7f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private EyeTimeStore store;
    private SensorManager sensorManager;
    private AudioManager audioManager;
    private PowerManager powerManager;
    private long lastMotionAt;
    private long lastTickAt;
    private boolean hasLastSensor;
    private float lastX;
    private float lastY;
    private float lastZ;
    private boolean counting;

    @Override public void onCreate() {
        super.onCreate();
        store = new EyeTimeStore(this);
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
        startForeground(FOREGROUND_ID, buildStatusNotification("计时服务运行中"));
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tickRunnable);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
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
        ActivityDecision decision = ActivityDecision.evaluate(screenOn, now - lastMotionAt, mediaActive, elapsed, MOTION_THRESHOLD_MS);
        counting = decision.isCounting();
        if (counting && elapsed > 0L && elapsed <= MAX_COUNTABLE_TICK_MS) {
            store.addSeconds(LocalDate.now(), elapsed / 1000L);
        }
        LocalDate todayDate = LocalDate.now();
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
            showReminder(reminderMinutes);
        }
        updateForegroundNotification(today.totalSeconds);
        sendBroadcast(new Intent(ACTION_STATE_CHANGED));
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "用眼时间记录", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("统计亮屏、机身动作和媒体播放推定的用眼时间");
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildStatusNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("用眼时间记录")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateForegroundNotification(long todaySeconds) {
        String status = counting ? "计时中" : "暂停";
        Notification notification = buildStatusNotification(status + " · 今天 " + DurationFormatter.format(todaySeconds));
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(FOREGROUND_ID, notification);
        }
    }

    private void showReminder(int reminderMinutes) {
        Notification notification = buildStatusNotification("今天用眼时间已达到 " + ReminderThreshold.format(reminderMinutes) + "，建议休息。");
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(REMINDER_ID, notification);
        }
    }
}
