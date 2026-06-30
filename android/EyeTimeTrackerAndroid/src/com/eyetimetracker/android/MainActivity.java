package com.eyetimetracker.android;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.time.DayOfWeek;
import java.time.LocalDate;

public final class MainActivity extends Activity {
    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(102, 112, 133);
    private static final int COLOR_GREEN = Color.rgb(22, 166, 125);
    private static final int COLOR_YELLOW = Color.rgb(217, 154, 19);
    private static final int COLOR_RED = Color.rgb(224, 82, 82);
    private static final int COLOR_SOFT = Color.rgb(237, 248, 244);
    private static final int COLOR_LINE = Color.rgb(223, 240, 233);
    private static final int COLOR_BUTTON_SOFT = Color.rgb(241, 244, 243);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EyeTimeStore store;
    private TextView todayValue;
    private TextView yesterdayValue;
    private TextView weekValue;
    private TextView monthValue;
    private TextView reminderValue;
    private TextView statusValue;
    private View statusDot;
    private boolean receiverRegistered;

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
        IntentFilter filter = new IntentFilter(EyeTimeService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        receiverRegistered = true;
        handler.post(refreshRunnable);
        refresh();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        if (receiverRegistered) {
            unregisterReceiver(receiver);
            receiverRegistered = false;
        }
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("用眼时间");
        title.setTextSize(30);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("亮屏 + 机身动作，或亮屏 + 媒体播放时计入统计（息屏播放不计时）");
        subtitle.setTextSize(14);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(12);
        root.addView(subtitle, subtitleParams);

        root.addView(buildDayRow(), matchWrapTop(28));

        todayValue = new TextView(this);
        todayValue.setTextSize(56);
        todayValue.setTypeface(Typeface.DEFAULT_BOLD);
        todayValue.setIncludeFontPadding(false);
        todayValue.setSingleLine(true);
        root.addView(todayValue, matchWrapTop(12));

        GridLayout cards = new GridLayout(this);
        cards.setColumnCount(2);
        cards.setUseDefaultMargins(false);
        LinearLayout.LayoutParams cardsParams = matchWrapTop(26);
        root.addView(cards, cardsParams);
        addCard(cards, buildMetricCard("昨天", yesterdayValue = cardValueText(), null), 0, 0);
        addCard(cards, buildMetricCard("本周", weekValue = cardValueText(), null), 0, 1);
        addCard(cards, buildMetricCard("本月", monthValue = cardValueText(), null), 1, 0);
        addCard(cards, buildMetricCard("提醒", reminderValue = cardValueText(), v -> showReminderDialog()), 1, 1);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = matchWrapTop(24);
        root.addView(actions, actionsParams);

        TextView resetButton = actionButton("重置显示", false);
        resetButton.setOnClickListener(v -> {
            store.resetDisplay(LocalDate.now());
            refresh();
        });
        actions.addView(resetButton, weightedButtonParams(0, dp(6)));

        TextView startButton = actionButton("后台运行", true);
        startButton.setOnClickListener(v -> startTrackerService());
        actions.addView(startButton, weightedButtonParams(dp(6), 0));

        TextView device = new TextView(this);
        device.setText("设备 ID：" + store.getDeviceId());
        device.setTextSize(11);
        device.setTextColor(Color.rgb(152, 162, 179));
        device.setSingleLine(true);
        LinearLayout.LayoutParams deviceParams = matchWrapTop(16);
        root.addView(device, deviceParams);
        return scroll;
    }

    private LinearLayout buildDayRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText("今天");
        label.setTextSize(18);
        label.setTextColor(COLOR_MUTED);
        row.addView(label, wrapWrap());

        statusDot = new View(this);
        statusDot.setBackground(oval(COLOR_GREEN));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotParams.leftMargin = dp(18);
        dotParams.rightMargin = dp(10);
        row.addView(statusDot, dotParams);

        statusValue = new TextView(this);
        statusValue.setText("后台统计中");
        statusValue.setTextSize(18);
        statusValue.setTextColor(COLOR_MUTED);
        row.addView(statusValue, wrapWrap());
        return row;
    }

    private LinearLayout buildMetricCard(String label, TextView value, View.OnClickListener clickListener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(12));
        card.setMinimumHeight(dp(88));
        card.setBackground(rounded(COLOR_SOFT, dp(20), COLOR_LINE, 1));
        if (clickListener != null) {
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(clickListener);
        }

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(16);
        labelView.setTextColor(COLOR_MUTED);
        card.addView(labelView, matchWrap());

        LinearLayout.LayoutParams valueParams = matchWrapTop(9);
        card.addView(value, valueParams);
        return card;
    }

    private TextView cardValueText() {
        TextView text = new TextView(this);
        text.setTextSize(24);
        text.setTextColor(COLOR_TEXT);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setSingleLine(true);
        text.setIncludeFontPadding(false);
        return text;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(54));
        button.setTextColor(primary ? Color.WHITE : Color.rgb(52, 64, 84));
        button.setBackground(rounded(primary ? COLOR_GREEN : COLOR_BUTTON_SOFT, dp(999), Color.TRANSPARENT, 0));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void addCard(GridLayout grid, View card, int row, int column) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(column, 1f));
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.setMargins(column == 0 ? 0 : dp(5), row == 0 ? 0 : dp(10), column == 0 ? dp(5) : 0, 0);
        grid.addView(card, params);
    }

    private void refresh() {
        LocalDate today = LocalDate.now();
        long todaySeconds = store.displayTodaySeconds(today);
        todayValue.setText(DurationFormatter.format(todaySeconds));
        todayValue.setTextColor(colorForTone(TodayTone.fromSeconds(todaySeconds)));
        yesterdayValue.setText(DurationFormatter.format(store.displayYesterdaySeconds(today)));
        weekValue.setText(DurationFormatter.format(store.displayWeekSeconds(today)));
        monthValue.setText(DurationFormatter.format(store.displayMonthSeconds(today)));
        reminderValue.setText(ReminderThreshold.format(store.getReminderMinutes()));
        statusValue.setText("后台统计中");
        statusDot.setBackground(oval(COLOR_GREEN));
    }

    private void showReminderDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(22), dp(22), dp(24));
        panel.setBackground(rounded(Color.WHITE, dp(26), Color.rgb(229, 235, 232), 1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(head, matchWrap());

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        head.addView(titleBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("提醒时间");
        title.setTextSize(24);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBlock.addView(title, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("单位：分钟（" + ReminderThreshold.formatEquivalent(store.getReminderMinutes()) + "）");
        hint.setTextSize(14);
        hint.setTextColor(COLOR_MUTED);
        titleBlock.addView(hint, matchWrapTop(6));

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(30);
        close.setTextColor(COLOR_MUTED);
        close.setGravity(Gravity.CENTER);
        close.setBackground(oval(Color.rgb(242, 244, 247)));
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(18), 0, dp(18), 0);
        field.setMinimumHeight(dp(86));
        field.setBackground(rounded(Color.rgb(249, 253, 251), dp(18), Color.rgb(207, 228, 220), 1));
        panel.addView(field, matchWrapTop(24));

        EditText input = new EditText(this);
        input.setText(String.valueOf(store.getReminderMinutes()));
        input.setTextSize(42);
        input.setTypeface(Typeface.DEFAULT_BOLD);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(COLOR_TEXT);
        input.setBackgroundColor(Color.TRANSPARENT);
        field.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView unit = new TextView(this);
        unit.setText("分钟");
        unit.setTextSize(22);
        unit.setTextColor(COLOR_MUTED);
        field.addView(unit, wrapWrap());

        LinearLayout repeatRow = new LinearLayout(this);
        repeatRow.setOrientation(LinearLayout.HORIZONTAL);
        repeatRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(repeatRow, matchWrapTop(14));

        Switch repeatSwitch = new Switch(this);
        repeatSwitch.setChecked(store.isRepeatReminderEnabled());
        repeatRow.addView(repeatSwitch, wrapWrap());

        TextView repeatLabel = new TextView(this);
        repeatLabel.setText(ReminderThreshold.formatRepeatLabel(store.getReminderMinutes()));
        repeatLabel.setTextSize(12);
        repeatLabel.setTextColor(COLOR_MUTED);
        repeatLabel.setSingleLine(true);
        LinearLayout.LayoutParams repeatTextParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        repeatTextParams.leftMargin = dp(8);
        repeatRow.addView(repeatLabel, repeatTextParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int minutes = parseMinutes(s.toString(), store.getReminderMinutes());
                hint.setText("单位：分钟（" + ReminderThreshold.formatEquivalent(minutes) + "）");
                repeatLabel.setText(ReminderThreshold.formatRepeatLabel(minutes));
            }

            @Override public void afterTextChanged(Editable s) {
            }
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        panel.addView(actions, matchWrapTop(20));

        TextView cancel = actionButton("取消", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, weightedButtonParams(0, dp(6)));

        TextView save = actionButton("保存", true);
        save.setOnClickListener(v -> {
            int minutes = parseMinutes(input.getText().toString(), ReminderThreshold.DEFAULT_MINUTES);
            store.saveReminderSettings(minutes, repeatSwitch.isChecked());
            refresh();
            dialog.dismiss();
            Toast.makeText(this, "提醒时间已保存", Toast.LENGTH_SHORT).show();
        });
        actions.addView(save, weightedButtonParams(dp(6), 0));

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        dialog.setOnShowListener(d -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                shownWindow.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.88f), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private int parseMinutes(String raw, int fallback) {
        try {
            return ReminderThreshold.clampMinutes(Integer.parseInt(raw.trim()));
        } catch (Exception ex) {
            return ReminderThreshold.clampMinutes(fallback);
        }
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

    private int colorForTone(TodayTone tone) {
        if (tone == TodayTone.DANGER) {
            return COLOR_RED;
        }
        if (tone == TodayTone.WARN) {
            return COLOR_YELLOW;
        }
        return COLOR_GREEN;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapTop(int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedButtonParams(int leftMargin, int rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1f);
        params.leftMargin = leftMargin;
        params.rightMargin = rightMargin;
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
