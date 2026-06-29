package com.eyetimetracker.android;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.DayOfWeek;
import java.time.LocalDate;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EyeTimeStore store;
    private TextView todayValue;
    private TextView yesterdayValue;
    private TextView weekValue;
    private TextView monthValue;
    private TextView statusValue;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store = new EyeTimeStore(this);
        requestNotificationPermission();
        setContentView(buildUi());
        startTrackerService();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(EyeTimeService.ACTION_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        handler.post(refreshRunnable);
        refresh();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        unregisterReceiver(receiver);
        super.onPause();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(20));
        root.setBackgroundColor(Color.rgb(246, 252, 249));

        TextView title = new TextView(this);
        title.setText("用眼时间记录");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 47, 45));
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("亮屏 + 机身动作或媒体播放时计时");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(79, 110, 106));
        root.addView(subtitle, matchWrap());

        root.addView(row("今天", todayValue = valueText()), matchWrap());
        root.addView(row("昨天", yesterdayValue = valueText()), matchWrap());
        root.addView(row("本周", weekValue = valueText()), matchWrap());
        root.addView(row("本月", monthValue = valueText()), matchWrap());

        statusValue = new TextView(this);
        statusValue.setTextSize(18);
        statusValue.setTextColor(Color.rgb(39, 158, 173));
        statusValue.setPadding(0, dp(18), 0, dp(10));
        root.addView(statusValue, matchWrap());

        Button startButton = new Button(this);
        startButton.setText("启动后台统计");
        startButton.setOnClickListener(v -> startTrackerService());
        root.addView(startButton, matchWrap());

        TextView device = new TextView(this);
        device.setText("设备ID: " + store.getDeviceId());
        device.setTextSize(11);
        device.setTextColor(Color.rgb(100, 120, 118));
        device.setPadding(0, dp(16), 0, 0);
        root.addView(device, matchWrap());
        return root;
    }

    private LinearLayout row(String label, TextView value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(18), 0, 0);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(18);
        labelView.setTextColor(Color.rgb(26, 55, 52));
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        return row;
    }

    private TextView valueText() {
        TextView text = new TextView(this);
        text.setTextSize(20);
        text.setTextColor(Color.rgb(0, 0, 0));
        text.setGravity(Gravity.START);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return text;
    }

    private void refresh() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        LocalDate monthStart = today.withDayOfMonth(1);
        todayValue.setText(DurationFormatter.format(store.getDay(today).totalSeconds));
        yesterdayValue.setText(DurationFormatter.format(store.getDay(yesterday).totalSeconds));
        weekValue.setText(DurationFormatter.format(store.sumRange(weekStart, today)));
        monthValue.setText(DurationFormatter.format(store.sumRange(monthStart, today)));
        statusValue.setText("状态：后台统计运行中");
    }

    private void startTrackerService() {
        Intent intent = new Intent(this, EyeTimeService.class);
        intent.setAction(EyeTimeService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 100);
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}