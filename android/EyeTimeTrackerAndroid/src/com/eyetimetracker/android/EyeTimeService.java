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
    public static final String ACTION_REMINDER = "com.eyetimetracker.android.REMINDER";
    public static final String ACTION_START = "com.eyetimetracker.android.START";
    public static final String ACTION_STOP = "com.eyetimetracker.android.STOP";
    public static final String EXTRA_REMINDER_MINUTES = "reminder_minutes";
    public static final String EXTRA_REMINDER_REPEAT = "reminder_repeat";
    public static final String EXTRA_REMINDER_STEP = "reminder_step";

    private static final String CHANNEL_ID = "eye_time_tracker";
    private static final String REMINDER_CHANNEL_ID = "eye_time_tracker_reminders";
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
            showReminder(reminderMinutes, repeatReminder, reminderStep);
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "\u7528\u773c\u65f6\u95f4\u8bb0\u5f55", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("\u540e\u53f0\u7edf\u8ba1\u670d\u52a1\u72b6\u6001");
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
            NotificationChannel reminderChannel = new NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "\u7528\u773c\u63d0\u9192",
                    NotificationManager.IMPORTANCE_HIGH);
            reminderChannel.setDescription("\u5230\u8fbe\u8bbe\u5b9a\u7528\u773c\u65f6\u957f\u65f6\u63d0\u9192\u4f11\u606f");
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
                .setContentTitle("\u7528\u773c\u65f6\u95f4\u8bb0\u5f55")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateForegroundNotification(long todaySeconds) {
        String status = counting ? "\u8ba1\u65f6\u4e2d" : "\u6682\u505c";
        Notification notification = buildStatusNotification(status + " \u00b7 \u4eca\u5929 " + DurationFormatter.format(todaySeconds));
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
        String message = ReminderAlert.message(reminderMinutes, repeatReminder, reminderStep);
        Notification notification = builder
                .setContentTitle(ReminderAlert.title())
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(alertPendingIntent)
                .setFullScreenIntent(alertPendingIntent, true)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_ALARM)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(REMINDER_ID, notification);
        }
    }
}
