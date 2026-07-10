package com.eyetimetracker.android;

public final class SyncMessages {
    public static final String PAIR_REQUEST = "pairRequest";
    public static final String PAIR_ACCEPT = "pairAccept";
    public static final String SYNC_REQUEST = "syncRequest";
    public static final String SYNC_RESPONSE = "syncResponse";
    public static final String DISCONNECT_REQUEST = "disconnectRequest";
    public static final String DISCONNECT_RESPONSE = "disconnectResponse";
    public static final String REMINDER_CLAIM = "reminderClaim";
    public static final String DISCOVERY_REQUEST = "discoveryRequest";
    public static final String DISCOVERY_RESPONSE = "discoveryResponse";
    public static final String FAMILY_BINDING_DISCOVERY_REQUEST = "familyBindingDiscoveryRequest";
    public static final String FAMILY_BINDING_DISCOVERY_RESPONSE = "familyBindingDiscoveryResponse";
    public static final String FAMILY_BINDING_JOIN_REQUEST = "familyBindingJoinRequest";
    public static final String FAMILY_BINDING_JOIN_RESPONSE = "familyBindingJoinResponse";
    public static final String FAMILY_STATS_DISCOVERY_REQUEST = "familyStatsDiscoveryRequest";
    public static final String FAMILY_STATS_DISCOVERY_RESPONSE = "familyStatsDiscoveryResponse";
    public static final String FAMILY_STATS_UPLOAD_REQUEST = "familyStatsUploadRequest";
    public static final String FAMILY_STATS_UPLOAD_RESPONSE = "familyStatsUploadResponse";

    private SyncMessages() {
    }
}
