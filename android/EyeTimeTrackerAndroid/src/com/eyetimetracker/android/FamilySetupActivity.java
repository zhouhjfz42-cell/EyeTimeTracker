package com.eyetimetracker.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
    public static final String EXTRA_SHOW_PARENT_BINDING = "com.eyetimetracker.android.SHOW_PARENT_BINDING";
    public static final String EXTRA_SHOW_CHILD_BINDING = "com.eyetimetracker.android.SHOW_CHILD_BINDING";

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
    private DeviceRole selectedRole = DeviceRole.PARENT_DEVICE;
    private String selectedAgeBand = ChildProfile.AGE_BAND_UNKNOWN;
    private String childNickname = "";
    private EditText nicknameInput;
    private EditText bindingCodeInput;
    private final StringBuilder passcode = new StringBuilder();
    private LinearLayout passcodeDots;
    private FamilyBindingInvite pendingInvite;
    private FamilyBindingLanServer bindingServer;
    private String activeBindingServerCode = "";
    private String bindingStatus = "";
    private boolean childJoinInProgress;
    private int step;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store = new EyeTimeStore(this);
        if (getIntent().getBooleanExtra(EXTRA_SHOW_PARENT_BINDING, false)) {
            prepareExistingParentBinding();
        } else if (getIntent().getBooleanExtra(EXTRA_SHOW_CHILD_BINDING, false)) {
            prepareChildBinding();
        }
        setContentView(buildUi());
        render();
    }

    private void prepareChildBinding() {
        selectedRole = DeviceRole.CHILD_DEVICE;
        step = 1;
    }

    private void prepareExistingParentBinding() {
        ChildProfile childProfile = store.getActiveChildProfile();
        if (childProfile == null || !childProfile.isValid()) {
            return;
        }
        FamilyBindingInvite invite = store.getFamilyBindingInvite();
        if (invite == null || !invite.isValid() || !childProfile.childId.equals(invite.childProfile.childId)) {
            invite = FamilyBindingInvite.create(null, childProfile, System.currentTimeMillis() / 1000L);
            store.saveFamilyBindingInvite(invite);
        }
        selectedRole = DeviceRole.PARENT_DEVICE;
        childNickname = childProfile.nickname;
        selectedAgeBand = childProfile.ageBand;
        pendingInvite = invite;
        step = 2;
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
        } else if (selectedRole == DeviceRole.CHILD_DEVICE) {
            renderChildBindingStep();
        } else if (step == 1) {
            renderParentSetupStep();
        } else {
            renderParentBindingStep();
        }
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView backButton = new TextView(this);
        backButton.setText("<");
        backButton.setTextSize(26);
        backButton.setTextColor(COLOR_GREEN);
        backButton.setTypeface(AppFonts.bold(this));
        backButton.setGravity(Gravity.CENTER);
        backButton.setIncludeFontPadding(false);
        backButton.setBackground(rounded(COLOR_SOFT, dp(999), COLOR_LINE, 1));
        backButton.setOnClickListener(v -> goBack());
        header.addView(backButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = new TextView(this);
        title.setText(R.string.family_setup_title);
        title.setTextSize(26);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(14);
        header.addView(title, titleParams);

        TextView stepLabel = new TextView(this);
        stepLabel.setText(getString(R.string.family_setup_step)
                .replace("{current}", String.valueOf(step + 1))
                .replace("{total}", String.valueOf(totalSteps())));
        stepLabel.setTextSize(15);
        stepLabel.setTextColor(COLOR_MUTED);
        stepLabel.setTypeface(AppFonts.bold(this));
        stepLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        stepLabel.setIncludeFontPadding(false);
        header.addView(stepLabel, wrapWrap());
        return header;
    }

    private View buildProgress() {
        LinearLayout progress = new LinearLayout(this);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        progress.setGravity(Gravity.CENTER_VERTICAL);
        int total = totalSteps();
        for (int i = 0; i < total; i++) {
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
        root.addView(primaryButton(getString(R.string.family_setup_continue), v -> {
            step = 1;
            render();
        }), matchWrapTop(28));
    }

    private void renderParentSetupStep() {
        addPageTitle(R.string.family_setup_child_title, R.string.family_setup_child_lead);

        root.addView(fieldLabel(getString(R.string.family_setup_child_nickname)), matchWrapTop(24));
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

        addPasscodeControls();

        root.addView(primaryButton(getString(R.string.family_setup_generate_binding), v -> {
            if (nicknameInput.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, R.string.family_setup_nickname_required, Toast.LENGTH_SHORT).show();
                return;
            }
            if (passcode.length() != 4) {
                Toast.makeText(this, R.string.family_setup_passcode_required, Toast.LENGTH_SHORT).show();
                return;
            }
            childNickname = nicknameInput.getText().toString().trim();
            saveParentAndShowBinding();
        }), matchWrapTop(24));
    }

    private void addPasscodeControls() {
        root.addView(fieldLabel(getString(R.string.family_setup_protection_title)), matchWrapTop(18));
        passcodeDots = new LinearLayout(this);
        passcodeDots.setGravity(Gravity.CENTER);
        passcodeDots.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(passcodeDots, matchWrapTop(12));
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
    }

    private void renderParentBindingStep() {
        FamilyBindingInvite invite = pendingInvite == null ? store.getFamilyBindingInvite() : pendingInvite;
        addPageTitle(R.string.family_binding_waiting_title, R.string.family_binding_waiting_desc);

        if (invite == null) {
            root.addView(helpText(getString(R.string.family_binding_missing_invite)), matchWrapTop(24));
            root.addView(primaryButton(getString(R.string.family_binding_enter_home), v -> openFamilyHome()), matchWrapTop(28));
            return;
        }
        startParentBindingServer(invite);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(22), dp(18), dp(22));
        card.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE, 1));

        TextView qr = new TextView(this);
        qr.setText("# # #\n ##  \n#  ##");
        qr.setTextSize(36);
        qr.setTextColor(COLOR_TEXT);
        qr.setGravity(Gravity.CENTER);
        qr.setTypeface(AppFonts.bold(this));
        qr.setIncludeFontPadding(false);
        card.addView(qr, matchWrap());

        TextView label = fieldLabel(getString(R.string.family_binding_code_label));
        label.setGravity(Gravity.CENTER);
        card.addView(label, matchWrapTop(18));

        TextView code = new TextView(this);
        code.setText(formatBindingCode(invite.bindingCode));
        code.setTextSize(40);
        code.setTextColor(COLOR_GREEN);
        code.setTypeface(AppFonts.bold(this));
        code.setGravity(Gravity.CENTER);
        code.setIncludeFontPadding(false);
        card.addView(code, matchWrapTop(8));
        root.addView(card, matchWrapTop(24));

        root.addView(helpText(getString(R.string.family_binding_once_help)), matchWrapTop(14));
        root.addView(disabledStatus(bindingStatus.isEmpty()
                ? getString(R.string.family_binding_waiting_status)
                : bindingStatus), matchWrapTop(22));
        root.addView(primaryButton(getString(R.string.family_binding_enter_home), v -> openFamilyHome()), matchWrapTop(12));
    }

    private void renderChildBindingStep() {
        addPageTitle(R.string.family_binding_child_title, R.string.family_binding_child_desc);

        bindingCodeInput = new EditText(this);
        bindingCodeInput.setTextColor(COLOR_TEXT);
        bindingCodeInput.setTextSize(22);
        bindingCodeInput.setSingleLine(true);
        bindingCodeInput.setHint(R.string.family_binding_input_hint);
        bindingCodeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        bindingCodeInput.setGravity(Gravity.CENTER);
        bindingCodeInput.setBackground(rounded(Color.rgb(244, 250, 247), dp(8), COLOR_LINE, 1));
        bindingCodeInput.setPadding(dp(14), 0, dp(14), 0);
        root.addView(bindingCodeInput, fixedHeightTop(56, 28));

        root.addView(secondaryButton(getString(R.string.family_binding_scan), v ->
                Toast.makeText(this, R.string.family_binding_scan_unavailable, Toast.LENGTH_SHORT).show()), matchWrapTop(14));

        FamilyBindingInvite invite = store.getFamilyBindingInvite();
        if (invite != null) {
            root.addView(bindingSummary(invite), matchWrapTop(18));
        } else {
            root.addView(helpText(getString(R.string.family_binding_missing_invite)), matchWrapTop(18));
        }

        root.addView(primaryButton(getString(R.string.family_binding_join), v -> joinFamilyByCode()), matchWrapTop(24));
    }

    private View roleOption(DeviceRole role, String optionTitle, String description) {
        boolean selected = selectedRole == role;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(selected ? COLOR_SOFT : Color.WHITE, dp(8), selected ? COLOR_GREEN : COLOR_LINE, 1));
        card.setOnClickListener(v -> {
            selectedRole = role;
            step = 0;
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
            if (nicknameInput != null) {
                childNickname = nicknameInput.getText().toString();
            }
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

    private void saveParentAndShowBinding() {
        try {
            long now = System.currentTimeMillis() / 1000L;
            FamilySetupDraft draft = FamilySetupDraft.createParent(
                    childNickname,
                    selectedAgeBand,
                    passcode.toString(),
                    now);
            pendingInvite = FamilyBindingInvite.create(null, draft.childProfile, now);
            store.saveProductMode(draft.productMode);
            store.saveDeviceRole(draft.deviceRole);
            store.saveChildProfiles(Collections.singletonList(draft.childProfile), draft.childProfile.childId);
            store.saveParentPasscode(draft.passcode);
            store.saveFamilyBindingInvite(pendingInvite);
            Toast.makeText(this, R.string.family_setup_saved, Toast.LENGTH_SHORT).show();
            step = 2;
            render();
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, R.string.family_setup_nickname_required, Toast.LENGTH_SHORT).show();
        }
    }

    private void joinFamilyByCode() {
        if (childJoinInProgress) {
            return;
        }
        String enteredCode = bindingCodeInput == null ? "" : bindingCodeInput.getText().toString();
        try {
            FamilyBindingDraft draft = FamilyBindingDraft.create(enteredCode);
            boolean consumed = store.consumeFamilyBindingCode(draft.bindingCode);
            if (consumed) {
                openFamilyHome();
            } else {
                joinFamilyOverLan(draft.bindingCode);
            }
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, R.string.family_binding_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private void joinFamilyOverLan(String bindingCode) {
        childJoinInProgress = true;
        Toast.makeText(this, R.string.family_binding_searching, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            FamilyBindingLanClient.JoinResult result = new FamilyBindingLanClient()
                    .join(store.getDeviceId(), bindingCode);
            runOnUiThread(() -> {
                childJoinInProgress = false;
                if (result.success) {
                    store.saveRemoteFamilyChildBinding(result.familyId, result.childProfile, result.parentPasscode);
                    Toast.makeText(this, R.string.family_binding_joined, Toast.LENGTH_SHORT).show();
                    openFamilyHome();
                    return;
                }
                Toast.makeText(this, R.string.family_binding_not_found, Toast.LENGTH_LONG).show();
            });
        }, "FamilyBindingLanJoin").start();
    }

    private void openFamilyHome() {
        startActivity(new Intent(this, FamilyHomeActivity.class));
        finish();
    }

    private void startParentBindingServer(FamilyBindingInvite invite) {
        if (invite == null || !invite.isValid()) {
            return;
        }
        if (bindingServer != null && invite.bindingCode.equals(activeBindingServerCode)) {
            return;
        }
        stopParentBindingServer();
        bindingServer = new FamilyBindingLanServer();
        activeBindingServerCode = invite.bindingCode;
        bindingStatus = getString(R.string.family_binding_waiting_status);
        bindingServer.start(invite, store.getDeviceId(), store.getParentPasscode(), new FamilyBindingLanServer.Listener() {
            @Override public void onStarted(int port) {
                runOnUiThread(() -> bindingStatus = getString(R.string.family_binding_waiting_status));
            }

            @Override public void onJoined(String childDeviceId) {
                runOnUiThread(() -> {
                    store.saveFamilyChildDeviceBinding(childDeviceId);
                    bindingStatus = getString(R.string.family_binding_child_joined_status);
                    render();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    bindingStatus = message == null || message.trim().isEmpty()
                            ? getString(R.string.family_binding_invalid)
                            : message;
                    render();
                });
            }
        });
    }

    private void stopParentBindingServer() {
        if (bindingServer != null) {
            bindingServer.stop();
            bindingServer = null;
        }
        activeBindingServerCode = "";
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

    private View bindingSummary(FamilyBindingInvite invite) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(Color.WHITE, dp(8), COLOR_LINE, 1));

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView label = new TextView(this);
        label.setText(R.string.family_binding_will_join);
        label.setTextSize(17);
        label.setTextColor(COLOR_TEXT);
        label.setTypeface(AppFonts.bold(this));
        label.setIncludeFontPadding(false);
        textBlock.addView(label, matchWrap());

        TextView child = helpText(invite.childProfile.nickname);
        textBlock.addView(child, matchWrapTop(6));

        TextView role = new TextView(this);
        role.setText(R.string.family_role_child);
        role.setTextSize(15);
        role.setTextColor(COLOR_TEXT);
        role.setTypeface(AppFonts.bold(this));
        role.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        role.setIncludeFontPadding(false);
        card.addView(role, wrapWrap());
        return card;
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

    private TextView secondaryButton(String label, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTextColor(COLOR_GREEN);
        button.setTypeface(AppFonts.bold(this));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setBackground(rounded(COLOR_SOFT, dp(999), COLOR_LINE, 1));
        button.setMinHeight(dp(54));
        button.setAutoSizeTextTypeUniformWithConfiguration(13, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView disabledStatus(String label) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(15);
        text.setTextColor(COLOR_DISABLED_TEXT);
        text.setTypeface(AppFonts.bold(this));
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        text.setBackground(rounded(COLOR_DISABLED, dp(999), Color.TRANSPARENT, 0));
        text.setMinHeight(dp(46));
        return text;
    }

    private String formatBindingCode(String code) {
        String normalized = FamilyBindingInvite.normalizeBindingCode(code);
        if (normalized.length() == 6) {
            return normalized.substring(0, 3) + " " + normalized.substring(3);
        }
        return normalized;
    }

    private int totalSteps() {
        return selectedRole == DeviceRole.CHILD_DEVICE ? 2 : 3;
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

    @Override protected void onDestroy() {
        stopParentBindingServer();
        super.onDestroy();
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
