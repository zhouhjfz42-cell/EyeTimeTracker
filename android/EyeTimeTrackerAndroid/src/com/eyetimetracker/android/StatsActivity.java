package com.eyetimetracker.android;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StatsActivity extends Activity {
    public static final String EXTRA_FAMILY_CHILD_STATS = "com.eyetimetracker.android.FAMILY_CHILD_STATS";

    private static final String DIAG_TAG = "EyeTimeDiag";
    private static final int COLOR_BG = Color.rgb(248, 252, 250);
    private static final int COLOR_TEXT = Color.rgb(17, 24, 39);
    private static final int COLOR_MUTED = Color.rgb(101, 114, 137);
    private static final int COLOR_GREEN = Color.rgb(22, 163, 127);
    private static final int COLOR_BLUE = Color.rgb(91, 92, 226);
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
    private boolean familyChildStatsMode;
    private int statsBuildGeneration;
    private int panelRefreshGeneration;
    private LinearLayout dayPanel;
    private LinearLayout weekPanel;
    private LinearLayout monthPanel;
    private LinearLayout summaryPanel;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        store = new EyeTimeStore(this);
        familyChildStatsMode = getIntent().getBooleanExtra(EXTRA_FAMILY_CHILD_STATS, false)
                && store.getProductMode() == ProductMode.FAMILY
                && store.getDeviceRole() == DeviceRole.PARENT_DEVICE;
        LocalDate today = LocalDate.now();
        selectedDay = today;
        selectedWeekStart = startOfWeek(today);
        selectedMonthStart = today.withDayOfMonth(1);
        rangeStart = selectedMonthStart;
        rangeEnd = today;
        reloadStatsUi(true);
    }

    private void reloadStatsUi(boolean syncAfterLoad) {
        int generation = ++statsBuildGeneration;
        setContentView(buildUi(placeholderStatsPayload(LocalDate.now())));
        new Thread(() -> {
            StatsPayload payload = loadStatsPayload();
            runOnUiThread(() -> {
                if (isFinishing() || generation != statsBuildGeneration) {
                    return;
                }
                setContentView(buildUi(payload));
                if (syncAfterLoad) {
                    runSyncOnOpen();
                }
            });
        }, "EyeTimeStatsBuild").start();
    }

    private void runSyncOnOpen() {
        if (familyChildStatsMode) {
            Log.i(DIAG_TAG, "StatsActivity syncOnOpen skipped familyChildStatsMode=true");
            return;
        }
        SyncSettings settings = store.getSyncSettings();
        AndroidSyncTriggerPolicy policy = new AndroidSyncTriggerPolicy();
        if (!settings.isPaired || !policy.shouldSyncForStatsOpen()) {
            Log.i(DIAG_TAG, "StatsActivity syncOnOpen skipped paired=" + settings.isPaired);
            return;
        }

        new Thread(() -> {
            long startedAt = System.currentTimeMillis();
            Log.i(DIAG_TAG, "StatsActivity syncOnOpen start " + store.diagnosticSnapshot());
            new AndroidSyncRunner(store).syncOnce();
            Log.i(DIAG_TAG, "StatsActivity syncOnOpen end ms=" + elapsed(startedAt) + " " + store.diagnosticSnapshot());
        }, "EyeTimeStatsSync").start();
    }

    private StatsPayload loadStatsPayload() {
        long totalStartedAt = System.currentTimeMillis();
        long stepStartedAt = totalStartedAt;
        Log.i(DIAG_TAG, "StatsActivity loadStatsPayload start day=" + selectedDay
                + " week=" + selectedWeekStart
                + " month=" + selectedMonthStart
                + " range=" + rangeStart + ".." + rangeEnd
                + " " + store.diagnosticSnapshot());
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = selectedMonthStart.getYear() == today.getYear() && selectedMonthStart.getMonthValue() == today.getMonthValue()
                ? today
                : selectedMonthStart.plusMonths(1).minusDays(1);
        LocalDate firstRange = rangeStart.isAfter(rangeEnd) ? rangeEnd : rangeStart;
        LocalDate lastRange = rangeStart.isAfter(rangeEnd) ? rangeStart : rangeEnd;

        LocalDate allStart = minDate(selectedDay, selectedWeekStart, selectedMonthStart, firstRange);
        LocalDate allEnd = maxDate(selectedDay, selectedWeekStart.plusDays(6), monthEnd, lastRange);
        List<DailySummary> allSummaries = statsDays(allStart, allEnd);
        stepStartedAt = logStep("StatsActivity get combined summaries", stepStartedAt);
        List<DeviceUsageBreakdown> allBreakdowns = statsDeviceBreakdowns(allStart, allEnd);
        stepStartedAt = logStep("StatsActivity get combined breakdowns", stepStartedAt);

        DailySummary todaySummary = summaryAt(allSummaries, allStart, selectedDay);
        DeviceUsageBreakdown deviceBreakdown = breakdownAt(allBreakdowns, allStart, selectedDay);
        List<DailySummary> week = summariesBetween(allSummaries, allStart, selectedWeekStart, selectedWeekStart.plusDays(6));
        List<DeviceUsageBreakdown> weekBreakdowns = breakdownsBetween(allBreakdowns, allStart, selectedWeekStart, selectedWeekStart.plusDays(6));
        List<DailySummary> month = summariesBetween(allSummaries, allStart, selectedMonthStart, monthEnd);
        DailySummary rangeSummary = combineSummaries(summariesBetween(allSummaries, allStart, firstRange, lastRange), firstRange + ".." + lastRange);
        DeviceUsageBreakdown rangeBreakdown = combineBreakdowns(breakdownsBetween(allBreakdowns, allStart, firstRange, lastRange));

        List<AppUsageEntry> appUsageEntries = statsAppUsageEntries(selectedDay, selectedDay);
        stepStartedAt = logStep("StatsActivity get app usage entries", stepStartedAt);
        Log.i(DIAG_TAG, "StatsActivity loadStatsPayload end totalMs=" + elapsed(totalStartedAt));
        return new StatsPayload(today, todaySummary, deviceBreakdown, week, weekBreakdowns, month, appUsageEntries, rangeSummary, rangeBreakdown);
    }

    private View buildUi(StatsPayload payload) {
        long totalStartedAt = System.currentTimeMillis();
        LocalDate today = payload.today;
        DailySummary todaySummary = payload.todaySummary;
        DeviceUsageBreakdown deviceBreakdown = payload.deviceBreakdown;
        List<DailySummary> week = payload.week;
        List<DeviceUsageBreakdown> weekBreakdowns = payload.weekBreakdowns;
        List<DailySummary> month = payload.month;
        List<AppUsageEntry> appUsageEntries = payload.appUsageEntries;
        DailySummary rangeSummary = payload.rangeSummary;
        DeviceUsageBreakdown rangeBreakdown = payload.rangeBreakdown;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(root);

        TextView title = text(getString(R.string.stats_title), 34, COLOR_TEXT, true);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrap());
        TextView subtitle = text(getString(familyChildStatsMode ? R.string.stats_family_child_subtitle : R.string.stats_subtitle), 16, COLOR_MUTED, false);
        root.addView(subtitle, matchWrapTop(10));

        dayPanel = panel();
        root.addView(dayPanel, matchWrapTop(24));
        populateDayPanel(dayPanel, today, todaySummary, deviceBreakdown, appUsageEntries);

        weekPanel = panel();
        root.addView(weekPanel, matchWrapTop(18));
        populateWeekPanel(weekPanel, today, week, weekBreakdowns);

        monthPanel = panel();
        root.addView(monthPanel, matchWrapTop(18));
        populateMonthPanel(monthPanel, today, month);

        summaryPanel = panel();
        root.addView(summaryPanel, matchWrapTop(18));
        populateSummaryPanel(summaryPanel, rangeSummary, rangeBreakdown);

        Log.i(DIAG_TAG, "StatsActivity buildUi end totalMs=" + elapsed(totalStartedAt));
        return scroll;
    }

    private void populateDayPanel(LinearLayout target, LocalDate today, DailySummary todaySummary, DeviceUsageBreakdown deviceBreakdown, List<AppUsageEntry> appUsageEntries) {
        target.removeAllViews();
        TextView dayPill = addPanelHead(target, getString(R.string.stats_daily_title), selectorText(dateLabel(selectedDay, today)));
        dayPill.setOnClickListener(v -> showDateSheet(DatePickMode.DAY));
        GridLayout metrics = new GridLayout(this);
        metrics.setColumnCount(2);
        target.addView(metrics, matchWrapTop(12));
        addCard(metrics, metricCard(getString(R.string.stats_daily_metric_total), formatDuration(todaySummary.totalSeconds), colorForToday(todaySummary.totalSeconds)), 0, 0);
        addCard(metrics, metricCard(getString(R.string.stats_daily_metric_longest), formatDuration(longestSession(todaySummary)), COLOR_YELLOW), 0, 1);
        addCard(metrics, metricCard(getString(R.string.stats_daily_metric_device_share), deviceBreakdown.pcPercent() + "%/" + deviceBreakdown.phonePercent() + "%", COLOR_TEXT), 1, 0);
        addCard(metrics, metricCard(
                getString(R.string.stats_daily_metric_reminders),
                countText(ReminderPolicy.displayCount(
                        todaySummary.totalSeconds,
                        store.getReminderMinutes(),
                        store.isRepeatReminderEnabled())),
                COLOR_TEXT), 1, 1);

        target.addView(deviceLegend(), matchWrapTop(8));

        HourlyHeatView heatView = new HourlyHeatView(this);
        heatView.setHourlySeconds(todaySummary.hourlySeconds);
        heatView.setSourceHourlySeconds(deviceBreakdown.pcHourlySeconds, deviceBreakdown.phoneHourlySeconds);
        LinearLayout.LayoutParams heatParams = new LinearLayout.LayoutParams(dp(226), dp(226));
        heatParams.gravity = Gravity.CENTER_HORIZONTAL;
        heatParams.topMargin = dp(2);
        target.addView(heatView, heatParams);

        LinearLayout insightRow = new LinearLayout(this);
        insightRow.setOrientation(LinearLayout.HORIZONTAL);
        target.addView(insightRow, matchWrapTop(8));
        addInsightCard(insightRow, insightInfoCard(getString(R.string.stats_daily_insight_peak), peakHour(todaySummary.hourlySeconds)), 0);
        addInsightCard(insightRow, insightInfoCard(getString(R.string.stats_daily_insight_night), formatDuration(nightSeconds(todaySummary.hourlySeconds))), 1);
        target.addView(appUsageCard(appUsageEntries, 4), matchWrapTop(10));
    }

    private void populateWeekPanel(LinearLayout target, LocalDate today, List<DailySummary> week, List<DeviceUsageBreakdown> weekBreakdowns) {
        target.removeAllViews();
        TextView weekPill = addPanelHead(target, getString(R.string.stats_week_title), selectorText(weekLabel(selectedWeekStart, today)));
        weekPill.setOnClickListener(v -> showDateSheet(DatePickMode.WEEK));
        WeekBarView weekView = new WeekBarView(this);
        weekView.setSummaries(week);
        weekView.setDeviceBreakdowns(weekBreakdowns);
        target.addView(weekView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210)));
        target.addView(note(formatResource(R.string.stats_week_total, "duration", formatDuration(sum(week)))), matchWrapTop(8));
    }

    private void populateMonthPanel(LinearLayout target, LocalDate today, List<DailySummary> month) {
        target.removeAllViews();
        TextView monthPill = addPanelHead(target, getString(R.string.stats_month_title), selectorText(monthLabel(selectedMonthStart, today)));
        monthPill.setOnClickListener(v -> showDateSheet(DatePickMode.MONTH));
        MonthTrendView monthView = new MonthTrendView(this);
        monthView.setSummaries(month);
        target.addView(monthView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));
        target.addView(note(formatResource(R.string.stats_month_recorded_days, "count", activeDays(month))), matchWrapTop(8));
    }

    private void populateSummaryPanel(LinearLayout target, DailySummary rangeSummary, DeviceUsageBreakdown rangeBreakdown) {
        target.removeAllViews();
        TextView rangePill = addPanelHead(target, getString(R.string.stats_summary_title), selectorText(compactDate(rangeStart)) + " " + selectorText(compactDate(rangeEnd)));
        rangePill.setOnClickListener(v -> showDateSheet(DatePickMode.RANGE));
        target.addView(summaryCard("", rangeSummary, rangeBreakdown), matchWrapTop(12));
    }

    private void refreshPanel(DatePickMode mode) {
        int generation = ++panelRefreshGeneration;
        LocalDate today = LocalDate.now();
        if (mode == DatePickMode.DAY) {
            LocalDate date = selectedDay;
            new Thread(() -> {
                DailySummary summary = statsDay(date);
                DeviceUsageBreakdown breakdown = statsDeviceBreakdown(date);
                List<AppUsageEntry> appUsageEntries = statsAppUsageEntries(date, date);
                runOnUiThread(() -> {
                    if (isFinishing() || generation != panelRefreshGeneration || dayPanel == null) {
                        return;
                    }
                    populateDayPanel(dayPanel, today, summary, breakdown, appUsageEntries);
                });
            }, "EyeTimeStatsDayPanel").start();
            return;
        }
        if (mode == DatePickMode.WEEK) {
            LocalDate weekStart = selectedWeekStart;
            LocalDate weekEnd = weekStart.plusDays(6);
            new Thread(() -> {
                List<DailySummary> week = statsDays(weekStart, weekEnd);
                List<DeviceUsageBreakdown> weekBreakdowns = statsDeviceBreakdowns(weekStart, weekEnd);
                runOnUiThread(() -> {
                    if (isFinishing() || generation != panelRefreshGeneration || weekPanel == null) {
                        return;
                    }
                    populateWeekPanel(weekPanel, today, week, weekBreakdowns);
                });
            }, "EyeTimeStatsWeekPanel").start();
            return;
        }
        if (mode == DatePickMode.MONTH) {
            LocalDate monthStart = selectedMonthStart;
            LocalDate monthEnd = monthStart.getYear() == today.getYear() && monthStart.getMonthValue() == today.getMonthValue()
                    ? today
                    : monthStart.plusMonths(1).minusDays(1);
            new Thread(() -> {
                List<DailySummary> month = statsDays(monthStart, monthEnd);
                runOnUiThread(() -> {
                    if (isFinishing() || generation != panelRefreshGeneration || monthPanel == null) {
                        return;
                    }
                    populateMonthPanel(monthPanel, today, month);
                });
            }, "EyeTimeStatsMonthPanel").start();
            return;
        }
        LocalDate firstRange = rangeStart.isAfter(rangeEnd) ? rangeEnd : rangeStart;
        LocalDate lastRange = rangeStart.isAfter(rangeEnd) ? rangeStart : rangeEnd;
        new Thread(() -> {
            DailySummary summary = combineSummaries(statsDays(firstRange, lastRange), firstRange + ".." + lastRange);
            DeviceUsageBreakdown breakdown = combineBreakdowns(statsDeviceBreakdowns(firstRange, lastRange));
            runOnUiThread(() -> {
                if (isFinishing() || generation != panelRefreshGeneration || summaryPanel == null) {
                    return;
                }
                populateSummaryPanel(summaryPanel, summary, breakdown);
            });
        }, "EyeTimeStatsSummaryPanel").start();
    }

    private long logStep(String label, long startedAt) {
        long now = System.currentTimeMillis();
        Log.i(DIAG_TAG, label + " ms=" + (now - startedAt));
        return now;
    }

    private static long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private DailySummary statsDay(LocalDate date) {
        return familyChildStatsMode ? store.getFamilyChildDay(date) : store.getDay(date);
    }

    private List<DailySummary> statsDays(LocalDate start, LocalDate end) {
        return familyChildStatsMode ? store.getFamilyChildDays(start, end) : store.getDays(start, end);
    }

    private DeviceUsageBreakdown statsDeviceBreakdown(LocalDate date) {
        return familyChildStatsMode ? store.getFamilyChildDeviceBreakdown(date) : store.getDeviceBreakdown(date);
    }

    private List<DeviceUsageBreakdown> statsDeviceBreakdowns(LocalDate start, LocalDate end) {
        return familyChildStatsMode ? store.getFamilyChildDeviceBreakdowns(start, end) : store.getDeviceBreakdowns(start, end);
    }

    private List<AppUsageEntry> statsAppUsageEntries(LocalDate start, LocalDate end) {
        return familyChildStatsMode ? store.getFamilyChildAppUsageEntries(start, end) : store.getAppUsageEntries(start, end);
    }

    private StatsPayload placeholderStatsPayload(LocalDate today) {
        LocalDate monthEnd = selectedMonthStart.getYear() == today.getYear() && selectedMonthStart.getMonthValue() == today.getMonthValue()
                ? today
                : selectedMonthStart.plusMonths(1).minusDays(1);
        LocalDate firstRange = rangeStart.isAfter(rangeEnd) ? rangeEnd : rangeStart;
        LocalDate lastRange = rangeStart.isAfter(rangeEnd) ? rangeStart : rangeEnd;
        return new StatsPayload(
                today,
                zeroSummary(selectedDay),
                new DeviceUsageBreakdown(0L, 0L),
                summariesBetween(new ArrayList<>(), selectedWeekStart, selectedWeekStart, selectedWeekStart.plusDays(6)),
                breakdownsBetween(new ArrayList<>(), selectedWeekStart, selectedWeekStart, selectedWeekStart.plusDays(6)),
                summariesBetween(new ArrayList<>(), selectedMonthStart, selectedMonthStart, monthEnd),
                new ArrayList<>(),
                zeroSummary(firstRange + ".." + lastRange),
                new DeviceUsageBreakdown(0L, 0L));
    }

    private static LocalDate minDate(LocalDate first, LocalDate... rest) {
        LocalDate value = first;
        for (LocalDate date : rest) {
            if (date != null && date.isBefore(value)) {
                value = date;
            }
        }
        return value;
    }

    private static LocalDate maxDate(LocalDate first, LocalDate... rest) {
        LocalDate value = first;
        for (LocalDate date : rest) {
            if (date != null && date.isAfter(value)) {
                value = date;
            }
        }
        return value;
    }

    private static DailySummary summaryAt(List<DailySummary> summaries, LocalDate start, LocalDate date) {
        int index = (int) (date.toEpochDay() - start.toEpochDay());
        if (summaries != null && index >= 0 && index < summaries.size() && summaries.get(index) != null) {
            return summaries.get(index);
        }
        return zeroSummary(date);
    }

    private static DeviceUsageBreakdown breakdownAt(List<DeviceUsageBreakdown> breakdowns, LocalDate start, LocalDate date) {
        int index = (int) (date.toEpochDay() - start.toEpochDay());
        if (breakdowns != null && index >= 0 && index < breakdowns.size() && breakdowns.get(index) != null) {
            return breakdowns.get(index);
        }
        return new DeviceUsageBreakdown(0L, 0L);
    }

    private static List<DailySummary> summariesBetween(List<DailySummary> summaries, LocalDate sourceStart, LocalDate start, LocalDate end) {
        List<DailySummary> values = new ArrayList<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            values.add(summaryAt(summaries, sourceStart, cursor));
        }
        return values;
    }

    private static List<DeviceUsageBreakdown> breakdownsBetween(List<DeviceUsageBreakdown> breakdowns, LocalDate sourceStart, LocalDate start, LocalDate end) {
        List<DeviceUsageBreakdown> values = new ArrayList<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            values.add(breakdownAt(breakdowns, sourceStart, cursor));
        }
        return values;
    }

    private static DailySummary zeroSummary(LocalDate date) {
        return zeroSummary(date.toString());
    }

    private static DailySummary zeroSummary(String date) {
        return new DailySummary(date, 0L, false, 0);
    }

    private DailySummary combineSummaries(List<DailySummary> summaries, String date) {
        long totalSeconds = 0L;
        long currentSessionSeconds = 0L;
        long[] hourly = new long[24];
        List<Long> sessions = new ArrayList<>();
        boolean reminderShown = false;
        int lastReminderStep = 0;
        if (summaries != null) {
            for (DailySummary summary : summaries) {
                if (summary == null) {
                    continue;
                }
                totalSeconds += summary.totalSeconds;
                currentSessionSeconds += summary.currentSessionSeconds;
                reminderShown |= summary.reminderShown;
                lastReminderStep = Math.max(lastReminderStep, summary.lastReminderStep);
                for (int i = 0; i < hourly.length && i < summary.hourlySeconds.length; i++) {
                    hourly[i] += summary.hourlySeconds[i];
                }
                for (long seconds : summary.sessionSeconds) {
                    if (seconds > 0L) {
                        sessions.add(seconds);
                    }
                }
            }
        }

        long[] sessionValues = new long[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) {
            sessionValues[i] = sessions.get(i);
        }
        return new DailySummary(date, totalSeconds, hourly, sessionValues, currentSessionSeconds, reminderShown, lastReminderStep);
    }

    private DeviceUsageBreakdown combineBreakdowns(List<DeviceUsageBreakdown> breakdowns) {
        long pcSeconds = 0L;
        long phoneSeconds = 0L;
        long[] pcHourly = new long[24];
        long[] phoneHourly = new long[24];
        for (DeviceUsageBreakdown breakdown : breakdowns) {
            if (breakdown == null) {
                continue;
            }
            pcSeconds += breakdown.pcSeconds;
            phoneSeconds += breakdown.phoneSeconds;
            for (int i = 0; i < 24; i++) {
                pcHourly[i] += breakdown.pcHourlySeconds[i];
                phoneHourly[i] += breakdown.phoneHourlySeconds[i];
            }
        }
        return new DeviceUsageBreakdown(pcSeconds, phoneSeconds, pcHourly, phoneHourly);
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
                ? getString(R.string.calendar_pick_week)
                : mode == DatePickMode.MONTH
                    ? getString(R.string.calendar_pick_month)
                    : mode == DatePickMode.RANGE
                        ? getString(R.string.calendar_pick_range_next)
                        : getString(R.string.calendar_pick_day);

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
            monthTitle.setText(formatResource(
                    R.string.calendar_month_title,
                    "year",
                    displayMonth[0].getYear(),
                    "month",
                    displayMonth[0].getMonthValue()));
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

        TextView cancel = text(getString(R.string.common_cancel), 16, COLOR_TEXT, true);
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
        String[] weekdays = weekdayLabels(this);
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
            refreshPanel(DatePickMode.DAY);
            return;
        }
        if (mode == DatePickMode.WEEK) {
            selectedWeekStart = startOfWeek(date);
            dialog.dismiss();
            refreshPanel(DatePickMode.WEEK);
            return;
        }
        if (mode == DatePickMode.MONTH) {
            selectedMonthStart = date.withDayOfMonth(1);
            dialog.dismiss();
            refreshPanel(DatePickMode.MONTH);
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
        refreshPanel(DatePickMode.RANGE);
    }

    private static LocalDate startOfWeek(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    private String dateLabel(LocalDate date, LocalDate today) {
        return date.equals(today) ? getString(R.string.common_today) : compactDate(date);
    }

    private String weekLabel(LocalDate weekStart, LocalDate today) {
        return weekStart.equals(startOfWeek(today)) ? getString(R.string.common_this_week) : compactDate(weekStart) + "-" + compactDate(weekStart.plusDays(6));
    }

    private String monthLabel(LocalDate monthStart, LocalDate today) {
        return monthStart.getYear() == today.getYear() && monthStart.getMonthValue() == today.getMonthValue()
                ? getString(R.string.common_this_month)
                : formatResource(R.string.calendar_month_compact, "month", monthStart.getMonthValue());
    }

    private static String compactDate(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
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
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setMinimumHeight(dp(74));
        card.setBackground(rounded(COLOR_SOFT, dp(16), COLOR_LINE, 1));
        card.addView(text(label, 14, COLOR_MUTED, false), matchWrap());
        TextView valueText = text(value, 22, COLOR_TEXT, true);
        valueText.setSingleLine(true);
        card.addView(valueText, matchWrapTop(5));
        return card;
    }

    private LinearLayout insightInfoCard(String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setMinimumHeight(dp(66));
        card.setBackground(rounded(COLOR_SOFT, dp(16), COLOR_LINE, 1));
        card.addView(text(label, 14, COLOR_MUTED, false), matchWrap());
        TextView valueText = text(value, 19, COLOR_TEXT, false);
        valueText.setSingleLine(true);
        valueText.setIncludeFontPadding(false);
        valueText.setAutoSizeTextTypeUniformWithConfiguration(15, 19, 1, TypedValue.COMPLEX_UNIT_SP);
        card.addView(valueText, matchWrapTop(8));
        return card;
    }

    private LinearLayout summaryCard(String title, DailySummary summary, DeviceUsageBreakdown breakdown) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(Color.WHITE, dp(16), COLOR_LINE, 1));

        if (title != null && !title.trim().isEmpty()) {
            TextView titleView = text(title, 20, COLOR_TEXT, true);
            titleView.setIncludeFontPadding(false);
            card.addView(titleView, matchWrap());
        }

        TextView peakText = text(formatResource(R.string.stats_daily_summary_peak_short, "range", peakHour(summary.hourlySeconds)), 14, COLOR_MUTED, false);
        peakText.setLineSpacing(0f, 1.15f);
        card.addView(peakText, matchWrapTop(title == null || title.trim().isEmpty() ? 0 : 8));

        TextView longestText = text(formatResource(R.string.stats_daily_summary_longest_short, "duration", formatDuration(longestSession(summary))), 14, COLOR_MUTED, false);
        longestText.setLineSpacing(0f, 1.15f);
        card.addView(longestText, matchWrapTop(6));

        TextView sourceText = text(summarySourceText(breakdown), 14, COLOR_MUTED, false);
        sourceText.setLineSpacing(0f, 1.15f);
        card.addView(sourceText, matchWrapTop(6));

        TextView careText = text(
                summaryCareText(
                        longestSession(summary),
                        nightSeconds(summary.hourlySeconds),
                        breakdown.phonePercent()),
                14,
                COLOR_GREEN,
                true);
        careText.setLineSpacing(0f, 1.15f);
        card.addView(careText, matchWrapTop(8));
        return card;
    }

    private LinearLayout appUsageCard(List<AppUsageEntry> entries, int maxRows) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(Color.WHITE, dp(16), COLOR_LINE, 1));
        TextView titleView = text(getString(R.string.stats_app_usage_title), 20, COLOR_TEXT, true);
        titleView.setIncludeFontPadding(false);
        card.addView(titleView, matchWrap());
        addAppUsageRanking(card, entries, maxRows);
        return card;
    }

    private void addAppUsageRanking(LinearLayout parent, List<AppUsageEntry> entries, int maxRows) {
        List<AppUsageEntry> ranked = rankAppUsage(entries, maxRows);
        if (ranked.isEmpty()) {
            parent.addView(note(getString(R.string.stats_app_usage_empty)), matchWrapTop(12));
            return;
        }

        long maxSeconds = 1L;
        for (AppUsageEntry entry : ranked) {
            maxSeconds = Math.max(maxSeconds, entry.durationSeconds);
        }
        for (int i = 0; i < ranked.size(); i++) {
            parent.addView(appUsageRow(ranked.get(i), maxSeconds, i), matchWrapTop(i == 0 ? 12 : 14));
        }
    }

    private LinearLayout appUsageRow(AppUsageEntry entry, long maxSeconds, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(appIconView(entry, index), new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        contentParams.leftMargin = dp(12);
        row.addView(content, contentParams);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        DeviceSourceIconView source = new DeviceSourceIconView(this, isPcSource(entry));
        nameRow.addView(source, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView name = text(entry.appName.isEmpty() ? entry.appId : entry.appName, 16, COLOR_TEXT, false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(7);
        nameRow.addView(name, nameParams);
        TextView duration = text(formatAppUsageDuration(entry.durationSeconds), 15, Color.rgb(142, 148, 160), true);
        duration.setGravity(Gravity.RIGHT);
        duration.setSingleLine(true);
        LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.WRAP_CONTENT);
        durationParams.leftMargin = dp(6);
        nameRow.addView(duration, durationParams);
        content.addView(nameRow, matchWrap());

        AppUsageBarView bar = new AppUsageBarView(this);
        bar.setProgress((float) (entry.durationSeconds / (double) Math.max(1L, maxSeconds)));
        content.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));
        return row;
    }

    private View appIconView(AppUsageEntry entry, int index) {
        Bitmap syncedIcon = decodeIconData(entry == null ? "" : entry.iconData);
        if (syncedIcon != null) {
            ImageView image = new ImageView(this);
            image.setImageBitmap(syncedIcon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(false);
            image.setPadding(dp(2), dp(2), dp(2), dp(2));
            return image;
        }

        Drawable icon = loadAppIcon(entry);
        if (icon != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(false);
            image.setPadding(dp(2), dp(2), dp(2), dp(2));
            return image;
        }

        TextView fallback = text(initial(entry.appName.isEmpty() ? entry.appId : entry.appName), 18, Color.WHITE, true);
        fallback.setGravity(Gravity.CENTER);
        fallback.setBackground(rounded(appIconColor(index), dp(14), Color.TRANSPARENT, 0));
        return fallback;
    }

    private Bitmap decodeIconData(String iconData) {
        if (iconData == null || iconData.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(iconData, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Drawable loadAppIcon(AppUsageEntry entry) {
        if (entry == null || isPcSource(entry) || entry.appId.trim().isEmpty()) {
            return null;
        }
        try {
            return getPackageManager().getApplicationIcon(entry.appId);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<AppUsageEntry> rankAppUsage(List<AppUsageEntry> entries, int maxRows) {
        Map<String, AppUsageEntry> bestByName = new HashMap<>();
        if (entries != null) {
            for (AppUsageEntry entry : entries) {
                if (entry != null && entry.durationSeconds > 0L) {
                    String key = appDisplayKey(entry);
                    AppUsageEntry existing = bestByName.get(key);
                    if (existing == null
                            || entry.durationSeconds > existing.durationSeconds
                            || (entry.durationSeconds == existing.durationSeconds
                            && entry.updatedAtUnixSeconds > existing.updatedAtUnixSeconds)) {
                        bestByName.put(key, entry);
                    }
                }
            }
        }
        List<AppUsageEntry> values = new ArrayList<>(bestByName.values());
        values.sort((left, right) -> Long.compare(right.durationSeconds, left.durationSeconds));
        if (values.size() <= maxRows) {
            return values;
        }
        return new ArrayList<>(values.subList(0, maxRows));
    }

    private String sourceLabel(AppUsageEntry entry) {
        return isPcSource(entry)
                ? getString(R.string.common_pc)
                : getString(R.string.common_phone);
    }

    private static boolean isPcSource(AppUsageEntry entry) {
        return entry != null
                && ("pc".equalsIgnoreCase(entry.source) || "windows".equalsIgnoreCase(entry.platform));
    }

    private static String appDisplayKey(AppUsageEntry entry) {
        String appName = entry.appName == null || entry.appName.trim().isEmpty()
                ? entry.appId
                : entry.appName;
        return safeKey(entry.source) + ":" + safeKey(appName);
    }

    private static String safeKey(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return safe.isEmpty() ? "_" : safe;
    }

    private static String initial(String value) {
        String text = value == null || value.trim().isEmpty() ? "A" : value.trim();
        return text.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    private static int appIconColor(int index) {
        switch (index % 4) {
            case 0: return Color.rgb(66, 133, 244);
            case 1: return Color.rgb(22, 196, 102);
            case 2: return Color.rgb(18, 125, 110);
            default: return Color.rgb(239, 39, 67);
        }
    }

    private LinearLayout deviceLegend() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(legendItem(getString(R.string.common_pc), COLOR_GREEN), wrapWrap());
        LinearLayout.LayoutParams phoneParams = wrapWrap();
        phoneParams.leftMargin = dp(22);
        row.addView(legendItem(getString(R.string.common_phone), COLOR_BLUE), phoneParams);
        return row;
    }

    private LinearLayout legendItem(String label, int color) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(rounded(color, dp(999), Color.TRANSPARENT, 0));
        item.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView labelView = text(label, 13, COLOR_MUTED, false);
        LinearLayout.LayoutParams labelParams = wrapWrap();
        labelParams.leftMargin = dp(6);
        item.addView(labelView, labelParams);
        return item;
    }

    private void addInsightCard(LinearLayout row, View card, int column) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int horizontalGap = dp(4);
        params.setMargins(column == 0 ? 0 : horizontalGap, 0, column == 1 ? 0 : horizontalGap, 0);
        row.addView(card, params);
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
        AppFonts.apply(text, bold);
        text.setIncludeFontPadding(false);
        return text;
    }

    private TextView note(String value) {
        TextView note = text(value, 13, COLOR_MUTED, false);
        note.setLineSpacing(0f, 1.15f);
        return note;
    }

    private String formatResource(int resId, Object... pairs) {
        return formatResource(this, resId, pairs);
    }

    private static String formatResource(Context context, int resId, Object... pairs) {
        String text = context.getString(resId);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return text;
    }

    private String formatDuration(long totalSeconds) {
        return formatDuration(this, totalSeconds);
    }

    private static String formatDuration(Context context, long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long totalMinutes = safeSeconds / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L) {
            return formatResource(
                    context,
                    R.string.duration_hours_minutes_padded,
                    "hours",
                    hours,
                    "minutes:00",
                    String.format("%02d", minutes));
        }
        return formatResource(context, R.string.duration_minutes, "minutes", minutes);
    }

    private static String formatAppUsageDuration(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long totalMinutes = safeSeconds / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + ":" + String.format(Locale.ROOT, "%02d", minutes);
    }

    private static String formatTooltipMinutes(Context context, long totalSeconds) {
        return formatResource(context, R.string.duration_minutes, "minutes", Math.max(0L, totalSeconds) / 60L);
    }

    private static String formatTooltipHours(Context context, long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long halfHourUnits = Math.round(safeSeconds / 1800D);
        long wholeHours = halfHourUnits / 2L;
        if (halfHourUnits % 2L == 0L) {
            return formatResource(context, R.string.duration_hours, "hours", wholeHours);
        }
        return formatResource(context, R.string.duration_half_hours, "hours", wholeHours);
    }

    private String countText(int count) {
        return formatResource(R.string.stats_sessions_count, "count", count);
    }

    private String summarySourceText(DeviceUsageBreakdown breakdown) {
        if (breakdown.pcSeconds + breakdown.phoneSeconds <= 0L) {
            return getString(R.string.stats_source_empty);
        }
        return breakdown.phonePercent() >= breakdown.pcPercent()
                ? formatResource(R.string.stats_source_phone_high, "percent", breakdown.phonePercent())
                : formatResource(R.string.stats_source_pc_high, "percent", breakdown.pcPercent());
    }

    private String summaryCareText(long longestSessionSeconds, long nightSeconds, int phonePercent) {
        if (longestSessionSeconds >= 45L * 60L) {
            return getString(R.string.stats_care_continuous_high);
        }
        if (nightSeconds >= 60L * 60L) {
            return getString(R.string.stats_care_night_high);
        }
        if (phonePercent >= 60) {
            return getString(R.string.stats_care_phone_high);
        }
        return longestSessionSeconds <= 0L && nightSeconds <= 0L
                ? getString(R.string.stats_care_no_pressure)
                : getString(R.string.stats_care_steady);
    }

    private static String[] weekdayLabels(Context context) {
        return new String[] {
                context.getString(R.string.calendar_weekday_mon),
                context.getString(R.string.calendar_weekday_tue),
                context.getString(R.string.calendar_weekday_wed),
                context.getString(R.string.calendar_weekday_thu),
                context.getString(R.string.calendar_weekday_fri),
                context.getString(R.string.calendar_weekday_sat),
                context.getString(R.string.calendar_weekday_sun)
        };
    }

    private static String selectorText(String text) {
        return text + " ▾";
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

    private String peakHour(long[] hourlySeconds) {
        long max = 0L;
        int hour = 0;
        for (int i = 0; i < hourlySeconds.length; i++) {
            if (hourlySeconds[i] > max) {
                max = hourlySeconds[i];
                hour = i;
            }
        }
        if (max <= 0L) {
            return getString(R.string.common_none);
        }
        return formatResource(
                R.string.time_hour_range,
                "start:00",
                String.format("%02d", hour),
                "end:00",
                String.format("%02d", (hour + 1) % 24));
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

    private static final class ChartHit {
        final RectF bounds;
        final String text;
        final float x;
        final float y;

        ChartHit(RectF bounds, String text, float x, float y) {
            this.bounds = bounds;
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    private static final class StatsPayload {
        final LocalDate today;
        final DailySummary todaySummary;
        final DeviceUsageBreakdown deviceBreakdown;
        final List<DailySummary> week;
        final List<DeviceUsageBreakdown> weekBreakdowns;
        final List<DailySummary> month;
        final List<AppUsageEntry> appUsageEntries;
        final DailySummary rangeSummary;
        final DeviceUsageBreakdown rangeBreakdown;

        StatsPayload(
                LocalDate today,
                DailySummary todaySummary,
                DeviceUsageBreakdown deviceBreakdown,
                List<DailySummary> week,
                List<DeviceUsageBreakdown> weekBreakdowns,
                List<DailySummary> month,
                List<AppUsageEntry> appUsageEntries,
                DailySummary rangeSummary,
                DeviceUsageBreakdown rangeBreakdown) {
            this.today = today;
            this.todaySummary = todaySummary;
            this.deviceBreakdown = deviceBreakdown;
            this.week = week;
            this.weekBreakdowns = weekBreakdowns;
            this.month = month;
            this.appUsageEntries = appUsageEntries;
            this.rangeSummary = rangeSummary;
            this.rangeBreakdown = rangeBreakdown;
        }
    }

    private static void drawChartTip(Canvas canvas, Paint paint, String text, float x, float y, float density, int viewWidth, Typeface typeface) {
        if (text == null || text.isEmpty()) {
            return;
        }

        paint.setTypeface(typeface);
        paint.setTextSize(13f * density);
        paint.setTextAlign(Paint.Align.LEFT);
        float paddingX = 10f * density;
        float paddingY = 7f * density;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float width = paint.measureText(text) + paddingX * 2f;
        float height = metrics.bottom - metrics.top + paddingY * 2f;
        float left = Math.max(4f * density, Math.min(x - width / 2f, viewWidth - width - 4f * density));
        float top = Math.max(4f * density, y - height - 10f * density);
        RectF rect = new RectF(left, top, left + width, top + height);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(252, 254, 253));
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(COLOR_LINE);
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_TEXT);
        canvas.drawText(text, left + paddingX, top + paddingY - metrics.top, paint);
    }

    public static final class HourlyHeatView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<ChartHit> hits = new ArrayList<>();
        private long[] hourlySeconds = new long[24];
        private long[] pcHourlySeconds = new long[24];
        private long[] phoneHourlySeconds = new long[24];
        private String tipText = null;
        private float tipX;
        private float tipY;

        public HourlyHeatView(android.content.Context context) {
            super(context);
        }

        public void setHourlySeconds(long[] values) {
            hourlySeconds = values == null ? new long[24] : values.clone();
            invalidate();
        }

        public void setSourceHourlySeconds(long[] pcValues, long[] phoneValues) {
            pcHourlySeconds = normalizeHourly(pcValues);
            phoneHourlySeconds = normalizeHourly(phoneValues);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            hits.clear();
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - dpLocal(8);
            float outer = Math.min(getWidth(), getHeight()) / 2f - dpLocal(34);
            float inner = 42f * getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpLocal(2));
            paint.setColor(COLOR_LINE);
            canvas.drawCircle(cx, cy, outer, paint);
            long max = 3600L;
            boolean hasSource = sum(pcHourlySeconds) + sum(phoneHourlySeconds) > 0L;
            for (int hour = 0; hour < 24; hour++) {
                long pcSeconds = hasSource ? pcHourlySeconds[hour] : 0L;
                long phoneSeconds = hasSource ? phoneHourlySeconds[hour] : hourlySeconds[hour];
                long[] capped = capHourSourceSeconds(pcSeconds, phoneSeconds);
                pcSeconds = capped[0];
                phoneSeconds = capped[1];
                long seconds = pcSeconds + phoneSeconds;
                if (seconds <= 0L) {
                    continue;
                }
                float length = dpLocal(10) + seconds * (outer - inner - dpLocal(12)) / (float) max;
                RectF bounds = drawSourceHourBar(canvas, cx, cy, -90f + hour * 15f, inner, length, pcSeconds, phoneSeconds);
                bounds.inset(-dpLocal(8), -dpLocal(8));
                hits.add(new ChartHit(bounds, formatTooltipMinutes(getContext(), seconds), bounds.centerX(), bounds.centerY()));
            }
            drawHourLabels(canvas, cx, cy, outer);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(252, 254, 253));
            canvas.drawCircle(cx, cy, inner, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpLocal(2));
            paint.setColor(COLOR_LINE);
            canvas.drawCircle(cx, cy, inner, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(AppFonts.bold(getContext()));
            paint.setTextSize(dpLocal(22));
            paint.setColor(COLOR_TEXT);
            canvas.drawText("24H", cx, cy + dpLocal(8), paint);
            drawChartTip(canvas, paint, tipText, tipX, tipY, getResources().getDisplayMetrics().density, getWidth(), AppFonts.bold(getContext()));
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return true;
            }

            updateTip(event.getX(), event.getY());
            return true;
        }

        private void updateTip(float x, float y) {
            for (ChartHit hit : hits) {
                if (hit.bounds.contains(x, y)) {
                    tipText = hit.text;
                    tipX = hit.x;
                    tipY = hit.y;
                    invalidate();
                    return;
                }
            }

            tipText = null;
            invalidate();
        }

        private RectF drawSourceHourBar(Canvas canvas, float cx, float cy, float degrees, float inner, float length, long pcSeconds, long phoneSeconds) {
            long total = Math.max(1L, pcSeconds + phoneSeconds);
            RectF bounds = null;
            float cursor = inner;
            if (pcSeconds > 0L) {
                float pcLength = length * pcSeconds / total;
                bounds = drawHourBarSection(canvas, cx, cy, degrees, inner, cursor - inner, cursor + pcLength - inner, length, COLOR_GREEN);
                cursor += pcLength;
            }
            if (phoneSeconds > 0L) {
                RectF phoneBounds = drawHourBarSection(canvas, cx, cy, degrees, inner, cursor - inner, length, length, COLOR_BLUE);
                if (bounds == null) {
                    bounds = phoneBounds;
                } else {
                    bounds.union(phoneBounds);
                }
            }
            return bounds == null ? new RectF() : bounds;
        }

        private static long[] capHourSourceSeconds(long pcSeconds, long phoneSeconds) {
            long hourSeconds = 3600L;
            pcSeconds = Math.max(0L, pcSeconds);
            phoneSeconds = Math.max(0L, phoneSeconds);
            long totalSeconds = pcSeconds + phoneSeconds;
            if (totalSeconds <= hourSeconds) {
                return new long[] { pcSeconds, phoneSeconds };
            }

            long scaledPcSeconds = Math.round(pcSeconds * (double) hourSeconds / totalSeconds);
            scaledPcSeconds = Math.max(0L, Math.min(hourSeconds, scaledPcSeconds));
            return new long[] { scaledPcSeconds, hourSeconds - scaledPcSeconds };
        }

        private RectF drawHourBarSection(Canvas canvas, float cx, float cy, float degrees, float baseInner, float startOffset, float endOffset, float fullLength, int color) {
            double angle = Math.toRadians(degrees);
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            float px = -dy;
            float py = dx;
            float innerHalf = interpolatedHalfWidth(startOffset, fullLength);
            float outerHalf = interpolatedHalfWidth(endOffset, fullLength);
            float inner = baseInner + startOffset;
            float outer = baseInner + endOffset;
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
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            return bounds;
        }

        private float interpolatedHalfWidth(float offset, float fullLength) {
            float innerHalf = dpLocal(3.5f);
            float outerHalf = dpLocal(6.5f);
            if (fullLength <= 0f) {
                return innerHalf;
            }
            float ratio = Math.max(0f, Math.min(1f, offset / fullLength));
            return innerHalf + (outerHalf - innerHalf) * ratio;
        }

        private void drawHourLabels(Canvas canvas, float cx, float cy, float outer) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_MUTED);
            paint.setTypeface(AppFonts.regular(getContext()));
            paint.setTextSize(dpLocal(11));
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baselineOffset = -(metrics.ascent + metrics.descent) / 2f;
            int[] hours = { 0, 6, 12, 18 };
            for (int hour : hours) {
                double angle = Math.toRadians(-90f + hour * 15f);
                float radius = outer + dpLocal(8);
                float x = cx + (float) Math.cos(angle) * radius;
                float y = cy + (float) Math.sin(angle) * radius + baselineOffset;
                canvas.drawText(String.valueOf(hour), x, y, paint);
            }
        }

        private static long[] normalizeHourly(long[] values) {
            long[] normalized = new long[24];
            if (values != null) {
                System.arraycopy(values, 0, normalized, 0, Math.min(24, values.length));
            }
            return normalized;
        }

        private static long sum(long[] values) {
            long total = 0L;
            for (long value : values) {
                total += value;
            }
            return total;
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    public static final class WeekBarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<ChartHit> hits = new ArrayList<>();
        private List<DailySummary> summaries = new ArrayList<>();
        private List<DeviceUsageBreakdown> deviceBreakdowns = new ArrayList<>();
        private String tipText = null;
        private float tipX;
        private float tipY;

        public WeekBarView(android.content.Context context) {
            super(context);
        }

        public void setSummaries(List<DailySummary> values) {
            summaries = values == null ? new ArrayList<>() : values;
            invalidate();
        }

        public void setDeviceBreakdowns(List<DeviceUsageBreakdown> values) {
            deviceBreakdowns = values == null ? new ArrayList<>() : values;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            hits.clear();
            long max = 1L;
            for (DailySummary summary : summaries) {
                max = Math.max(max, summary.totalSeconds);
            }
            String[] labels = weekdayLabels(getContext());
            float slot = getWidth() / 7f;
            float maxHeight = getHeight() - dpLocal(34);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(AppFonts.regular(getContext()));
            paint.setTextSize(dpLocal(12));
            for (int i = 0; i < 7; i++) {
                long seconds = i < summaries.size() ? summaries.get(i).totalSeconds : 0L;
                float h = Math.max(dpLocal(4), seconds * maxHeight / max);
                float left = i * slot + slot * 0.32f;
                float top = getHeight() - dpLocal(24) - h;
                paint.setStyle(Paint.Style.FILL);
                RectF bar = new RectF(left, top, left + slot * 0.36f, getHeight() - dpLocal(24));
                DeviceUsageBreakdown breakdown = i < deviceBreakdowns.size() ? deviceBreakdowns.get(i) : new DeviceUsageBreakdown(0L, 0L);
                long sourceSeconds = breakdown.pcSeconds + breakdown.phoneSeconds;
                if (sourceSeconds > 0L) {
                    float phoneHeight = h * breakdown.phoneSeconds / sourceSeconds;
                    paint.setColor(COLOR_BLUE);
                    canvas.drawRect(left, top, left + slot * 0.36f, top + phoneHeight, paint);
                    paint.setColor(COLOR_GREEN);
                    canvas.drawRect(left, top + phoneHeight, left + slot * 0.36f, getHeight() - dpLocal(24), paint);
                } else {
                    paint.setColor(COLOR_BLUE);
                    canvas.drawRect(bar, paint);
                }
                RectF hit = new RectF(bar);
                hit.inset(-dpLocal(8), -dpLocal(8));
                hits.add(new ChartHit(hit, formatTooltipHours(getContext(), seconds), hit.centerX(), top));
                paint.setColor(COLOR_MUTED);
                canvas.drawText(labels[i], i * slot + slot / 2f, getHeight() - dpLocal(4), paint);
            }
            drawChartTip(canvas, paint, tipText, tipX, tipY, getResources().getDisplayMetrics().density, getWidth(), AppFonts.bold(getContext()));
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return true;
            }

            updateTip(event.getX(), event.getY());
            return true;
        }

        private void updateTip(float x, float y) {
            for (ChartHit hit : hits) {
                if (hit.bounds.contains(x, y)) {
                    tipText = hit.text;
                    tipX = hit.x;
                    tipY = hit.y;
                    invalidate();
                    return;
                }
            }

            tipText = null;
            invalidate();
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    public static final class MonthTrendView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<ChartHit> hits = new ArrayList<>();
        private List<DailySummary> summaries = new ArrayList<>();
        private String tipText = null;
        private float tipX;
        private float tipY;

        public MonthTrendView(android.content.Context context) {
            super(context);
        }

        public void setSummaries(List<DailySummary> values) {
            summaries = values == null ? new ArrayList<>() : values;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            hits.clear();
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
            ArrayList<float[]> points = new ArrayList<>();
            for (int i = 0; i < summaries.size(); i++) {
                float x = summaries.size() == 1 ? getWidth() / 2f : dpLocal(10) + i * ((getWidth() - dpLocal(20)) / (summaries.size() - 1));
                float y = getHeight() - dpLocal(18) - summaries.get(i).totalSeconds * (getHeight() - dpLocal(36)) / (float) max;
                points.add(new float[] { x, y });
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
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_BLUE);
            for (int i = 0; i < points.size(); i++) {
                float x = points.get(i)[0];
                float y = points.get(i)[1];
                canvas.drawCircle(x, y, dpLocal(3.5f), paint);
                RectF hit = new RectF(x - dpLocal(12), y - dpLocal(12), x + dpLocal(12), y + dpLocal(12));
                hits.add(new ChartHit(hit, formatTooltipHours(getContext(), summaries.get(i).totalSeconds), x, y));
            }
            drawChartTip(canvas, paint, tipText, tipX, tipY, getResources().getDisplayMetrics().density, getWidth(), AppFonts.bold(getContext()));
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return true;
            }

            updateTip(event.getX(), event.getY());
            return true;
        }

        private void updateTip(float x, float y) {
            for (ChartHit hit : hits) {
                if (hit.bounds.contains(x, y)) {
                    tipText = hit.text;
                    tipX = hit.x;
                    tipY = hit.y;
                    invalidate();
                    return;
                }
            }

            tipText = null;
            invalidate();
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
            String[] labels = {
                    getContext().getString(R.string.stats_sessions_band_under30),
                    getContext().getString(R.string.stats_sessions_band_30to45),
                    getContext().getString(R.string.stats_sessions_band_over45)
            };
            int[] colors = { COLOR_GREEN, COLOR_YELLOW, COLOR_RED };
            paint.setTypeface(AppFonts.regular(getContext()));
            paint.setTextSize(dpLocal(12));
            paint.setTextAlign(Paint.Align.LEFT);
            for (int i = 0; i < 3; i++) {
                float y = dpLocal(18) + i * dpLocal(42);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(COLOR_MUTED);
                canvas.drawText(labels[i], 0, y, paint);
                float left = dpLocal(82);
                float top = y - dpLocal(12);
                float width = Math.max(dpLocal(20), getWidth() - dpLocal(154));
                paint.setColor(Color.rgb(232, 243, 239));
                canvas.drawRect(left, top, left + width, top + dpLocal(12), paint);
                paint.setColor(colors[i]);
                canvas.drawRect(left, top, left + width * counts[i] / max, top + dpLocal(12), paint);
                paint.setColor(COLOR_TEXT);
                canvas.drawText(formatResource(getContext(), R.string.stats_sessions_count, "count", counts[i]), getWidth() - dpLocal(60), y, paint);
            }
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    public static final class AppUsageBarView extends View {
        private float progress;

        public AppUsageBarView(android.content.Context context) {
            super(context);
        }

        public void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            float radius = dpLocal(4);
            float centerY = getHeight() / 2f;
            RectF track = new RectF(0, centerY - radius, getWidth(), centerY + radius);
            paint.setColor(Color.rgb(232, 243, 239));
            canvas.drawRoundRect(track, radius, radius, paint);
            if (progress <= 0f) {
                return;
            }
            RectF fill = new RectF(0, centerY - radius, Math.max(dpLocal(8), getWidth() * progress), centerY + radius);
            paint.setColor(Color.rgb(66, 133, 244));
            canvas.drawRoundRect(fill, radius, radius, paint);
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    public static final class DeviceSourceIconView extends View {
        private final boolean pc;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public DeviceSourceIconView(android.content.Context context, boolean pc) {
            super(context);
            this.pc = pc;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpLocal(pc ? 2.1f : 2.3f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(pc ? COLOR_GREEN : COLOR_BLUE);

            if (pc) {
                RectF screen = new RectF(width * 0.12f, height * 0.18f, width * 0.88f, height * 0.68f);
                canvas.drawRoundRect(screen, dpLocal(2.5f), dpLocal(2.5f), paint);
                canvas.drawLine(width * 0.50f, height * 0.68f, width * 0.50f, height * 0.86f, paint);
                canvas.drawLine(width * 0.34f, height * 0.86f, width * 0.66f, height * 0.86f, paint);
                return;
            }

            RectF phone = new RectF(width * 0.28f, height * 0.08f, width * 0.72f, height * 0.92f);
            canvas.drawRoundRect(phone, dpLocal(3.2f), dpLocal(3.2f), paint);
            canvas.drawLine(width * 0.43f, height * 0.80f, width * 0.57f, height * 0.80f, paint);
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
