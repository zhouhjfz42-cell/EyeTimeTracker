package com.eyetimetracker.android;

public final class ParentPasscode {
    public final String hash;
    public final String salt;
    public final long updatedAtUnixSeconds;

    public ParentPasscode(String hash, String salt, long updatedAtUnixSeconds) {
        this.hash = safe(hash).trim();
        this.salt = safe(salt).trim();
        this.updatedAtUnixSeconds = Math.max(0L, updatedAtUnixSeconds);
    }

    public boolean isConfigured() {
        return !hash.isEmpty() && !salt.isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
