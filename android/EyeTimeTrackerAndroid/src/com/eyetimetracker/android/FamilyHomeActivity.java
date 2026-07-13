package com.eyetimetracker.android;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    private static final long FAMILY_UPLOAD_INTERVAL_MS = 15_000L;
    private static final String DISPLAY_CACHE_PREFS = "family_home_display_cache";
    private static final String CACHE_TODAY_SECONDS = "todaySeconds";
    private static final String CACHE_YESTERDAY_SECONDS = "yesterdaySeconds";
    private static final String CACHE_WEEK_SECONDS = "weekSeconds";
    private static final String CACHE_MONTH_SECONDS = "monthSeconds";
    private static final String CACHE_TOP_APP_TEXT = "topAppText";
    private static final String CACHE_UPDATED_AT = "updatedAtUnixSeconds";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EyeTimeStore store;
    private FamilyHomeState state;
    private boolean settingsMode;
    private boolean rulesMode;
    private FamilyEyeRules rulesDraft;
    private FamilyStatsLanServer familyStatsServer;
    private int familyStatsServerPort;
    private boolean familyUploadRunning;
    private boolean familyUploadRequestRunning;
    private boolean statsRefreshInFlight;
    private boolean statsRefreshQueued;
    private boolean childReminderDialogShowing;
    private long lastFamilyUploadStartedAt;
    private TextView todayValue;
    private TextView yesterdayValue;
    private TextView weekValue;
    private TextView monthValue;
    private TextView reminderValue;
    private TextView syncStatusValue;
    private boolean loadedOnCreate;

    private final Runnable familyNetworkStartupRunnable = new Runnable() {
        @Override public void run() {
            if (store == null || state == null || isFinishing()) {
                return;
            }
            startFamilyStatsServerIfNeeded();
            requestFamilyChildUploadNow(false);
            handler.removeCallbacks(familyUploadRequestRetryRunnable);
            handler.removeCallbacks(familyUploadRequestFallbackRunnable);
            handler.postDelayed(familyUploadRequestRetryRunnable, 1000L);
            handler.postDelayed(familyUploadRequestFallbackRunnable, 3000L);
            triggerFamilyStatsUploadIfNeeded(false);
        }
    };

    private final Runnable familyUploadRequestRetryRunnable = new Runnable() {
        @Override public void run() {
            requestFamilyChildUploadNow(false);
        }
    };

    private final Runnable familyUploadRequestFallbackRunnable = new Runnable() {
        @Override public void run() {
            requestFamilyChildUploadNow(true);
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
        long startedAt = System.currentTimeMillis();
        store = new EyeTimeStore(this);
        if (!loadStateOrOpenSetup()) {
            return;
        }
        loadedOnCreate = true;
        render();
        Log.i(DIAG_TAG, "FamilyHomeActivity onCreate ms=" + (System.currentTimeMillis() - startedAt));
    }

    @Override protected void onResume() {
        super.onResume();
        long startedAt = System.currentTimeMillis();
        if (store != null && (consumeLoadedOnCreate() || loadStateOrOpenSetup())) {
            refreshStats();
            handler.removeCallbacks(familyNetworkStartupRunnable);
            handler.postDelayed(familyNetworkStartupRunnable, 250L);
            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        }
        Log.i(DIAG_TAG, "FamilyHomeActivity onResume ms=" + (System.currentTimeMillis() - startedAt));
    }

    @Override protected void onPause() {
        handler.removeCallbacks(familyNetworkStartupRunnable);
        handler.removeCallbacks(familyUploadRequestRetryRunnable);
        handler.removeCallbacks(familyUploadRequestFallbackRunnable);
        handler.removeCallbacks(refreshRunnable);
        stopFamilyStatsServer();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (rulesMode) {
            rulesMode = false;
            settingsMode = true;
            rulesDraft = null;
            render();
            return;
        }
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
        setContentView(rulesMode ? buildRulesUi() : (settingsMode ? buildSettingsUi() : buildHomeUi()));
        if (!settingsMode && !rulesMode) {
            handler.post(this::refreshStats);
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
        syncStatusValue = helpText("");
        syncStatusValue.setTextSize(12);
        syncStatusValue.setVisibility(View.GONE);
        root.addView(syncStatusValue, matchWrapTop(8));

        GridLayout cards = new GridLayout(this);
        cards.setColumnCount(2);
        cards.setUseDefaultMargins(false);
        root.addView(cards, matchWrapTop(26));
        addCard(cards, buildMetricCard(getString(R.string.main_card_yesterday), yesterdayValue = cardValueText()), 0, 0);
        addCard(cards, buildMetricCard(getString(R.string.main_card_week), weekValue = cardValueText()), 0, 1);
        addCard(cards, buildMetricCard(getString(R.string.main_card_month), monthValue = cardValueText()), 1, 0);
        addCard(cards, buildMetricCard(getString(R.string.family_home_top_app), reminderValue = cardValueText()), 1, 1);
        yesterdayValue.setText(DurationFormatter.formatMainCard(this, 0));
        weekValue.setText(DurationFormatter.formatMainCard(this, 0));
        monthValue.setText(DurationFormatter.formatMainCard(this, 0));
        reminderValue.setText(getString(R.string.common_none));
        FamilyStatsSnapshot cachedSnapshot = readFamilyStatsDisplayCache();
        if (cachedSnapshot != null) {
            applyFamilyStatsSnapshot(cachedSnapshot, false);
        }

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
                store.getFamilyEyeRules().summaryText(),
                false,
                false,
                v -> openFamilyRules()), matchWrapTop(10));
        list.addView(settingRow(
                getString(R.string.family_home_exit),
                getString(R.string.family_home_exit_desc),
                ">",
                false,
                true,
                v -> confirmLeaveFamilyMode()), matchWrapTop(10));
        return scroll;
    }

    private View buildRulesUi() {
        if (rulesDraft == null) {
            rulesDraft = store.getFamilyEyeRules();
        }
        ScrollView scroll = baseScroll();
        LinearLayout root = baseRoot(scroll);
        root.addView(buildHeader(
                getString(R.string.family_rules_title),
                "",
                v -> {
                    rulesMode = false;
                    settingsMode = true;
                    rulesDraft = null;
                    render();
                },
                null), matchWrap());
        root.addView(buildStatusLine(), matchWrapTop(18));

        TextView note = helpText(getString(R.string.family_rules_note));
        note.setTextSize(15);
        root.addView(note, matchWrapTop(14));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, matchWrapTop(24));

        list.addView(settingRow(
                getString(R.string.family_rules_continuous_title),
                getString(R.string.family_rules_continuous_desc),
                onOffText(rulesDraft.continuousUseReminderEnabled),
                false,
                false,
                v -> updateRulesDraft(rulesDraft.withContinuousUse(
                        !rulesDraft.continuousUseReminderEnabled,
                        rulesDraft.continuousUseMinutes,
                        rulesDraft.restMinutes,
                        nowSeconds()))), matchWrap());
        if (rulesDraft.continuousUseReminderEnabled) {
            list.addView(inlineMinuteRow(
                    getString(R.string.family_rules_continuous_minutes),
                    getString(R.string.family_rules_continuous_minutes_desc),
                    rulesDraft.continuousUseMinutes,
                    1,
                    720,
                    value -> rulesDraft = rulesDraft.withContinuousUse(
                            true,
                            value,
                            rulesDraft.restMinutes,
                            nowSeconds())), matchWrapTop(10));
            TextView continuousNote = helpText(getString(R.string.family_rules_continuous_soft_note));
            continuousNote.setTextSize(14);
            list.addView(continuousNote, matchWrapTop(14));
        }

        list.addView(disabledPeriodsCard(), matchWrapTop(10));

        TextView softNote = helpText(getString(R.string.family_rules_disabled_soft_note));
        softNote.setTextSize(14);
        root.addView(softNote, matchWrapTop(18));

        TextView saveButton = actionButton(getString(R.string.common_save), true);
        saveButton.setOnClickListener(v -> saveFamilyRules());
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.addView(saveButton, centeredButtonParams());
        root.addView(actions, matchWrapTop(24));
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

    private View inlineMinuteRow(String title, String description, int currentValue, int min, int max, IntSubmit submit) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setMinimumHeight(dp(68));
        row.setBackground(rounded(Color.WHITE, dp(12), COLOR_LINE, 1));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        row.addView(textBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(17);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(AppFonts.bold(this));
        titleView.setIncludeFontPadding(false);
        textBlock.addView(titleView, matchWrap());

        TextView descView = helpText(description);
        descView.setTextSize(13);
        textBlock.addView(descView, matchWrapTop(6));

        LinearLayout valueWrap = new LinearLayout(this);
        valueWrap.setOrientation(LinearLayout.HORIZONTAL);
        valueWrap.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout.LayoutParams valueWrapParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueWrapParams.leftMargin = dp(12);
        row.addView(valueWrap, valueWrapParams);

        EditText input = new EditText(this);
        input.setText(String.valueOf(currentValue));
        input.setTextSize(17);
        input.setTextColor(COLOR_MUTED);
        input.setTypeface(AppFonts.bold(this));
        input.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setIncludeFontPadding(false);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, 0, 0, 0);
        valueWrap.addView(input, new LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView unit = new TextView(this);
        unit.setText(getString(R.string.family_rules_minutes_unit));
        unit.setTextSize(17);
        unit.setTextColor(COLOR_MUTED);
        unit.setTypeface(AppFonts.bold(this));
        unit.setIncludeFontPadding(false);
        valueWrap.addView(unit, wrapWrap());

        int[] lastValue = new int[] { currentValue };
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commitInlineMinutes(input, lastValue, min, max, submit);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (commitInlineMinutes(input, lastValue, min, max, submit)) {
                    hideKeyboard(input);
                    input.clearFocus();
                }
                return true;
            }
            return false;
        });
        row.setOnClickListener(v -> focusInlineInput(input));
        valueWrap.setOnClickListener(v -> focusInlineInput(input));
        return row;
    }

    private boolean commitInlineMinutes(EditText input, int[] lastValue, int min, int max, IntSubmit submit) {
        String raw = input.getText().toString().trim();
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new NumberFormatException("out of range");
            }
            input.setText(String.valueOf(value));
            if (value != lastValue[0]) {
                lastValue[0] = value;
                submit.onSubmit(value);
            }
            return true;
        } catch (NumberFormatException ex) {
            Toast.makeText(this, getString(R.string.family_rules_number_invalid)
                    .replace("{min}", String.valueOf(min))
                    .replace("{max}", String.valueOf(max)), Toast.LENGTH_SHORT).show();
            input.setText(String.valueOf(lastValue[0]));
            input.selectAll();
            return false;
        }
    }

    private void focusInlineInput(EditText input) {
        input.requestFocus();
        input.selectAll();
        input.post(() -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void openFamilyRules() {
        if (state.deviceRole != DeviceRole.PARENT_DEVICE) {
            return;
        }
        rulesDraft = store.getFamilyEyeRules();
        settingsMode = false;
        rulesMode = true;
        render();
    }

    private void updateRulesDraft(FamilyEyeRules rules) {
        rulesDraft = rules;
        render();
    }

    private void addDisabledPeriod() {
        showDisabledPeriodPickerDialog(FamilyEyeRules.DisabledPeriod.defaults(), period -> {
            List<FamilyEyeRules.DisabledPeriod> periods = rulesDraft.disabledPeriodEnabled
                    ? copyDisabledPeriods()
                    : new ArrayList<>();
            periods.add(period);
            updateRulesDraft(rulesDraft.withDisabledPeriods(true, periods, nowSeconds()));
        });
    }

    private void editDisabledPeriod(int index) {
        if (index < 0 || index >= rulesDraft.disabledPeriods.size()) {
            return;
        }
        showDisabledPeriodPickerDialog(rulesDraft.disabledPeriods.get(index), period -> replaceDisabledPeriod(index, period.startMinutes, period.endMinutes));
    }

    private void removeDisabledPeriod(int index) {
        if (index < 0 || index >= rulesDraft.disabledPeriods.size()) {
            return;
        }
        List<FamilyEyeRules.DisabledPeriod> periods = copyDisabledPeriods();
        periods.remove(index);
        updateRulesDraft(rulesDraft.withDisabledPeriods(!periods.isEmpty(), periods, nowSeconds()));
    }

    private void replaceDisabledPeriod(int index, int startMinutes, int endMinutes) {
        if (index < 0 || index >= rulesDraft.disabledPeriods.size()) {
            return;
        }
        List<FamilyEyeRules.DisabledPeriod> periods = copyDisabledPeriods();
        periods.set(index, FamilyEyeRules.DisabledPeriod.create(startMinutes, endMinutes));
        updateRulesDraft(rulesDraft.withDisabledPeriods(true, periods, nowSeconds()));
    }

    private List<FamilyEyeRules.DisabledPeriod> copyDisabledPeriods() {
        return new ArrayList<>(rulesDraft == null ? FamilyEyeRules.defaults(store.getActiveChildId()).disabledPeriods : rulesDraft.disabledPeriods);
    }

    private String endTimeText(FamilyEyeRules.DisabledPeriod period) {
        String value = FamilyEyeRules.formatMinuteOfDay(period.endMinutes);
        return period.startMinutes > period.endMinutes ? getString(R.string.family_rules_next_day_time).replace("{time}", value) : value;
    }

    private String disabledPeriodText(FamilyEyeRules.DisabledPeriod period) {
        return FamilyEyeRules.formatMinuteOfDay(period.startMinutes) + " - " + endTimeText(period);
    }

    private View disabledPeriodsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(rounded(Color.WHITE, dp(12), COLOR_LINE, 1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        header.addView(textBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(getString(R.string.family_rules_disabled_period_title));
        title.setTextSize(17);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        textBlock.addView(title, matchWrap());

        TextView add = new TextView(this);
        add.setText("+");
        add.setTextSize(18);
        add.setTextColor(Color.WHITE);
        add.setTypeface(AppFonts.bold(this));
        add.setGravity(Gravity.CENTER);
        add.setIncludeFontPadding(false);
        add.setBackground(oval(COLOR_GREEN));
        add.setClickable(true);
        add.setFocusable(true);
        add.setOnClickListener(v -> addDisabledPeriod());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        addParams.leftMargin = dp(14);
        header.addView(add, addParams);
        card.addView(header, matchWrap());

        if (rulesDraft.disabledPeriodEnabled) {
            for (int i = 0; i < rulesDraft.disabledPeriods.size(); i++) {
                int index = i;
                card.addView(disabledPeriodRow(rulesDraft.disabledPeriods.get(i), () -> editDisabledPeriod(index), () -> removeDisabledPeriod(index)), matchWrapTop(12));
            }
        } else {
            TextView off = helpText(getString(R.string.family_rules_off));
            off.setTextSize(15);
            off.setTypeface(AppFonts.bold(this));
            card.addView(off, matchWrapTop(14));
        }
        return card;
    }

    private View disabledPeriodRow(FamilyEyeRules.DisabledPeriod period, Runnable onEdit, Runnable onRemove) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(2));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> onEdit.run());

        TextView value = new TextView(this);
        value.setText(disabledPeriodText(period));
        value.setTextSize(17);
        value.setTextColor(COLOR_TEXT);
        AppFonts.apply(value, false);
        value.setIncludeFontPadding(false);
        row.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextSize(18);
        remove.setTextColor(COLOR_MUTED);
        remove.setTypeface(AppFonts.bold(this));
        remove.setGravity(Gravity.CENTER);
        remove.setIncludeFontPadding(false);
        remove.setBackground(oval(COLOR_DISABLED));
        remove.setClickable(true);
        remove.setFocusable(true);
        remove.setOnClickListener(v -> onRemove.run());
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        removeParams.leftMargin = dp(10);
        row.addView(remove, removeParams);
        return row;
    }

    private void showNumberInputDialog(String title, String hint, int currentValue, int min, int max, IntSubmit submit) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle(title), matchWrap());

        EditText input = new EditText(this);
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setText(String.valueOf(currentValue));
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setBackground(rounded(Color.rgb(244, 250, 247), dp(8), COLOR_LINE, 1));
        input.setPadding(dp(14), 0, dp(14), 0);
        panel.addView(input, fixedHeightTop(52, 18));

        LinearLayout actions = dialogActions();
        actions.addView(dialogButton(getString(R.string.common_cancel), false, false, v -> dialog.dismiss()), dialogButtonParams(0));
        actions.addView(dialogButton(getString(R.string.common_save), true, false, v -> {
            try {
                int value = Integer.parseInt(input.getText().toString().trim());
                if (value < min || value > max) {
                    Toast.makeText(this, getString(R.string.family_rules_number_invalid)
                            .replace("{min}", String.valueOf(min))
                            .replace("{max}", String.valueOf(max)), Toast.LENGTH_SHORT).show();
                    return;
                }
                submit.onSubmit(value);
                dialog.dismiss();
            } catch (NumberFormatException ex) {
                Toast.makeText(this, getString(R.string.family_rules_number_invalid)
                        .replace("{min}", String.valueOf(min))
                        .replace("{max}", String.valueOf(max)), Toast.LENGTH_SHORT).show();
            }
        }), dialogButtonParams(dp(10)));
        panel.addView(actions, matchWrapTop(22));
        showStyledDialog(dialog, panel, null);
    }

    private void showDisabledPeriodPickerDialog(FamilyEyeRules.DisabledPeriod current, PeriodSubmit submit) {
        FamilyEyeRules.DisabledPeriod initial = current == null
                ? FamilyEyeRules.DisabledPeriod.defaults()
                : current;
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel();
        panel.setPadding(dp(18), dp(24), dp(18), dp(24));

        LinearLayout pickers = new LinearLayout(this);
        pickers.setOrientation(LinearLayout.HORIZONTAL);
        pickers.setGravity(Gravity.CENTER);
        pickers.setPadding(0, 0, 0, 0);

        TextView from = pickerLabel("从");
        TimeColumn startHour = new TimeColumn(0, 23, initial.startMinutes / 60);
        TimeColumn startMinute = new TimeColumn(0, 59, initial.startMinutes % 60);
        TextView to = pickerLabel("到");
        TimeColumn endHour = new TimeColumn(0, 23, initial.endMinutes / 60);
        TimeColumn endMinute = new TimeColumn(0, 59, initial.endMinutes % 60);

        pickers.addView(from, pickerLabelParams());
        pickers.addView(startHour.view(), pickerParams());
        pickers.addView(startMinute.view(), pickerParams());
        pickers.addView(to, pickerLabelParams());
        pickers.addView(endHour.view(), pickerParams());
        pickers.addView(endMinute.view(), pickerParams());
        panel.addView(pickers, matchWrap());

        LinearLayout actions = dialogActions();
        actions.addView(dialogButton(getString(R.string.common_cancel), false, false, v -> dialog.dismiss()), dialogButtonParams(0));
        actions.addView(dialogButton(getString(R.string.common_confirm), true, false, v -> {
            FamilyEyeRules.DisabledPeriod period = FamilyEyeRules.DisabledPeriod.create(
                    startHour.value() * 60 + startMinute.value(),
                    endHour.value() * 60 + endMinute.value());
            if (!period.isValid()) {
                Toast.makeText(this, "开始和结束时间不能相同", Toast.LENGTH_SHORT).show();
                return;
            }
            submit.onSubmit(period);
            dialog.dismiss();
        }), dialogButtonParams(dp(10)));
        panel.addView(actions, matchWrapTop(22));
        showBottomSheetDialog(dialog, panel, null);
    }

    private void saveFamilyRules() {
        if (rulesDraft == null) {
            return;
        }
        store.saveFamilyEyeRules(rulesDraft);
        Toast.makeText(this, R.string.family_rules_saved, Toast.LENGTH_SHORT).show();
        rulesMode = false;
        settingsMode = true;
        rulesDraft = null;
        render();
    }

    private String onOffText(boolean enabled) {
        return enabled ? getString(R.string.family_rules_on) : getString(R.string.family_rules_off);
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private interface IntSubmit {
        void onSubmit(int value);
    }

    private interface PeriodSubmit {
        void onSubmit(FamilyEyeRules.DisabledPeriod period);
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

    private TextView pickerLabel(String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(21);
        label.setTextColor(COLOR_TEXT);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        label.setTypeface(AppFonts.bold(this));
        return label;
    }

    private LinearLayout.LayoutParams pickerLabelParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(30), dp(116));
        params.leftMargin = 0;
        params.rightMargin = 0;
        return params;
    }

    private LinearLayout.LayoutParams pickerParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(116), 1f);
        params.leftMargin = dp(1);
        params.rightMargin = dp(1);
        return params;
    }

    private final class TimeColumn {
        private final int min;
        private final int max;
        private int value;
        private final LinearLayout root;
        private final TextView previous;
        private final TextView current;
        private final TextView next;
        private float touchStartY;
        private float lastTouchY;
        private float touchRemainderY;
        private boolean movedByTouch;

        TimeColumn(int min, int max, int value) {
            this.min = min;
            this.max = max;
            this.value = Math.max(min, Math.min(max, value));
            root = new LinearLayout(FamilyHomeActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);

            previous = pickerNumber(false);
            current = pickerNumber(true);
            next = pickerNumber(false);

            View.OnTouchListener touchListener = (v, event) -> handleTouch(v, event);
            root.setOnTouchListener(touchListener);
            previous.setOnTouchListener(touchListener);
            current.setOnTouchListener(touchListener);
            next.setOnTouchListener(touchListener);
            root.setClickable(true);
            previous.setClickable(true);
            current.setClickable(true);
            next.setClickable(true);

            root.addView(previous, pickerNumberParams(28));
            root.addView(pickerLine(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
            root.addView(current, pickerNumberParams(38));
            root.addView(pickerLine(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
            root.addView(next, pickerNumberParams(28));
            update();
        }

        View view() {
            return root;
        }

        int value() {
            return value;
        }

        private void increment() {
            value = value >= max ? min : value + 1;
            update();
        }

        private void decrement() {
            value = value <= min ? max : value - 1;
            update();
        }

        private boolean handleTouch(View touchedView, MotionEvent event) {
            int stepDistance = dp(24);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartY = event.getRawY();
                    lastTouchY = touchStartY;
                    touchRemainderY = 0f;
                    movedByTouch = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float currentY = event.getRawY();
                    float delta = currentY - lastTouchY;
                    lastTouchY = currentY;
                    touchRemainderY += delta;
                    while (touchRemainderY <= -stepDistance) {
                        increment();
                        touchRemainderY += stepDistance;
                        movedByTouch = true;
                    }
                    while (touchRemainderY >= stepDistance) {
                        decrement();
                        touchRemainderY -= stepDistance;
                        movedByTouch = true;
                    }
                    if (Math.abs(currentY - touchStartY) > dp(8)) {
                        movedByTouch = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!movedByTouch) {
                        if (touchedView == previous) {
                            decrement();
                        } else {
                            increment();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return false;
            }
        }

        private void update() {
            previous.setText(twoDigits(value <= min ? max : value - 1));
            current.setText(twoDigits(value));
            next.setText(twoDigits(value >= max ? min : value + 1));
        }
    }

    private TextView pickerNumber(boolean selected) {
        TextView text = new TextView(this);
        text.setTextSize(selected ? 26 : 18);
        text.setTextColor(selected ? COLOR_GREEN : Color.rgb(96, 96, 96));
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        if (selected) {
            text.setTypeface(AppFonts.bold(this));
        } else {
            AppFonts.apply(text, false);
        }
        return text;
    }

    private View pickerLine() {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(134, 134, 134));
        return line;
    }

    private LinearLayout.LayoutParams pickerNumberParams(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height));
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
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

    private void showBottomSheetDialog(Dialog dialog, View panel, Runnable onDismiss) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout outer = new LinearLayout(this);
        outer.setPadding(dp(12), 0, dp(12), dp(18));
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
            window.setGravity(Gravity.BOTTOM);
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
        if (todayValue == null || state == null) {
            return;
        }
        if (statsRefreshInFlight) {
            statsRefreshQueued = true;
            return;
        }
        statsRefreshInFlight = true;
        LocalDate today = LocalDate.now();
        boolean showFamilyChildStats = state.deviceRole == DeviceRole.PARENT_DEVICE && state.hasBoundChildDevice;
        String emptyText = getString(R.string.common_none);
        new Thread(() -> {
            FamilyStatsSnapshot snapshot = buildFamilyStatsSnapshot(today, showFamilyChildStats, emptyText);
            runOnUiThread(() -> {
                statsRefreshInFlight = false;
                if (todayValue == null || isFinishing()) {
                    return;
                }
                applyFamilyStatsSnapshot(snapshot);
                if (snapshot.showFamilyChildStats) {
                    maybeShowFamilyChildReminder(snapshot.today, snapshot.todaySeconds);
                }
                triggerFamilyStatsUploadIfNeeded(true);
                if (statsRefreshQueued) {
                    statsRefreshQueued = false;
                    refreshStats();
                }
            });
        }, "FamilyHomeStatsRefresh").start();
    }

    private FamilyStatsSnapshot buildFamilyStatsSnapshot(LocalDate today, boolean showFamilyChildStats, String emptyText) {
        FamilyChildHomeSnapshot childHomeSnapshot = showFamilyChildStats
                ? store.getFamilyChildHomeSnapshot(today)
                : null;
        HomeStatsSnapshot familyChildStats = showFamilyChildStats && childHomeSnapshot == null
                ? store.displayFamilyChildHomeStats(today)
                : null;
        long todaySeconds = showFamilyChildStats
                ? (childHomeSnapshot != null ? childHomeSnapshot.todaySeconds : familyChildStats.todaySeconds)
                : store.displayTodaySeconds(today);
        long yesterdaySeconds = showFamilyChildStats
                ? (childHomeSnapshot != null ? childHomeSnapshot.yesterdaySeconds : familyChildStats.yesterdaySeconds)
                : store.displayYesterdaySeconds(today);
        long weekSeconds = showFamilyChildStats
                ? (childHomeSnapshot != null ? childHomeSnapshot.weekSeconds : familyChildStats.weekSeconds)
                : store.displayWeekSeconds(today);
        long monthSeconds = showFamilyChildStats
                ? (childHomeSnapshot != null ? childHomeSnapshot.monthSeconds : familyChildStats.monthSeconds)
                : store.displayMonthSeconds(today);
        String topApp = childHomeSnapshot != null && childHomeSnapshot.topAppSeconds > 0L
                ? childHomeSnapshot.topAppName + " " + DurationFormatter.formatMainCard(this, childHomeSnapshot.topAppSeconds)
                : topAppText(today, showFamilyChildStats, emptyText);
        return new FamilyStatsSnapshot(
                today,
                showFamilyChildStats,
                todaySeconds,
                yesterdaySeconds,
                weekSeconds,
                monthSeconds,
                topApp,
                childHomeSnapshot == null ? 0L : childHomeSnapshot.updatedAtUnixSeconds);
    }

    private void applyFamilyStatsSnapshot(FamilyStatsSnapshot snapshot) {
        applyFamilyStatsSnapshot(snapshot, true);
    }

    private void applyFamilyStatsSnapshot(FamilyStatsSnapshot snapshot, boolean saveCache) {
        todayValue.setText(DurationFormatter.format(this, snapshot.todaySeconds));
        todayValue.setTextColor(colorForTone(TodayTone.fromSeconds(snapshot.todaySeconds)));
        yesterdayValue.setText(DurationFormatter.formatMainCard(this, snapshot.yesterdaySeconds));
        weekValue.setText(DurationFormatter.formatMainCard(this, snapshot.weekSeconds));
        monthValue.setText(DurationFormatter.formatMainCard(this, snapshot.monthSeconds));
        reminderValue.setText(snapshot.topAppText);
        if (syncStatusValue != null) {
            String status = familySnapshotStatusText(snapshot);
            syncStatusValue.setText(status);
            syncStatusValue.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (saveCache) {
            saveFamilyStatsDisplayCache(snapshot);
        }
    }

    private String familySnapshotStatusText(FamilyStatsSnapshot snapshot) {
        if (snapshot == null || !snapshot.showFamilyChildStats || snapshot.updatedAtUnixSeconds <= 0L) {
            return "";
        }
        long ageSeconds = Math.max(0L, System.currentTimeMillis() / 1000L - snapshot.updatedAtUnixSeconds);
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes <= 0L) {
            return "刚刚更新";
        }
        return ageMinutes + "分钟前更新";
    }

    private FamilyStatsSnapshot readFamilyStatsDisplayCache() {
        SharedPreferences prefs = getSharedPreferences(DISPLAY_CACHE_PREFS, MODE_PRIVATE);
        if (!prefs.contains(CACHE_TODAY_SECONDS)) {
            return null;
        }
        return new FamilyStatsSnapshot(
                LocalDate.now(),
                state != null && state.deviceRole == DeviceRole.PARENT_DEVICE && state.hasBoundChildDevice,
                Math.max(0L, prefs.getLong(CACHE_TODAY_SECONDS, 0L)),
                Math.max(0L, prefs.getLong(CACHE_YESTERDAY_SECONDS, 0L)),
                Math.max(0L, prefs.getLong(CACHE_WEEK_SECONDS, 0L)),
                Math.max(0L, prefs.getLong(CACHE_MONTH_SECONDS, 0L)),
                prefs.getString(CACHE_TOP_APP_TEXT, getString(R.string.common_none)),
                Math.max(0L, prefs.getLong(CACHE_UPDATED_AT, 0L)));
    }

    private void saveFamilyStatsDisplayCache(FamilyStatsSnapshot snapshot) {
        getSharedPreferences(DISPLAY_CACHE_PREFS, MODE_PRIVATE)
                .edit()
                .putLong(CACHE_TODAY_SECONDS, Math.max(0L, snapshot.todaySeconds))
                .putLong(CACHE_YESTERDAY_SECONDS, Math.max(0L, snapshot.yesterdaySeconds))
                .putLong(CACHE_WEEK_SECONDS, Math.max(0L, snapshot.weekSeconds))
                .putLong(CACHE_MONTH_SECONDS, Math.max(0L, snapshot.monthSeconds))
                .putString(CACHE_TOP_APP_TEXT, snapshot.topAppText)
                .putLong(CACHE_UPDATED_AT, Math.max(0L, snapshot.updatedAtUnixSeconds))
                .apply();
    }

    private String topAppText(LocalDate date, boolean familyChildStats, String emptyText) {
        List<AppUsageEntry> entries = familyChildStats
                ? store.getFamilyChildAppUsageEntries(date, date)
                : store.getAppUsageEntries(date, date);
        AppUsageEntry best = null;
        for (AppUsageEntry entry : entries) {
            if (entry == null || entry.durationSeconds <= 0L) {
                continue;
            }
            if (best == null || entry.durationSeconds > best.durationSeconds) {
                best = entry;
            }
        }
        if (best == null) {
            return emptyText;
        }
        String name = best.appName == null || best.appName.trim().isEmpty() ? best.appId : best.appName;
        return name + " " + DurationFormatter.formatMainCard(this, best.durationSeconds);
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
                familyStatsServerPort = port;
                Log.i(DIAG_TAG, "FamilyStatsLanServer started port=" + port);
            }

            @Override public void onUploaded(String childDeviceId, int changedSegments, String childHost) {
                store.saveFamilyChildLastKnownHost(childHost);
                Log.i(DIAG_TAG, "FamilyStatsLanServer uploaded child=" + childDeviceId
                        + " changed=" + changedSegments
                        + " host=" + childHost);
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
        familyStatsServerPort = 0;
    }

    private void requestFamilyChildUploadNow(boolean allowScanFallback) {
        if (state == null
                || state.deviceRole != DeviceRole.PARENT_DEVICE
                || !state.isFamilyMode
                || !state.hasBoundChildDevice
                || familyUploadRequestRunning) {
            return;
        }
        familyUploadRequestRunning = true;
        new Thread(() -> {
            String childHost = store.getFamilyChildLastKnownHost();
            long startedAt = System.currentTimeMillis();
            FamilyStatsUploadRequestClient.RequestResult result = new FamilyStatsUploadRequestClient(
                    familyStatsServerPort,
                    childHost,
                    allowScanFallback).requestUpload(store);
            Log.i(DIAG_TAG, "FamilyStats upload request requested=" + result.requested
                    + " skipped=" + result.skipped
                    + " parentPort=" + familyStatsServerPort
                    + " childHost=" + childHost
                    + " scanFallback=" + allowScanFallback
                    + " udpSent=" + result.udpSent
                    + " tcpAccepted=" + result.tcpAccepted
                    + " ms=" + (System.currentTimeMillis() - startedAt)
                    + " error=" + result.error);
            runOnUiThread(() -> familyUploadRequestRunning = false);
        }, "FamilyStatsUploadRequest").start();
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

    private static final class FamilyStatsSnapshot {
        final LocalDate today;
        final boolean showFamilyChildStats;
        final long todaySeconds;
        final long yesterdaySeconds;
        final long weekSeconds;
        final long monthSeconds;
        final String topAppText;
        final long updatedAtUnixSeconds;

        FamilyStatsSnapshot(
                LocalDate today,
                boolean showFamilyChildStats,
                long todaySeconds,
                long yesterdaySeconds,
                long weekSeconds,
                long monthSeconds,
                String topAppText,
                long updatedAtUnixSeconds) {
            this.today = today;
            this.showFamilyChildStats = showFamilyChildStats;
            this.todaySeconds = todaySeconds;
            this.yesterdaySeconds = yesterdaySeconds;
            this.weekSeconds = weekSeconds;
            this.monthSeconds = monthSeconds;
            this.topAppText = topAppText == null ? "" : topAppText;
            this.updatedAtUnixSeconds = Math.max(0L, updatedAtUnixSeconds);
        }
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
