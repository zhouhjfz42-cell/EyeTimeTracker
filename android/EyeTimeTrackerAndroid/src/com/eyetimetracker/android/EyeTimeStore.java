package com.eyetimetracker.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class EyeTimeStore {
    private static final String DIAG_TAG = "EyeTimeDiag";
    private static final String PREFS = "eye_time_tracker";
    private static final String STATE = "state_json";
    private static final String DAILY_STATS_CACHE = "dailyStatsCache";
    private static final String FAMILY_CHILD_DAILY_STATS_CACHE = "familyChildDailyStatsCache";
    private static final String FAMILY_CHILD_DAILY_STATS_CACHE_CHANGED = "_familyChildDailyStatsCacheChanged";
    private static final String PRODUCT_MODE = "productMode";
    private static final String DEVICE_ROLE = "deviceRole";
    private static final String FAMILY_ID = "familyId";
    private static final String FAMILY_CHILD_DEVICE_ID = "familyChildDeviceId";
    private static final String FAMILY_CHILD_DEVICE_JOINED_AT_UNIX_SECONDS = "familyChildDeviceJoinedAtUnixSeconds";
    private static final String FAMILY_CHILD_SEGMENTS = "familyChildSegments";
    private static final String PENDING_FAMILY_BINDING_INVITE = "pendingFamilyBindingInvite";
    private static final String CHILD_PROFILES = "childProfiles";
    private static final String ACTIVE_CHILD_ID = "activeChildId";
    private static final String PARENT_PASSCODE_HASH = "parentPasscodeHash";
    private static final String PARENT_PASSCODE_SALT = "parentPasscodeSalt";
    private static final String PARENT_PASSCODE_UPDATED_AT = "parentPasscodeUpdatedAtUnixSeconds";
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
    private static final String LOCAL_REMINDER_COUNTING = "local_reminder_counting";
    private static final String LOCAL_REMINDER_SESSION_STARTED = "local_reminder_session_started";
    private static final String PEER_REMINDER_DEVICE_ID = "peer_reminder_device_id";
    private static final String PEER_REMINDER_PLATFORM = "peer_reminder_platform";
    private static final String PEER_REMINDER_COUNTING = "peer_reminder_counting";
    private static final String PEER_REMINDER_SESSION_STARTED = "peer_reminder_session_started";
    private static final String FAMILY_CHILD_REMINDER_DATE = "family_child_reminder_date";
    private static final String FAMILY_CHILD_REMINDER_SHOWN = "family_child_reminder_shown";
    private static final String FAMILY_CHILD_LAST_REMINDER_STEP = "family_child_last_reminder_step";
    private static final String RESET_DATE = "display_reset_date";
    private static final String RESET_TODAY_SECONDS = "display_reset_today_seconds";
    private static final String RESET_YESTERDAY_SECONDS = "display_reset_yesterday_seconds";
    private static final String RESET_WEEK_START = "display_reset_week_start";
    private static final String RESET_WEEK_SECONDS = "display_reset_week_seconds";
    private static final String RESET_MONTH_START = "display_reset_month_start";
    private static final String RESET_MONTH_SECONDS = "display_reset_month_seconds";
    private static final String MAIN_ACTIVITY_VISIBLE = "main_activity_visible";
    private static final String PLATFORM = "android";
    private static final int PARENT_PASSCODE_SALT_BYTES = 16;
    private static final int PARENT_PASSCODE_ITERATIONS = 120_000;
    private static final int PARENT_PASSCODE_HASH_BITS = 256;
    private static final String PARENT_PASSCODE_ALGORITHM = "PBKDF2WithHmacSHA256";

    private final SharedPreferences prefs;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
            DailyStatsSnapshot cached = readCachedDailyStats(state, date);
            if (cached != null) {
                return cached.summary;
            }
            DailyStatsSnapshot snapshot = buildDailyStatsSnapshot(state, date, null);
            if (cacheDailyStatsIfStable(state, date, snapshot)) {
                saveState(state);
            }
            return snapshot.summary;
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
            removeDailyStatsCache(state, date.toString());
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized void addSegment(UsageSegment segment) {
        try {
            JSONObject state = loadState();
            addSegment(state, segment);
            if (segment != null) {
                removeDailyStatsCache(state, segment.localDate);
            }
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized int addSegments(List<UsageSegment> newSegments) {
        try {
            JSONObject state = loadState();
            int changed = mergeSegments(state, newSegments);
            if (changed > 0) {
                for (String date : segmentDates(newSegments)) {
                    removeDailyStatsCache(state, date);
                }
                saveState(state);
            }
            return changed;
        } catch (JSONException ignored) {
            return 0;
        }
    }

    public synchronized int addFamilyChildSegments(List<UsageSegment> newSegments) {
        try {
            JSONObject state = loadState();
            int changed = mergeFamilyChildSegments(state, newSegments);
            if (changed > 0) {
                for (String date : segmentDates(newSegments)) {
                    removeFamilyChildDailyStatsCache(state, date);
                }
                warmPastFamilyChildDailyStatsCache(state, LocalDate.now());
                saveState(state);
            }
            return changed;
        } catch (JSONException ignored) {
            return 0;
        }
    }

    public synchronized List<UsageSegment> getSegments(LocalDate start, LocalDate end) {
        try {
            return readEffectiveSegments(loadState(), start, end);
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
    }

    public synchronized List<UsageSegment> getFamilyChildSegments(LocalDate start, LocalDate end) {
        try {
            return readFamilyChildSegments(loadState(), start, end);
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
    }

    public synchronized DailySummary getFamilyChildDay(LocalDate date) {
        List<DailySummary> values = getFamilyChildDays(date, date);
        if (!values.isEmpty()) {
            return values.get(0);
        }
        return new DailySummary(date.toString(), 0L, false, 0);
    }

    public synchronized List<DailySummary> getFamilyChildDays(LocalDate start, LocalDate end) {
        long startedAt = System.currentTimeMillis();
        List<DailySummary> values = new ArrayList<>();
        try {
            JSONObject state = loadState();
            for (DailyStatsSnapshot snapshot : buildFamilyChildSnapshots(state, start, end)) {
                values.add(snapshot.summary);
            }
            saveFamilyChildCacheIfChanged(state);
        } catch (JSONException ignored) {
            values.clear();
        }
        Log.i(DIAG_TAG, "EyeTimeStore getFamilyChildDays range=" + start + ".." + end
                + " days=" + values.size()
                + " ms=" + elapsed(startedAt));
        return values;
    }

    public synchronized DeviceUsageBreakdown getFamilyChildDeviceBreakdown(LocalDate date) {
        List<DeviceUsageBreakdown> values = getFamilyChildDeviceBreakdowns(date, date);
        return values.isEmpty() ? new DeviceUsageBreakdown(0L, 0L) : values.get(0);
    }

    public synchronized List<DeviceUsageBreakdown> getFamilyChildDeviceBreakdowns(LocalDate start, LocalDate end) {
        List<DeviceUsageBreakdown> values = new ArrayList<>();
        try {
            JSONObject state = loadState();
            for (DailyStatsSnapshot snapshot : buildFamilyChildSnapshots(state, start, end)) {
                values.add(snapshot.breakdown);
            }
            saveFamilyChildCacheIfChanged(state);
        } catch (JSONException ignored) {
            values.clear();
        }
        if (values.isEmpty()) {
            values.add(new DeviceUsageBreakdown(0L, 0L));
        }
        return values;
    }

    public synchronized DeviceUsageBreakdown getDeviceBreakdown(LocalDate date) {
        List<DeviceUsageBreakdown> values = getDeviceBreakdowns(date, date);
        return values.isEmpty() ? new DeviceUsageBreakdown(0L, 0L) : values.get(0);
    }

    public synchronized List<DeviceUsageBreakdown> getDeviceBreakdowns(LocalDate start, LocalDate end) {
        List<DeviceUsageBreakdown> values = new ArrayList<>();
        try {
            JSONObject state = loadState();
            boolean hasMissing = false;
            for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
                DailyStatsSnapshot cached = readCachedDailyStats(state, cursor);
                if (cached != null) {
                    values.add(cached.breakdown);
                } else {
                    values.add(null);
                    hasMissing = true;
                }
            }
            if (hasMissing) {
                Map<String, List<UsageSegment>> segmentsByDate = groupSegmentsByDate(readEffectiveSegments(state, start, end));
                boolean changedCache = false;
                int index = 0;
                for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
                    if (values.get(index) == null) {
                        DailyStatsSnapshot snapshot = buildDailyStatsSnapshot(state, cursor, segmentsByDate.get(cursor.toString()));
                        values.set(index, snapshot.breakdown);
                        changedCache |= cacheDailyStatsIfStable(state, cursor, snapshot);
                    }
                    index++;
                }
                if (changedCache) {
                    saveState(state);
                }
            }
        } catch (JSONException ignored) {
            values.clear();
        }
        if (values.isEmpty()) {
            values.add(new DeviceUsageBreakdown(0L, 0L));
        }
        return values;
    }

    public synchronized List<DailySummary> getDays(LocalDate start, LocalDate end) {
        long startedAt = System.currentTimeMillis();
        List<DailySummary> summaries = new ArrayList<>();
        try {
            JSONObject state = loadState();
            boolean hasMissing = false;
            for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
                DailyStatsSnapshot cached = readCachedDailyStats(state, cursor);
                if (cached != null) {
                    summaries.add(cached.summary);
                } else {
                    summaries.add(null);
                    hasMissing = true;
                }
            }
            if (hasMissing) {
                Map<String, List<UsageSegment>> segmentsByDate = groupSegmentsByDate(readEffectiveSegments(state, start, end));
                boolean changedCache = false;
                int index = 0;
                for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
                    if (summaries.get(index) == null) {
                        DailyStatsSnapshot snapshot = buildDailyStatsSnapshot(state, cursor, segmentsByDate.get(cursor.toString()));
                        summaries.set(index, snapshot.summary);
                        changedCache |= cacheDailyStatsIfStable(state, cursor, snapshot);
                    }
                    index++;
                }
                if (changedCache) {
                    saveState(state);
                }
            }
        } catch (JSONException ignored) {
            summaries.clear();
        }
        Log.i(DIAG_TAG, "EyeTimeStore getDays range=" + start + ".." + end
                + " days=" + summaries.size()
                + " ms=" + elapsed(startedAt));
        return summaries;
    }

    public synchronized void warmPastDailyStatsCache(LocalDate today) {
        long startedAt = System.currentTimeMillis();
        try {
            JSONObject state = loadState();
            Set<String> dates = collectPastDates(state, today);
            if (dates.isEmpty()) {
                return;
            }

            LocalDate start = null;
            LocalDate end = null;
            List<LocalDate> missingDates = new ArrayList<>();
            for (String dateValue : dates) {
                LocalDate date = parseDateOrNull(dateValue);
                if (date == null || readCachedDailyStats(state, date) != null) {
                    continue;
                }
                missingDates.add(date);
                if (start == null || date.isBefore(start)) {
                    start = date;
                }
                if (end == null || date.isAfter(end)) {
                    end = date;
                }
            }

            if (missingDates.isEmpty() || start == null || end == null) {
                return;
            }

            Map<String, List<UsageSegment>> segmentsByDate = groupSegmentsByDate(readEffectiveSegments(state, start, end));
            boolean changedCache = false;
            for (LocalDate date : missingDates) {
                DailyStatsSnapshot snapshot = buildDailyStatsSnapshot(state, date, segmentsByDate.get(date.toString()));
                changedCache |= cacheDailyStatsIfStable(state, date, snapshot);
            }
            if (changedCache) {
                saveState(state);
            }
            Log.i(DIAG_TAG, "EyeTimeStore warmPastDailyStatsCache dates=" + missingDates.size()
                    + " range=" + start + ".." + end
                    + " ms=" + elapsed(startedAt));
        } catch (JSONException ignored) {
        }
    }

    public synchronized void warmPastFamilyChildDailyStatsCache(LocalDate today) {
        long startedAt = System.currentTimeMillis();
        try {
            JSONObject state = loadState();
            int cached = warmPastFamilyChildDailyStatsCache(state, today);
            if (cached > 0) {
                saveState(state);
            }
            Log.i(DIAG_TAG, "EyeTimeStore warmPastFamilyChildDailyStatsCache dates=" + cached
                    + " ms=" + elapsed(startedAt));
        } catch (JSONException ignored) {
        }
    }

    private static int warmPastFamilyChildDailyStatsCache(JSONObject state, LocalDate today) throws JSONException {
        Set<String> dates = collectPastFamilyChildDates(state, today);
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate start = null;
        LocalDate end = null;
        List<LocalDate> missingDates = new ArrayList<>();
        for (String dateValue : dates) {
            LocalDate date = parseDateOrNull(dateValue);
            if (date == null || readCachedFamilyChildDailyStats(state, date) != null) {
                continue;
            }
            missingDates.add(date);
            if (start == null || date.isBefore(start)) {
                start = date;
            }
            if (end == null || date.isAfter(end)) {
                end = date;
            }
        }
        if (missingDates.isEmpty() || start == null || end == null) {
            return 0;
        }
        Map<String, List<UsageSegment>> segmentsByDate = groupSegmentsByDate(readFamilyChildSegments(state, start, end));
        int changed = 0;
        for (LocalDate date : missingDates) {
            DailyStatsSnapshot snapshot = buildFamilyChildStatsSnapshot(state, date, segmentsByDate.get(date.toString()));
            if (cacheFamilyChildDailyStatsIfStable(state, date, snapshot)) {
                changed++;
            }
        }
        return changed;
    }

    private DailyStatsSnapshot buildDailyStatsSnapshot(
            JSONObject state,
            LocalDate date,
            List<UsageSegment> effectiveSegments) throws JSONException {
        String dateValue = date.toString();
        JSONObject record = getOrCreateRecord(state, dateValue);
        DailySummary legacy = readLegacyDailySummary(dateValue, record);
        List<UsageSegment> segments = effectiveSegments == null
                ? readEffectiveSegments(state, date, date)
                : effectiveSegments;
        DailySummary summary = legacy;
        if (!segments.isEmpty()) {
            DailySummary merged = UsageSegmentMerger.buildDailySummary(dateValue, segments);
            summary = DailySummaryReconciler.useSegmentSummaryForSyncedDay(legacy, merged);
        }
        return new DailyStatsSnapshot(summary, DeviceUsageBreakdown.build(dateValue, segments));
    }

    private DailyStatsSnapshot readCachedDailyStats(JSONObject state, LocalDate date) throws JSONException {
        return readCachedDailyStats(state, date, DAILY_STATS_CACHE);
    }

    private static DailyStatsSnapshot readCachedFamilyChildDailyStats(JSONObject state, LocalDate date) throws JSONException {
        return readCachedDailyStats(state, date, FAMILY_CHILD_DAILY_STATS_CACHE);
    }

    private static DailyStatsSnapshot readCachedDailyStats(JSONObject state, LocalDate date, String cacheKey) throws JSONException {
        if (!isStableCacheDate(date)) {
            return null;
        }
        JSONArray cache = state.optJSONArray(cacheKey);
        if (cache == null) {
            return null;
        }
        String dateValue = date.toString();
        for (int i = 0; i < cache.length(); i++) {
            JSONObject item = cache.optJSONObject(i);
            if (item != null && dateValue.equals(item.optString("date"))) {
                return dailyStatsFromJson(item);
            }
        }
        return null;
    }

    private boolean cacheDailyStatsIfStable(JSONObject state, LocalDate date, DailyStatsSnapshot snapshot) throws JSONException {
        return cacheDailyStatsIfStable(state, date, snapshot, DAILY_STATS_CACHE);
    }

    private static boolean cacheFamilyChildDailyStatsIfStable(JSONObject state, LocalDate date, DailyStatsSnapshot snapshot) throws JSONException {
        return cacheDailyStatsIfStable(state, date, snapshot, FAMILY_CHILD_DAILY_STATS_CACHE);
    }

    private static boolean cacheDailyStatsIfStable(JSONObject state, LocalDate date, DailyStatsSnapshot snapshot, String cacheKey) throws JSONException {
        if (!isStableCacheDate(date) || snapshot == null) {
            return false;
        }
        removeDailyStatsCache(state, date.toString(), cacheKey);
        JSONArray cache = state.optJSONArray(cacheKey);
        if (cache == null) {
            cache = new JSONArray();
            state.put(cacheKey, cache);
        }
        cache.put(dailyStatsToJson(snapshot));
        return true;
    }

    private static boolean isStableCacheDate(LocalDate date) {
        return date != null && date.isBefore(LocalDate.now());
    }

    private static void removeDailyStatsCache(JSONObject state, String date) throws JSONException {
        removeDailyStatsCache(state, date, DAILY_STATS_CACHE);
    }

    private static void removeFamilyChildDailyStatsCache(JSONObject state, String date) throws JSONException {
        removeDailyStatsCache(state, date, FAMILY_CHILD_DAILY_STATS_CACHE);
    }

    private static void removeDailyStatsCache(JSONObject state, String date, String cacheKey) throws JSONException {
        if (state == null || date == null || date.trim().isEmpty()) {
            return;
        }
        JSONArray cache = state.optJSONArray(cacheKey);
        if (cache == null) {
            return;
        }
        JSONArray kept = new JSONArray();
        for (int i = 0; i < cache.length(); i++) {
            JSONObject item = cache.optJSONObject(i);
            if (item != null && !date.equals(item.optString("date"))) {
                kept.put(item);
            }
        }
        state.put(cacheKey, kept);
    }

    private static Set<String> segmentDates(List<UsageSegment> segments) {
        Set<String> dates = new HashSet<>();
        if (segments == null) {
            return dates;
        }
        for (UsageSegment segment : segments) {
            if (segment != null && segment.localDate != null && !segment.localDate.trim().isEmpty()) {
                dates.add(segment.localDate);
            }
        }
        return dates;
    }

    private static Set<String> collectPastDates(JSONObject state, LocalDate today) throws JSONException {
        Set<String> dates = new HashSet<>();
        collectPastRecordDates(state, today, dates);
        collectPastSegmentDates(state, today, dates);
        return dates;
    }

    private static void collectPastRecordDates(JSONObject state, LocalDate today, Set<String> dates) throws JSONException {
        JSONArray records = state.getJSONArray("records");
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) {
                continue;
            }
            addPastDate(record.optString("date", ""), today, dates);
        }
    }

    private static void collectPastSegmentDates(JSONObject state, LocalDate today, Set<String> dates) throws JSONException {
        JSONArray segments = ensureSegments(state);
        for (int i = 0; i < segments.length(); i++) {
            JSONObject json = segments.optJSONObject(i);
            if (json == null) {
                continue;
            }
            addPastDate(json.optString("localDate", ""), today, dates);
        }
    }

    private static Set<String> collectPastFamilyChildDates(JSONObject state, LocalDate today) throws JSONException {
        Set<String> dates = new HashSet<>();
        JSONArray segments = ensureFamilyChildSegments(state);
        for (int i = 0; i < segments.length(); i++) {
            JSONObject json = segments.optJSONObject(i);
            if (json == null) {
                continue;
            }
            addPastDate(json.optString("localDate", ""), today, dates);
        }
        return dates;
    }

    private static void addPastDate(String dateValue, LocalDate today, Set<String> dates) {
        LocalDate date = parseDateOrNull(dateValue);
        if (date != null && today != null && date.isBefore(today)) {
            dates.add(date.toString());
        }
    }

    private static LocalDate parseDateOrNull(String dateValue) {
        if (dateValue == null || dateValue.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateValue);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JSONObject dailyStatsToJson(DailyStatsSnapshot snapshot) throws JSONException {
        JSONObject json = new JSONObject();
        DailySummary summary = snapshot.summary;
        DeviceUsageBreakdown breakdown = snapshot.breakdown;
        json.put("date", summary.date);
        json.put("totalSeconds", summary.totalSeconds);
        json.put("hourlySeconds", longArrayToJson(summary.hourlySeconds));
        json.put("sessionSeconds", longArrayToJson(summary.sessionSeconds));
        json.put("currentSessionSeconds", summary.currentSessionSeconds);
        json.put("reminderShown", summary.reminderShown);
        json.put("lastReminderStep", summary.lastReminderStep);
        json.put("pcSeconds", breakdown.pcSeconds);
        json.put("phoneSeconds", breakdown.phoneSeconds);
        json.put("pcHourlySeconds", longArrayToJson(breakdown.pcHourlySeconds));
        json.put("phoneHourlySeconds", longArrayToJson(breakdown.phoneHourlySeconds));
        return json;
    }

    private static DailyStatsSnapshot dailyStatsFromJson(JSONObject json) {
        String date = json.optString("date");
        DailySummary summary = new DailySummary(
                date,
                json.optLong("totalSeconds", 0L),
                longArrayFromJson(json.optJSONArray("hourlySeconds"), 24),
                longArrayFromJson(json.optJSONArray("sessionSeconds"), -1),
                json.optLong("currentSessionSeconds", 0L),
                json.optBoolean("reminderShown", false),
                json.optInt("lastReminderStep", 0));
        DeviceUsageBreakdown breakdown = new DeviceUsageBreakdown(
                json.optLong("pcSeconds", 0L),
                json.optLong("phoneSeconds", 0L),
                longArrayFromJson(json.optJSONArray("pcHourlySeconds"), 24),
                longArrayFromJson(json.optJSONArray("phoneHourlySeconds"), 24));
        return new DailyStatsSnapshot(summary, breakdown);
    }

    private static JSONArray longArrayToJson(long[] values) {
        JSONArray json = new JSONArray();
        if (values != null) {
            for (long value : values) {
                json.put(value);
            }
        }
        return json;
    }

    private static long[] longArrayFromJson(JSONArray json, int fixedLength) {
        int length = fixedLength >= 0 ? fixedLength : (json == null ? 0 : json.length());
        long[] values = new long[length];
        if (json == null) {
            return values;
        }
        for (int i = 0; i < values.length && i < json.length(); i++) {
            values[i] = json.optLong(i, 0L);
        }
        return values;
    }

    private static final class DailyStatsSnapshot {
        final DailySummary summary;
        final DeviceUsageBreakdown breakdown;

        DailyStatsSnapshot(DailySummary summary, DeviceUsageBreakdown breakdown) {
            this.summary = summary;
            this.breakdown = breakdown;
        }
    }

    private static Map<String, List<UsageSegment>> groupSegmentsByDate(List<UsageSegment> segments) {
        Map<String, List<UsageSegment>> grouped = new HashMap<>();
        if (segments == null) {
            return grouped;
        }
        for (UsageSegment segment : segments) {
            if (segment == null || segment.localDate == null || segment.localDate.trim().isEmpty()) {
                continue;
            }
            List<UsageSegment> values = grouped.get(segment.localDate);
            if (values == null) {
                values = new ArrayList<>();
                grouped.put(segment.localDate, values);
            }
            values.add(segment);
        }
        return grouped;
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

    public synchronized boolean isFamilyChildReminderShown(LocalDate date) {
        ensureFamilyChildReminderDate(date);
        return prefs.getBoolean(FAMILY_CHILD_REMINDER_SHOWN, false);
    }

    public synchronized int getFamilyChildLastReminderStep(LocalDate date) {
        ensureFamilyChildReminderDate(date);
        return prefs.getInt(FAMILY_CHILD_LAST_REMINDER_STEP, 0);
    }

    public synchronized void markFamilyChildReminderShown(LocalDate date, int reminderStep) {
        if (date == null) {
            return;
        }
        prefs.edit()
                .putString(FAMILY_CHILD_REMINDER_DATE, date.toString())
                .putBoolean(FAMILY_CHILD_REMINDER_SHOWN, true)
                .putInt(FAMILY_CHILD_LAST_REMINDER_STEP, Math.max(1, reminderStep))
                .apply();
    }

    private void ensureFamilyChildReminderDate(LocalDate date) {
        if (date == null) {
            return;
        }
        String currentDate = date.toString();
        if (!currentDate.equals(prefs.getString(FAMILY_CHILD_REMINDER_DATE, ""))) {
            prefs.edit()
                    .putString(FAMILY_CHILD_REMINDER_DATE, currentDate)
                    .putBoolean(FAMILY_CHILD_REMINDER_SHOWN, false)
                    .putInt(FAMILY_CHILD_LAST_REMINDER_STEP, 0)
                    .apply();
        }
    }

    public synchronized int getReminderMinutes() {
        return ReminderThreshold.clampMinutes(prefs.getInt(REMINDER_MINUTES, ReminderThreshold.DEFAULT_MINUTES));
    }

    public synchronized boolean isRepeatReminderEnabled() {
        return prefs.getBoolean(REPEAT_REMINDER, false);
    }

    public synchronized void saveReminderSettings(int reminderMinutes, boolean repeatReminder) {
        int safeMinutes = ReminderThreshold.clampMinutes(reminderMinutes);
        prefs.edit()
                .putInt(REMINDER_MINUTES, safeMinutes)
                .putBoolean(REPEAT_REMINDER, repeatReminder)
                .apply();
        alignTodayReminderAfterSettingsChange(safeMinutes);
    }

    private void alignTodayReminderAfterSettingsChange(int reminderMinutes) {
        LocalDate todayDate = LocalDate.now();
        DailySummary today = getDay(todayDate);
        int reachedStep = ReminderPolicy.alignedStepAfterSettingsChange(today.totalSeconds, reminderMinutes);
        try {
            JSONObject state = loadState();
            JSONObject record = getOrCreateRecord(state, todayDate.toString());
            record.put("reminderShown", reachedStep > 0);
            record.put("lastReminderStep", reachedStep);
            record.put("updatedAt", System.currentTimeMillis());
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized void setMainActivityVisible(boolean visible) {
        prefs.edit().putBoolean(MAIN_ACTIVITY_VISIBLE, visible).apply();
    }

    public synchronized boolean isMainActivityVisible() {
        return prefs.getBoolean(MAIN_ACTIVITY_VISIBLE, false);
    }

    public synchronized ProductMode getProductMode() {
        try {
            return readProductMode(loadState());
        } catch (JSONException ignored) {
            return ProductMode.PERSONAL;
        }
    }

    public synchronized FamilyHomeState getFamilyHomeState() {
        try {
            return readFamilyHomeState(loadState());
        } catch (JSONException ignored) {
            return FamilyHomeState.create(ProductMode.PERSONAL, DeviceRole.PERSONAL_DEVICE, null, false, false);
        }
    }

    public synchronized void saveProductMode(ProductMode mode) {
        try {
            JSONObject state = loadState();
            writeProductMode(state, mode);
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized DeviceRole getDeviceRole() {
        try {
            return readDeviceRole(loadState());
        } catch (JSONException ignored) {
            return DeviceRole.PERSONAL_DEVICE;
        }
    }

    public synchronized void saveDeviceRole(DeviceRole role) {
        try {
            JSONObject state = loadState();
            writeDeviceRole(state, role);
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized List<ChildProfile> getChildProfiles() {
        try {
            return readChildProfiles(loadState());
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
    }

    public synchronized String getActiveChildId() {
        try {
            return readActiveChildId(loadState());
        } catch (JSONException ignored) {
            return "";
        }
    }

    public synchronized ChildProfile getActiveChildProfile() {
        String activeChildId = getActiveChildId();
        for (ChildProfile profile : getChildProfiles()) {
            if (profile.childId.equals(activeChildId)) {
                return profile;
            }
        }
        return null;
    }

    public synchronized void saveChildProfiles(List<ChildProfile> profiles, String activeChildId) {
        try {
            JSONObject state = loadState();
            writeChildProfiles(state, profiles, activeChildId);
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized FamilyBindingInvite getFamilyBindingInvite() {
        try {
            return readFamilyBindingInvite(loadState());
        } catch (JSONException ignored) {
            return null;
        }
    }

    public synchronized void saveFamilyBindingInvite(FamilyBindingInvite invite) {
        try {
            JSONObject state = loadState();
            writeFamilyBindingInvite(state, invite);
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized boolean consumeFamilyBindingCode(String bindingCode) {
        try {
            JSONObject state = loadState();
            boolean consumed = consumeFamilyBindingCode(state, bindingCode);
            if (consumed) {
                saveState(state);
            }
            return consumed;
        } catch (JSONException ignored) {
            return false;
        }
    }

    public synchronized void saveRemoteFamilyChildBinding(String familyId, ChildProfile childProfile) {
        saveRemoteFamilyChildBinding(familyId, childProfile, null);
    }

    public synchronized void saveRemoteFamilyChildBinding(String familyId, ChildProfile childProfile, ParentPasscode parentPasscode) {
        if (childProfile == null || !childProfile.isValid()) {
            return;
        }
        try {
            JSONObject state = loadState();
            writeRemoteFamilyChildBinding(state, familyId, childProfile, parentPasscode);
            saveState(state);
        } catch (JSONException ex) {
            throw new IllegalStateException("Failed to save remote family child binding.", ex);
        }
    }

    public synchronized void saveFamilyChildDeviceBinding(String childDeviceId) {
        try {
            JSONObject state = loadState();
            writeFamilyChildDeviceBinding(state, childDeviceId, System.currentTimeMillis() / 1000L);
            saveState(state);
        } catch (JSONException ex) {
            throw new IllegalStateException("Failed to save family child device binding.", ex);
        }
    }

    public synchronized String getFamilyId() {
        try {
            return readFamilyId(loadState());
        } catch (JSONException ignored) {
            return "";
        }
    }

    public synchronized String getFamilyChildDeviceId() {
        try {
            return readFamilyChildDeviceId(loadState());
        } catch (JSONException ignored) {
            return "";
        }
    }

    public synchronized boolean hasFamilyChildDeviceBinding() {
        try {
            return hasFamilyChildDeviceBinding(loadState());
        } catch (JSONException ignored) {
            return false;
        }
    }

    public synchronized void leaveFamilyMode() {
        try {
            JSONObject state = loadState();
            writeLeaveFamilyMode(state);
            saveState(state);
        } catch (JSONException ex) {
            throw new IllegalStateException("Failed to leave family mode.", ex);
        }
    }

    public synchronized boolean hasParentPasscode() {
        try {
            return hasParentPasscode(loadState());
        } catch (JSONException ignored) {
            return false;
        }
    }

    public synchronized void saveParentPasscode(String passcode) {
        if (passcode == null || passcode.trim().isEmpty()) {
            return;
        }
        saveParentPasscode(createParentPasscode(passcode, System.currentTimeMillis() / 1000L));
    }

    public synchronized void saveParentPasscode(ParentPasscode passcode) {
        if (passcode == null || !passcode.isConfigured()) {
            return;
        }
        try {
            JSONObject state = loadState();
            writeParentPasscode(state, passcode);
            saveState(state);
        } catch (JSONException ignored) {
        }
    }

    public synchronized boolean verifyParentPasscode(String passcode) {
        try {
            return verifyParentPasscode(readParentPasscode(loadState()), passcode);
        } catch (JSONException ignored) {
            return false;
        }
    }

    public synchronized ParentPasscode getParentPasscode() {
        try {
            return readParentPasscode(loadState());
        } catch (JSONException ignored) {
            return null;
        }
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
        settings.peerReminderState = getPeerReminderState();
        return settings;
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

    public synchronized ReminderRuntimeState getLocalReminderState() {
        return new ReminderRuntimeState(
                ensureDeviceId(),
                PLATFORM,
                prefs.getBoolean(LOCAL_REMINDER_COUNTING, false),
                prefs.getLong(LOCAL_REMINDER_SESSION_STARTED, 0L));
    }

    public synchronized void saveLocalReminderState(boolean isCounting, long currentSessionStartedUnixSeconds) {
        prefs.edit()
                .putBoolean(LOCAL_REMINDER_COUNTING, isCounting)
                .putLong(LOCAL_REMINDER_SESSION_STARTED, Math.max(0L, currentSessionStartedUnixSeconds))
                .apply();
    }

    public synchronized ReminderRuntimeState getPeerReminderState() {
        return new ReminderRuntimeState(
                prefs.getString(PEER_REMINDER_DEVICE_ID, ""),
                prefs.getString(PEER_REMINDER_PLATFORM, ""),
                prefs.getBoolean(PEER_REMINDER_COUNTING, false),
                prefs.getLong(PEER_REMINDER_SESSION_STARTED, 0L));
    }

    public synchronized void savePeerReminderState(ReminderRuntimeState state) {
        if (state == null) {
            return;
        }

        prefs.edit()
                .putString(PEER_REMINDER_DEVICE_ID, safe(state.deviceId))
                .putString(PEER_REMINDER_PLATFORM, safe(state.platform))
                .putBoolean(PEER_REMINDER_COUNTING, state.isCounting)
                .putLong(PEER_REMINDER_SESSION_STARTED, Math.max(0L, state.currentSessionStartedUnixSeconds))
                .apply();
    }

    public synchronized String diagnosticSnapshot() {
        try {
            JSONObject state = loadState();
            JSONArray records = state.optJSONArray("records");
            JSONArray segments = state.optJSONArray("segments");
            SyncSettings settings = getSyncSettings();
            return "records=" + (records == null ? 0 : records.length())
                    + " segments=" + (segments == null ? 0 : segments.length())
                    + " stateChars=" + state.toString().length()
                    + " paired=" + settings.isPaired
                    + " peerHost=" + safe(settings.peerHost)
                    + " peerPort=" + settings.peerPort
                    + " lastSync=" + settings.lastSyncUnixSeconds
                    + " lastError=" + safe(settings.lastError);
        } catch (Exception ex) {
            return "diagnosticError=" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
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

    public synchronized long displayFamilyChildTodaySeconds(LocalDate today) {
        return displayFamilyChildHomeStats(today).todaySeconds;
    }

    public synchronized long displayFamilyChildYesterdaySeconds(LocalDate today) {
        return displayFamilyChildHomeStats(today).yesterdaySeconds;
    }

    public synchronized long displayFamilyChildWeekSeconds(LocalDate today) {
        return displayFamilyChildHomeStats(today).weekSeconds;
    }

    public synchronized long displayFamilyChildMonthSeconds(LocalDate today) {
        return displayFamilyChildHomeStats(today).monthSeconds;
    }

    public synchronized HomeStatsSnapshot displayFamilyChildHomeStats(LocalDate today) {
        try {
            JSONObject state = loadState();
            HomeStatsSnapshot snapshot = buildFamilyChildHomeStatsFromSnapshots(state, today);
            saveFamilyChildCacheIfChanged(state);
            return snapshot;
        } catch (JSONException ignored) {
            return HomeStatsSnapshot.empty();
        }
    }

    public synchronized long sumRange(LocalDate start, LocalDate end) {
        long total = 0L;
        for (DailySummary summary : getDays(start, end)) {
            total += summary.totalSeconds;
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
            state.put(FAMILY_CHILD_SEGMENTS, new JSONArray());
            ensureFamilyModeState(state);
            return state;
        }
        JSONObject state = new JSONObject(raw);
        if (!state.has("records")) {
            state.put("records", new JSONArray());
        }
        if (!state.has("segments")) {
            state.put("segments", new JSONArray());
        }
        if (!state.has(FAMILY_CHILD_SEGMENTS)) {
            state.put(FAMILY_CHILD_SEGMENTS, new JSONArray());
        }
        ensureFamilyModeState(state);
        return state;
    }

    private void saveState(JSONObject state) {
        prefs.edit().putString(STATE, state.toString()).apply();
    }

    private void saveFamilyChildCacheIfChanged(JSONObject state) {
        if (state != null && state.optBoolean(FAMILY_CHILD_DAILY_STATS_CACHE_CHANGED, false)) {
            state.remove(FAMILY_CHILD_DAILY_STATS_CACHE_CHANGED);
            saveState(state);
        }
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
        mergeSegments(state, java.util.Collections.singletonList(segment));
    }

    static int mergeSegments(JSONObject state, List<UsageSegment> newSegments) throws JSONException {
        return mergeSegments(state, newSegments, "segments");
    }

    static int mergeFamilyChildSegments(JSONObject state, List<UsageSegment> newSegments) throws JSONException {
        return mergeSegments(state, newSegments, FAMILY_CHILD_SEGMENTS);
    }

    private static int mergeSegments(JSONObject state, List<UsageSegment> newSegments, String key) throws JSONException {
        if (newSegments == null || newSegments.isEmpty()) {
            return 0;
        }
        JSONArray segments = ensureSegments(state, key);
        Set<String> existingIds = new HashSet<>();
        for (int i = 0; i < segments.length(); i++) {
            JSONObject existing = segments.optJSONObject(i);
            if (existing != null) {
                existingIds.add(existing.optString("segmentId"));
            }
        }

        int changed = 0;
        for (UsageSegment segment : newSegments) {
            if (segment == null || segment.segmentId == null || segment.segmentId.trim().isEmpty()) {
                continue;
            }
            if (existingIds.add(segment.segmentId)) {
                segments.put(segmentToJson(segment));
                changed++;
            }
        }
        return changed;
    }

    private static JSONArray ensureSegments(JSONObject state) throws JSONException {
        return ensureSegments(state, "segments");
    }

    private static JSONArray ensureFamilyChildSegments(JSONObject state) throws JSONException {
        return ensureSegments(state, FAMILY_CHILD_SEGMENTS);
    }

    private static JSONArray ensureSegments(JSONObject state, String key) throws JSONException {
        JSONArray segments = state.optJSONArray(key);
        if (segments == null) {
            segments = new JSONArray();
            state.put(key, segments);
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

    static List<UsageSegment> readSegments(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        return readSegments(state, start, end, "segments");
    }

    static List<UsageSegment> readFamilyChildSegments(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        return readSegments(state, start, end, FAMILY_CHILD_SEGMENTS);
    }

    private static List<UsageSegment> readSegments(JSONObject state, LocalDate start, LocalDate end, String key) throws JSONException {
        JSONArray raw = FAMILY_CHILD_SEGMENTS.equals(key) ? ensureFamilyChildSegments(state) : ensureSegments(state, key);
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

    static long sumFamilyChildDay(JSONObject state, LocalDate date) throws JSONException {
        if (state == null || date == null) {
            return 0L;
        }
        return UsageSegmentMerger.buildDailySummary(date.toString(), readFamilyChildSegments(state, date, date)).totalSeconds;
    }

    static long sumFamilyChildRange(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        if (state == null || start == null || end == null) {
            return 0L;
        }
        LocalDate first = start.isAfter(end) ? end : start;
        LocalDate last = start.isAfter(end) ? start : end;
        long total = 0L;
        for (LocalDate cursor = first; !cursor.isAfter(last); cursor = cursor.plusDays(1)) {
            total += sumFamilyChildDay(state, cursor);
        }
        return total;
    }

    static DailySummary buildFamilyChildDay(JSONObject state, LocalDate date) throws JSONException {
        if (state == null || date == null) {
            return new DailySummary("", 0L, false, 0);
        }
        return UsageSegmentMerger.buildDailySummary(date.toString(), readFamilyChildSegments(state, date, date));
    }

    static List<DailySummary> buildFamilyChildDays(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        List<DailySummary> values = new ArrayList<>();
        if (state == null || start == null || end == null) {
            return values;
        }
        LocalDate first = start.isAfter(end) ? end : start;
        LocalDate last = start.isAfter(end) ? start : end;
        Map<String, List<UsageSegment>> segmentsByDate = groupSegmentsByDate(readFamilyChildSegments(state, first, last));
        for (LocalDate cursor = first; !cursor.isAfter(last); cursor = cursor.plusDays(1)) {
            values.add(UsageSegmentMerger.buildDailySummary(cursor.toString(), segmentsByDate.get(cursor.toString())));
        }
        return values;
    }

    private static List<DailyStatsSnapshot> buildFamilyChildSnapshots(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        List<DailyStatsSnapshot> values = new ArrayList<>();
        if (state == null || start == null || end == null) {
            return values;
        }
        LocalDate first = start.isAfter(end) ? end : start;
        LocalDate last = start.isAfter(end) ? start : end;
        boolean hasMissing = false;
        for (LocalDate cursor = first; !cursor.isAfter(last); cursor = cursor.plusDays(1)) {
            DailyStatsSnapshot cached = readCachedFamilyChildDailyStats(state, cursor);
            if (cached != null) {
                values.add(cached);
            } else {
                values.add(null);
                hasMissing = true;
            }
        }
        if (hasMissing) {
            Map<String, List<UsageSegment>> segmentsByDate = groupSegmentsByDate(readFamilyChildSegments(state, first, last));
            boolean changedCache = false;
            int index = 0;
            for (LocalDate cursor = first; !cursor.isAfter(last); cursor = cursor.plusDays(1)) {
                if (values.get(index) == null) {
                    DailyStatsSnapshot snapshot = buildFamilyChildStatsSnapshot(state, cursor, segmentsByDate.get(cursor.toString()));
                    values.set(index, snapshot);
                    changedCache |= cacheFamilyChildDailyStatsIfStable(state, cursor, snapshot);
                }
                index++;
            }
            if (changedCache) {
                // Caller owns persistence so bulk reads can update the cache once.
                state.put(FAMILY_CHILD_DAILY_STATS_CACHE_CHANGED, true);
            }
        }
        return values;
    }

    private static DailyStatsSnapshot buildFamilyChildStatsSnapshot(
            JSONObject state,
            LocalDate date,
            List<UsageSegment> segments) throws JSONException {
        String dateValue = date.toString();
        List<UsageSegment> safeSegments = segments == null
                ? readFamilyChildSegments(state, date, date)
                : segments;
        DailySummary summary = UsageSegmentMerger.buildDailySummary(dateValue, safeSegments);
        return new DailyStatsSnapshot(summary, DeviceUsageBreakdown.build(dateValue, safeSegments));
    }

    static HomeStatsSnapshot buildFamilyChildHomeStats(JSONObject state, LocalDate today) throws JSONException {
        if (state == null || today == null) {
            return HomeStatsSnapshot.empty();
        }
        LocalDate weekStart = weekStart(today);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate start = earliest(today.minusDays(1), weekStart, monthStart);
        Map<String, Long> totals = dailyTotals(buildFamilyChildDays(state, start, today));
        return new HomeStatsSnapshot(
                dailyTotal(totals, today),
                dailyTotal(totals, today.minusDays(1)),
                sumDailyTotals(totals, weekStart, today),
                sumDailyTotals(totals, monthStart, today));
    }

    private static HomeStatsSnapshot buildFamilyChildHomeStatsFromSnapshots(JSONObject state, LocalDate today) throws JSONException {
        if (state == null || today == null) {
            return HomeStatsSnapshot.empty();
        }
        LocalDate weekStart = weekStart(today);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate start = earliest(today.minusDays(1), weekStart, monthStart);
        List<DailySummary> summaries = new ArrayList<>();
        for (DailyStatsSnapshot snapshot : buildFamilyChildSnapshots(state, start, today)) {
            summaries.add(snapshot.summary);
        }
        Map<String, Long> totals = dailyTotals(summaries);
        return new HomeStatsSnapshot(
                dailyTotal(totals, today),
                dailyTotal(totals, today.minusDays(1)),
                sumDailyTotals(totals, weekStart, today),
                sumDailyTotals(totals, monthStart, today));
    }

    static DeviceUsageBreakdown buildFamilyChildDeviceBreakdown(JSONObject state, LocalDate date) throws JSONException {
        if (state == null || date == null) {
            return new DeviceUsageBreakdown(0L, 0L);
        }
        return DeviceUsageBreakdown.build(date.toString(), readFamilyChildSegments(state, date, date));
    }

    static List<DeviceUsageBreakdown> buildFamilyChildDeviceBreakdowns(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        List<DeviceUsageBreakdown> values = new ArrayList<>();
        if (state == null || start == null || end == null) {
            return values;
        }
        LocalDate first = start.isAfter(end) ? end : start;
        LocalDate last = start.isAfter(end) ? start : end;
        Map<String, List<UsageSegment>> segmentsByDate = groupSegmentsByDate(readFamilyChildSegments(state, first, last));
        for (LocalDate cursor = first; !cursor.isAfter(last); cursor = cursor.plusDays(1)) {
            values.add(DeviceUsageBreakdown.build(cursor.toString(), segmentsByDate.get(cursor.toString())));
        }
        return values;
    }

    private List<UsageSegment> readEffectiveSegments(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        long startedAt = System.currentTimeMillis();
        List<UsageSegment> segments = readSegments(state, start, end);
        List<DailySummary> legacySummaries = readLegacyDailySummaries(state, start, end);
        List<UsageSegment> effective = LegacyUsageSegments.normalizeEffectiveSegments(
                segments,
                legacySummaries,
                ensureDeviceId(),
                PLATFORM);
        Log.i(DIAG_TAG, "EyeTimeStore readEffectiveSegments range=" + start + ".." + end
                + " stored=" + segments.size()
                + " legacyDays=" + legacySummaries.size()
                + " effective=" + effective.size()
                + " ms=" + elapsed(startedAt));
        return effective;
    }

    private List<DailySummary> readLegacyDailySummaries(JSONObject state, LocalDate start, LocalDate end) throws JSONException {
        JSONArray records = state.getJSONArray("records");
        List<DailySummary> values = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) {
                continue;
            }
            String dateValue = record.optString("date", "");
            if (dateValue.trim().isEmpty()) {
                continue;
            }
            LocalDate date = LocalDate.parse(dateValue);
            if (!date.isBefore(start) && !date.isAfter(end)) {
                values.add(readLegacyDailySummary(dateValue, record));
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

    private static LocalDate earliest(LocalDate first, LocalDate second, LocalDate third) {
        LocalDate value = first;
        if (second.isBefore(value)) {
            value = second;
        }
        if (third.isBefore(value)) {
            value = third;
        }
        return value;
    }

    private static Map<String, Long> dailyTotals(List<DailySummary> summaries) {
        Map<String, Long> totals = new HashMap<>();
        if (summaries == null) {
            return totals;
        }
        for (DailySummary summary : summaries) {
            if (summary != null && summary.date != null && !summary.date.trim().isEmpty()) {
                totals.put(summary.date, summary.totalSeconds);
            }
        }
        return totals;
    }

    private static long dailyTotal(Map<String, Long> totals, LocalDate date) {
        Long value = totals.get(date.toString());
        return value == null ? 0L : value;
    }

    private static long sumDailyTotals(Map<String, Long> totals, LocalDate start, LocalDate end) {
        long total = 0L;
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            total += dailyTotal(totals, cursor);
        }
        return total;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void ensureFamilyModeState(JSONObject state) throws JSONException {
        if (!state.has(PRODUCT_MODE)) {
            writeProductMode(state, ProductMode.PERSONAL);
        }
        if (!state.has(DEVICE_ROLE)) {
            writeDeviceRole(state, DeviceRole.PERSONAL_DEVICE);
        }
    }

    static ProductMode readProductMode(JSONObject state) {
        if (state == null) {
            return ProductMode.PERSONAL;
        }
        return ProductMode.fromStorageValue(state.optString(PRODUCT_MODE, ProductMode.PERSONAL.storageValue()));
    }

    static FamilyHomeState readFamilyHomeState(JSONObject state) {
        ProductMode productMode = readProductMode(state);
        DeviceRole deviceRole = readDeviceRole(state);
        String activeChildId = readActiveChildId(state);
        ChildProfile activeChild = null;
        for (ChildProfile profile : readChildProfiles(state)) {
            if (profile.childId.equals(activeChildId)) {
                activeChild = profile;
                break;
            }
        }
        return FamilyHomeState.create(
                productMode,
                deviceRole,
                activeChild,
                hasParentPasscode(state),
                hasFamilyChildDeviceBinding(state));
    }

    static void writeProductMode(JSONObject state, ProductMode mode) throws JSONException {
        if (state == null) {
            return;
        }
        ProductMode safeMode = mode == null ? ProductMode.PERSONAL : mode;
        state.put(PRODUCT_MODE, safeMode.storageValue());
    }

    static DeviceRole readDeviceRole(JSONObject state) {
        if (state == null) {
            return DeviceRole.PERSONAL_DEVICE;
        }
        return DeviceRole.fromStorageValue(state.optString(DEVICE_ROLE, DeviceRole.PERSONAL_DEVICE.storageValue()));
    }

    static void writeDeviceRole(JSONObject state, DeviceRole role) throws JSONException {
        if (state == null) {
            return;
        }
        DeviceRole safeRole = role == null ? DeviceRole.PERSONAL_DEVICE : role;
        state.put(DEVICE_ROLE, safeRole.storageValue());
    }

    static List<ChildProfile> readChildProfiles(JSONObject state) {
        List<ChildProfile> profiles = new ArrayList<>();
        if (state == null) {
            return profiles;
        }
        JSONArray raw = state.optJSONArray(CHILD_PROFILES);
        if (raw == null) {
            return profiles;
        }
        for (int i = 0; i < raw.length(); i++) {
            JSONObject json = raw.optJSONObject(i);
            if (json == null) {
                continue;
            }
            ChildProfile profile = childProfileFromJson(json);
            if (profile.isValid()) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    static void writeChildProfiles(JSONObject state, List<ChildProfile> profiles, String activeChildId) throws JSONException {
        if (state == null) {
            return;
        }
        JSONArray raw = new JSONArray();
        String firstChildId = "";
        boolean hasActiveChild = false;
        String safeActiveChildId = safe(activeChildId).trim();
        if (profiles != null) {
            for (ChildProfile profile : profiles) {
                if (profile == null || !profile.isValid()) {
                    continue;
                }
                raw.put(childProfileToJson(profile));
                if (firstChildId.isEmpty()) {
                    firstChildId = profile.childId;
                }
                if (profile.childId.equals(safeActiveChildId)) {
                    hasActiveChild = true;
                }
            }
        }
        state.put(CHILD_PROFILES, raw);
        state.put(ACTIVE_CHILD_ID, hasActiveChild ? safeActiveChildId : firstChildId);
    }

    static String readActiveChildId(JSONObject state) {
        if (state == null) {
            return "";
        }
        return safe(state.optString(ACTIVE_CHILD_ID, "")).trim();
    }

    static FamilyBindingInvite readFamilyBindingInvite(JSONObject state) {
        if (state == null) {
            return null;
        }
        JSONObject raw = state.optJSONObject(PENDING_FAMILY_BINDING_INVITE);
        if (raw == null) {
            return null;
        }
        JSONObject childJson = raw.optJSONObject("childProfile");
        if (childJson == null) {
            return null;
        }
        FamilyBindingInvite invite = new FamilyBindingInvite(
                state.optString(FAMILY_ID, raw.optString(FAMILY_ID, "")),
                raw.optString("bindingCode", ""),
                childProfileFromJson(childJson),
                raw.optLong("createdAtUnixSeconds", 0L));
        return invite.isValid() ? invite : null;
    }

    static void writeFamilyBindingInvite(JSONObject state, FamilyBindingInvite invite) throws JSONException {
        if (state == null || invite == null || !invite.isValid()) {
            return;
        }
        state.put(FAMILY_ID, invite.familyId);
        JSONObject raw = new JSONObject();
        raw.put("familyId", invite.familyId);
        raw.put("bindingCode", invite.bindingCode);
        raw.put("createdAtUnixSeconds", invite.createdAtUnixSeconds);
        raw.put("childProfile", childProfileToJson(invite.childProfile));
        state.put(PENDING_FAMILY_BINDING_INVITE, raw);
    }

    static boolean consumeFamilyBindingCode(JSONObject state, String bindingCode) throws JSONException {
        FamilyBindingInvite invite = readFamilyBindingInvite(state);
        if (state == null || invite == null) {
            return false;
        }
        String normalized = FamilyBindingInvite.normalizeBindingCode(bindingCode);
        if (!invite.bindingCode.equals(normalized)) {
            return false;
        }
        writeProductMode(state, ProductMode.FAMILY);
        writeDeviceRole(state, DeviceRole.CHILD_DEVICE);
        writeChildProfiles(state, java.util.Collections.singletonList(invite.childProfile), invite.childProfile.childId);
        state.put(FAMILY_ID, invite.familyId);
        state.put(PENDING_FAMILY_BINDING_INVITE, null);
        return true;
    }

    static void writeRemoteFamilyChildBinding(JSONObject state, String familyId, ChildProfile childProfile) throws JSONException {
        writeRemoteFamilyChildBinding(state, familyId, childProfile, null);
    }

    static void writeRemoteFamilyChildBinding(JSONObject state, String familyId, ChildProfile childProfile, ParentPasscode parentPasscode) throws JSONException {
        if (state == null || childProfile == null || !childProfile.isValid()) {
            return;
        }
        writeProductMode(state, ProductMode.FAMILY);
        writeDeviceRole(state, DeviceRole.CHILD_DEVICE);
        writeChildProfiles(state, java.util.Collections.singletonList(childProfile), childProfile.childId);
        state.put(FAMILY_ID, familyId == null ? "" : familyId.trim());
        if (parentPasscode != null && parentPasscode.isConfigured()) {
            writeParentPasscode(state, parentPasscode);
        }
        state.put(PENDING_FAMILY_BINDING_INVITE, null);
    }

    static void writeFamilyChildDeviceBinding(JSONObject state, String childDeviceId, long joinedAtUnixSeconds) throws JSONException {
        if (state == null) {
            return;
        }
        String safeChildDeviceId = safe(childDeviceId).trim();
        state.put(FAMILY_CHILD_DEVICE_ID, safeChildDeviceId);
        state.put(FAMILY_CHILD_DEVICE_JOINED_AT_UNIX_SECONDS, safeChildDeviceId.isEmpty() ? 0L : Math.max(0L, joinedAtUnixSeconds));
    }

    static boolean hasFamilyChildDeviceBinding(JSONObject state) {
        return state != null && !safe(state.optString(FAMILY_CHILD_DEVICE_ID, "")).trim().isEmpty();
    }

    static String readFamilyId(JSONObject state) {
        return state == null ? "" : safe(state.optString(FAMILY_ID, "")).trim();
    }

    static String readFamilyChildDeviceId(JSONObject state) {
        return state == null ? "" : safe(state.optString(FAMILY_CHILD_DEVICE_ID, "")).trim();
    }

    static void writeLeaveFamilyMode(JSONObject state) throws JSONException {
        if (state == null) {
            return;
        }
        writeProductMode(state, ProductMode.PERSONAL);
        writeDeviceRole(state, DeviceRole.PERSONAL_DEVICE);
        writeChildProfiles(state, java.util.Collections.emptyList(), "");
        state.put(FAMILY_ID, "");
        state.put(FAMILY_CHILD_DEVICE_ID, "");
        state.put(FAMILY_CHILD_DEVICE_JOINED_AT_UNIX_SECONDS, 0L);
        state.put(FAMILY_CHILD_SEGMENTS, new JSONArray());
        state.put(PENDING_FAMILY_BINDING_INVITE, null);
        state.put(PARENT_PASSCODE_HASH, "");
        state.put(PARENT_PASSCODE_SALT, "");
        state.put(PARENT_PASSCODE_UPDATED_AT, 0L);
    }

    private static JSONObject childProfileToJson(ChildProfile profile) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("childId", profile.childId);
        json.put("nickname", profile.nickname);
        json.put("ageBand", profile.ageBand);
        json.put("createdAtUnixSeconds", profile.createdAtUnixSeconds);
        json.put("updatedAtUnixSeconds", profile.updatedAtUnixSeconds);
        return json;
    }

    private static ChildProfile childProfileFromJson(JSONObject json) {
        return new ChildProfile(
                json.optString("childId", ""),
                json.optString("nickname", ""),
                json.optString("ageBand", ChildProfile.AGE_BAND_UNKNOWN),
                json.optLong("createdAtUnixSeconds", 0L),
                json.optLong("updatedAtUnixSeconds", 0L));
    }

    static ParentPasscode createParentPasscode(String passcode, long updatedAtUnixSeconds) {
        byte[] salt = new byte[PARENT_PASSCODE_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return createParentPasscode(passcode, salt, updatedAtUnixSeconds);
    }

    static ParentPasscode createParentPasscode(String passcode, byte[] salt, long updatedAtUnixSeconds) {
        byte[] safeSalt = salt == null ? new byte[0] : salt.clone();
        return new ParentPasscode(
                hashParentPasscode(passcode, safeSalt),
                Base64.getEncoder().encodeToString(safeSalt),
                updatedAtUnixSeconds);
    }

    static boolean hasParentPasscode(JSONObject state) {
        ParentPasscode passcode = readParentPasscode(state);
        return passcode != null && passcode.isConfigured();
    }

    static ParentPasscode readParentPasscode(JSONObject state) {
        if (state == null) {
            return null;
        }
        ParentPasscode passcode = new ParentPasscode(
                state.optString(PARENT_PASSCODE_HASH, ""),
                state.optString(PARENT_PASSCODE_SALT, ""),
                state.optLong(PARENT_PASSCODE_UPDATED_AT, 0L));
        return passcode.isConfigured() ? passcode : null;
    }

    static void writeParentPasscode(JSONObject state, ParentPasscode passcode) throws JSONException {
        if (state == null || passcode == null || !passcode.isConfigured()) {
            return;
        }
        state.put(PARENT_PASSCODE_HASH, passcode.hash);
        state.put(PARENT_PASSCODE_SALT, passcode.salt);
        state.put(PARENT_PASSCODE_UPDATED_AT, passcode.updatedAtUnixSeconds);
    }

    static boolean verifyParentPasscode(ParentPasscode parentPasscode, String passcode) {
        if (parentPasscode == null || !parentPasscode.isConfigured() || passcode == null) {
            return false;
        }
        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(parentPasscode.salt);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String candidateHash = hashParentPasscode(passcode, salt);
        return MessageDigest.isEqual(
                parentPasscode.hash.getBytes(StandardCharsets.UTF_8),
                candidateHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String hashParentPasscode(String passcode, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    safe(passcode).toCharArray(),
                    salt == null ? new byte[0] : salt,
                    PARENT_PASSCODE_ITERATIONS,
                    PARENT_PASSCODE_HASH_BITS);
            try {
                byte[] hash = SecretKeyFactory
                        .getInstance(PARENT_PASSCODE_ALGORITHM)
                        .generateSecret(spec)
                        .getEncoded();
                return Base64.getEncoder().encodeToString(hash);
            } finally {
                spec.clearPassword();
            }
        } catch (InvalidKeySpecException | java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Parent passcode hashing is unavailable.", ex);
        }
    }

    private static long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }
}
