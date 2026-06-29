package com.eyetimetracker.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.UUID;

public final class EyeTimeStore {
    private static final String PREFS = "eye_time_tracker";
    private static final String STATE = "state_json";
    private static final String DEVICE_ID = "device_id";
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
            return new DailySummary(date.toString(), record.optLong("totalSeconds", 0L), record.optBoolean("reminderShown", false));
        } catch (JSONException ex) {
            return new DailySummary(date.toString(), 0L, false);
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

    public synchronized void markReminderShown(LocalDate date) {
        try {
            JSONObject state = loadState();
            JSONObject record = getOrCreateRecord(state, date.toString());
            record.put("reminderShown", true);
            record.put("updatedAt", System.currentTimeMillis());
            saveState(state);
        } catch (JSONException ignored) {
        }
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
        record.put("updatedAt", System.currentTimeMillis());
        records.put(record);
        return record;
    }
}