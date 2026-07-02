package com.eyetimetracker.android;

public final class AndroidPairingClient {
    private final AndroidSyncClient client;

    public AndroidPairingClient() {
        this(new AndroidSyncClient());
    }

    public AndroidPairingClient(AndroidSyncClient client) {
        this.client = client;
    }

    public boolean pair(SyncSettings settings, String phoneDeviceId, String pairingCode) {
        if (settings == null) {
            return false;
        }
        String requestJson = buildPairRequest(phoneDeviceId, pairingCode);
        String responseJson = client.sendJson(settings.peerHost, settings.peerPort, requestJson, settings);
        if (responseJson.isEmpty()) {
            return false;
        }

        if (!readAccepted(responseJson)) {
            String error = readString(responseJson, "Error", "error");
            settings.lastError = error.isEmpty() ? "Pairing was rejected." : error;
            return false;
        }

        settings.isPaired = true;
        settings.peerDeviceId = readString(responseJson, "DeviceId", "deviceId");
        settings.peerPlatform = readString(responseJson, "Platform", "platform");
        settings.sharedSecret = readString(responseJson, "SharedSecret", "sharedSecret");
        settings.lastError = "";
        return !settings.peerDeviceId.isEmpty() && !settings.sharedSecret.isEmpty();
    }

    public boolean disconnect(SyncSettings settings, String phoneDeviceId) {
        if (settings == null || !settings.isPaired) {
            return true;
        }

        String requestJson = buildDisconnectRequest(phoneDeviceId);
        String responseJson = client.sendJson(settings, requestJson);
        return responseJson.isEmpty() || readAccepted(responseJson);
    }

    private static String buildPairRequest(String phoneDeviceId, String pairingCode) {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        return "{"
                + "\"Type\":\"" + SyncMessages.PAIR_REQUEST + "\","
                + "\"PairingCode\":\"" + escape(pairingCode) + "\","
                + "\"DeviceId\":\"" + escape(phoneDeviceId) + "\","
                + "\"Platform\":\"android\","
                + "\"TimestampUnixSeconds\":" + nowSeconds
                + "}";
    }

    private static String buildDisconnectRequest(String phoneDeviceId) {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        return "{"
                + "\"Type\":\"" + SyncMessages.DISCONNECT_REQUEST + "\","
                + "\"DeviceId\":\"" + escape(phoneDeviceId) + "\","
                + "\"Platform\":\"android\","
                + "\"TimestampUnixSeconds\":" + nowSeconds
                + "}";
    }

    private static boolean readAccepted(String json) {
        return "true".equalsIgnoreCase(readRawValue(json, "Accepted", "accepted"));
    }

    private static String readString(String json, String pascalName, String camelName) {
        String raw = readRawValue(json, pascalName, camelName);
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return raw;
    }

    private static String readRawValue(String json, String pascalName, String camelName) {
        String value = readRawValue(json, pascalName);
        return value.isEmpty() ? readRawValue(json, camelName) : value;
    }

    private static String readRawValue(String json, String name) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(name) + "\"\\s*:\\s*(\"(?:\\\\.|[^\"])*\"|-?\\d+|true|false|null)",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1);
        return "null".equals(value) ? "" : value;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
