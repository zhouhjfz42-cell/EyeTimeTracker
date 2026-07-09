package com.eyetimetracker.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;

public final class FamilySetupActivity extends Activity {
    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(102, 112, 133);
    private static final int COLOR_GREEN = Color.rgb(22, 166, 125);
    private static final int COLOR_SOFT = Color.rgb(237, 248, 244);
    private static final int COLOR_LINE = Color.rgb(223, 240, 233);
    private static final int COLOR_DISABLED = Color.rgb(230, 235, 233);
    private static final int COLOR_DISABLED_TEXT = Color.rgb(152, 162, 160);

    private EyeTimeStore store;
    private LinearLayout root;
    private LinearLayout progress;
    private TextView stepLabel;
    private TextView title;
    private TextView backButton;
    private int step;
    private DeviceRole selectedRole = DeviceRole.PARENT_DEVICE;
    private String selectedAgeBand = ChildProfile.AGE_BAND_UNKNOWN;
    private String childNickname = "";
    private EditText nicknameInput;
    private final StringBuilder passcode = new StringBuilder();
    private LinearLayout passcodeDots;
    private TextView completeButton;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store = new EyeTimeStore(this);
        setContentView(buildUi());
        render();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void render() {
        root.removeAllViews();
        root.addView(buildHeader(), matchWrap());
        root.addView(buildProgress(), matchWrapTop(16));
        if (step == 0) {
            renderRoleStep();
        } else if (step == 1) {
            renderChildStep();
        } else {
            renderPasscodeStep();
        }
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        backButton = new TextView(this);
        backButton.setText("‹");
        backButton.setTextSize(26);
        backButton.setTextColor(COLOR_GREEN);
        backButton.setTypeface(AppFonts.bold(this));
        backButton.setGravity(Gravity.CENTER);
        backButton.setIncludeFontPadding(false);
        backButton.setBackground(rounded(COLOR_SOFT, dp(999), COLOR_LINE, 1));
        backButton.setOnClickListener(v -> goBack());
        header.addView(backButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        title = new TextView(this);
        title.setText(R.string.family_setup_title);
        title.setTextSize(26);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(14);
        header.addView(title, titleParams);

        stepLabel = new TextView(this);
        stepLabel.setText(getString(R.string.family_setup_step).replace("{current}", String.valueOf(step + 1)).replace("{total}", "3"));
        stepLabel.setTextSize(15);
        stepLabel.setTextColor(COLOR_MUTED);
        stepLabel.setTypeface(AppFonts.bold(this));
        stepLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        stepLabel.setIncludeFontPadding(false);
        header.addView(stepLabel, wrapWrap());
        return header;
    }

    private View buildProgress() {
        progress = new LinearLayout(this);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        progress.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 3; i++) {
            View segment = new View(this);
            segment.setBackground(rounded(i <= step ? COLOR_GREEN : Color.rgb(229, 238, 234), dp(999), Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(5), 1f);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            progress.addView(segment, params);
        }
        return progress;
    }

    private void renderRoleStep() {
        addPageTitle(R.string.family_setup_role_title, R.string.family_setup_role_lead);
        root.addView(roleOption(
                DeviceRole.PARENT_DEVICE,
                getString(R.string.family_setup_role_parent),
                getString(R.string.family_setup_role_parent_desc)), matchWrapTop(24));
        root.addView(roleOption(
                DeviceRole.CHILD_DEVICE,
                getString(R.string.family_setup_role_child),
                getString(R.string.family_setup_role_child_desc)), matchWrapTop(12));
        root.addView(primaryButton(getString(R.string.family_setup_continue_child), v -> {
            step = 1;
            render();
        }), matchWrapTop(28));
    }

    private void renderChildStep() {
        addPageTitle(R.string.family_setup_child_title, R.string.family_setup_child_lead);

        TextView nicknameLabel = fieldLabel(getString(R.string.family_setup_child_nickname));
        root.addView(nicknameLabel, matchWrapTop(24));
        nicknameInput = new EditText(this);
        nicknameInput.setTextColor(COLOR_TEXT);
        nicknameInput.setTextSize(17);
        nicknameInput.setSingleLine(true);
        nicknameInput.setHint(R.string.family_setup_child_nickname_hint);
        nicknameInput.setText(childNickname);
        nicknameInput.setSelection(nicknameInput.getText().length());
        nicknameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nicknameInput.setBackground(rounded(Color.rgb(244, 250, 247), dp(8), COLOR_LINE, 1));
        nicknameInput.setPadding(dp(14), 0, dp(14), 0);
        root.addView(nicknameInput, fixedHeightTop(50, 8));

        root.addView(fieldLabel(getString(R.string.family_setup_age_band)), matchWrapTop(18));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        chips.addView(ageRow("3-6", "7-9", "10-12"), matchWrap());
        chips.addView(ageRow("13-15", "16-17", ChildProfile.AGE_BAND_UNKNOWN), matchWrapTop(8));
        root.addView(chips, matchWrapTop(8));

        root.addView(primaryButton(getString(R.string.family_setup_continue_protection), v -> {
            if (nicknameInput.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, R.string.family_setup_nickname_required, Toast.LENGTH_SHORT).show();
                return;
            }
            childNickname = nicknameInput.getText().toString().trim();
            step = 2;
            render();
        }), matchWrapTop(26));
    }

    private void renderPasscodeStep() {
        addPageTitle(R.string.family_setup_protection_title, R.string.family_setup_protection_lead);
        passcodeDots = new LinearLayout(this);
        passcodeDots.setGravity(Gravity.CENTER);
        passcodeDots.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(passcodeDots, matchWrapTop(28));
        refreshPasscodeDots();

        GridLayout keypad = new GridLayout(this);
        keypad.setColumnCount(3);
        String[] keys = { "1", "2", "3", "4", "5", "6", "7", "8", "9", getString(R.string.family_setup_clear), "0", getString(R.string.family_setup_delete) };
        for (String key : keys) {
            TextView keyView = keypadButton(key);
            keyView.setOnClickListener(v -> onKeyPressed(((TextView) v).getText().toString()));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(48);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            keypad.addView(keyView, params);
        }
        root.addView(keypad, matchWrapTop(24));

        TextView notice = helpText(getString(R.string.family_setup_protection_notice));
        notice.setGravity(Gravity.CENTER);
        root.addView(notice, matchWrapTop(14));

        completeButton = primaryButton(getString(R.string.family_setup_complete), v -> saveAndFinish());
        root.addView(completeButton, matchWrapTop(24));
        refreshCompleteButton();
    }

    private View roleOption(DeviceRole role, String optionTitle, String description) {
        boolean selected = selectedRole == role;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(selected ? COLOR_SOFT : Color.WHITE, dp(8), selected ? COLOR_GREEN : COLOR_LINE, 1));
        card.setOnClickListener(v -> {
            selectedRole = role;
            render();
        });

        TextView titleView = new TextView(this);
        titleView.setText(optionTitle);
        titleView.setTextSize(17);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(AppFonts.bold(this));
        titleView.setIncludeFontPadding(false);
        card.addView(titleView, matchWrap());

        TextView descView = helpText(description);
        card.addView(descView, matchWrapTop(8));
        return card;
    }

    private LinearLayout ageRow(String first, String second, String third) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(ageChip(first), weightedChip(0, dp(4)));
        row.addView(ageChip(second), weightedChip(dp(4), dp(4)));
        row.addView(ageChip(third), weightedChip(dp(4), 0));
        return row;
    }

    private TextView ageChip(String ageBand) {
        boolean unknown = ChildProfile.AGE_BAND_UNKNOWN.equals(ageBand);
        String label = unknown ? getString(R.string.family_setup_age_skip) : ageBand;
        boolean selected = ChildProfile.normalizeAgeBand(ageBand).equals(selectedAgeBand);
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(14);
        chip.setTextColor(selected ? Color.WHITE : COLOR_MUTED);
        chip.setTypeface(AppFonts.bold(this));
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setBackground(rounded(selected ? COLOR_GREEN : Color.WHITE, dp(999), COLOR_LINE, 1));
        chip.setOnClickListener(v -> {
            selectedAgeBand = ChildProfile.normalizeAgeBand(ageBand);
            render();
        });
        return chip;
    }

    private TextView keypadButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(label.length() == 1 ? 18 : 14);
        button.setTextColor(COLOR_TEXT);
        button.setTypeface(AppFonts.bold(this));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE, 1));
        return button;
    }

    private void onKeyPressed(String key) {
        if (getString(R.string.family_setup_clear).equals(key)) {
            passcode.setLength(0);
        } else if (getString(R.string.family_setup_delete).equals(key)) {
            if (passcode.length() > 0) {
                passcode.deleteCharAt(passcode.length() - 1);
            }
        } else if (passcode.length() < 4 && key.matches("\\d")) {
            passcode.append(key);
        }
        refreshPasscodeDots();
        refreshCompleteButton();
    }

    private void refreshPasscodeDots() {
        if (passcodeDots == null) {
            return;
        }
        passcodeDots.removeAllViews();
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            dot.setBackground(oval(i < passcode.length() ? COLOR_GREEN : COLOR_DISABLED));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(18), dp(18));
            if (i > 0) {
                params.leftMargin = dp(12);
            }
            passcodeDots.addView(dot, params);
        }
    }

    private void refreshCompleteButton() {
        if (completeButton == null) {
            return;
        }
        boolean enabled = passcode.length() == 4;
        completeButton.setEnabled(enabled);
        completeButton.setTextColor(enabled ? Color.WHITE : COLOR_DISABLED_TEXT);
        completeButton.setBackground(rounded(enabled ? COLOR_GREEN : COLOR_DISABLED, dp(999), Color.TRANSPARENT, 0));
    }

    private void saveAndFinish() {
        if (passcode.length() != 4) {
            Toast.makeText(this, R.string.family_setup_passcode_required, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            FamilySetupDraft draft = FamilySetupDraft.create(
                    selectedRole,
                    childNickname,
                    selectedAgeBand,
                    passcode.toString(),
                    System.currentTimeMillis() / 1000L);
            store.saveProductMode(draft.productMode);
            store.saveDeviceRole(draft.deviceRole);
            store.saveChildProfiles(Collections.singletonList(draft.childProfile), draft.childProfile.childId);
            store.saveParentPasscode(draft.passcode);
            Toast.makeText(this, R.string.family_setup_saved, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, FamilyHomeActivity.class));
            finish();
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, R.string.family_setup_nickname_required, Toast.LENGTH_SHORT).show();
        }
    }

    private void addPageTitle(int titleId, int leadId) {
        TextView titleView = new TextView(this);
        titleView.setText(titleId);
        titleView.setTextSize(32);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(AppFonts.bold(this));
        titleView.setIncludeFontPadding(false);
        titleView.setLineSpacing(0f, 1.03f);
        root.addView(titleView, matchWrapTop(28));

        TextView leadView = helpText(getString(leadId));
        leadView.setTextSize(17);
        root.addView(leadView, matchWrapTop(14));
    }

    private TextView fieldLabel(String label) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(14);
        text.setTextColor(COLOR_MUTED);
        text.setTypeface(AppFonts.bold(this));
        text.setIncludeFontPadding(false);
        return text;
    }

    private TextView helpText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(14);
        text.setTextColor(COLOR_MUTED);
        AppFonts.apply(text, false);
        text.setLineSpacing(0f, 1.18f);
        text.setIncludeFontPadding(false);
        return text;
    }

    private TextView primaryButton(String label, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTextColor(Color.WHITE);
        button.setTypeface(AppFonts.bold(this));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setBackground(rounded(COLOR_GREEN, dp(999), Color.TRANSPARENT, 0));
        button.setMinHeight(dp(54));
        button.setAutoSizeTextTypeUniformWithConfiguration(13, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setOnClickListener(listener);
        return button;
    }

    private void goBack() {
        if (step <= 0) {
            finish();
            return;
        }
        step--;
        render();
    }

    @Override public void onBackPressed() {
        goBack();
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

    private LinearLayout.LayoutParams fixedHeightTop(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height));
        params.topMargin = dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedChip(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        params.leftMargin = left;
        params.rightMargin = right;
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
