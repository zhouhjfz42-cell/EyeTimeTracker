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

    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(102, 112, 133);
    private static final int COLOR_GREEN = Color.rgb(22, 166, 125);
    private static final int COLOR_LINE = Color.rgb(223, 240, 233);

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        int reminderMinutes = ReminderThreshold.clampMinutes(
                getIntent().getIntExtra(EXTRA_REMINDER_MINUTES, ReminderThreshold.DEFAULT_MINUTES));
        setContentView(buildUi(reminderMinutes));
    }

    private LinearLayout buildUi(int reminderMinutes) {
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

        TextView title = new TextView(this);
        title.setText(ReminderAlert.title());
        title.setTextSize(28);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        card.addView(title, matchWrap());

        TextView message = new TextView(this);
        message.setText(ReminderAlert.message(reminderMinutes));
        message.setTextSize(18);
        message.setTextColor(COLOR_MUTED);
        message.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(22);
        card.addView(message, messageParams);

        TextView okButton = new TextView(this);
        okButton.setText("\u6211\u77e5\u9053\u4e86");
        okButton.setTextSize(18);
        okButton.setTypeface(Typeface.DEFAULT_BOLD);
        okButton.setGravity(Gravity.CENTER);
        okButton.setTextColor(Color.WHITE);
        okButton.setMinHeight(dp(56));
        okButton.setBackground(rounded(COLOR_GREEN, dp(999), Color.TRANSPARENT, 0));
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
