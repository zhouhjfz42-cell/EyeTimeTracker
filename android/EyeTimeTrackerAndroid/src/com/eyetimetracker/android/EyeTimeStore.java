package com.eyetimetracker.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

public final class EyeTimeStore {
    private static final String PREFS = "eye_time_tracker";
    private static final String STATE = "state_json";
    private static final String DEVICE_ID = "device_id";
    private static final String REMINDER_MINUTES = "reminder_minutes";
    private static final String REPEAT_REMINDER = "repeat_reminder";
    private static final String RESET_DATE = "display_reset_date";
    private static final String RESET_TODAY_SECONDS = "display_reset_today_seconds";
    private static final String RESET_YESTERDAY_SECONDS = "display_reset_yesterday_seconds";
    private static final String RESET_WEEK_START = "display_reset_week_start";
    private static final String RESET_WEEK_SECONDS = "display_reset_week_seconds";
    private static final String RESET_MONTH_START = "display_reset_month_start";
    private static final String RESET_MONTH_SECONDS = "display_reset_month_seconds";
    private static final String PLATFORM = "android";

    private final SharedPreferences prefs;

    public EyeTimeStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureDeviceId();
    }

    public synchronized String getDeviceId() {
        return ensureDeviceId();
    }

    public synchronized DailySummary getDay(LocalDate date) {
        try {
            JSONObject record = getOrCreateRecord(loadState(), date.toString());
            return new DailySummary(
                    date.toString(),
                    record.optLong("totalSeconds", 0L),
                    record.optBoolean("reminderShown", false),
                    record.optInt("lastReminderStep", record.optBoolean("reminderShown", false) ? 1 : 0));
        } catch (JSONException ex) {
            return new DailySummary(date.toString(), 0L, false, 0);
        }
    }

    public synchronized void addSeconds(LocalDate date, long secondsToAdd) {
        if (secondsToAdd <= 0L) {
            return;
        }
        try {
            JSONObject state = loadState();
            JSONObject record = getOrCreateRecord(state, date.toString());
            record.put("totalSeconds", record.optLong("totalSeconds", 0L) + secondsToAdd);
            record.put("updatedAt", System.currentTimeMillis());
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized boolean isReminderShown(LocalDate date) {
        return getDay(date).reminderShown;
    }

    public synchronized void markReminderShown(LocalDate date, int reminderStep) {
        try {
            JSONObject state = loadState();
            JSONObject record = getOrCreateRecord(state, date.toString());
            record.put("reminderShown", true);
            record.put("lastReminderStep", Math.max(1, reminderStep));
            record.put("updatedAt", System.currentTimeMillis());
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized int getReminderMinutes() {
        return ReminderThreshold.clampMinutes(prefs.getInt(REMINDER_MINUTES, ReminderThreshold.DEFAULT_MINUTES));
    }

    public synchronized boolean isRepeatReminderEnabled() {
        return prefs.getBoolean(REPEAT_REMINDER, false);
    }

    public synchronized void saveReminderSettings(int reminderMinutes, boolean repeatReminder) {
        prefs.edit()
                .putInt(REMINDER_MINUTES, ReminderThreshold.clampMinutes(reminderMinutes))
                .putBoolean(REPEAT_REMINDER, repeatReminder)
                .apply();
    }

    public synchronized void resetDisplay(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        LocalDate weekStart = weekStart(today);
        LocalDate monthStart = today.withDayOfMonth(1);
        prefs.edit()
                .putString(RESET_DATE, today.toString())
                .putLong(RESET_TODAY_SECONDS, getDay(today).totalSeconds)
                .putLong(RESET_YESTERDAY_SECONDS, getDay(yesterday).totalSeconds)
                .putString(RESET_WEEK_START, weekStart.toString())
                .putLong(RESET_WEEK_SECONDS, sumRange(weekStart, today))
                .putString(RESET_MONTH_START, monthStart.toString())
                .putLong(RESET_MONTH_SECONDS, sumRange(monthStart, today))
                .apply();
    }

    public synchronized long displayTodaySeconds(LocalDate today) {
        long raw = getDay(today).totalSeconds;
        if (today.toString().equals(prefs.getString(RESET_DATE, ""))) {
            return Math.max(0L, raw - prefs.getLong(RESET_TODAY_SECONDS, 0L));
        }
        return raw;
    }

    public synchronized long displayYesterdaySeconds(LocalDate today) {
        long raw = getDay(today.minusDays(1)).totalSeconds;
        if (today.toString().equals(prefs.getString(RESET_DATE, ""))) {
            return Math.max(0L, raw - prefs.getLong(RESET_YESTERDAY_SECONDS, 0L));
        }
        return raw;
    }

    public synchronized long displayWeekSeconds(LocalDate today) {
        LocalDate weekStart = weekStart(today);
        long raw = sumRange(weekStart, today);
        if (weekStart.toString().equals(prefs.getString(RESET_WEEK_START, ""))) {
            return Math.max(0L, raw - prefs.getLong(RESET_WEEK_SECONDS, 0L));
        }
        return raw;
    }

    public synchronized long displayMonthSeconds(LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        long raw = sumRange(monthStart, today);
        if (monthStart.toString().equals(prefs.getString(RESET_MONTH_START, ""))) {
            return Math.max(0L, raw - prefs.getLong(RESET_MONTH_SECONDS, 0L));
        }
        return raw;
    }

    public synchronized long sumRange(LocalDate start, LocalDate end) {
        long total = 0L;
        try {
            JSONObject state = loadState();
            JSONArray records = state.optJSONArray("records");
            if (records == null) {
                return 0L;
            }
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record == null) {
                    continue;
                }
                LocalDate date = LocalDate.parse(record.optString("date", "1970-01-01"));
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    total += record.optLong("totalSeconds", 0L);
                }
            }
        } catch (Exception ignored) {
            return total;
        }
        return total;
    }

    private String ensureDeviceId() {
        String id = prefs.getString(DEVICE_ID, null);
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(DEVICE_ID, id).apply();
        }
        return id;
    }

    private JSONObject loadState() throws JSONException {
        String raw = prefs.getString(STATE, null);
        if (raw == null || raw.trim().isEmpty()) {
            JSONObject state = new JSONObject();
            state.put("deviceId", ensureDeviceId());
            state.put("platform", PLATFORM);
            state.put("records", new JSONArray());
            return state;
        }
        JSONObject state = new JSONObject(raw);
        if (!state.has("records")) {
            state.put("records", new JSONArray());
        }
        return state;
    }

    private void saveState(JSONObject state) {
        prefs.edit().putString(STATE, state.toString()).apply();
    }

    private JSONObject getOrCreateRecord(JSONObject state, String date) throws JSONException {
        JSONArray records = state.getJSONArray("records");
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (date.equals(record.optString("date"))) {
                return record;
            }
        }
        JSONObject record = new JSONObject();
        record.put("deviceId", ensureDeviceId());
        record.put("platform", PLATFORM);
        record.put("date", date);
        record.put("totalSeconds", 0L);
        record.put("reminderShown", false);
        record.put("lastReminderStep", 0);
        record.put("updatedAt", System.currentTimeMillis());
        records.put(record);
        return record;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }
}
