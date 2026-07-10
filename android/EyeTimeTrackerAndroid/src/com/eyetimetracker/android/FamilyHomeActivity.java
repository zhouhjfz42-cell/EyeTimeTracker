package com.eyetimetracker.android;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;

public final class FamilyHomeActivity extends Activity {
    private static final String DIAG_TAG = "EyeTimeDiag";
    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(102, 112, 133);
    private static final int COLOR_GREEN = Color.rgb(22, 166, 125);
    private static final int COLOR_YELLOW = Color.rgb(217, 154, 19);
    private static final int COLOR_RED = Color.rgb(224, 82, 82);
    private static final int COLOR_SOFT = Color.rgb(237, 248, 244);
    private static final int COLOR_LINE = Color.rgb(223, 240, 233);
    private static final int COLOR_DISABLED = Color.rgb(239, 242, 241);
    private static final int COLOR_DISABLED_TEXT = Color.rgb(152, 162, 160);
    private static final long REFRESH_INTERVAL_MS = 10_000L;
    private static final long INITIAL_STATS_REFRESH_DELAY_MS = 120L;
    private static final long FAMILY_UPLOAD_INTERVAL_MS = 15_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EyeTimeStore store;
    private FamilyHomeState state;
    private boolean settingsMode;
    private FamilyStatsLanServer familyStatsServer;
    private boolean familyUploadRunning;
    private boolean childReminderDialogShowing;
    private long lastFamilyUploadStartedAt;
    private TextView todayValue;
    private TextView yesterdayValue;
    private TextView weekValue;
    private TextView monthValue;
    private TextView reminderValue;
    private boolean loadedOnCreate;

    private final Runnable immediateRefreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStats();
        }
    };

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStats();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store = new EyeTimeStore(this);
        if (!loadStateOrOpenSetup()) {
            return;
        }
        loadedOnCreate = true;
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null && (consumeLoadedOnCreate() || loadStateOrOpenSetup())) {
            scheduleImmediateStatsRefresh();
            startFamilyStatsServerIfNeeded();
            triggerFamilyStatsUploadIfNeeded(false);
            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(immediateRefreshRunnable);
        handler.removeCallbacks(refreshRunnable);
        stopFamilyStatsServer();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (settingsMode) {
            settingsMode = false;
            render();
            return;
        }
        super.onBackPressed();
    }

    private boolean loadStateOrOpenSetup() {
        state = store.getFamilyHomeState();
        if (!state.isFamilyMode || !state.hasChildProfile()) {
            startActivity(new Intent(this, FamilySetupActivity.class));
            finish();
            return false;
        }
        return true;
    }

    private boolean consumeLoadedOnCreate() {
        if (!loadedOnCreate) {
            return false;
        }
        loadedOnCreate = false;
        return state != null;
    }

    private void render() {
        setContentView(settingsMode ? buildSettingsUi() : buildHomeUi());
        if (!settingsMode) {
            scheduleImmediateStatsRefresh();
        }
    }

    private View buildHomeUi() {
        ScrollView scroll = baseScroll();
        LinearLayout root = baseRoot(scroll);
        root.addView(buildHeader(
                getString(R.string.family_home_title),
                state.showSettingsEntry ? getString(R.string.family_home_settings) : "",
                v -> finish(),
                v -> {
                    settingsMode = true;
                    render();
                }), matchWrap());
        root.addView(buildStatusLine(), matchWrapTop(18));

        todayValue = new TextView(this);
        todayValue.setTextSize(56);
        todayValue.setTypeface(AppFonts.bold(this));
        todayValue.setIncludeFontPadding(false);
        todayValue.setSingleLine(true);
        todayValue.setText(DurationFormatter.format(this, 0));
        todayValue.setTextColor(colorForTone(TodayTone.fromSeconds(0)));
        root.addView(todayValue, matchWrapTop(18));

        TextView note = helpText(state.deviceRole == DeviceRole.CHILD_DEVICE
                ? getString(R.string.family_home_child_note)
                : getString(R.string.family_home_parent_note));
        note.setTextSize(14);
        root.addView(note, matchWrapTop(12));

        GridLayout cards = new GridLayout(this);
        cards.setColumnCount(2);
        cards.setUseDefaultMargins(false);
        root.addView(cards, matchWrapTop(26));
        addCard(cards, buildMetricCard(getString(R.string.main_card_yesterday), yesterdayValue = cardValueText()), 0, 0);
        addCard(cards, buildMetricCard(getString(R.string.main_card_week), weekValue = cardValueText()), 0, 1);
        addCard(cards, buildMetricCard(getString(R.string.main_card_month), monthValue = cardValueText()), 1, 0);
        addCard(cards, buildMetricCard(getString(R.string.main_card_reminder), reminderValue = cardValueText()), 1, 1);
        yesterdayValue.setText(DurationFormatter.formatMainCard(this, 0));
        weekValue.setText(DurationFormatter.formatMainCard(this, 0));
        monthValue.setText(DurationFormatter.formatMainCard(this, 0));
        reminderValue.setText(ReminderThreshold.format(this, store.getReminderMinutes()));

        TextView statsButton = actionButton(getString(R.string.common_stats_page), true);
        statsButton.setOnClickListener(v -> openStatsPage());
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.addView(statsButton, centeredButtonParams());
        root.addView(actions, matchWrapTop(24));
        return scroll;
    }

    private View buildSettingsUi() {
        ScrollView scroll = baseScroll();
        LinearLayout root = baseRoot(scroll);
        root.addView(buildHeader(
                getString(R.string.family_home_settings_title),
                "",
                v -> {
                    settingsMode = false;
                    render();
                },
                null), matchWrap());
        root.addView(buildStatusLine(), matchWrapTop(18));
        TextView note = helpText(state.deviceRole == DeviceRole.CHILD_DEVICE
                ? getString(R.string.family_home_child_settings_note)
                : getString(R.string.family_home_settings_note));
        note.setTextSize(16);
        root.addView(note, matchWrapTop(12));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, matchWrapTop(24));
        list.addView(settingRow(
                getString(R.string.family_home_child_profile),
                getString(R.string.family_home_child_profile_desc),
                childSummary(),
                false,
                false), matchWrap());
        if (state.deviceRole == DeviceRole.CHILD_DEVICE) {
            list.addView(settingRow(
                    getString(R.string.family_home_device_role),
                    getString(R.string.family_home_device_role_desc),
                    roleLabel(state.deviceRole),
                    false,
                    false), matchWrapTop(10));
            list.addView(settingRow(
                    getString(R.string.family_home_rebind_child_device),
                    getString(R.string.family_home_rebind_child_device_desc),
                    ">",
                    false,
                    false,
                    v -> runProtectedChildSettingsAction(
                            FamilySettingsGuard.Action.REBIND_CHILD_DEVICE,
                            this::openChildBinding)), matchWrapTop(10));
            list.addView(settingRow(
                    getString(R.string.family_home_exit),
                    getString(R.string.family_home_exit_desc),
                    ">",
                    false,
                    true,
                    v -> runProtectedChildSettingsAction(
                            FamilySettingsGuard.Action.LEAVE_FAMILY_MODE,
                            this::confirmLeaveFamilyMode)), matchWrapTop(10));
            return scroll;
        }
        list.addView(settingRow(
                getString(R.string.family_home_add_child_device),
                getString(R.string.family_home_add_child_device_desc),
                state.hasBoundChildDevice
                        ? getString(R.string.family_home_child_device_joined)
                        : getString(R.string.family_home_child_device_not_joined),
                false,
                false,
                v -> openAddChildDevice()), matchWrapTop(10));
        list.addView(settingRow(
                getString(R.string.family_home_device_role),
                getString(R.string.family_home_device_role_desc),
                roleLabel(state.deviceRole),
                false,
                false), matchWrapTop(10));
        list.addView(settingRow(
                getString(R.string.family_home_passcode),
                getString(R.string.family_home_passcode_desc),
                state.hasParentPasscode ? getString(R.string.family_home_passcode_change) : getString(R.string.family_home_passcode_not_set),
                false,
                false,
                v -> showParentPasscodeChangeDialog()), matchWrapTop(10));
        list.addView(settingRow(
                getString(R.string.family_home_rules),
                getString(R.string.family_home_rules_desc),
                getString(R.string.family_home_not_enabled),
                true,
                false), matchWrapTop(10));
        list.addView(settingRow(
                getString(R.string.family_home_exit),
                getString(R.string.family_home_exit_desc),
                ">",
                false,
                true,
                v -> confirmLeaveFamilyMode()), matchWrapTop(10));
        return scroll;
    }

    private LinearLayout buildHeader(String titleText, String actionText, View.OnClickListener backClick, View.OnClickListener actionClick) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(COLOR_GREEN);
        back.setTypeface(AppFonts.bold(this));
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setBackground(rounded(COLOR_SOFT, dp(999), COLOR_LINE, 1));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(backClick);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(28);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(14);
        header.addView(title, titleParams);

        TextView action = topActionButton(actionText);
        if (actionText == null || actionText.trim().isEmpty()) {
            action.setVisibility(View.INVISIBLE);
        } else if (actionClick != null) {
            action.setOnClickListener(actionClick);
        }
        header.addView(action, new LinearLayout.LayoutParams(dp(76), dp(38)));
        return header;
    }

    private LinearLayout buildStatusLine() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView child = statusText(childName());
        row.addView(child, wrapWrap());

        View dot = new View(this);
        dot.setBackground(oval(COLOR_GREEN));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotParams.leftMargin = dp(14);
        dotParams.rightMargin = dp(10);
        row.addView(dot, dotParams);

        TextView role = statusText(roleLabel(state.deviceRole));
        row.addView(role, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private LinearLayout buildMetricCard(String label, TextView value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(12));
        card.setMinimumHeight(dp(88));
        card.setBackground(rounded(COLOR_SOFT, dp(20), COLOR_LINE, 1));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(16);
        labelView.setTextColor(COLOR_MUTED);
        AppFonts.apply(labelView, false);
        labelView.setSingleLine(true);
        labelView.setIncludeFontPadding(false);
        card.addView(labelView, matchWrap());

        card.addView(value, matchWrapTop(9));
        return card;
    }

    private View settingRow(String title, String description, String value, boolean disabled, boolean danger) {
        return settingRow(title, description, value, disabled, danger, null);
    }

    private View settingRow(String title, String description, String value, boolean disabled, boolean danger, View.OnClickListener clickListener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setMinimumHeight(dp(68));
        row.setBackground(rounded(disabled ? COLOR_DISABLED : Color.WHITE, dp(12), COLOR_LINE, 1));
        if (!disabled && clickListener != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(clickListener);
        }

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        row.addView(textBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(17);
        titleView.setTextColor(danger ? Color.rgb(180, 35, 24) : (disabled ? COLOR_DISABLED_TEXT : COLOR_TEXT));
        titleView.setTypeface(AppFonts.bold(this));
        titleView.setIncludeFontPadding(false);
        textBlock.addView(titleView, matchWrap());

        TextView descView = helpText(description);
        descView.setTextSize(13);
        if (disabled) {
            descView.setTextColor(COLOR_DISABLED_TEXT);
        }
        textBlock.addView(descView, matchWrapTop(6));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(danger ? 24 : 15);
        valueView.setTextColor(disabled ? COLOR_DISABLED_TEXT : COLOR_MUTED);
        valueView.setTypeface(AppFonts.bold(this));
        valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        valueView.setIncludeFontPadding(false);
        valueView.setSingleLine(true);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = dp(12);
        row.addView(valueView, valueParams);
        return row;
    }

    private void openAddChildDevice() {
        if (!FamilySettingsPolicy.canOpenAddChildDeviceBinding(state.deviceRole, state.hasBoundChildDevice)) {
            Toast.makeText(this, R.string.family_home_add_child_device_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, FamilySetupActivity.class);
        intent.putExtra(FamilySetupActivity.EXTRA_SHOW_PARENT_BINDING, true);
        startActivity(intent);
    }

    private void openChildBinding() {
        Intent intent = new Intent(this, FamilySetupActivity.class);
        intent.putExtra(FamilySetupActivity.EXTRA_SHOW_CHILD_BINDING, true);
        startActivity(intent);
    }

    private void openStatsPage() {
        Intent intent = new Intent(this, StatsActivity.class);
        if (state.deviceRole == DeviceRole.PARENT_DEVICE && state.hasBoundChildDevice) {
            intent.putExtra(StatsActivity.EXTRA_FAMILY_CHILD_STATS, true);
        }
        startActivity(intent);
    }

    private void runProtectedChildSettingsAction(FamilySettingsGuard.Action action, Runnable afterVerified) {
        if (!FamilySettingsGuard.requiresPasscode(state.deviceRole, action)) {
            afterVerified.run();
            return;
        }
        if (!store.hasParentPasscode()) {
            Toast.makeText(this, R.string.family_home_passcode_missing, Toast.LENGTH_LONG).show();
            return;
        }
        showParentPasscodeDialog(afterVerified);
    }

    private void showParentPasscodeDialog(Runnable afterVerified) {
        showPasscodeInputDialog(
                getString(R.string.family_home_passcode_dialog_title),
                getString(R.string.family_home_passcode_dialog_message),
                getString(R.string.family_home_passcode_input_hint),
                getString(R.string.common_confirm),
                value -> {
            if (store.verifyParentPasscode(value)) {
                afterVerified.run();
                return true;
            }
            Toast.makeText(this, R.string.family_home_passcode_wrong, Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    private void showParentPasscodeChangeDialog() {
        showPasscodeInputDialog(
                getString(R.string.family_home_passcode_change_title),
                getString(R.string.family_home_passcode_change_message),
                getString(R.string.family_home_passcode_input_hint),
                getString(R.string.common_save),
                value -> {
            if (!value.matches("\\d{4}")) {
                Toast.makeText(this, R.string.family_home_passcode_invalid, Toast.LENGTH_SHORT).show();
                return false;
            }
            store.saveParentPasscode(value);
            loadStateOrOpenSetup();
            Toast.makeText(this, R.string.family_home_passcode_saved, Toast.LENGTH_SHORT).show();
            render();
            return true;
        });
    }

    private void showPasscodeInputDialog(String title, String message, String hint, String confirmLabel, PasscodeSubmit submit) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle(title), matchWrap());
        panel.addView(dialogMessage(message), matchWrapTop(14));

        EditText input = new EditText(this);
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setBackground(rounded(Color.rgb(244, 250, 247), dp(8), COLOR_LINE, 1));
        input.setPadding(dp(14), 0, dp(14), 0);
        panel.addView(input, fixedHeightTop(52, 18));

        LinearLayout actions = dialogActions();
        actions.addView(dialogButton(getString(R.string.common_cancel), false, false, v -> dialog.dismiss()), dialogButtonParams(0));
        actions.addView(dialogButton(confirmLabel, true, false, v -> {
            if (submit.onSubmit(input.getText().toString())) {
                dialog.dismiss();
            }
        }), dialogButtonParams(dp(10)));
        panel.addView(actions, matchWrapTop(22));
        showStyledDialog(dialog, panel, null);
    }

    private interface PasscodeSubmit {
        boolean onSubmit(String value);
    }

    private void confirmLeaveFamilyMode() {
        showDecisionDialog(
                getString(R.string.family_home_leave_confirm_title),
                getString(R.string.family_home_leave_confirm_message),
                getString(R.string.family_home_exit),
                true,
                true,
                this::leaveFamilyMode,
                null);
    }

    private void showDecisionDialog(
            String title,
            String message,
            String confirmLabel,
            boolean danger,
            boolean showCancel,
            Runnable onConfirm,
            Runnable onDismiss) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle(title), matchWrap());
        panel.addView(dialogMessage(message), matchWrapTop(14));

        LinearLayout actions = dialogActions();
        if (showCancel) {
            actions.addView(dialogButton(getString(R.string.common_cancel), false, false, v -> dialog.dismiss()), dialogButtonParams(0));
        }
        actions.addView(dialogButton(confirmLabel, true, danger, v -> {
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.run();
            }
        }), dialogButtonParams(showCancel ? dp(10) : 0));
        panel.addView(actions, matchWrapTop(24));
        showStyledDialog(dialog, panel, onDismiss);
    }

    private LinearLayout dialogPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(22), dp(22), dp(20));
        panel.setBackground(rounded(Color.WHITE, dp(16), COLOR_LINE, 1));
        return panel;
    }

    private TextView dialogTitle(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(24);
        text.setTextColor(COLOR_TEXT);
        text.setTypeface(AppFonts.bold(this));
        text.setIncludeFontPadding(false);
        return text;
    }

    private TextView dialogMessage(String value) {
        TextView text = helpText(value);
        text.setTextSize(16);
        text.setLineSpacing(0f, 1.22f);
        return text;
    }

    private LinearLayout dialogActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        return actions;
    }

    private TextView dialogButton(String label, boolean primary, boolean danger, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTypeface(AppFonts.bold(this));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setSingleLine(true);
        button.setAutoSizeTextTypeUniformWithConfiguration(13, 16, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setTextColor(primary ? Color.WHITE : (danger ? COLOR_RED : COLOR_GREEN));
        button.setBackground(rounded(primary ? (danger ? COLOR_RED : COLOR_GREEN) : COLOR_SOFT, dp(999), primary ? Color.TRANSPARENT : COLOR_LINE, primary ? 0 : 1));
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams dialogButtonParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
        params.leftMargin = leftMargin;
        return params;
    }

    private void showStyledDialog(Dialog dialog, View panel, Runnable onDismiss) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout outer = new LinearLayout(this);
        outer.setPadding(dp(24), 0, dp(24), 0);
        outer.addView(panel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(outer);
        dialog.setOnDismissListener(d -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout.LayoutParams fixedHeightTop(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height));
        params.topMargin = dp(topMargin);
        return params;
    }

    private void leaveFamilyMode() {
        store.leaveFamilyMode();
        finish();
    }

    private void scheduleImmediateStatsRefresh() {
        handler.removeCallbacks(immediateRefreshRunnable);
        handler.postDelayed(immediateRefreshRunnable, INITIAL_STATS_REFRESH_DELAY_MS);
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
        button.setTextColor(primary ? Color.WHITE : COLOR_MUTED);
        button.setBackground(rounded(primary ? COLOR_GREEN : COLOR_SOFT, dp(999), Color.TRANSPARENT, 0));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView topActionButton(String label) {
        TextView button = actionButton(label == null ? "" : label, false);
        button.setTextSize(15);
        button.setTextColor(COLOR_GREEN);
        button.setBackground(rounded(COLOR_SOFT, dp(999), COLOR_LINE, 1));
        return button;
    }

    private TextView statusText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(18);
        text.setTextColor(COLOR_MUTED);
        text.setTypeface(AppFonts.bold(this));
        text.setSingleLine(true);
        text.setIncludeFontPadding(false);
        return text;
    }

    private TextView helpText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(12);
        text.setTextColor(COLOR_MUTED);
        AppFonts.apply(text, false);
        text.setIncludeFontPadding(false);
        text.setLineSpacing(0f, 1.15f);
        return text;
    }

    private void refreshStats() {
        if (todayValue == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        boolean showFamilyChildStats = state.deviceRole == DeviceRole.PARENT_DEVICE && state.hasBoundChildDevice;
        HomeStatsSnapshot familyChildStats = showFamilyChildStats
                ? store.displayFamilyChildHomeStats(today)
                : null;
        long todaySeconds = showFamilyChildStats
                ? familyChildStats.todaySeconds
                : store.displayTodaySeconds(today);
        todayValue.setText(DurationFormatter.format(this, todaySeconds));
        todayValue.setTextColor(colorForTone(TodayTone.fromSeconds(todaySeconds)));
        yesterdayValue.setText(DurationFormatter.formatMainCard(this, showFamilyChildStats
                ? familyChildStats.yesterdaySeconds
                : store.displayYesterdaySeconds(today)));
        weekValue.setText(DurationFormatter.formatMainCard(this, showFamilyChildStats
                ? familyChildStats.weekSeconds
                : store.displayWeekSeconds(today)));
        monthValue.setText(DurationFormatter.formatMainCard(this, showFamilyChildStats
                ? familyChildStats.monthSeconds
                : store.displayMonthSeconds(today)));
        reminderValue.setText(ReminderThreshold.format(this, store.getReminderMinutes()));
        if (showFamilyChildStats) {
            maybeShowFamilyChildReminder(today, todaySeconds);
        }
        triggerFamilyStatsUploadIfNeeded(true);
    }

    private void maybeShowFamilyChildReminder(LocalDate today, long childTodaySeconds) {
        int reminderMinutes = store.getReminderMinutes();
        boolean repeatReminder = store.isRepeatReminderEnabled();
        boolean reminderShown = store.isFamilyChildReminderShown(today);
        int lastReminderStep = store.getFamilyChildLastReminderStep(today);
        if (!ReminderPolicy.shouldNotify(childTodaySeconds, reminderMinutes, repeatReminder, reminderShown, lastReminderStep)) {
            return;
        }
        int reminderStep = ReminderPolicy.reachedStep(childTodaySeconds, reminderMinutes);
        store.markFamilyChildReminderShown(today, reminderStep);
        showFamilyChildReminderDialog(reminderMinutes, repeatReminder, reminderStep);
    }

    private void showFamilyChildReminderDialog(int reminderMinutes, boolean repeatReminder, int reminderStep) {
        if (childReminderDialogShowing || isFinishing()) {
            return;
        }
        childReminderDialogShowing = true;
        String title = getString(R.string.family_home_child_reminder_title)
                .replace("{child}", childName());
        String message = getString(repeatReminder && reminderStep > 0
                ? R.string.family_home_child_reminder_body_repeat
                : R.string.family_home_child_reminder_body_once)
                .replace("{child}", childName())
                .replace("{duration}", ReminderThreshold.format(this, reminderMinutes))
                .replace("{minutes}", String.valueOf(reminderMinutes))
                .replace("{step}", String.valueOf(reminderStep));
        showDecisionDialog(
                title,
                message,
                getString(R.string.common_ok),
                false,
                false,
                null,
                () -> childReminderDialogShowing = false);
    }

    private void startFamilyStatsServerIfNeeded() {
        if (state == null || state.deviceRole != DeviceRole.PARENT_DEVICE || !state.isFamilyMode) {
            stopFamilyStatsServer();
            return;
        }
        if (familyStatsServer != null) {
            return;
        }
        familyStatsServer = new FamilyStatsLanServer(store);
        familyStatsServer.start(new FamilyStatsLanServer.Listener() {
            @Override public void onStarted(int port) {
                Log.i(DIAG_TAG, "FamilyStatsLanServer started port=" + port);
            }

            @Override public void onUploaded(String childDeviceId, int changedSegments) {
                Log.i(DIAG_TAG, "FamilyStatsLanServer uploaded child=" + childDeviceId + " changed=" + changedSegments);
                runOnUiThread(() -> refreshStats());
            }

            @Override public void onError(String message) {
                Log.i(DIAG_TAG, "FamilyStatsLanServer error=" + message);
            }
        });
    }

    private void stopFamilyStatsServer() {
        if (familyStatsServer != null) {
            familyStatsServer.stop();
            familyStatsServer = null;
        }
    }

    private void triggerFamilyStatsUploadIfNeeded(boolean respectInterval) {
        if (state == null || state.deviceRole != DeviceRole.CHILD_DEVICE || !state.isFamilyMode || familyUploadRunning) {
            return;
        }
        long now = System.currentTimeMillis();
        if (respectInterval && now - lastFamilyUploadStartedAt < FAMILY_UPLOAD_INTERVAL_MS) {
            return;
        }
        familyUploadRunning = true;
        lastFamilyUploadStartedAt = now;
        new Thread(() -> {
            FamilyStatsLanClient.UploadResult result = new FamilyStatsLanClient().upload(store);
            Log.i(DIAG_TAG, "FamilyStatsLanClient upload success=" + result.success
                    + " skipped=" + result.skipped
                    + " changed=" + result.changedSegments
                    + " error=" + result.error);
            runOnUiThread(() -> familyUploadRunning = false);
        }, "FamilyStatsLanUpload").start();
    }

    private String childName() {
        return state.childNickname.isEmpty() ? getString(R.string.family_home_unknown_child) : state.childNickname;
    }

    private String childSummary() {
        if (ChildProfile.AGE_BAND_UNKNOWN.equals(state.childAgeBand)) {
            return childName();
        }
        return childName() + ", " + state.childAgeBand;
    }

    private String roleLabel(DeviceRole role) {
        if (role == DeviceRole.PARENT_DEVICE) {
            return getString(R.string.family_role_parent);
        }
        if (role == DeviceRole.CHILD_DEVICE) {
            return getString(R.string.family_role_child);
        }
        if (role == DeviceRole.PC_COMPANION) {
            return getString(R.string.family_role_pc);
        }
        return getString(R.string.family_role_personal);
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

    private ScrollView baseScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);
        return scroll;
    }

    private LinearLayout baseRoot(ScrollView scroll) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return root;
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

    private LinearLayout.LayoutParams centeredButtonParams() {
        return new LinearLayout.LayoutParams(dp(260), dp(54));
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
