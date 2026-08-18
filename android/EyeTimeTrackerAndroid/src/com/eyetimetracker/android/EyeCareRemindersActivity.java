package com.eyetimetracker.android;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class EyeCareRemindersActivity extends Activity {
    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(102, 112, 133);
    private static final int COLOR_GREEN = Color.rgb(22, 166, 125);
    private static final int COLOR_SOFT = Color.rgb(237, 248, 244);
    private static final int COLOR_LINE = Color.rgb(223, 240, 233);
    private static final int COLOR_BUTTON_SOFT = Color.rgb(241, 244, 243);
    private static final int COLOR_DISABLED_TEXT = Color.rgb(152, 162, 160);
    private static final int REQUEST_ACTIVITY_RECOGNITION = 42;

    private EyeTimeStore store;
    private TextView cumulativeValue;
    private TextView continuousValue;
    private TextView walkingValue;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store = new EyeTimeStore(this);
        setContentView(buildUi());
        refreshValues();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshValues();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ACTIVITY_RECOGNITION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            store.saveWalkingReminderEnabled(granted);
            refreshValues();
            if (granted) {
                Toast.makeText(this, R.string.eye_care_reminders_settings_saved, Toast.LENGTH_SHORT).show();
                startTrackerService();
            }
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
        header.setOrientation(LinearLayout.HORIZONTAL);
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
        title.setText(R.string.eye_care_reminders_dialog_title);
        title.setTextSize(34);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(14);
        header.addView(title, titleParams);

        TextView lead = new TextView(this);
        lead.setText(R.string.eye_care_reminders_dialog_lead);
        lead.setTextSize(12);
        lead.setTextColor(COLOR_MUTED);
        AppFonts.apply(lead, false);
        lead.setIncludeFontPadding(false);
        lead.setLineSpacing(0f, 1.15f);
        root.addView(lead, matchWrapTop(26));

        root.addView(settingCard(
                getString(R.string.eye_care_reminders_cumulative_title),
                getString(R.string.eye_care_reminders_cumulative_desc),
                cumulativeValue = valueText(),
                v -> showCumulativeDialog()), matchWrapTop(28));
        root.addView(settingCard(
                getString(R.string.eye_care_reminders_continuous_title),
                getString(R.string.eye_care_reminders_continuous_desc),
                continuousValue = valueText(),
                v -> showContinuousDialog()), matchWrapTop(18));
        root.addView(settingCard(
                getString(R.string.eye_care_reminders_walking_title),
                getString(R.string.eye_care_reminders_walking_desc),
                walkingValue = valueText(),
                v -> showWalkingDialog()), matchWrapTop(18));

        TextView footer = new TextView(this);
        footer.setText(R.string.eye_care_reminders_footer);
        footer.setTextSize(12);
        footer.setTextColor(COLOR_MUTED);
        AppFonts.apply(footer, false);
        footer.setIncludeFontPadding(false);
        footer.setLineSpacing(0f, 1.15f);
        root.addView(footer, matchWrapTop(24));

        return scroll;
    }

    private void refreshValues() {
        if (cumulativeValue == null || continuousValue == null || walkingValue == null || store == null) {
            return;
        }
        cumulativeValue.setText(ReminderThreshold.format(this, store.getReminderMinutes()));
        continuousValue.setText(store.isContinuousReminderEnabled()
                ? ReminderThreshold.format(this, store.getContinuousReminderMinutes())
                : getString(R.string.eye_care_reminders_disabled));
        if (!hasActivityRecognitionPermission()) {
            walkingValue.setText(R.string.eye_care_reminders_permission_needed);
        } else {
            walkingValue.setText(store.isWalkingReminderEnabled()
                    ? getString(R.string.eye_care_reminders_enabled)
                    : getString(R.string.eye_care_reminders_disabled));
        }
    }

    private LinearLayout settingCard(String titleText, String descText, TextView value, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setMinimumHeight(dp(110));
        card.setBackground(rounded(COLOR_SOFT, dp(20), COLOR_LINE, 1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(listener);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        card.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(22);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        copy.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText(descText);
        desc.setTextSize(15);
        desc.setTextColor(COLOR_MUTED);
        desc.setTypeface(AppFonts.bold(this));
        desc.setIncludeFontPadding(false);
        desc.setLineSpacing(0f, 1.12f);
        copy.addView(desc, matchWrapTop(10));

        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        card.addView(value, new LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void showCumulativeDialog() {
        showMinutesDialog(
                getString(R.string.eye_care_reminders_cumulative_title),
                getString(R.string.eye_care_reminders_cumulative_desc),
                store.getReminderMinutes(),
                false,
                true,
                (enabled, minutes) -> store.saveReminderSettings(minutes, true));
    }

    private void showContinuousDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle(getString(R.string.eye_care_reminders_continuous_title)), matchWrap());
        panel.addView(dialogMessage(getString(R.string.eye_care_reminders_continuous_desc)), matchWrapTop(12));

        Switch enabledSwitch = new Switch(this);
        enabledSwitch.setChecked(store.isContinuousReminderEnabled());
        panel.addView(switchRow(getString(R.string.eye_care_reminders_enable_reminder), enabledSwitch), matchWrapTop(22));

        LinearLayout field = minuteInputField(store.getContinuousReminderMinutes());
        panel.addView(field, matchWrapTop(28));

        List<ReminderExemptionPeriod> periods = new ArrayList<>(store.getContinuousReminderExemptions());
        Switch exemptionSwitch = new Switch(this);
        exemptionSwitch.setChecked(!periods.isEmpty());
        panel.addView(switchRow(getString(R.string.eye_care_reminders_exemption_title), exemptionSwitch), matchWrapTop(18));

        LinearLayout periodRows = new LinearLayout(this);
        periodRows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(periodRows, matchWrapTop(10));

        TextView add = actionButton(getString(R.string.eye_care_reminders_exemption_add), false);
        add.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        add.setPadding(dp(18), 0, dp(18), 0);
        panel.addView(add, fixedHeightTop(54, 8));

        Runnable refreshPeriods = () -> refreshExemptionRows(periodRows, periods, exemptionSwitch.isChecked());
        exemptionSwitch.setOnCheckedChangeListener((button, checked) -> {
            refreshPeriods.run();
            add.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        add.setOnClickListener(v -> showExemptionPeriodPickerDialog(
                ReminderExemptionPeriod.defaults(),
                period -> {
                    periods.add(period);
                    exemptionSwitch.setChecked(true);
                    refreshPeriods.run();
                }));
        refreshPeriods.run();

        LinearLayout actions = actionRow();
        TextView cancel = actionButton(getString(R.string.common_cancel), false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, weightedButtonParams(0, dp(6)));

        TextView save = actionButton(getString(R.string.common_save), true);
        save.setOnClickListener(v -> {
            EditText input = (EditText) field.getTag();
            int minutes = parseMinutes(input.getText().toString(), store.getContinuousReminderMinutes());
            store.saveContinuousReminderSettings(
                    enabledSwitch.isChecked(),
                    minutes,
                    exemptionSwitch.isChecked() ? periods : new ArrayList<>());
            refreshValues();
            startTrackerService();
            dialog.dismiss();
            Toast.makeText(this, R.string.eye_care_reminders_settings_saved, Toast.LENGTH_SHORT).show();
        });
        actions.addView(save, weightedButtonParams(dp(6), 0));
        panel.addView(actions, matchWrapTop(18));
        showDialog(dialog, panel);

        EditText input = (EditText) field.getTag();
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200L);
    }

    private void showWalkingDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle(getString(R.string.eye_care_reminders_walking_title)), matchWrap());
        panel.addView(dialogMessage(getString(R.string.eye_care_reminders_walking_desc)), matchWrapTop(12));

        Switch enabledSwitch = new Switch(this);
        enabledSwitch.setChecked(store.isWalkingReminderEnabled() && hasActivityRecognitionPermission());
        panel.addView(switchRow(getString(R.string.eye_care_reminders_enable_reminder), enabledSwitch), matchWrapTop(22));

        if (!hasActivityRecognitionPermission()) {
            TextView permission = new TextView(this);
            permission.setText(getString(R.string.eye_care_reminders_walking_permission_title) + "\n" + getString(R.string.eye_care_reminders_walking_permission_desc));
            permission.setTextSize(15);
            permission.setTextColor(COLOR_MUTED);
            permission.setTypeface(AppFonts.bold(this));
            permission.setLineSpacing(0f, 1.16f);
            permission.setPadding(dp(16), dp(14), dp(16), dp(14));
            permission.setBackground(rounded(COLOR_SOFT, dp(18), COLOR_LINE, 1));
            panel.addView(permission, matchWrapTop(14));
        }

        LinearLayout actions = actionRow();
        TextView cancel = actionButton(getString(R.string.common_cancel), false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, weightedButtonParams(0, dp(6)));

        TextView save = actionButton(hasActivityRecognitionPermission()
                ? getString(R.string.common_save)
                : getString(R.string.eye_care_reminders_walking_permission_action), true);
        save.setOnClickListener(v -> {
            boolean enabled = enabledSwitch.isChecked();
            if (enabled && !hasActivityRecognitionPermission()) {
                dialog.dismiss();
                requestActivityRecognitionPermission();
                return;
            }
            store.saveWalkingReminderEnabled(enabled);
            refreshValues();
            startTrackerService();
            dialog.dismiss();
            Toast.makeText(this, R.string.eye_care_reminders_settings_saved, Toast.LENGTH_SHORT).show();
        });
        actions.addView(save, weightedButtonParams(dp(6), 0));
        panel.addView(actions, matchWrapTop(22));
        showDialog(dialog, panel);
    }

    private void showMinutesDialog(
            String titleText,
            String messageText,
            int initialMinutes,
            boolean hasSwitch,
            boolean initialEnabled,
            ReminderSettingsSaver saver) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle(titleText), matchWrap());
        panel.addView(dialogMessage(messageText), matchWrapTop(12));

        Switch enabledSwitch = null;
        if (hasSwitch) {
            enabledSwitch = new Switch(this);
            enabledSwitch.setChecked(initialEnabled);
            panel.addView(switchRow(getString(R.string.eye_care_reminders_enable_reminder), enabledSwitch), matchWrapTop(22));
        }

        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(18), 0, dp(18), 0);
        field.setMinimumHeight(dp(74));
        field.setBackground(rounded(Color.rgb(249, 253, 251), dp(18), Color.rgb(207, 228, 220), 1));
        panel.addView(field, matchWrapTop(16));

        EditText input = new EditText(this);
        input.setText(String.valueOf(initialMinutes));
        input.setTextSize(34);
        input.setTypeface(AppFonts.bold(this));
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(COLOR_TEXT);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setImeOptions(EditorInfoCompat.actionDone());
        field.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView unit = new TextView(this);
        unit.setText(R.string.eye_care_reminders_minutes_input_hint);
        unit.setTextSize(20);
        unit.setTextColor(COLOR_MUTED);
        unit.setTypeface(AppFonts.bold(this));
        unit.setIncludeFontPadding(false);
        field.addView(unit, wrapWrap());

        TextView hint = dialogMessage(ReminderThreshold.formatEquivalent(this, initialMinutes));
        panel.addView(hint, matchWrapTop(10));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int minutes = parseMinutes(s.toString(), initialMinutes);
                hint.setText(ReminderThreshold.formatEquivalent(EyeCareRemindersActivity.this, minutes));
            }

            @Override public void afterTextChanged(Editable s) {
            }
        });

        LinearLayout actions = actionRow();
        TextView cancel = actionButton(getString(R.string.common_cancel), false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, weightedButtonParams(0, dp(6)));

        Switch finalEnabledSwitch = enabledSwitch;
        TextView save = actionButton(getString(R.string.common_save), true);
        save.setOnClickListener(v -> {
            boolean enabled = finalEnabledSwitch == null || finalEnabledSwitch.isChecked();
            int minutes = parseMinutes(input.getText().toString(), initialMinutes);
            saver.save(enabled, minutes);
            refreshValues();
            startTrackerService();
            dialog.dismiss();
            Toast.makeText(this, R.string.eye_care_reminders_settings_saved, Toast.LENGTH_SHORT).show();
        });
        actions.addView(save, weightedButtonParams(dp(6), 0));
        panel.addView(actions, matchWrapTop(22));
        showDialog(dialog, panel);

        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200L);
    }

    private LinearLayout minuteInputField(int initialMinutes) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(18), 0, dp(18), 0);
        field.setMinimumHeight(dp(68));
        field.setBackground(rounded(COLOR_SOFT, dp(18), COLOR_LINE, 1));

        TextView label = new TextView(this);
        label.setText(R.string.eye_care_reminders_reminder_time);
        label.setTextSize(20);
        label.setTextColor(COLOR_TEXT);
        label.setTypeface(AppFonts.bold(this));
        label.setIncludeFontPadding(false);
        field.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        EditText input = new EditText(this);
        input.setText(String.valueOf(initialMinutes));
        input.setTextSize(22);
        input.setTypeface(AppFonts.bold(this));
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(COLOR_MUTED);
        input.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        input.setPadding(0, 0, 0, 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setImeOptions(EditorInfoCompat.actionDone());
        field.addView(input, new LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView unit = new TextView(this);
        unit.setText(R.string.eye_care_reminders_minutes_input_hint);
        unit.setTextSize(20);
        unit.setTextColor(COLOR_MUTED);
        unit.setTypeface(AppFonts.bold(this));
        unit.setIncludeFontPadding(false);
        unit.setPadding(dp(6), 0, 0, 0);
        field.addView(unit, wrapWrap());
        field.setTag(input);
        field.setOnClickListener(v -> {
            input.requestFocus();
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        return field;
    }

    private void refreshExemptionRows(
            LinearLayout container,
            List<ReminderExemptionPeriod> periods,
            boolean enabled) {
        container.removeAllViews();
        container.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            return;
        }
        if (periods.isEmpty()) {
            TextView empty = dialogMessage(getString(R.string.eye_care_reminders_exemption_empty));
            empty.setTextSize(14);
            container.addView(empty, matchWrap());
            return;
        }
        for (int index = 0; index < periods.size(); index++) {
            int capturedIndex = index;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(38));
            row.setClickable(true);
            row.setOnClickListener(v -> showExemptionPeriodPickerDialog(
                    periods.get(capturedIndex),
                    period -> {
                        periods.set(capturedIndex, period);
                        refreshExemptionRows(container, periods, true);
                    }));

            TextView value = new TextView(this);
            value.setText(periods.get(index).summaryText());
            value.setTextSize(16);
            value.setTextColor(COLOR_TEXT);
            value.setTypeface(AppFonts.bold(this));
            value.setIncludeFontPadding(false);
            row.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView remove = new TextView(this);
            remove.setText("×");
            remove.setTextSize(20);
            remove.setTextColor(COLOR_MUTED);
            remove.setTypeface(AppFonts.bold(this));
            remove.setGravity(Gravity.CENTER);
            remove.setIncludeFontPadding(false);
            remove.setBackground(rounded(COLOR_BUTTON_SOFT, dp(999), Color.TRANSPARENT, 0));
            remove.setOnClickListener(v -> {
                periods.remove(capturedIndex);
                refreshExemptionRows(container, periods, !periods.isEmpty());
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(36), dp(34)));
            container.addView(row, matchWrap());
        }
    }

    private void showExemptionPeriodPickerDialog(
            ReminderExemptionPeriod current,
            ReminderPeriodSubmit submit) {
        ReminderExemptionPeriod initial = current == null
                ? ReminderExemptionPeriod.defaults()
                : current;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = dialogPanel();
        panel.setPadding(dp(18), dp(24), dp(18), dp(24));

        LinearLayout pickers = new LinearLayout(this);
        pickers.setOrientation(LinearLayout.HORIZONTAL);
        pickers.setGravity(Gravity.CENTER);

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

        LinearLayout actions = actionRow();
        TextView cancel = actionButton(getString(R.string.common_cancel), false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, weightedButtonParams(0, dp(6)));
        TextView confirm = actionButton(getString(R.string.common_confirm), true);
        confirm.setOnClickListener(v -> {
            ReminderExemptionPeriod period = ReminderExemptionPeriod.create(
                    startHour.value() * 60 + startMinute.value(),
                    endHour.value() * 60 + endMinute.value());
            if (!period.isValid()) {
                Toast.makeText(this, R.string.eye_care_reminders_exemption_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            submit.onSubmit(period);
            dialog.dismiss();
        });
        actions.addView(confirm, weightedButtonParams(dp(6), 0));
        panel.addView(actions, matchWrapTop(22));
        showDialog(dialog, panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private boolean hasActivityRecognitionPermission() {
        return Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= 29) {
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQUEST_ACTIVITY_RECOGNITION);
        } else {
            store.saveWalkingReminderEnabled(true);
            refreshValues();
            startTrackerService();
        }
    }

    private void startTrackerService() {
        Intent intent = new Intent(this, EyeTimeService.class).setAction(EyeTimeService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private LinearLayout dialogPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(22), dp(22), dp(22));
        panel.setBackground(rounded(Color.WHITE, dp(26), Color.rgb(229, 235, 232), 1));
        return panel;
    }

    private TextView dialogTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(28);
        view.setTextColor(COLOR_TEXT);
        view.setTypeface(AppFonts.bold(this));
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView dialogMessage(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(COLOR_MUTED);
        view.setTypeface(AppFonts.bold(this));
        view.setLineSpacing(0f, 1.15f);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout switchRow(String label, Switch switchView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setMinimumHeight(dp(70));
        row.setBackground(rounded(Color.rgb(249, 253, 251), dp(18), COLOR_LINE, 1));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(18);
        text.setTextColor(COLOR_TEXT);
        text.setTypeface(AppFonts.bold(this));
        text.setIncludeFontPadding(false);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(switchView, wrapWrap());
        return row;
    }

    private void showDialog(Dialog dialog, View content) {
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.88f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTypeface(AppFonts.bold(this));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinHeight(dp(54));
        button.setTextColor(primary ? Color.WHITE : COLOR_GREEN);
        button.setBackground(rounded(primary ? COLOR_GREEN : COLOR_BUTTON_SOFT, dp(999), Color.TRANSPARENT, 0));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView valueText() {
        TextView text = new TextView(this);
        text.setTextSize(20);
        text.setTextColor(COLOR_MUTED);
        text.setTypeface(AppFonts.bold(this));
        text.setSingleLine(true);
        text.setIncludeFontPadding(false);
        text.setAutoSizeTextTypeUniformWithConfiguration(16, 20, 1, TypedValue.COMPLEX_UNIT_SP);
        return text;
    }

    private int parseMinutes(String value, int fallback) {
        try {
            return ReminderThreshold.clampMinutes(Integer.parseInt(value.trim()));
        } catch (RuntimeException ex) {
            return ReminderThreshold.clampMinutes(fallback);
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapTop(int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams fixedHeightTop(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(height));
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
        return new LinearLayout.LayoutParams(dp(30), dp(116));
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
            root = new LinearLayout(EyeCareRemindersActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            previous = pickerNumber(false);
            current = pickerNumber(true);
            next = pickerNumber(false);
            View.OnTouchListener listener = (view, event) -> handleTouch(view, event);
            root.setOnTouchListener(listener);
            previous.setOnTouchListener(listener);
            current.setOnTouchListener(listener);
            next.setOnTouchListener(listener);
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

    private interface ReminderSettingsSaver {
        void save(boolean enabled, int minutes);
    }

    private interface ReminderPeriodSubmit {
        void onSubmit(ReminderExemptionPeriod period);
    }

    private static final class EditorInfoCompat {
        static int actionDone() {
            return android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
        }
    }
}
