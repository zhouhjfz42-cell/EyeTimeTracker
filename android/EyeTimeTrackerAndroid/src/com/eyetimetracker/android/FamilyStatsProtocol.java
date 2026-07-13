package com.eyetimetracker.android;

import java.util.ArrayList;
import java.util.List;

public final class FamilyStatsProtocol {
    public static final int DEFAULT_DISCOVERY_PORT = 17430;

    public static String buildDiscoveryRequest(String familyId, String childDeviceId) {
        return "{"
                + "\"Type\":\"" + SyncMessages.FAMILY_STATS_DISCOVERY_REQUEST + "\","
                + "\"FamilyId\":\"" + escape(familyId) + "\","
                + "\"ChildDeviceId\":\"" + escape(childDeviceId) + "\","
                + "\"Platform\":\"android\""
                + "}";
    }

    public static DiscoveryRequest parseDiscoveryRequest(String jsonText) {
        if (!SyncMessages.FAMILY_STATS_DISCOVERY_REQUEST.equals(readString(jsonText, "Type"))) {
            return DiscoveryRequest.invalid();
        }
        String familyId = readString(jsonText, "FamilyId");
        String childDeviceId = readString(jsonText, "ChildDeviceId");
        return new DiscoveryRequest(!familyId.isEmpty() && !childDeviceId.isEmpty(), familyId, childDeviceId);
    }

    public static String buildDiscoveryResponse(String familyId, String parentDeviceId, int port) {
        return "{"
                + "\"Type\":\"" + SyncMessages.FAMILY_STATS_DISCOVERY_RESPONSE + "\","
                + "\"FamilyId\":\"" + escape(familyId) + "\","
                + "\"ParentDeviceId\":\"" + escape(parentDeviceId) + "\","
                + "\"Platform\":\"android\","
                + "\"Port\":" + Math.max(0, port)
                + "}";
    }

    public static DiscoveryResponse parseDiscoveryResponse(String jsonText) {
        if (!SyncMessages.FAMILY_STATS_DISCOVERY_RESPONSE.equals(readString(jsonText, "Type"))) {
            return DiscoveryResponse.empty();
        }
        return new DiscoveryResponse(
                true,
                readString(jsonText, "FamilyId"),
                readString(jsonText, "ParentDeviceId"),
                readInt(jsonText, "Port"));
    }

    public static String buildUploadNowRequest(String familyId, String childDeviceId, String parentDeviceId) {
        return buildUploadNowRequest(familyId, childDeviceId, parentDeviceId, 0);
    }

    public static String buildUploadNowRequest(String familyId, String childDeviceId, String parentDeviceId, int parentStatsPort) {
        return "{"
                + "\"Type\":\"" + SyncMessages.FAMILY_STATS_UPLOAD_NOW_REQUEST + "\","
                + "\"FamilyId\":\"" + escape(familyId) + "\","
                + "\"ChildDeviceId\":\"" + escape(childDeviceId) + "\","
                + "\"ParentDeviceId\":\"" + escape(parentDeviceId) + "\","
                + "\"ParentStatsPort\":" + Math.max(0, parentStatsPort) + ","
                + "\"Platform\":\"android\","
                + "\"TimestampUnixSeconds\":" + (System.currentTimeMillis() / 1000L)
                + "}";
    }

    public static UploadNowRequest parseUploadNowRequest(String jsonText) {
        if (!SyncMessages.FAMILY_STATS_UPLOAD_NOW_REQUEST.equals(readString(jsonText, "Type"))) {
            return UploadNowRequest.invalid();
        }
        String familyId = readString(jsonText, "FamilyId");
        String childDeviceId = readString(jsonText, "ChildDeviceId");
        return new UploadNowRequest(
                !familyId.isEmpty() && !childDeviceId.isEmpty(),
                familyId,
                childDeviceId,
                readString(jsonText, "ParentDeviceId"),
                readInt(jsonText, "ParentStatsPort"));
    }

    public static String buildUploadNowResponse(boolean accepted, String error) {
        return "{"
                + "\"Type\":\"" + SyncMessages.FAMILY_STATS_UPLOAD_NOW_RESPONSE + "\","
                + "\"Accepted\":" + accepted + ","
                + "\"Error\":\"" + escape(error) + "\","
                + "\"TimestampUnixSeconds\":" + (System.currentTimeMillis() / 1000L)
                + "}";
    }

    public static UploadNowResponse parseUploadNowResponse(String jsonText) {
        if (!SyncMessages.FAMILY_STATS_UPLOAD_NOW_RESPONSE.equals(readString(jsonText, "Type"))) {
            return new UploadNowResponse(false, "Invalid response.");
        }
        return new UploadNowResponse(readBoolean(jsonText, "Accepted"), readString(jsonText, "Error"));
    }

    public static String buildUploadRequest(
            String familyId,
            String childId,
            String childDeviceId,
            List<UsageSegment> segments) {
        return buildUploadRequest(familyId, childId, childDeviceId, segments, new ArrayList<>());
    }

    public static String buildUploadRequest(
            String familyId,
            String childId,
            String childDeviceId,
            List<UsageSegment> segments,
            List<AppUsageEntry> appUsageEntries) {
        return buildUploadRequest(familyId, childId, childDeviceId, segments, appUsageEntries, null);
    }

    public static String buildUploadRequest(
            String familyId,
            String childId,
            String childDeviceId,
            List<UsageSegment> segments,
            List<AppUsageEntry> appUsageEntries,
            FamilyChildHomeSnapshot homeSnapshot) {
        return "{"
                + "\"Type\":\"" + SyncMessages.FAMILY_STATS_UPLOAD_REQUEST + "\","
                + "\"Accepted\":true,"
                + "\"FamilyId\":\"" + escape(familyId) + "\","
                + "\"ChildId\":\"" + escape(childId) + "\","
                + "\"ChildDeviceId\":\"" + escape(childDeviceId) + "\","
                + "\"Platform\":\"android\","
                + "\"Segments\":" + segmentsToJson(segments) + ","
                + "\"AppUsageEntries\":" + appUsageEntriesToJson(appUsageEntries) + ","
                + "\"HasHomeSnapshot\":" + (homeSnapshot != null) + ","
                + "\"HomeSnapshot\":" + homeSnapshotToJson(homeSnapshot) + ","
                + "\"TimestampUnixSeconds\":" + (System.currentTimeMillis() / 1000L)
                + "}";
    }

    public static UploadRequest parseUploadRequest(String jsonText) {
        if (!SyncMessages.FAMILY_STATS_UPLOAD_REQUEST.equals(readString(jsonText, "Type"))) {
            return UploadRequest.invalid();
        }
        String familyId = readString(jsonText, "FamilyId");
        String childDeviceId = readString(jsonText, "ChildDeviceId");
        return new UploadRequest(
                !familyId.isEmpty() && !childDeviceId.isEmpty(),
                familyId,
                readString(jsonText, "ChildId"),
                childDeviceId,
                AndroidSyncResponseReader.readSegments(jsonText),
                AndroidSyncResponseReader.readAppUsageEntries(jsonText),
                readBoolean(jsonText, "HasHomeSnapshot")
                        ? homeSnapshotFromJson(readObject(jsonText, "HomeSnapshot"))
                        : null);
    }

    public static String buildUploadResponse(boolean accepted, String error, int changedSegments) {
        return buildUploadResponse(accepted, error, changedSegments, 0, false, false, null, null);
    }

    public static String buildUploadResponse(
            boolean accepted,
            String error,
            int changedSegments,
            int reminderMinutes,
            boolean repeatReminder) {
        return buildUploadResponse(accepted, error, changedSegments, reminderMinutes, repeatReminder, null);
    }

    public static String buildUploadResponse(
            boolean accepted,
            String error,
            int changedSegments,
            int reminderMinutes,
            boolean repeatReminder,
            ParentPasscode parentPasscode) {
        return buildUploadResponse(accepted, error, changedSegments, reminderMinutes, repeatReminder, parentPasscode, null);
    }

    public static String buildUploadResponse(
            boolean accepted,
            String error,
            int changedSegments,
            int reminderMinutes,
            boolean repeatReminder,
            ParentPasscode parentPasscode,
            FamilyEyeRules familyEyeRules) {
        return buildUploadResponse(accepted, error, changedSegments, reminderMinutes, repeatReminder, true, parentPasscode, familyEyeRules);
    }

    private static String buildUploadResponse(
            boolean accepted,
            String error,
            int changedSegments,
            int reminderMinutes,
            boolean repeatReminder,
            boolean hasReminderSettings,
            ParentPasscode parentPasscode,
            FamilyEyeRules familyEyeRules) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"Type\":\"").append(SyncMessages.FAMILY_STATS_UPLOAD_RESPONSE).append("\",")
                .append("\"Accepted\":").append(accepted).append(",")
                .append("\"Error\":\"").append(escape(error)).append("\",")
                .append("\"ChangedSegments\":").append(Math.max(0, changedSegments)).append(",")
                .append("\"HasReminderSettings\":").append(hasReminderSettings).append(",")
                .append("\"ReminderMinutes\":").append(ReminderThreshold.clampMinutes(reminderMinutes)).append(",")
                .append("\"RepeatReminder\":").append(repeatReminder);
        if (accepted && parentPasscode != null && parentPasscode.isConfigured()) {
            json.append(",\"ParentPasscode\":").append(parentPasscodeToJsonText(parentPasscode));
        }
        if (accepted && familyEyeRules != null) {
            json.append(",\"HasFamilyEyeRules\":true")
                    .append(",\"FamilyEyeRules\":").append(familyEyeRulesToJsonText(familyEyeRules));
        } else {
            json.append(",\"HasFamilyEyeRules\":false");
        }
        json.append(",\"TimestampUnixSeconds\":").append(System.currentTimeMillis() / 1000L)
                .append("}");
        return json.toString();
    }

    public static UploadResponse parseUploadResponse(String jsonText) {
        if (!SyncMessages.FAMILY_STATS_UPLOAD_RESPONSE.equals(readString(jsonText, "Type"))) {
            return UploadResponse.rejected("Invalid response.");
        }
        boolean accepted = readBoolean(jsonText, "Accepted");
        boolean hasReminderSettings = readBoolean(jsonText, "HasReminderSettings");
        String passcodeJson = readObject(jsonText, "ParentPasscode");
        boolean hasFamilyEyeRules = readBoolean(jsonText, "HasFamilyEyeRules");
        String rulesJson = readObject(jsonText, "FamilyEyeRules");
        return new UploadResponse(
                accepted,
                readString(jsonText, "Error"),
                readInt(jsonText, "ChangedSegments"),
                readInt(jsonText, "ReminderMinutes"),
                readBoolean(jsonText, "RepeatReminder"),
                hasReminderSettings,
                passcodeJson.isEmpty() ? null : parentPasscodeFromJsonText(passcodeJson),
                hasFamilyEyeRules && !rulesJson.isEmpty() ? familyEyeRulesFromJsonText(rulesJson) : null);
    }

    private static String segmentsToJson(List<UsageSegment> segments) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        if (segments != null) {
            boolean first = true;
            for (UsageSegment segment : segments) {
                if (segment == null || segment.segmentId.trim().isEmpty() || segment.endUnixSeconds <= segment.startUnixSeconds) {
                    continue;
                }
                if (!first) {
                    json.append(",");
                }
                first = false;
                json.append("{")
                        .append("\"SegmentId\":\"").append(escape(segment.segmentId)).append("\",")
                        .append("\"DeviceId\":\"").append(escape(segment.deviceId)).append("\",")
                        .append("\"Platform\":\"").append(escape(segment.platform)).append("\",")
                        .append("\"Source\":\"").append(escape(segment.source)).append("\",")
                        .append("\"StartUnixSeconds\":").append(segment.startUnixSeconds).append(",")
                        .append("\"EndUnixSeconds\":").append(segment.endUnixSeconds).append(",")
                        .append("\"LocalDate\":\"").append(escape(segment.localDate)).append("\",")
                        .append("\"CreatedAtUnixSeconds\":").append(segment.createdAtUnixSeconds).append(",")
                        .append("\"UpdatedAtUnixSeconds\":").append(segment.updatedAtUnixSeconds)
                        .append("}");
            }
        }
        json.append("]");
        return json.toString();
    }

    private static String appUsageEntriesToJson(List<AppUsageEntry> entries) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        if (entries != null) {
            boolean first = true;
            for (AppUsageEntry entry : entries) {
                if (entry == null || entry.entryId.trim().isEmpty() || entry.appId.trim().isEmpty() || entry.durationSeconds <= 0L) {
                    continue;
                }
                if (!first) {
                    json.append(",");
                }
                first = false;
                json.append("{")
                        .append("\"EntryId\":\"").append(escape(entry.entryId)).append("\",")
                        .append("\"DeviceId\":\"").append(escape(entry.deviceId)).append("\",")
                        .append("\"Platform\":\"").append(escape(entry.platform)).append("\",")
                        .append("\"Source\":\"").append(escape(entry.source)).append("\",")
                        .append("\"AppId\":\"").append(escape(entry.appId)).append("\",")
                        .append("\"AppName\":\"").append(escape(entry.appName)).append("\",")
                        .append("\"LocalDate\":\"").append(escape(entry.localDate)).append("\",")
                        .append("\"DurationSeconds\":").append(entry.durationSeconds).append(",")
                        .append("\"UpdatedAtUnixSeconds\":").append(entry.updatedAtUnixSeconds)
                        .append("}");
            }
        }
        json.append("]");
        return json.toString();
    }

    private static String homeSnapshotToJson(FamilyChildHomeSnapshot snapshot) {
        if (snapshot == null) {
            return "null";
        }
        return "{"
                + "\"Date\":\"" + escape(snapshot.date) + "\","
                + "\"TodaySeconds\":" + snapshot.todaySeconds + ","
                + "\"YesterdaySeconds\":" + snapshot.yesterdaySeconds + ","
                + "\"WeekSeconds\":" + snapshot.weekSeconds + ","
                + "\"MonthSeconds\":" + snapshot.monthSeconds + ","
                + "\"TopAppName\":\"" + escape(snapshot.topAppName) + "\","
                + "\"TopAppSeconds\":" + snapshot.topAppSeconds + ","
                + "\"UpdatedAtUnixSeconds\":" + snapshot.updatedAtUnixSeconds
                + "}";
    }

    private static FamilyChildHomeSnapshot homeSnapshotFromJson(String jsonText) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return null;
        }
        FamilyChildHomeSnapshot snapshot = new FamilyChildHomeSnapshot(
                readString(jsonText, "Date"),
                readLong(jsonText, "TodaySeconds"),
                readLong(jsonText, "YesterdaySeconds"),
                readLong(jsonText, "WeekSeconds"),
                readLong(jsonText, "MonthSeconds"),
                readString(jsonText, "TopAppName"),
                readLong(jsonText, "TopAppSeconds"),
                readLong(jsonText, "UpdatedAtUnixSeconds"));
        return snapshot.updatedAtUnixSeconds > 0L && !snapshot.date.trim().isEmpty() ? snapshot : null;
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

    private static String familyEyeRulesToJsonText(FamilyEyeRules rules) {
        return "{"
                + "\"childId\":\"" + escape(rules.childId) + "\","
                + "\"continuousUseReminderEnabled\":" + rules.continuousUseReminderEnabled + ","
                + "\"continuousUseMinutes\":" + rules.continuousUseMinutes + ","
                + "\"restMinutes\":" + rules.restMinutes + ","
                + "\"disabledPeriodEnabled\":" + rules.disabledPeriodEnabled + ","
                + "\"disabledPeriodStartMinutes\":" + rules.disabledPeriodStartMinutes + ","
                + "\"disabledPeriodEndMinutes\":" + rules.disabledPeriodEndMinutes + ","
                + "\"disabledPeriods\":\"" + escape(FamilyEyeRules.encodeDisabledPeriods(rules.disabledPeriods)) + "\","
                + "\"disabledPeriodReminderIntervalMinutes\":" + rules.disabledPeriodReminderIntervalMinutes + ","
                + "\"updatedAtUnixSeconds\":" + rules.updatedAtUnixSeconds
                + "}";
    }

    private static FamilyEyeRules familyEyeRulesFromJsonText(String jsonText) {
        String disabledPeriods = readString(jsonText, "disabledPeriods");
        java.util.List<FamilyEyeRules.DisabledPeriod> periods = disabledPeriods.isEmpty()
                ? java.util.Collections.singletonList(FamilyEyeRules.DisabledPeriod.create(
                        readInt(jsonText, "disabledPeriodStartMinutes"),
                        readInt(jsonText, "disabledPeriodEndMinutes")))
                : FamilyEyeRules.decodeDisabledPeriods(disabledPeriods);
        return FamilyEyeRules.createWithDisabledPeriods(
                readString(jsonText, "childId"),
                readBoolean(jsonText, "continuousUseReminderEnabled"),
                readInt(jsonText, "continuousUseMinutes"),
                readInt(jsonText, "restMinutes"),
                readBoolean(jsonText, "disabledPeriodEnabled"),
                periods,
                readInt(jsonText, "disabledPeriodReminderIntervalMinutes"),
                readLong(jsonText, "updatedAtUnixSeconds"));
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
        public final String familyId;
        public final String parentDeviceId;
        public final int port;

        private DiscoveryResponse(boolean found, String familyId, String parentDeviceId, int port) {
            this.found = found;
            this.familyId = safe(familyId);
            this.parentDeviceId = safe(parentDeviceId);
            this.port = port;
        }

        public static DiscoveryResponse empty() {
            return new DiscoveryResponse(false, "", "", 0);
        }
    }

    public static final class DiscoveryRequest {
        public final boolean valid;
        public final String familyId;
        public final String childDeviceId;

        private DiscoveryRequest(boolean valid, String familyId, String childDeviceId) {
            this.valid = valid;
            this.familyId = safe(familyId);
            this.childDeviceId = safe(childDeviceId);
        }

        public static DiscoveryRequest invalid() {
            return new DiscoveryRequest(false, "", "");
        }
    }

    public static final class UploadRequest {
        public final boolean valid;
        public final String familyId;
        public final String childId;
        public final String childDeviceId;
        public final List<UsageSegment> segments;
        public final List<AppUsageEntry> appUsageEntries;
        public final FamilyChildHomeSnapshot homeSnapshot;

        private UploadRequest(
                boolean valid,
                String familyId,
                String childId,
                String childDeviceId,
                List<UsageSegment> segments,
                List<AppUsageEntry> appUsageEntries,
                FamilyChildHomeSnapshot homeSnapshot) {
            this.valid = valid;
            this.familyId = safe(familyId);
            this.childId = safe(childId);
            this.childDeviceId = safe(childDeviceId);
            this.segments = segments == null ? new ArrayList<>() : segments;
            this.appUsageEntries = appUsageEntries == null ? new ArrayList<>() : appUsageEntries;
            this.homeSnapshot = homeSnapshot;
        }

        public static UploadRequest invalid() {
            return new UploadRequest(false, "", "", "", new ArrayList<>(), new ArrayList<>(), null);
        }
    }

    public static final class UploadNowRequest {
        public final boolean valid;
        public final String familyId;
        public final String childDeviceId;
        public final String parentDeviceId;
        public final int parentStatsPort;

        private UploadNowRequest(boolean valid, String familyId, String childDeviceId, String parentDeviceId, int parentStatsPort) {
            this.valid = valid;
            this.familyId = safe(familyId);
            this.childDeviceId = safe(childDeviceId);
            this.parentDeviceId = safe(parentDeviceId);
            this.parentStatsPort = Math.max(0, parentStatsPort);
        }

        public static UploadNowRequest invalid() {
            return new UploadNowRequest(false, "", "", "", 0);
        }
    }

    public static final class UploadNowResponse {
        public final boolean accepted;
        public final String error;

        private UploadNowResponse(boolean accepted, String error) {
            this.accepted = accepted;
            this.error = safe(error);
        }
    }

    public static final class UploadResponse {
        public final boolean accepted;
        public final String error;
        public final int changedSegments;
        public final int reminderMinutes;
        public final boolean repeatReminder;
        public final boolean hasReminderSettings;
        public final ParentPasscode parentPasscode;
        public final boolean hasFamilyEyeRules;
        public final FamilyEyeRules familyEyeRules;

        private UploadResponse(
                boolean accepted,
                String error,
                int changedSegments,
                int reminderMinutes,
                boolean repeatReminder,
                boolean hasReminderSettings,
                ParentPasscode parentPasscode,
                FamilyEyeRules familyEyeRules) {
            this.accepted = accepted;
            this.error = safe(error);
            this.changedSegments = Math.max(0, changedSegments);
            this.reminderMinutes = ReminderThreshold.clampMinutes(reminderMinutes);
            this.repeatReminder = repeatReminder;
            this.hasReminderSettings = hasReminderSettings;
            this.parentPasscode = parentPasscode;
            this.hasFamilyEyeRules = familyEyeRules != null;
            this.familyEyeRules = familyEyeRules;
        }

        public static UploadResponse rejected(String error) {
            return new UploadResponse(false, error, 0, 0, false, false, null, null);
        }
    }

    private FamilyStatsProtocol() {
    }
}
