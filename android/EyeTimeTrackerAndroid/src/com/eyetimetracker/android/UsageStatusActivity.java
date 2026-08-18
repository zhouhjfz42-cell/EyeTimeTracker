package com.eyetimetracker.android;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class UsageStatusActivity extends Activity {
    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(102, 112, 133);
    private static final int COLOR_GREEN = Color.rgb(22, 166, 125);
    private static final int COLOR_SOFT = Color.rgb(237, 248, 244);
    private static final int COLOR_LINE = Color.rgb(223, 240, 233);
    private static final int REQUEST_ACTIVITY_RECOGNITION = 4101;

    private TextView summaryValue;
    private TextView trackingValue;
    private TextView usageValue;
    private TextView notificationValue;
    private TextView backgroundValue;
    private TextView activityValue;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ensureTrackerServiceRunning();
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        refreshValues();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ACTIVITY_RECOGNITION) {
            refreshValues();
        }
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(34), dp(28), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, matchWrap());

        TextView back = new TextView(this);
        back.setText("\u2039");
        back.setTextSize(44);
        back.setGravity(Gravity.CENTER);
        back.setTextColor(COLOR_GREEN);
        back.setIncludeFontPadding(false);
        back.setBackground(oval(COLOR_SOFT));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView title = new TextView(this);
        title.setText(R.string.usage_status_title);
        title.setTextSize(30);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(14);
        header.addView(title, titleParams);

        TextView lead = descriptionText(getString(R.string.usage_status_lead));
        root.addView(lead, matchWrapTop(26));

        LinearLayout summary = new LinearLayout(this);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(18), dp(16), dp(18), dp(16));
        summary.setMinimumHeight(dp(76));
        summary.setBackground(rounded(Color.WHITE, dp(20), COLOR_LINE, 1));
        TextView summaryTitle = new TextView(this);
        summaryTitle.setText(R.string.usage_status_title);
        summaryTitle.setTextSize(18);
        summaryTitle.setTextColor(COLOR_TEXT);
        summaryTitle.setTypeface(AppFonts.bold(this));
        summaryTitle.setIncludeFontPadding(false);
        summary.addView(summaryTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        summaryValue = statusValue();
        summary.addView(summaryValue, wrapWrap());
        root.addView(summary, matchWrapTop(24));

        root.addView(statusCard(
                getString(R.string.usage_status_tracking_title),
                getString(R.string.usage_status_tracking_desc),
                trackingValue = statusValue(),
                v -> ensureTrackerServiceRunning()), matchWrapTop(18));
        root.addView(statusCard(
                getString(R.string.usage_status_usage_title),
                getString(R.string.usage_status_usage_desc),
                usageValue = statusValue(),
                v -> openUsageAccessSettings()), matchWrapTop(14));
        root.addView(statusCard(
                getString(R.string.usage_status_notifications_title),
                getString(R.string.usage_status_notifications_desc),
                notificationValue = statusValue(),
                v -> openNotificationSettings()), matchWrapTop(14));
        root.addView(statusCard(
                getString(R.string.usage_status_background_title),
                getString(R.string.usage_status_background_desc),
                backgroundValue = statusValue(),
                v -> openBackgroundSettings()), matchWrapTop(14));
        root.addView(statusCard(
                getString(R.string.usage_status_activity_title),
                getString(R.string.usage_status_activity_desc),
                activityValue = statusValue(),
                v -> requestActivityRecognitionPermission()), matchWrapTop(14));

        TextView instructions = descriptionText(getString(R.string.usage_status_instructions));
        root.addView(instructions, matchWrapTop(24));
        return scroll;
    }

    private void refreshValues() {
        boolean trackingReady = EyeTimeService.isRunning();
        boolean usageReady = hasUsageAccess();
        boolean notificationsReady = hasNotificationPermission();
        boolean backgroundReady = isIgnoringBatteryOptimizations();
        boolean activityReady = hasActivityRecognitionPermission();
        int missing = (usageReady ? 0 : 1) + (notificationsReady ? 0 : 1) + (backgroundReady ? 0 : 1);

        if (summaryValue != null) {
            if (missing == 0) {
                summaryValue.setText(R.string.usage_status_ready);
            } else {
                summaryValue.setText(getString(R.string.usage_status_needs_attention, missing));
            }
            summaryValue.setTextColor(missing == 0 ? COLOR_GREEN : COLOR_MUTED);
        }
        setStatus(trackingValue, trackingReady, false);
        setStatus(usageValue, usageReady, false);
        setStatus(notificationValue, notificationsReady, false);
        setStatus(backgroundValue, backgroundReady, true);
        setStatus(activityValue, activityReady, false);
    }

    private void setStatus(TextView view, boolean ready, boolean recommended) {
        if (view == null) {
            return;
        }
        view.setText(ready
                ? R.string.usage_status_state_ready
                : (recommended ? R.string.usage_status_state_recommended : R.string.usage_status_state_missing));
        view.setTextColor(ready ? COLOR_GREEN : COLOR_MUTED);
    }

    private LinearLayout statusCard(String titleText, String description, TextView value, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setMinimumHeight(dp(96));
        card.setBackground(rounded(COLOR_SOFT, dp(20), COLOR_LINE, 1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(listener);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(18);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        copy.addView(title, matchWrap());
        TextView desc = descriptionText(description);
        LinearLayout.LayoutParams descParams = matchWrapTop(8);
        copy.addView(desc, descParams);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        card.addView(value, new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private TextView statusValue() {
        TextView value = new TextView(this);
        value.setTextSize(16);
        value.setTypeface(AppFonts.bold(this));
        value.setIncludeFontPadding(false);
        value.setSingleLine(true);
        return value;
    }

    private TextView descriptionText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(COLOR_MUTED);
        AppFonts.apply(view, false);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private boolean hasUsageAccess() {
        AppOpsManager manager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (manager == null) {
            return false;
        }
        return manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return manager == null || manager.areNotificationsEnabled();
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean hasActivityRecognitionPermission() {
        return Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureTrackerServiceRunning() {
        Intent intent = new Intent(this, EyeTimeService.class).setAction(EyeTimeService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void openUsageAccessSettings() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void openBackgroundSettings() {
        Toast.makeText(this, R.string.usage_status_background_hint, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void requestActivityRecognitionPermission() {
        if (hasActivityRecognitionPermission()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQUEST_ACTIVITY_RECOGNITION);
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapTop(int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(1, COLOR_LINE);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
