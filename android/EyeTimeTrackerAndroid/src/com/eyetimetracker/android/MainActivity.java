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
import android.util.TypedValue;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private TextView pairingButton;
    private View statusDot;
    private boolean syncStatusCheckInFlight;
    private long nextSyncStatusCheckMillis;
    private boolean receiverRegistered;

        private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent != null && EyeTimeService.ACTION_REMINDER.equals(intent.getAction())) {
                int reminderMinutes = intent.getIntExtra(EyeTimeService.EXTRA_REMINDER_MINUTES, store.getReminderMinutes());
                boolean repeatReminder = intent.getBooleanExtra(EyeTimeService.EXTRA_REMINDER_REPEAT, store.isRepeatReminderEnabled());
                int reminderStep = Math.max(0, intent.getIntExtra(EyeTimeService.EXTRA_REMINDER_STEP, 0));
                showReminderAlert(reminderMinutes, repeatReminder, reminderStep);
            }
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
        store.setMainActivityVisible(true);
        IntentFilter filter = new IntentFilter(EyeTimeService.ACTION_STATE_CHANGED);
        filter.addAction(EyeTimeService.ACTION_REMINDER);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        receiverRegistered = true;
        handler.post(refreshRunnable);
        nextSyncStatusCheckMillis = 0L;
        refresh();
    }

    @Override protected void onPause() {
        store.setMainActivityVisible(false);
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

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, matchWrap());

        TextView title = new TextView(this);
        title.setText("用眼时间");
        title.setTextSize(30);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView startButton = lightTopButton("启动");
        startButton.setOnClickListener(v -> startTrackerService());
        header.addView(startButton, new LinearLayout.LayoutParams(dp(76), dp(38)));

        TextView subtitle = new TextView(this);
        subtitle.setText("亮屏时计入统计");
        subtitle.setTextSize(14);
        subtitle.setTextColor(COLOR_MUTED);
        AppFonts.apply(subtitle, false);
        subtitle.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(12);
        root.addView(subtitle, subtitleParams);

        root.addView(buildDayRow(), matchWrapTop(28));

        todayValue = new TextView(this);
        todayValue.setTextSize(56);
        todayValue.setTypeface(AppFonts.bold(this));
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

        TextView statsButton = actionButton("统计页", true);
        statsButton.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
        actions.addView(statsButton, centeredButtonParams());

        root.addView(helpText("1. 将手机和电脑配对，共同统计注视两块屏幕的时间，有统计到重合时段的会删除重复统计，但可能会有部分误差；"), matchWrapTop(18));
        root.addView(helpText("2. 使用手机几秒内会自动开始统计，如果没有开始，点击右上角的“启动”。"), matchWrapTop(8));
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
        AppFonts.apply(label, false);
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);
        row.addView(label, wrapWrap());

        statusDot = new View(this);
        statusDot.setBackground(oval(COLOR_GREEN));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotParams.leftMargin = dp(18);
        dotParams.rightMargin = dp(10);
        row.addView(statusDot, dotParams);

        statusValue = new TextView(this);
        statusValue.setText("统计中");
        statusValue.setTextSize(18);
        statusValue.setTextColor(COLOR_MUTED);
        AppFonts.apply(statusValue, false);
        statusValue.setSingleLine(true);
        statusValue.setIncludeFontPadding(false);
        row.addView(statusValue, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        pairingButton = statusActionButton("\u624b\u673a\u914d\u5bf9");
        pairingButton.setOnClickListener(v -> {
            if (store.getSyncSettings().isPaired) {
                showDisconnectDialog();
                return;
            }

            showPairingDialog();
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(104), dp(34));
        buttonParams.leftMargin = dp(8);
        row.addView(pairingButton, buttonParams);
        return row;
    }

    private TextView helpText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(12);
        text.setTextColor(COLOR_MUTED);
        AppFonts.apply(text, false);
        text.setLineSpacing(0f, 1.15f);
        return text;
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
        AppFonts.apply(labelView, false);
        labelView.setSingleLine(true);
        labelView.setIncludeFontPadding(false);
        card.addView(labelView, matchWrap());

        LinearLayout.LayoutParams valueParams = matchWrapTop(9);
        card.addView(value, valueParams);
        return card;
    }

    private TextView cardValueText() {
        TextView text = new TextView(this);
        text.setTextSize(24);
        text.setTextColor(COLOR_TEXT);
        text.setTypeface(AppFonts.bold(this));
        text.setSingleLine(true);
        text.setIncludeFontPadding(false);
        text.setAutoSizeTextTypeUniformWithConfiguration(18, 24, 1, TypedValue.COMPLEX_UNIT_SP);
        return text;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTypeface(AppFonts.bold(this));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(dp(54));
        button.setTextColor(primary ? Color.WHITE : Color.rgb(52, 64, 84));
        button.setBackground(rounded(primary ? COLOR_GREEN : COLOR_BUTTON_SOFT, dp(999), Color.TRANSPARENT, 0));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView statusActionButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTypeface(AppFonts.bold(this));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(0, 0, 0, 0);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(COLOR_GREEN, dp(999), Color.TRANSPARENT, 0));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView lightTopButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(AppFonts.bold(this));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(0, 0, 0, 0);
        button.setTextColor(COLOR_GREEN);
        button.setBackground(rounded(COLOR_SOFT, dp(999), COLOR_LINE, 1));
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
        yesterdayValue.setText(DurationFormatter.formatMainCard(store.displayYesterdaySeconds(today)));
        weekValue.setText(DurationFormatter.formatMainCard(store.displayWeekSeconds(today)));
        monthValue.setText(DurationFormatter.formatMainCard(store.displayMonthSeconds(today)));
        reminderValue.setText(ReminderThreshold.format(store.getReminderMinutes()));
        SyncSettings syncSettings = store.getSyncSettings();
        statusValue.setText(ConnectionStatusText.format("统计中", syncSettings.isPaired, syncSettings.lastError == null || syncSettings.lastError.trim().isEmpty(), "电脑"));
        if (pairingButton != null) {
            pairingButton.setText(syncSettings.isPaired ? "\u65ad\u5f00" : "\u624b\u673a\u914d\u5bf9");
            ViewGroup.LayoutParams buttonParams = pairingButton.getLayoutParams();
            if (buttonParams != null) {
                buttonParams.width = syncSettings.isPaired ? dp(68) : dp(104);
                pairingButton.setLayoutParams(buttonParams);
            }
        }
        statusDot.setBackground(oval(COLOR_GREEN));
        checkPcStillConnected(syncSettings);
    }

    private void checkPcStillConnected(SyncSettings syncSettings) {
        if (syncSettings == null || !syncSettings.isPaired || syncStatusCheckInFlight) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextSyncStatusCheckMillis) {
            return;
        }

        syncStatusCheckInFlight = true;
        nextSyncStatusCheckMillis = now + 5000L;
        new Thread(() -> {
            new AndroidSyncRunner(store).syncOnce();
            handler.post(() -> {
                syncStatusCheckInFlight = false;
                refresh();
            });
        }, "EyeTimeConnectionCheck").start();
    }

    private void showReminderDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(22), dp(22), dp(22));
        panel.setBackground(rounded(Color.WHITE, dp(26), Color.rgb(229, 235, 232), 1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.TOP);
        panel.addView(head, matchWrap());

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        head.addView(titleBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("提醒时间");
        title.setTextSize(24);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        titleBlock.addView(title, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("单位：分钟（" + ReminderThreshold.formatEquivalent(store.getReminderMinutes()) + "）");
        hint.setTextSize(14);
        hint.setTextColor(COLOR_MUTED);
        AppFonts.apply(hint, false);
        hint.setIncludeFontPadding(false);
        titleBlock.addView(hint, matchWrapTop(6));

        TextView close = new TextView(this);
        close.setText("\u00d7");
        close.setTextSize(24);
        close.setTextColor(COLOR_MUTED);
        close.setTypeface(AppFonts.regular(this));
        close.setIncludeFontPadding(false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(oval(Color.rgb(242, 244, 247)));
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(18), 0, dp(18), 0);
        field.setMinimumHeight(dp(74));
        field.setBackground(rounded(Color.rgb(249, 253, 251), dp(18), Color.rgb(207, 228, 220), 1));
        panel.addView(field, matchWrapTop(18));

        EditText input = new EditText(this);
        input.setText(String.valueOf(store.getReminderMinutes()));
        input.setTextSize(42);
        input.setTypeface(AppFonts.bold(this));
        input.setSingleLine(true);
        input.setIncludeFontPadding(false);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(COLOR_TEXT);
        input.setBackgroundColor(Color.TRANSPARENT);
        field.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView unit = new TextView(this);
        unit.setText("分钟");
        unit.setTextSize(22);
        unit.setTextColor(COLOR_MUTED);
        AppFonts.apply(unit, false);
        unit.setIncludeFontPadding(false);
        field.addView(unit, wrapWrap());

        LinearLayout repeatRow = new LinearLayout(this);
        repeatRow.setOrientation(LinearLayout.HORIZONTAL);
        repeatRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(repeatRow, matchWrapTop(12));

        Switch repeatSwitch = new Switch(this);
        repeatSwitch.setChecked(store.isRepeatReminderEnabled());
        repeatRow.addView(repeatSwitch, wrapWrap());

        TextView repeatLabel = new TextView(this);
        repeatLabel.setText(ReminderThreshold.formatRepeatLabel(store.getReminderMinutes()));
        repeatLabel.setTextSize(11);
        repeatLabel.setTextColor(COLOR_MUTED);
        AppFonts.apply(repeatLabel, false);
        repeatLabel.setSingleLine(false);
        repeatLabel.setMaxLines(2);
        repeatLabel.setIncludeFontPadding(false);
        repeatLabel.setLineSpacing(0f, 1.05f);
        LinearLayout.LayoutParams repeatTextParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        repeatTextParams.leftMargin = dp(10);
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
        panel.addView(actions, matchWrapTop(16));

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
            runSyncAfterSettingsChange();
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

    private void showPairingDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        SyncSettings currentSettings = store.getSyncSettings();
        String pairingCode = PairingCodeGenerator.generate();
        AtomicBoolean pairingStopped = new AtomicBoolean(false);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(22), dp(22), dp(24));
        panel.setBackground(rounded(Color.WHITE, dp(26), Color.rgb(229, 235, 232), 1));

        TextView title = new TextView(this);
        title.setText("\u624b\u673a\u914d\u5bf9");
        title.setTextSize(24);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        panel.addView(title, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("\u5148\u5728\u7535\u8111\u4e0a\u70b9\u51fb\u201c\u624b\u673a\u914d\u5bf9\u201d\uff0c\u8f93\u5165\u4e0b\u9762\u7684 6 \u4f4d\u7801\u3002\u624b\u673a\u4f1a\u81ea\u52a8\u5bfb\u627e\u7535\u8111\uff0c\u627e\u4e0d\u5230\u65f6\u518d\u624b\u52a8\u586b\u5730\u5740\u3002");
        hint.setTextSize(14);
        hint.setTextColor(COLOR_MUTED);
        hint.setLineSpacing(0f, 1.12f);
        panel.addView(hint, matchWrapTop(10));

        TextView code = new TextView(this);
        code.setText(pairingCode);
        code.setTextSize(42);
        code.setTypeface(AppFonts.bold(this));
        code.setGravity(Gravity.CENTER);
        code.setTextColor(COLOR_GREEN);
        code.setIncludeFontPadding(false);
        code.setBackground(rounded(COLOR_SOFT, dp(18), COLOR_LINE, 1));
        LinearLayout.LayoutParams codeParams = matchWrapTop(18);
        codeParams.height = dp(78);
        panel.addView(code, codeParams);

        EditText hostInput = new EditText(this);
        hostInput.setHint("\u7535\u8111 IP \u5730\u5740\uff0c\u4f8b\u5982 192.168.1.8");
        hostInput.setText(currentSettings.peerHost);
        hostInput.setTextSize(16);
        hostInput.setSingleLine(true);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hostInput.setTextColor(COLOR_TEXT);
        hostInput.setVisibility(View.GONE);
        panel.addView(hostInput, matchWrapTop(18));

        EditText portInput = new EditText(this);
        portInput.setHint("\u7aef\u53e3");
        portInput.setText(String.valueOf(currentSettings.peerPort > 0 ? currentSettings.peerPort : 17420));
        portInput.setTextSize(16);
        portInput.setSingleLine(true);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setTextColor(COLOR_TEXT);
        portInput.setVisibility(View.GONE);
        panel.addView(portInput, matchWrapTop(10));

        TextView status = new TextView(this);
        status.setText("\u6b63\u5728\u540c\u4e00\u4e2a WiFi \u91cc\u5bfb\u627e\u7535\u8111\u2026");
        status.setTextSize(13);
        status.setTextColor(COLOR_MUTED);
        status.setLineSpacing(0f, 1.1f);
        panel.addView(status, matchWrapTop(12));

        final TextView[] pairButton = new TextView[1];
        TextView manual = lightTopButton("\u627e\u4e0d\u5230\u7535\u8111\uff1f\u624b\u52a8\u586b\u5199");
        manual.setOnClickListener(v -> {
            pairingStopped.set(true);
            hostInput.setVisibility(View.VISIBLE);
            portInput.setVisibility(View.VISIBLE);
            manual.setVisibility(View.GONE);
            if (pairButton[0] != null) {
                pairButton[0].setEnabled(true);
                pairButton[0].setText("\u624b\u52a8\u8fde\u63a5");
            }
            status.setText("\u8bf7\u8f93\u5165\u7535\u8111 IP \u5730\u5740\uff0c\u7136\u540e\u70b9\u51fb\u624b\u52a8\u8fde\u63a5\u3002");
        });
        panel.addView(manual, matchWrapTop(12));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        panel.addView(actions, matchWrapTop(20));

        TextView cancel = actionButton("\u53d6\u6d88", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, weightedButtonParams(0, dp(6)));

        TextView pair = actionButton("\u7b49\u5f85\u7535\u8111\u786e\u8ba4", true);
        pairButton[0] = pair;
        pair.setEnabled(false);
        pair.setOnClickListener(v -> {
            String manualHost = hostInput.getText().toString().trim();
            int manualPort = parsePort(portInput.getText().toString(), 17420);
            if (manualHost.isEmpty()) {
                Toast.makeText(this, "\u8bf7\u8f93\u5165\u7535\u8111 IP \u5730\u5740", Toast.LENGTH_SHORT).show();
                return;
            }
            if (manualPort <= 0 || manualPort > 65535) {
                Toast.makeText(this, "\u8bf7\u8f93\u5165\u6b63\u786e\u7aef\u53e3", Toast.LENGTH_SHORT).show();
                return;
            }

            pair.setEnabled(false);
            pair.setText("\u8fde\u63a5\u4e2d");
            status.setText("\u6b63\u5728\u8fde\u63a5\u7535\u8111\u2026");
            new Thread(() -> {
                SyncSettings settings = store.getSyncSettings();
                settings.peerHost = manualHost;
                settings.peerPort = manualPort;

                boolean paired = new AndroidPairingClient().pair(settings, store.getDeviceId(), pairingCode);
                store.saveSyncSettings(settings);
                handler.post(() -> {
                    pair.setEnabled(true);
                    pair.setText("\u624b\u52a8\u8fde\u63a5");
                    status.setText(paired ? "\u5df2\u548c\u7535\u8111\u914d\u5bf9\u3002" : "\u914d\u5bf9\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u914d\u5bf9\u7801\u548c\u7535\u8111\u662f\u5426\u5728\u540c\u4e00 WiFi\u3002");
                    Toast.makeText(
                            this,
                            paired ? "\u5df2\u548c\u7535\u8111\u914d\u5bf9" : "\u914d\u5bf9\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u914d\u5bf9\u7801\u548c\u7535\u8111\u72b6\u6001",
                            Toast.LENGTH_SHORT).show();
                    if (paired) {
                        refresh();
                        dialog.dismiss();
                    }
                });
            }, "EyeTimePairing").start();
        });
        actions.addView(pair, weightedButtonParams(dp(6), 0));

        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> pairingStopped.set(true));
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            shownWindow.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        startAutomaticPairing(pairingStopped, pairingCode, status, pair, hostInput, portInput, manual, dialog);
    }

    private void showDisconnectDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setBackground(rounded(Color.WHITE, dp(26), Color.rgb(229, 235, 232), 1));

        TextView title = new TextView(this);
        title.setText("\u65ad\u5f00\u8fde\u63a5");
        title.setTextSize(24);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        panel.addView(title, matchWrap());

        TextView message = new TextView(this);
        message.setText("\u65ad\u5f00\u540e\uff0c\u4e0b\u6b21\u8fde\u63a5\u9700\u8981\u91cd\u65b0\u914d\u5bf9\u3002");
        message.setTextSize(15);
        message.setTextColor(COLOR_MUTED);
        message.setLineSpacing(0f, 1.15f);
        panel.addView(message, matchWrapTop(12));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        panel.addView(actions, matchWrapTop(22));

        TextView cancel = actionButton("\u53d6\u6d88", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, weightedButtonParams(0, dp(6)));

        TextView disconnect = actionButton("\u65ad\u5f00", true);
        disconnect.setOnClickListener(v -> {
            disconnect.setEnabled(false);
            SyncDisconnectPlan plan = SyncDisconnectPlan.create(store.getSyncSettings());
            store.saveSyncSettings(plan.localSettings);
            refresh();
            Toast.makeText(this, "\u5df2\u65ad\u5f00\u7535\u8111", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            new Thread(() -> {
                new AndroidPairingClient().disconnect(plan.peerNotificationSettings, store.getDeviceId());
            }, "EyeTimeDisconnect").start();
        });
        actions.addView(disconnect, weightedButtonParams(dp(6), 0));

        dialog.setContentView(panel);
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            shownWindow.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void startAutomaticPairing(
            AtomicBoolean stopped,
            String pairingCode,
            TextView status,
            TextView pair,
            EditText hostInput,
            EditText portInput,
            TextView manual,
            Dialog dialog) {
        new Thread(() -> {
            AndroidPcDiscoveryClient discoveryClient = new AndroidPcDiscoveryClient();
            AndroidPairingClient pairingClient = new AndroidPairingClient();
            for (int attempt = 0; attempt < 150 && !stopped.get(); attempt++) {
                AndroidPcDiscoveryClient.DiscoveryResult discovery = discoveryClient.discover();
                if (stopped.get()) {
                    return;
                }
                if (!discovery.found) {
                    if (attempt == 2) {
                        handler.post(() -> {
                            if (!stopped.get()) {
                                status.setText("\u8fd8\u5728\u5bfb\u627e\u7535\u8111\u3002\u8bf7\u786e\u8ba4\u7535\u8111\u5df2\u6253\u5f00\u201c\u624b\u673a\u914d\u5bf9\u201d\u7a97\u53e3\u3002");
                            }
                        });
                    }
                    sleepPairingInterval(stopped);
                    continue;
                }

                SyncSettings settings = store.getSyncSettings();
                settings.peerHost = discovery.host;
                settings.peerPort = discovery.port;
                boolean paired = pairingClient.pair(settings, store.getDeviceId(), pairingCode);
                store.saveSyncSettings(settings);
                if (paired) {
                    stopped.set(true);
                    handler.post(() -> {
                        status.setText("\u5df2\u548c\u7535\u8111\u914d\u5bf9\u3002");
                        Toast.makeText(this, "\u5df2\u548c\u7535\u8111\u914d\u5bf9", Toast.LENGTH_SHORT).show();
                        refresh();
                        dialog.dismiss();
                    });
                    return;
                }

                handler.post(() -> {
                    if (!stopped.get()) {
                        status.setText("\u5df2\u627e\u5230\u7535\u8111\uff0c\u7b49\u5f85\u7535\u8111\u786e\u8ba4\u914d\u5bf9\u7801\u2026");
                    }
                });
                sleepPairingInterval(stopped);
            }

            handler.post(() -> {
                if (!stopped.get()) {
                    pair.setEnabled(true);
                    pair.setText("\u624b\u52a8\u8fde\u63a5");
                    hostInput.setVisibility(View.VISIBLE);
                    portInput.setVisibility(View.VISIBLE);
                    manual.setVisibility(View.GONE);
                    status.setText("\u6682\u65f6\u6ca1\u6709\u81ea\u52a8\u8fde\u4e0a\u3002\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u7535\u8111 IP \u5730\u5740\u540e\u518d\u8fde\u63a5\u3002");
                }
            });
        }, "EyeTimeAutoPairing").start();
    }

    private static void sleepPairingInterval(AtomicBoolean stopped) {
        for (int i = 0; i < 20 && !stopped.get(); i++) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                stopped.set(true);
                return;
            }
        }
    }

    private void showReminderAlert(int reminderMinutes, boolean repeatReminder, int reminderStep) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(26), dp(24), dp(26), dp(24));
        content.setBackground(rounded(Color.WHITE, dp(28), COLOR_LINE, 1));

        TextView title = new TextView(this);
        title.setText(ReminderAlert.title());
        title.setTextSize(26);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        content.addView(title, matchWrap());

        TextView message = new TextView(this);
        message.setText(ReminderAlert.message(reminderMinutes, repeatReminder, reminderStep));
        message.setTextSize(18);
        message.setTextColor(COLOR_MUTED);
        message.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams messageParams = matchWrapTop(20);
        content.addView(message, messageParams);

        TextView okButton = actionButton("\u6211\u77e5\u9053\u4e86", true);
        okButton.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams buttonParams = matchWrapTop(26);
        content.addView(okButton, buttonParams);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            shownWindow.setLayout(dp(326), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private int parseMinutes(String raw, int fallback) {
        try {
            return ReminderThreshold.clampMinutes(Integer.parseInt(raw.trim()));
        } catch (Exception ex) {
            return ReminderThreshold.clampMinutes(fallback);
        }
    }

    private int parsePort(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ex) {
            return fallback;
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

    private void runSyncAfterSettingsChange() {
        SyncSettings settings = store.getSyncSettings();
        if (!settings.isPaired) {
            return;
        }

        new Thread(() -> new AndroidSyncRunner(store).syncOnce(), "EyeTimeSettingsSync").start();
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

    private LinearLayout.LayoutParams centeredButtonParams() {
        return new LinearLayout.LayoutParams(dp(260), dp(54));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
