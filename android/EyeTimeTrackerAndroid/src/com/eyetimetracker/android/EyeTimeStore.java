package com.eyetimetracker.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EyeTimeStore {
    private static final String PREFS = "eye_time_tracker";
    private static final String STATE = "state_json";
    private static final String DEVICE_ID = "device_id";
    private static final String REMINDER_MINUTES = "reminder_minutes";
    private static final String REPEAT_REMINDER = "repeat_reminder";
    private static final String SYNC_IS_PAIRED = "sync_is_paired";
    private static final String SYNC_PEER_DEVICE_ID = "sync_peer_device_id";
    private static final String SYNC_PEER_PLATFORM = "sync_peer_platform";
    private static final String SYNC_PEER_HOST = "sync_peer_host";
    private static final String SYNC_PEER_PORT = "sync_peer_port";
    private static final String SYNC_SHARED_SECRET = "sync_shared_secret";
    private static final String SYNC_LAST_SYNC_UNIX_SECONDS = "sync_last_sync_unix_seconds";
    private static final String SYNC_LAST_ERROR = "sync_last_error";
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
            JSONObject state = loadState();
            JSONObject record = getOrCreateRecord(state, date.toString());
            DailySummary legacy = readLegacyDailySummary(date.toString(), record);
            List<UsageSegment> segments = readSegments(state, date, date);
            if (!segments.isEmpty()) {
                DailySummary merged = UsageSegmentMerger.buildDailySummary(date.toString(), segments);
                return DailySummaryReconciler.preserveVisibleTotal(legacy, merged);
            }
            return legacy;
        } catch (JSONException ex) {
            return new DailySummary(date.toString(), 0L, false, 0);
        }
    }

    public synchronized void addSeconds(LocalDate date, long secondsToAdd) {
        addSeconds(date, secondsToAdd, System.currentTimeMillis() / 1000L, "android-screen");
    }

    public synchronized void addSeconds(LocalDate date, long secondsToAdd, long endUnixSeconds, String source) {
        if (secondsToAdd <= 0L) {
            return;
        }
        try {
            JSONObject state = loadState();
            JSONObject record = getOrCreateRecord(state, date.toString());
            record.put("totalSeconds", record.optLong("totalSeconds", 0L) + secondsToAdd);
            JSONArray hourlySeconds = ensureHourlySeconds(record);
            int hour = java.time.LocalTime.now().getHour();
            hourlySeconds.put(hour, hourlySeconds.optLong(hour, 0L) + secondsToAdd);
            record.put("currentSessionSeconds", record.optLong("currentSessionSeconds", 0L) + secondsToAdd);
            record.put("updatedAt", System.currentTimeMillis());
            addSegment(state, createSegment(date, secondsToAdd, endUnixSeconds, source));
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized void addSegment(UsageSegment segment) {
        try {
            JSONObject state = loadState();
            addSegment(state, segment);
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized List<UsageSegment> getSegments(LocalDate start, LocalDate end) {
        try {
            return readSegments(loadState(), start, end);
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
    }

    public synchronized void finishCurrentSession(LocalDate date) {
        try {
            JSONObject state = loadState();
            JSONObject record = getOrCreateRecord(state, date.toString());
            long currentSessionSeconds = record.optLong("currentSessionSeconds", 0L);
            if (currentSessionSeconds > 0L) {
                JSONArray sessions = ensureSessionSeconds(record);
                sessions.put(currentSessionSeconds);
                record.put("currentSessionSeconds", 0L);
                record.put("updatedAt", System.currentTimeMillis());
                saveState(state);
            }
        } catch (JSONException ignored) {
        }
    }

    public synchronized List<DailySummary> getDays(LocalDate start, LocalDate end) {
        List<DailySummary> summaries = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            summaries.add(getDay(cursor));
            cursor = cursor.plusDays(1);
        }
        return summaries;
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

    public synchronized SyncSettings getSyncSettings() {
        SyncSettings settings = new SyncSettings();
        settings.isPaired = prefs.getBoolean(SYNC_IS_PAIRED, false);
        settings.peerDeviceId = prefs.getString(SYNC_PEER_DEVICE_ID, "");
        settings.peerPlatform = prefs.getString(SYNC_PEER_PLATFORM, "");
        settings.peerHost = prefs.getString(SYNC_PEER_HOST, "");
        settings.peerPort = prefs.getInt(SYNC_PEER_PORT, 0);
        settings.sharedSecret = prefs.getString(SYNC_SHARED_SECRET, "");
        settings.lastSyncUnixSeconds = prefs.getLong(SYNC_LAST_SYNC_UNIX_SECONDS, 0L);
        settings.lastError = prefs.getString(SYNC_LAST_ERROR, "");
        return settings;
    }

    public synchronized void saveSyncSettings(SyncSettings settings) {
        if (settings == null) {
            settings = SyncSettings.unpaired();
        }
        prefs.edit()
                .putBoolean(SYNC_IS_PAIRED, settings.isPaired)
                .putString(SYNC_PEER_DEVICE_ID, safe(settings.peerDeviceId))
                .putString(SYNC_PEER_PLATFORM, safe(settings.peerPlatform))
                .putString(SYNC_PEER_HOST, safe(settings.peerHost))
                .putInt(SYNC_PEER_PORT, settings.peerPort)
                .putString(SYNC_SHARED_SECRET, safe(settings.sharedSecret))
                .putLong(SYNC_LAST_SYNC_UNIX_SECONDS, settings.lastSyncUnixSeconds)
                .putString(SYNC_LAST_ERROR, safe(settings.lastError))
                .apply();
    }

    public synchronized void saveSyncResult(SyncSettings settings) {
        if (settings == null) {
            return;
        }
        prefs.edit()
                .putLong(SYNC_LAST_SYNC_UNIX_SECONDS, settings.lastSyncUnixSeconds)
                .putString(SYNC_LAST_ERROR, safe(settings.lastError))
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
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            total += getDay(cursor).totalSeconds;
            cursor = cursor.plusDays(1);
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
            state.put("segments", new JSONArray());
            return state;
        }
        JSONObject state = new JSONObject(raw);
        if (!state.has("records")) {
            state.put("records", new JSONArray());
        }
        if (!state.has("segments")) {
            state.put("segments", new JSONArray());
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
        record.put("hourlySeconds", new JSONArray());
        record.put("sessionSeconds", new JSONArray());
        record.put("currentSessionSeconds", 0L);
        record.put("reminderShown", false);
        record.put("lastReminderStep", 0);
        record.put("updatedAt", System.currentTimeMillis());
        records.put(record);
        return record;
    }

    private UsageSegment createSegment(LocalDate date, long secondsToAdd, long endUnixSeconds, String source) {
        long startUnixSeconds = endUnixSeconds - secondsToAdd;
        String safeSource = source == null || source.trim().isEmpty() ? "android-screen" : source;
        return new UsageSegment(
                UsageSegmentId.create(ensureDeviceId(), safeSource, startUnixSeconds, endUnixSeconds),
                ensureDeviceId(),
                PLATFORM,
                safeSource,
                startUnixSeconds,
                endUnixSeconds,
                date.toString(),
                startUnixSeconds,
                endUnixSeconds);
    }

    private static void addSegment(JSONObject state, UsageSegment segment) throws JSONException {
        JSONArray segments = ensureSegments(state);
        for (int i = 0; i < segments.length(); i++) {
            JSONObject existing = segments.optJSONObject(i);
            if (existing != null && segment.segmentId.equals(existing.optString("segmentId"))) {
                return;
            }
        }
        segments.put(segmentToJson(segment));
    }

    private static JSONArray ensureSegments(JSONObject state) throws JSONException {
        JSONArray segments = state.optJSONArray("segments");
        if (segments == null) {
            segments = new JSONArray();
            state.put("segments", segments);
        }
        return segments;
    }

    private static JSONObject segmentToJson(UsageSegment segment) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("segmentId", segment.segmentId);
        json.put("deviceId", segment.deviceId);
        json.put("platform", segment.platform);
        json.put("source", segment.source);
        json.put("startUnixSeconds", segment.startUnixSeconds);
        json.put("endUnixSeconds", segment.endUnixSeconds);
        json.put("localDate", segment.localDate);
        json.put("createdAtUnixSeconds", segment.createdAtUnixSeconds);
        json.put("updatedAtUnixSeconds", segment.updatedAtUnixSeconds);
        return json;
    }

    private static UsageSegment segmentFromJson(JSONObject json) {
        return new UsageSegment(
                json.optString("segmentId", ""),
                json.optString("deviceId", ""),
                json.optString("platform", ""),
                json.optString("source", ""),
                json.optLong("startUnixSeconds", 0L),
                json.optLong("endUnixSeconds", 0L),
                json.optString("localDate", ""),
                json.optLong("createdAtUnixSeconds", 0L),
                json.optLong("updatedAtUnixSeconds", 0L));
    }

    private static List<UsageSegment> readSegments(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        JSONArray raw = ensureSegments(state);
        List<UsageSegment> values = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject json = raw.optJSONObject(i);
            if (json == null) {
                continue;
            }
            UsageSegment segment = segmentFromJson(json);
            if (segment.localDate == null || segment.localDate.trim().isEmpty()) {
                continue;
            }
            LocalDate date = LocalDate.parse(segment.localDate);
            if (!date.isBefore(start) && !date.isAfter(end)) {
                values.add(segment);
            }
        }
        return values;
    }

    private static JSONArray ensureHourlySeconds(JSONObject record) throws JSONException {
        JSONArray hourlySeconds = record.optJSONArray("hourlySeconds");
        if (hourlySeconds == null) {
            hourlySeconds = new JSONArray();
            record.put("hourlySeconds", hourlySeconds);
        }
        while (hourlySeconds.length() < 24) {
            hourlySeconds.put(0L);
        }
        return hourlySeconds;
    }

    private static JSONArray ensureSessionSeconds(JSONObject record) throws JSONException {
        JSONArray sessions = record.optJSONArray("sessionSeconds");
        if (sessions == null) {
            sessions = new JSONArray();
            record.put("sessionSeconds", sessions);
        }
        return sessions;
    }

    private static long[] readHourlySeconds(JSONObject record) throws JSONException {
        JSONArray hourly = ensureHourlySeconds(record);
        long[] values = new long[24];
        for (int i = 0; i < values.length; i++) {
            values[i] = hourly.optLong(i, 0L);
        }
        return values;
    }

    private static long[] readSessionSeconds(JSONObject record) throws JSONException {
        JSONArray sessions = ensureSessionSeconds(record);
        long[] values = new long[sessions.length()];
        for (int i = 0; i < values.length; i++) {
            values[i] = sessions.optLong(i, 0L);
        }
        return values;
    }

    private static DailySummary readLegacyDailySummary(String date, JSONObject record) throws JSONException {
        boolean reminderShown = record.optBoolean("reminderShown", false);
        return new DailySummary(
                date,
                record.optLong("totalSeconds", 0L),
                readHourlySeconds(record),
                readSessionSeconds(record),
                record.optLong("currentSessionSeconds", 0L),
                reminderShown,
                record.optInt("lastReminderStep", reminderShown ? 1 : 0));
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
