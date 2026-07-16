package com.eyetimetracker.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import java.time.LocalDate;
import java.util.List;

public final class AndroidSyncRunner {
    private static final String DIAG_TAG = "EyeTimeDiag";
    private static final Object SYNC_LOCK = new Object();
    private static boolean syncRunning;
    private final EyeTimeStore store;
    private final Context context;
    private final AndroidSyncClient client;
    private final AndroidPcDiscoveryClient discoveryClient;

    public AndroidSyncRunner(EyeTimeStore store) {
        this(store, null, new AndroidSyncClient(), new AndroidPcDiscoveryClient());
    }

    public AndroidSyncRunner(EyeTimeStore store, Context context) {
        this(store, context, new AndroidSyncClient(), new AndroidPcDiscoveryClient());
    }

    public AndroidSyncRunner(EyeTimeStore store, AndroidSyncClient client) {
        this(store, null, client, new AndroidPcDiscoveryClient());
    }

    public AndroidSyncRunner(EyeTimeStore store, AndroidSyncClient client, AndroidPcDiscoveryClient discoveryClient) {
        this(store, null, client, discoveryClient);
    }

    public AndroidSyncRunner(EyeTimeStore store, Context context, AndroidSyncClient client, AndroidPcDiscoveryClient discoveryClient) {
        this.store = store;
        this.context = context == null ? null : context.getApplicationContext();
        this.client = client;
        this.discoveryClient = discoveryClient;
    }

    public void syncOnce() {
        if (!tryEnterSync()) {
            Log.i(DIAG_TAG, "AndroidSyncRunner syncOnce skipped reason=already-running");
            return;
        }
        try {
            syncOnceCore();
        } finally {
            exitSync();
        }
    }

    private void syncOnceCore() {
        long startedAt = System.currentTimeMillis();
        SyncSettings settings = store.getSyncSettings();
        Log.i(DIAG_TAG, "AndroidSyncRunner syncOnce start paired=" + settings.isPaired
                + " host=" + settings.peerHost
                + " port=" + settings.peerPort
                + " lastSync=" + settings.lastSyncUnixSeconds);
        String requestJson = buildSyncRequestJson(settings);
        if (requestJson.isEmpty()) {
            settings.lastError = "Sync settings are incomplete.";
            store.saveSyncResult(settings);
            Log.i(DIAG_TAG, "AndroidSyncRunner syncOnce skipped reason=incomplete ms=" + elapsed(startedAt));
            return;
        }

        long sendStartedAt = System.currentTimeMillis();
        String responseJson = client.sendJson(settings, requestJson);
        Log.i(DIAG_TAG, "AndroidSyncRunner direct send responseEmpty=" + responseJson.isEmpty()
                + " lastError=" + settings.lastError
                + " ms=" + elapsed(sendStartedAt));
        if (SyncConnectionState.shouldClearPairingAfterSync(responseJson, settings.lastError)) {
            String recoveredResponse = tryReconnectWithDiscovery(settings, requestJson);
            if (recoveredResponse.isEmpty()) {
                store.saveSyncSettings(SyncSettings.unpaired());
                Log.i(DIAG_TAG, "AndroidSyncRunner syncOnce unpaired after failed recovery ms=" + elapsed(startedAt));
                return;
            }
            responseJson = recoveredResponse;
        } else if (responseJson.isEmpty()) {
            responseJson = tryReconnectWithDiscovery(settings, requestJson);
        }

        if (!responseJson.isEmpty()) {
            String error = AndroidSyncResponseReader.readError(responseJson);
            if (error.isEmpty()) {
                int changed = store.addSegments(AndroidSyncResponseReader.readSegments(responseJson));
                int changedApps = store.addAppUsageEntries(AndroidSyncResponseReader.readAppUsageEntries(responseJson));
                store.savePeerReminderState(AndroidSyncResponseReader.readReminderState(responseJson));
                Log.i(DIAG_TAG, "AndroidSyncRunner merged segments changed=" + changed + " appUsageChanged=" + changedApps);
                long responseTimestamp = AndroidSyncResponseReader.readTimestampUnixSeconds(responseJson);
                if (responseTimestamp > 0L) {
                    settings.lastSyncUnixSeconds = responseTimestamp;
                }
                settings.lastError = "";
            } else {
                settings.lastError = error;
            }
        }
        store.saveSyncResult(settings);
        Log.i(DIAG_TAG, "AndroidSyncRunner syncOnce end responseEmpty=" + responseJson.isEmpty()
                + " lastError=" + settings.lastError
                + " ms=" + elapsed(startedAt));
    }

    private static boolean tryEnterSync() {
        synchronized (SYNC_LOCK) {
            if (syncRunning) {
                return false;
            }
            syncRunning = true;
            return true;
        }
    }

    private static void exitSync() {
        synchronized (SYNC_LOCK) {
            syncRunning = false;
        }
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
            request.put("AppUsageEntries", appUsageEntriesToJson(store.getAppUsageEntries(LocalDate.now().minusDays(30), LocalDate.now())));
            request.put("Settings", settingsToJson());
            request.put("ReminderState", reminderStateToJson(store.getLocalReminderState()));
            request.put("SinceUnixSeconds", settings.lastSyncUnixSeconds);
            request.put("TimestampUnixSeconds", nowSeconds);
            request.put("Signature", SyncMessageSigner.sign(
                    SyncMessages.SYNC_REQUEST,
                    nowSeconds,
                    SyncSignatureBody.forSyncRequest(store.getDeviceId(), "android", settings.lastSyncUnixSeconds),
                    settings.sharedSecret));
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

    private String tryReconnectWithDiscovery(SyncSettings settings, String requestJson) {
        long startedAt = System.currentTimeMillis();
        if (settings == null || requestJson == null || requestJson.isEmpty()) {
            Log.i(DIAG_TAG, "AndroidSyncRunner discovery skipped reason=empty ms=" + elapsed(startedAt));
            return "";
        }

        AndroidPcDiscoveryClient.DiscoveryResult discovery = discoveryClient.discoverKnownPeer(settings.peerHost, settings.peerPort);
        Log.i(DIAG_TAG, "AndroidSyncRunner discovery found=" + discovery.found
                + " host=" + discovery.host
                + " port=" + discovery.port
                + " device=" + discovery.deviceId
                + " ms=" + elapsed(startedAt));
        if (!AndroidSyncEndpointResolver.applyDiscoveredPeer(settings, discovery)) {
            return "";
        }

        long sendStartedAt = System.currentTimeMillis();
        String responseJson = client.sendJson(settings, requestJson);
        Log.i(DIAG_TAG, "AndroidSyncRunner recovered send responseEmpty=" + responseJson.isEmpty()
                + " lastError=" + settings.lastError
                + " ms=" + elapsed(sendStartedAt));
        if (!responseJson.isEmpty() && AndroidSyncResponseReader.readError(responseJson).isEmpty()) {
            store.saveSyncSettings(settings);
        }
        return responseJson;
    }

    private static long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
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

    private static JSONArray appUsageEntriesToJson(List<AppUsageEntry> entries) throws JSONException {
        JSONArray json = new JSONArray();
        for (AppUsageEntry entry : entries) {
            JSONObject item = new JSONObject();
            item.put("EntryId", entry.entryId);
            item.put("DeviceId", entry.deviceId);
            item.put("Platform", entry.platform);
            item.put("Source", entry.source);
            item.put("AppId", entry.appId);
            item.put("AppName", entry.appName);
            item.put("IconData", entry.iconData);
            item.put("LocalDate", entry.localDate);
            item.put("DurationSeconds", entry.durationSeconds);
            item.put("UpdatedAtUnixSeconds", entry.updatedAtUnixSeconds);
            json.put(item);
        }
        return json;
    }

    private static JSONObject reminderStateToJson(ReminderRuntimeState state) throws JSONException {
        JSONObject json = new JSONObject();
        if (state == null) {
            state = new ReminderRuntimeState();
        }
        json.put("DeviceId", state.deviceId);
        json.put("Platform", state.platform);
        json.put("IsCounting", state.isCounting);
        json.put("CurrentSessionStartedUnixSeconds", state.currentSessionStartedUnixSeconds);
        return json;
    }
}
