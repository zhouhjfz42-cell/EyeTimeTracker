package com.eyetimetracker.android;

import org.json.JSONException;
import org.json.JSONObject;

public final class FamilyBindingProtocol {
    public static final int DEFAULT_DISCOVERY_PORT = 17429;

    public static String buildDiscoveryRequest(String deviceId) {
        return "{"
                + "\"Type\":\"" + SyncMessages.FAMILY_BINDING_DISCOVERY_REQUEST + "\","
                + "\"DeviceId\":\"" + escape(deviceId) + "\","
                + "\"Platform\":\"android\""
                + "}";
    }

    public static String buildDiscoveryResponse(String deviceId, int port) {
        return "{"
                + "\"Type\":\"" + SyncMessages.FAMILY_BINDING_DISCOVERY_RESPONSE + "\","
                + "\"DeviceId\":\"" + escape(deviceId) + "\","
                + "\"Platform\":\"android\","
                + "\"Port\":" + Math.max(0, port)
                + "}";
    }

    public static DiscoveryResponse parseDiscoveryResponse(String jsonText) {
        if (!SyncMessages.FAMILY_BINDING_DISCOVERY_RESPONSE.equals(readString(jsonText, "Type"))) {
            return DiscoveryResponse.empty();
        }
        return new DiscoveryResponse(
                true,
                readString(jsonText, "DeviceId"),
                readInt(jsonText, "Port"));
    }

    public static String buildJoinRequest(String deviceId, String bindingCode) {
        return "{"
                + "\"Type\":\"" + SyncMessages.FAMILY_BINDING_JOIN_REQUEST + "\","
                + "\"DeviceId\":\"" + escape(deviceId) + "\","
                + "\"Platform\":\"android\","
                + "\"BindingCode\":\"" + escape(FamilyBindingInvite.normalizeBindingCode(bindingCode)) + "\","
                + "\"TimestampUnixSeconds\":" + (System.currentTimeMillis() / 1000L)
                + "}";
    }

    public static JoinRequest parseJoinRequest(String jsonText) {
        if (!SyncMessages.FAMILY_BINDING_JOIN_REQUEST.equals(readString(jsonText, "Type"))) {
            return new JoinRequest("", "");
        }
        return new JoinRequest(
                readString(jsonText, "DeviceId"),
                readString(jsonText, "BindingCode"));
    }

    public static String buildJoinResponse(boolean accepted, String error, FamilyBindingInvite invite) {
        return buildJoinResponse(accepted, error, invite, null);
    }

    public static String buildJoinResponse(boolean accepted, String error, FamilyBindingInvite invite, ParentPasscode parentPasscode) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"Type\":\"").append(SyncMessages.FAMILY_BINDING_JOIN_RESPONSE).append("\",")
                .append("\"Accepted\":").append(accepted).append(",")
                .append("\"Error\":\"").append(escape(error)).append("\"");
        if (accepted && invite != null && invite.isValid()) {
            json.append(",\"FamilyId\":\"").append(escape(invite.familyId)).append("\"")
                    .append(",\"ChildProfile\":").append(childProfileToJsonText(invite.childProfile));
            if (parentPasscode != null && parentPasscode.isConfigured()) {
                json.append(",\"ParentPasscode\":").append(parentPasscodeToJsonText(parentPasscode));
            }
        }
        json.append(",\"TimestampUnixSeconds\":").append(System.currentTimeMillis() / 1000L);
        json.append("}");
        return json.toString();
    }

    public static JoinResponse parseJoinResponse(String jsonText) {
        if (!SyncMessages.FAMILY_BINDING_JOIN_RESPONSE.equals(readString(jsonText, "Type"))) {
            return JoinResponse.rejected("Invalid response.");
        }
        boolean accepted = readBoolean(jsonText, "Accepted");
        String childJson = readObject(jsonText, "ChildProfile");
        ChildProfile child = childJson.isEmpty() ? null : childProfileFromJsonText(childJson);
        String passcodeJson = readObject(jsonText, "ParentPasscode");
        ParentPasscode passcode = passcodeJson.isEmpty() ? null : parentPasscodeFromJsonText(passcodeJson);
        return new JoinResponse(
                accepted,
                readString(jsonText, "Error"),
                readString(jsonText, "FamilyId"),
                child,
                passcode);
    }

    public static JSONObject childProfileToJson(ChildProfile profile) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("childId", profile.childId);
        json.put("nickname", profile.nickname);
        json.put("ageBand", profile.ageBand);
        json.put("createdAtUnixSeconds", profile.createdAtUnixSeconds);
        json.put("updatedAtUnixSeconds", profile.updatedAtUnixSeconds);
        return json;
    }

    public static ChildProfile childProfileFromJson(JSONObject json) {
        return new ChildProfile(
                json.optString("childId", ""),
                json.optString("nickname", ""),
                json.optString("ageBand", ChildProfile.AGE_BAND_UNKNOWN),
                json.optLong("createdAtUnixSeconds", 0L),
                json.optLong("updatedAtUnixSeconds", 0L));
    }

    private static String childProfileToJsonText(ChildProfile profile) {
        return "{"
                + "\"childId\":\"" + escape(profile.childId) + "\","
                + "\"nickname\":\"" + escape(profile.nickname) + "\","
                + "\"ageBand\":\"" + escape(profile.ageBand) + "\","
                + "\"createdAtUnixSeconds\":" + profile.createdAtUnixSeconds + ","
                + "\"updatedAtUnixSeconds\":" + profile.updatedAtUnixSeconds
                + "}";
    }

    private static ChildProfile childProfileFromJsonText(String jsonText) {
        return new ChildProfile(
                readString(jsonText, "childId"),
                readString(jsonText, "nickname"),
                readString(jsonText, "ageBand"),
                readLong(jsonText, "createdAtUnixSeconds"),
                readLong(jsonText, "updatedAtUnixSeconds"));
    }

    private static String parentPasscodeToJsonText(ParentPasscode passcode) {
        return "{"
                + "\"hash\":\"" + escape(passcode.hash) + "\","
                + "\"salt\":\"" + escape(passcode.salt) + "\","
                + "\"updatedAtUnixSeconds\":" + passcode.updatedAtUnixSeconds
                + "}";
    }

    private static ParentPasscode parentPasscodeFromJsonText(String jsonText) {
        ParentPasscode passcode = new ParentPasscode(
                readString(jsonText, "hash"),
                readString(jsonText, "salt"),
                readLong(jsonText, "updatedAtUnixSeconds"));
        return passcode.isConfigured() ? passcode : null;
    }

    private static String readString(String json, String name) {
        String raw = readRawValue(json, name);
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return unescape(raw.substring(1, raw.length() - 1));
        }
        return "";
    }

    private static boolean readBoolean(String json, String name) {
        return "true".equalsIgnoreCase(readRawValue(json, name));
    }

    private static int readInt(String json, String name) {
        try {
            return Integer.parseInt(readRawValue(json, name));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long readLong(String json, String name) {
        try {
            return Long.parseLong(readRawValue(json, name));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String readObject(String json, String name) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(name) + "\"\\s*:\\s*(\\{[^{}]*\\})",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(json == null ? "" : json);
        return matcher.find() ? matcher.group(1) : "";
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
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return safe(value)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class DiscoveryResponse {
        public final boolean found;
        public final String deviceId;
        public final int port;

        public DiscoveryResponse(boolean found, String deviceId, int port) {
            this.found = found;
            this.deviceId = safe(deviceId);
            this.port = port;
        }

        public static DiscoveryResponse empty() {
            return new DiscoveryResponse(false, "", 0);
        }
    }

    public static final class JoinRequest {
        public final String deviceId;
        public final String bindingCode;

        public JoinRequest(String deviceId, String bindingCode) {
            this.deviceId = safe(deviceId);
            this.bindingCode = FamilyBindingInvite.normalizeBindingCode(bindingCode);
        }
    }

    public static final class JoinResponse {
        public final boolean accepted;
        public final String error;
        public final String familyId;
        public final ChildProfile childProfile;
        public final ParentPasscode parentPasscode;

        public JoinResponse(boolean accepted, String error, String familyId, ChildProfile childProfile) {
            this(accepted, error, familyId, childProfile, null);
        }

        public JoinResponse(boolean accepted, String error, String familyId, ChildProfile childProfile, ParentPasscode parentPasscode) {
            this.accepted = accepted;
            this.error = safe(error);
            this.familyId = safe(familyId);
            this.childProfile = childProfile;
            this.parentPasscode = parentPasscode;
        }

        public static JoinResponse rejected(String error) {
            return new JoinResponse(false, error, "", null, null);
        }
    }

    private FamilyBindingProtocol() {
    }
}
