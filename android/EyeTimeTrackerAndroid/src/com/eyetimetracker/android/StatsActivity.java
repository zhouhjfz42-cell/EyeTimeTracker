package com.eyetimetracker.android;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class StatsActivity extends Activity {
    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(101, 114, 137);
    private static final int COLOR_GREEN = Color.rgb(22, 163, 127);
    private static final int COLOR_BLUE = Color.rgb(79, 141, 247);
    private static final int COLOR_YELLOW = Color.rgb(240, 184, 58);
    private static final int COLOR_RED = Color.rgb(233, 104, 104);
    private static final int COLOR_SOFT = Color.rgb(238, 249, 245);
    private static final int COLOR_LINE = Color.rgb(217, 238, 231);

    private EyeTimeStore store;
    private LocalDate selectedDay;
    private LocalDate selectedWeekStart;
    private LocalDate selectedMonthStart;
    private LocalDate rangeStart;
    private LocalDate rangeEnd;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store = new EyeTimeStore(this);
        LocalDate today = LocalDate.now();
        selectedDay = today;
        selectedWeekStart = startOfWeek(today);
        selectedMonthStart = today.withDayOfMonth(1);
        rangeStart = selectedMonthStart;
        rangeEnd = today;
        setContentView(buildUi());
    }

    private View buildUi() {
        LocalDate today = LocalDate.now();
        DailySummary todaySummary = store.getDay(selectedDay);
        List<DailySummary> week = store.getDays(selectedWeekStart, selectedWeekStart.plusDays(6));
        LocalDate monthEnd = selectedMonthStart.getYear() == today.getYear() && selectedMonthStart.getMonthValue() == today.getMonthValue()
                ? today
                : selectedMonthStart.plusMonths(1).minusDays(1);
        List<DailySummary> month = store.getDays(selectedMonthStart, monthEnd);
        LocalDate firstRange = rangeStart.isAfter(rangeEnd) ? rangeEnd : rangeStart;
        LocalDate lastRange = rangeStart.isAfter(rangeEnd) ? rangeStart : rangeEnd;
        List<Long> sessions = collectSessions(store.getDays(firstRange, lastRange));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(root);

        TextView title = text("统计", 34, COLOR_TEXT, true);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrap());
        TextView subtitle = text("看趋势、分布和连续使用情况", 16, COLOR_MUTED, false);
        root.addView(subtitle, matchWrapTop(10));

        LinearLayout dayPanel = panel();
        root.addView(dayPanel, matchWrapTop(24));
        TextView dayPill = addPanelHead(dayPanel, "单日情况", dateLabel(selectedDay, today) + " ▾");
        dayPill.setOnClickListener(v -> showDateSheet(DatePickMode.DAY));
        GridLayout metrics = new GridLayout(this);
        metrics.setColumnCount(2);
        dayPanel.addView(metrics, matchWrapTop(16));
        addCard(metrics, metricCard("今日用眼", DurationFormatter.format(todaySummary.totalSeconds), colorForToday(todaySummary.totalSeconds)), 0, 0);
        addCard(metrics, metricCard("最长连续", DurationFormatter.format(longestSession(todaySummary)), COLOR_YELLOW), 0, 1);
        addCard(metrics, metricCard("电脑 / 手机", todaySummary.totalSeconds > 0 ? "0% / 100%" : "0% / 0%", COLOR_TEXT), 1, 0);
        addCard(metrics, metricCard("提醒触发", Math.max(0, todaySummary.lastReminderStep) + "次", COLOR_TEXT), 1, 1);

        HourlyHeatView heatView = new HourlyHeatView(this);
        heatView.setHourlySeconds(todaySummary.hourlySeconds);
        LinearLayout.LayoutParams heatParams = new LinearLayout.LayoutParams(dp(250), dp(250));
        heatParams.gravity = Gravity.CENTER_HORIZONTAL;
        heatParams.topMargin = dp(18);
        dayPanel.addView(heatView, heatParams);

        GridLayout insightGrid = new GridLayout(this);
        insightGrid.setColumnCount(3);
        dayPanel.addView(insightGrid, matchWrapTop(14));
        addCard(insightGrid, insightInfoCard("最集中", peakHour(todaySummary.hourlySeconds)), 0, 0);
        addCard(insightGrid, insightInfoCard("夜间", DurationFormatter.format(nightSeconds(todaySummary.hourlySeconds))), 0, 1);
        addCard(insightGrid, insightInfoCard("最长连续", DurationFormatter.format(longestSession(todaySummary))), 0, 2);

        LinearLayout weekPanel = panel();
        root.addView(weekPanel, matchWrapTop(18));
        TextView weekPill = addPanelHead(weekPanel, "本周用眼", weekLabel(selectedWeekStart, today) + " ▾");
        weekPill.setOnClickListener(v -> showDateSheet(DatePickMode.WEEK));
        WeekBarView weekView = new WeekBarView(this);
        weekView.setSummaries(week);
        weekPanel.addView(weekView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210)));
        weekPanel.addView(note("本周合计 " + DurationFormatter.format(sum(week))), matchWrapTop(8));

        LinearLayout monthPanel = panel();
        root.addView(monthPanel, matchWrapTop(18));
        TextView monthPill = addPanelHead(monthPanel, "月度趋势", monthLabel(selectedMonthStart, today) + " ▾");
        monthPill.setOnClickListener(v -> showDateSheet(DatePickMode.MONTH));
        MonthTrendView monthView = new MonthTrendView(this);
        monthView.setSummaries(month);
        monthPanel.addView(monthView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));
        monthPanel.addView(note("本月已记录 " + activeDays(month) + " 天"), matchWrapTop(8));

        LinearLayout sessionsPanel = panel();
        root.addView(sessionsPanel, matchWrapTop(18));
        TextView rangePill = addPanelHead(sessionsPanel, "连续使用分析", compactDate(rangeStart) + " 至 " + compactDate(rangeEnd) + " ▾");
        rangePill.setOnClickListener(v -> showDateSheet(DatePickMode.RANGE));
        ContinuousBandsView bandsView = new ContinuousBandsView(this);
        bandsView.setSessions(sessions);
        sessionsPanel.addView(bandsView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));
        sessionsPanel.addView(note(sessions.isEmpty()
                ? "暂无连续使用片段。"
                : "最长连续 " + DurationFormatter.format(max(sessions)) + "，建议减少 45 分钟以上的连续使用。"), matchWrapTop(8));

        return scroll;
    }

    private TextView addPanelHead(LinearLayout parent, String title, String pillText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row, matchWrap());
        TextView titleView = text(title, 22, COLOR_TEXT, true);
        row.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView pill = text(pillText, 13, COLOR_TEXT, true);
        pill.setGravity(Gravity.CENTER);
        pill.setMinHeight(dp(30));
        pill.setPadding(dp(12), 0, dp(12), 0);
        pill.setBackground(rounded(COLOR_SOFT, dp(999), COLOR_LINE, 1));
        pill.setClickable(true);
        pill.setFocusable(true);
        row.addView(pill, wrapWrap());
        return pill;
    }

    private enum DatePickMode {
        DAY,
        WEEK,
        MONTH,
        RANGE
    }

    private void showDateSheet(DatePickMode mode) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(22));
        panel.setBackground(rounded(Color.WHITE, dp(28), COLOR_LINE, 1));

        String hintText = mode == DatePickMode.WEEK
                ? "点击任意一天选择整周"
                : mode == DatePickMode.MONTH
                    ? "点击任意一天选择整月"
                    : mode == DatePickMode.RANGE
                        ? "先选开始日期，再选结束日期"
                        : "点击日期选择单日";

        TextView hint = text(hintText, 14, COLOR_MUTED, false);
        panel.addView(hint, matchWrap());

        LocalDate initial = mode == DatePickMode.DAY
                ? selectedDay
                : mode == DatePickMode.WEEK
                    ? selectedWeekStart
                    : mode == DatePickMode.MONTH
                        ? selectedMonthStart
                        : rangeStart;
        final LocalDate[] displayMonth = new LocalDate[] { initial.withDayOfMonth(1) };
        final boolean[] selectingRangeEnd = new boolean[] { false };

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(head, matchWrapTop(14));

        TextView prev = calendarNav("‹");
        head.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView monthTitle = text("", 22, COLOR_TEXT, true);
        monthTitle.setGravity(Gravity.CENTER);
        head.addView(monthTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView next = calendarNav("›");
        head.addView(next, new LinearLayout.LayoutParams(dp(42), dp(42)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        panel.addView(grid, matchWrapTop(12));

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            monthTitle.setText(displayMonth[0].getYear() + "年 " + displayMonth[0].getMonthValue() + "月");
            renderCalendarGrid(grid, mode, displayMonth[0], dialog, selectingRangeEnd, render[0]);
        };

        prev.setOnClickListener(v -> {
            displayMonth[0] = displayMonth[0].minusMonths(1);
            render[0].run();
        });
        next.setOnClickListener(v -> {
            displayMonth[0] = displayMonth[0].plusMonths(1);
            render[0].run();
        });

        TextView cancel = text("取消", 16, COLOR_TEXT, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinHeight(dp(44));
        cancel.setBackground(rounded(Color.rgb(239, 242, 244), dp(999), Color.TRANSPARENT, 0));
        cancel.setOnClickListener(v -> dialog.dismiss());
        panel.addView(cancel, matchWrapTop(16));

        dialog.setContentView(panel);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setGravity(Gravity.BOTTOM);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        render[0].run();
    }

    private TextView calendarNav(String value) {
        TextView view = text(value, 26, COLOR_MUTED, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(COLOR_SOFT, dp(999), COLOR_LINE, 1));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void renderCalendarGrid(GridLayout grid, DatePickMode mode, LocalDate monthStart, Dialog dialog, boolean[] selectingRangeEnd, Runnable rerender) {
        grid.removeAllViews();
        String[] weekdays = { "一", "二", "三", "四", "五", "六", "日" };
        for (String weekday : weekdays) {
            TextView label = text(weekday, 13, COLOR_MUTED, false);
            label.setGravity(Gravity.CENTER);
            grid.addView(label, calendarCellParams());
        }

        LocalDate first = monthStart.minusDays(monthStart.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        for (int i = 0; i < 42; i++) {
            LocalDate date = first.plusDays(i);
            TextView cell = text(String.valueOf(date.getDayOfMonth()), 16, colorForDateCell(mode, date, monthStart), isSelectedDateCell(mode, date));
            cell.setGravity(Gravity.CENTER);
            cell.setMinHeight(dp(42));
            if (isHighlightedDateCell(mode, date)) {
                cell.setBackground(rounded(isSelectedDateCell(mode, date) ? COLOR_GREEN : COLOR_SOFT, dp(12), Color.TRANSPARENT, 0));
                if (isSelectedDateCell(mode, date)) {
                    cell.setTextColor(Color.WHITE);
                }
            }
            cell.setOnClickListener(v -> {
                applyDateSelection(mode, date, dialog, selectingRangeEnd, rerender);
            });
            grid.addView(cell, calendarCellParams());
        }
    }

    private GridLayout.LayoutParams calendarCellParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = (getResources().getDisplayMetrics().widthPixels - dp(40)) / 7;
        params.height = dp(42);
        params.setMargins(0, dp(2), 0, dp(2));
        return params;
    }

    private int colorForDateCell(DatePickMode mode, LocalDate date, LocalDate monthStart) {
        if (isSelectedDateCell(mode, date)) {
            return Color.WHITE;
        }
        return date.getMonthValue() == monthStart.getMonthValue() ? COLOR_TEXT : Color.rgb(184, 192, 202);
    }

    private boolean isHighlightedDateCell(DatePickMode mode, LocalDate date) {
        if (mode == DatePickMode.DAY) {
            return date.equals(selectedDay);
        }
        if (mode == DatePickMode.WEEK) {
            LocalDate end = selectedWeekStart.plusDays(6);
            return !date.isBefore(selectedWeekStart) && !date.isAfter(end);
        }
        if (mode == DatePickMode.MONTH) {
            return date.getYear() == selectedMonthStart.getYear() && date.getMonthValue() == selectedMonthStart.getMonthValue();
        }
        LocalDate first = rangeStart.isAfter(rangeEnd) ? rangeEnd : rangeStart;
        LocalDate last = rangeStart.isAfter(rangeEnd) ? rangeStart : rangeEnd;
        return !date.isBefore(first) && !date.isAfter(last);
    }

    private boolean isSelectedDateCell(DatePickMode mode, LocalDate date) {
        if (mode == DatePickMode.DAY) {
            return date.equals(selectedDay);
        }
        if (mode == DatePickMode.WEEK) {
            return date.equals(selectedWeekStart) || date.equals(selectedWeekStart.plusDays(6));
        }
        if (mode == DatePickMode.MONTH) {
            return date.equals(selectedMonthStart);
        }
        return date.equals(rangeStart) || date.equals(rangeEnd);
    }

    private void applyDateSelection(DatePickMode mode, LocalDate date, Dialog dialog, boolean[] selectingRangeEnd, Runnable rerender) {
        if (mode == DatePickMode.DAY) {
            selectedDay = date;
            dialog.dismiss();
            setContentView(buildUi());
            return;
        }
        if (mode == DatePickMode.WEEK) {
            selectedWeekStart = startOfWeek(date);
            dialog.dismiss();
            setContentView(buildUi());
            return;
        }
        if (mode == DatePickMode.MONTH) {
            selectedMonthStart = date.withDayOfMonth(1);
            dialog.dismiss();
            setContentView(buildUi());
            return;
        }

        if (!selectingRangeEnd[0]) {
            rangeStart = date;
            rangeEnd = date;
            selectingRangeEnd[0] = true;
            rerender.run();
            return;
        }

        if (date.isBefore(rangeStart)) {
            rangeEnd = rangeStart;
            rangeStart = date;
        } else {
            rangeEnd = date;
        }
        dialog.dismiss();
        setContentView(buildUi());
    }

    private static LocalDate startOfWeek(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    private static String dateLabel(LocalDate date, LocalDate today) {
        return date.equals(today) ? "今天" : compactDate(date);
    }

    private static String weekLabel(LocalDate weekStart, LocalDate today) {
        return weekStart.equals(startOfWeek(today)) ? "本周" : compactDate(weekStart) + "-" + compactDate(weekStart.plusDays(6));
    }

    private static String monthLabel(LocalDate monthStart, LocalDate today) {
        return monthStart.getYear() == today.getYear() && monthStart.getMonthValue() == today.getMonthValue()
                ? "本月"
                : monthStart.getMonthValue() + "月";
    }

    private static String compactDate(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(rounded(Color.rgb(252, 254, 253), dp(22), COLOR_LINE, 1));
        return panel;
    }

    private LinearLayout metricCard(String label, String value, int color) {
        LinearLayout card = smallInfoCard(label, value);
        ((TextView) card.getChildAt(1)).setTextColor(color);
        return card;
    }

    private LinearLayout smallInfoCard(String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(10));
        card.setMinimumHeight(dp(86));
        card.setBackground(rounded(COLOR_SOFT, dp(16), COLOR_LINE, 1));
        card.addView(text(label, 14, COLOR_MUTED, false), matchWrap());
        TextView valueText = text(value, 22, COLOR_TEXT, true);
        valueText.setSingleLine(true);
        card.addView(valueText, matchWrapTop(8));
        return card;
    }

    private LinearLayout insightInfoCard(String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(12), dp(8), dp(10));
        card.setMinimumHeight(dp(78));
        card.setBackground(rounded(COLOR_SOFT, dp(16), COLOR_LINE, 1));
        card.addView(text(label, 14, COLOR_MUTED, false), matchWrap());
        TextView valueText = text(value, 19, COLOR_TEXT, false);
        valueText.setSingleLine(true);
        valueText.setIncludeFontPadding(false);
        card.addView(valueText, matchWrapTop(12));
        return card;
    }

    private void addCard(GridLayout grid, View card, int row, int column) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(row, 1f), GridLayout.spec(column, 1f));
        int columns = Math.max(1, grid.getColumnCount());
        int horizontalGap = dp(4);
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.setMargins(column == 0 ? 0 : horizontalGap, row == 0 ? 0 : dp(10), column == columns - 1 ? 0 : horizontalGap, 0);
        grid.addView(card, params);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private TextView note(String value) {
        TextView note = text(value, 13, COLOR_MUTED, false);
        note.setLineSpacing(0f, 1.15f);
        return note;
    }

    private int colorForToday(long seconds) {
        TodayTone tone = TodayTone.fromSeconds(seconds);
        if (tone == TodayTone.DANGER) {
            return COLOR_RED;
        }
        if (tone == TodayTone.WARN) {
            return COLOR_YELLOW;
        }
        return COLOR_GREEN;
    }

    private static long longestSession(DailySummary summary) {
        long max = summary.currentSessionSeconds;
        for (long seconds : summary.sessionSeconds) {
            max = Math.max(max, seconds);
        }
        return max;
    }

    private static String peakHour(long[] hourlySeconds) {
        long max = 0L;
        int hour = 0;
        for (int i = 0; i < hourlySeconds.length; i++) {
            if (hourlySeconds[i] > max) {
                max = hourlySeconds[i];
                hour = i;
            }
        }
        if (max <= 0L) {
            return "暂无";
        }
        return String.format("%02d-%02d点", hour, (hour + 1) % 24);
    }

    private static long nightSeconds(long[] hourlySeconds) {
        long total = 0L;
        for (int hour = 0; hour < hourlySeconds.length; hour++) {
            if (hour >= 22 || hour < 6) {
                total += hourlySeconds[hour];
            }
        }
        return total;
    }

    private static long sum(List<DailySummary> summaries) {
        long total = 0L;
        for (DailySummary summary : summaries) {
            total += summary.totalSeconds;
        }
        return total;
    }

    private static int activeDays(List<DailySummary> summaries) {
        int count = 0;
        for (DailySummary summary : summaries) {
            if (summary.totalSeconds > 0L) {
                count++;
            }
        }
        return count;
    }

    private static List<Long> collectSessions(List<DailySummary> summaries) {
        List<Long> sessions = new ArrayList<>();
        for (DailySummary summary : summaries) {
            for (long seconds : summary.sessionSeconds) {
                if (seconds > 0L) {
                    sessions.add(seconds);
                }
            }
            if (summary.currentSessionSeconds > 0L) {
                sessions.add(summary.currentSessionSeconds);
            }
        }
        return sessions;
    }

    private static long max(List<Long> values) {
        long max = 0L;
        for (long value : values) {
            max = Math.max(max, value);
        }
        return max;
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    public static final class HourlyHeatView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long[] hourlySeconds = new long[24];

        public HourlyHeatView(android.content.Context context) {
            super(context);
        }

        public void setHourlySeconds(long[] values) {
            hourlySeconds = values == null ? new long[24] : values.clone();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float outer = Math.min(getWidth(), getHeight()) / 2f - 18f;
            float inner = 42f * getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpLocal(2));
            paint.setColor(COLOR_LINE);
            canvas.drawCircle(cx, cy, outer, paint);
            long max = 1L;
            for (long seconds : hourlySeconds) {
                max = Math.max(max, seconds);
            }
            for (int hour = 0; hour < 24; hour++) {
                if (hourlySeconds[hour] <= 0L) {
                    continue;
                }
                float length = dpLocal(10) + hourlySeconds[hour] * (outer - inner - dpLocal(12)) / (float) max;
                drawHourBar(canvas, cx, cy, -90f + hour * 15f, inner, inner + length, COLOR_BLUE);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(252, 254, 253));
            canvas.drawCircle(cx, cy, inner, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpLocal(2));
            paint.setColor(COLOR_LINE);
            canvas.drawCircle(cx, cy, inner, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dpLocal(22));
            paint.setColor(COLOR_TEXT);
            canvas.drawText("24H", cx, cy + dpLocal(8), paint);
        }

        private void drawHourBar(Canvas canvas, float cx, float cy, float degrees, float inner, float outer, int color) {
            double angle = Math.toRadians(degrees);
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            float px = -dy;
            float py = dx;
            float innerHalf = dpLocal(3.5f);
            float outerHalf = dpLocal(6.5f);
            float ix = cx + dx * inner;
            float iy = cy + dy * inner;
            float ox = cx + dx * outer;
            float oy = cy + dy * outer;
            Path path = new Path();
            path.moveTo(ix + px * innerHalf, iy + py * innerHalf);
            path.lineTo(ox + px * outerHalf, oy + py * outerHalf);
            path.lineTo(ox - px * outerHalf, oy - py * outerHalf);
            path.lineTo(ix - px * innerHalf, iy - py * innerHalf);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    public static final class WeekBarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<DailySummary> summaries = new ArrayList<>();

        public WeekBarView(android.content.Context context) {
            super(context);
        }

        public void setSummaries(List<DailySummary> values) {
            summaries = values == null ? new ArrayList<>() : values;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            long max = 1L;
            for (DailySummary summary : summaries) {
                max = Math.max(max, summary.totalSeconds);
            }
            String[] labels = { "一", "二", "三", "四", "五", "六", "日" };
            float slot = getWidth() / 7f;
            float maxHeight = getHeight() - dpLocal(34);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dpLocal(12));
            for (int i = 0; i < 7; i++) {
                long seconds = i < summaries.size() ? summaries.get(i).totalSeconds : 0L;
                float h = Math.max(dpLocal(4), seconds * maxHeight / max);
                float left = i * slot + slot * 0.32f;
                float top = getHeight() - dpLocal(24) - h;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(COLOR_BLUE);
                canvas.drawRect(left, top, left + slot * 0.36f, getHeight() - dpLocal(24), paint);
                paint.setColor(COLOR_MUTED);
                canvas.drawText(labels[i], i * slot + slot / 2f, getHeight() - dpLocal(4), paint);
            }
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    public static final class MonthTrendView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<DailySummary> summaries = new ArrayList<>();

        public MonthTrendView(android.content.Context context) {
            super(context);
        }

        public void setSummaries(List<DailySummary> values) {
            summaries = values == null ? new ArrayList<>() : values;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpLocal(2));
            paint.setColor(COLOR_YELLOW);
            canvas.drawLine(dpLocal(8), getHeight() * 0.32f, getWidth() - dpLocal(8), getHeight() * 0.32f, paint);
            if (summaries.isEmpty()) {
                return;
            }
            long max = 6L * 3600L;
            for (DailySummary summary : summaries) {
                max = Math.max(max, summary.totalSeconds);
            }
            Path path = new Path();
            for (int i = 0; i < summaries.size(); i++) {
                float x = summaries.size() == 1 ? getWidth() / 2f : dpLocal(10) + i * ((getWidth() - dpLocal(20)) / (summaries.size() - 1));
                float y = getHeight() - dpLocal(18) - summaries.get(i).totalSeconds * (getHeight() - dpLocal(36)) / (float) max;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            paint.setColor(COLOR_BLUE);
            paint.setStrokeWidth(dpLocal(4));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    public static final class ContinuousBandsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<Long> sessions = new ArrayList<>();

        public ContinuousBandsView(android.content.Context context) {
            super(context);
        }

        public void setSessions(List<Long> values) {
            sessions = values == null ? new ArrayList<>() : values;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            int[] counts = new int[3];
            for (long seconds : sessions) {
                if (seconds < 30 * 60L) {
                    counts[0]++;
                } else if (seconds < 45 * 60L) {
                    counts[1]++;
                } else {
                    counts[2]++;
                }
            }
            int max = Math.max(1, Math.max(counts[0], Math.max(counts[1], counts[2])));
            String[] labels = { "0-30分", "30-45分", "45分以上" };
            int[] colors = { COLOR_GREEN, COLOR_YELLOW, COLOR_RED };
            paint.setTextSize(dpLocal(12));
            for (int i = 0; i < 3; i++) {
                float y = dpLocal(18) + i * dpLocal(42);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(COLOR_MUTED);
                canvas.drawText(labels[i], 0, y, paint);
                float left = dpLocal(82);
                float top = y - dpLocal(12);
                float width = getWidth() - dpLocal(124);
                paint.setColor(Color.rgb(232, 243, 239));
                canvas.drawRect(left, top, left + width, top + dpLocal(12), paint);
                paint.setColor(colors[i]);
                canvas.drawRect(left, top, left + width * counts[i] / max, top + dpLocal(12), paint);
                paint.setColor(COLOR_TEXT);
                canvas.drawText(counts[i] + "次", getWidth() - dpLocal(32), y, paint);
            }
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
