package com.eyetimetracker.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ReminderActivity extends Activity {
    public static final String EXTRA_REMINDER_MINUTES = "reminder_minutes";
    public static final String EXTRA_REMINDER_REPEAT = "reminder_repeat";
    public static final String EXTRA_REMINDER_STEP = "reminder_step";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_ACCENT_COLOR = "accent_color";

    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(102, 112, 133);
    private static final int COLOR_GREEN = Color.rgb(22, 166, 125);
    private static final int COLOR_LINE = Color.rgb(223, 240, 233);

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        int reminderMinutes = ReminderThreshold.clampMinutes(
                getIntent().getIntExtra(EXTRA_REMINDER_MINUTES, ReminderThreshold.DEFAULT_MINUTES));
        boolean repeatReminder = getIntent().getBooleanExtra(EXTRA_REMINDER_REPEAT, false);
        int reminderStep = Math.max(0, getIntent().getIntExtra(EXTRA_REMINDER_STEP, 0));
        String customTitle = getIntent().getStringExtra(EXTRA_TITLE);
        String customMessage = getIntent().getStringExtra(EXTRA_MESSAGE);
        boolean hasCustomContent = customTitle != null && !customTitle.trim().isEmpty();
        String title = hasCustomContent
                ? customTitle
                : ReminderAlert.cumulativeTitle(this, reminderMinutes, reminderStep);
        String message = customMessage == null || customMessage.trim().isEmpty()
                ? ReminderAlert.message(this, reminderMinutes, repeatReminder, reminderStep)
                : customMessage;
        // 连续用眼=绿色，累计用眼=橙红；未指定时按累计（本页默认入口是累计提醒广播）
        int accent = getIntent().getIntExtra(
                EXTRA_ACCENT_COLOR,
                hasCustomContent ? COLOR_GREEN : ReminderNotificationProfile.ACCENT_CUMULATIVE);
        setContentView(buildUi(title, message, accent));
    }

    private LinearLayout buildUi(String titleText, String messageText, int accentColor) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(COLOR_BG);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(24), dp(26), dp(24));
        card.setBackground(rounded(Color.WHITE, dp(28), COLOR_LINE, 1));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        android.view.View band = new android.view.View(this);
        band.setBackground(rounded(accentColor, dp(999), Color.TRANSPARENT, 0));
        card.addView(band, new LinearLayout.LayoutParams(dp(56), dp(8)));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(28);
        title.setTextColor(accentColor);
        title.setTypeface(AppFonts.bold(this));
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(14);
        card.addView(title, titleParams);

        TextView message = new TextView(this);
        message.setText(messageText);
        message.setTextSize(18);
        message.setTextColor(COLOR_MUTED);
        message.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(22);
        card.addView(message, messageParams);

        TextView okButton = new TextView(this);
        okButton.setText(R.string.common_got_it);
        okButton.setTextSize(18);
        okButton.setTypeface(AppFonts.bold(this));
        okButton.setGravity(Gravity.CENTER);
        okButton.setTextColor(Color.WHITE);
        okButton.setMinHeight(dp(56));
        okButton.setBackground(rounded(accentColor, dp(999), Color.TRANSPARENT, 0));
        okButton.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(28);
        card.addView(okButton, buttonParams);

        return root;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
