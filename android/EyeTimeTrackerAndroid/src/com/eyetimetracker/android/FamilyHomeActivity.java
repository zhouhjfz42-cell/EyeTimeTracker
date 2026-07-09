package com.eyetimetracker.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;

public final class FamilyHomeActivity extends Activity {
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EyeTimeStore store;
    private FamilyHomeState state;
    private boolean settingsMode;
    private TextView todayValue;
    private TextView yesterdayValue;
    private TextView weekValue;
    private TextView monthValue;
    private TextView reminderValue;

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
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null && loadStateOrOpenSetup()) {
            refreshStats();
            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
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
        state = FamilyHomeState.create(
                store.getProductMode(),
                store.getDeviceRole(),
                store.getActiveChildProfile(),
                store.hasParentPasscode());
        if (!state.isFamilyMode || !state.hasChildProfile()) {
            startActivity(new Intent(this, FamilySetupActivity.class));
            finish();
            return false;
        }
        return true;
    }

    private void render() {
        setContentView(settingsMode ? buildSettingsUi() : buildHomeUi());
        if (!settingsMode) {
            refreshStats();
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

        TextView statsButton = actionButton(getString(R.string.common_stats_page), true);
        statsButton.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
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
        TextView note = helpText(getString(R.string.family_home_settings_note));
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
        list.addView(settingRow(
                getString(R.string.family_home_device_role),
                getString(R.string.family_home_device_role_desc),
                roleLabel(state.deviceRole),
                false,
                false), matchWrapTop(10));
        list.addView(settingRow(
                getString(R.string.family_home_passcode),
                getString(R.string.family_home_passcode_desc),
                state.hasParentPasscode ? getString(R.string.family_home_passcode_set) : getString(R.string.family_home_passcode_not_set),
                false,
                false), matchWrapTop(10));
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
                true), matchWrapTop(10));
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setMinimumHeight(dp(68));
        row.setBackground(rounded(disabled ? COLOR_DISABLED : Color.WHITE, dp(12), COLOR_LINE, 1));

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
        long todaySeconds = store.displayTodaySeconds(today);
        todayValue.setText(DurationFormatter.format(this, todaySeconds));
        todayValue.setTextColor(colorForTone(TodayTone.fromSeconds(todaySeconds)));
        yesterdayValue.setText(DurationFormatter.formatMainCard(this, store.displayYesterdaySeconds(today)));
        weekValue.setText(DurationFormatter.formatMainCard(this, store.displayWeekSeconds(today)));
        monthValue.setText(DurationFormatter.formatMainCard(this, store.displayMonthSeconds(today)));
        reminderValue.setText(ReminderThreshold.format(this, store.getReminderMinutes()));
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
