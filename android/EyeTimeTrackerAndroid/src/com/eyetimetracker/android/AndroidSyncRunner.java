package com.eyetimetracker.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.List;

public final class AndroidSyncRunner {
    private final EyeTimeStore store;
    private final AndroidSyncClient client;

    public AndroidSyncRunner(EyeTimeStore store) {
        this(store, new AndroidSyncClient());
    }

    public AndroidSyncRunner(EyeTimeStore store, AndroidSyncClient client) {
        this.store = store;
        this.client = client;
    }

    public void syncOnce() {
        SyncSettings settings = store.getSyncSettings();
        String requestJson = buildSyncRequestJson(settings);
        if (requestJson.isEmpty()) {
            settings.lastError = "Sync settings are incomplete.";
            store.saveSyncResult(settings);
            return;
        }

        String responseJson = client.sendJson(settings, requestJson);
        if (SyncConnectionState.shouldClearPairingAfterSync(responseJson, settings.lastError)) {
            store.saveSyncSettings(SyncSettings.unpaired());
            return;
        }

        if (!responseJson.isEmpty()) {
            String error = AndroidSyncResponseReader.readError(responseJson);
            if (error.isEmpty()) {
                for (UsageSegment segment : AndroidSyncResponseReader.readSegments(responseJson)) {
                    store.addSegment(segment);
                }
                settings.lastError = "";
            } else {
                settings.lastError = error;
            }
        }
        store.saveSyncResult(settings);
    }

    private String buildSyncRequestJson(SyncSettings settings) {
        if (settings == null || !settings.isPaired || settings.sharedSecret == null || settings.sharedSecret.isEmpty()) {
            return "";
        }

        try {
            long nowSeconds = System.currentTimeMillis() / 1000L;
            JSONObject request = new JSONObject();
            request.put("Type", SyncMessages.SYNC_REQUEST);
            request.put("DeviceId", store.getDeviceId());
            request.put("Platform", "android");
            request.put("Segments", segmentsToJson(store.getSegments(LocalDate.now().minusDays(30), LocalDate.now())));
            request.put("Settings", settingsToJson());
            request.put("SinceUnixSeconds", settings.lastSyncUnixSeconds);
            request.put("TimestampUnixSeconds", nowSeconds);
            request.put("Signature", "");
            return request.toString();
        } catch (JSONException ex) {
            settings.lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return "";
        }
    }

    private JSONObject settingsToJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("IdleThresholdSeconds", 180);
        json.put("CountAudio", true);
        json.put("ReminderThresholdSeconds", store.getReminderMinutes() * 60);
        json.put("StartWithWindows", false);
        json.put("RepeatReminder", store.isRepeatReminderEnabled());
        return json;
    }

    private static JSONArray segmentsToJson(List<UsageSegment> segments) throws JSONException {
        JSONArray json = new JSONArray();
        for (UsageSegment segment : segments) {
            JSONObject item = new JSONObject();
            item.put("SegmentId", segment.segmentId);
            item.put("DeviceId", segment.deviceId);
            item.put("Platform", segment.platform);
            item.put("Source", segment.source);
            item.put("StartUnixSeconds", segment.startUnixSeconds);
            item.put("EndUnixSeconds", segment.endUnixSeconds);
            item.put("LocalDate", segment.localDate);
            item.put("CreatedAtUnixSeconds", segment.createdAtUnixSeconds);
            item.put("UpdatedAtUnixSeconds", segment.updatedAtUnixSeconds);
            json.put(item);
        }
        return json;
    }
}
