package com.eyetimetracker.android;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

public final class FamilyBindingInvite {
    private static final SecureRandom RANDOM = new SecureRandom();

    public final String familyId;
    public final String bindingCode;
    public final ChildProfile childProfile;
    public final long createdAtUnixSeconds;

    public FamilyBindingInvite(
            String familyId,
            String bindingCode,
            ChildProfile childProfile,
            long createdAtUnixSeconds) {
        this.familyId = safe(familyId).trim();
        this.bindingCode = normalizeBindingCode(bindingCode);
        this.childProfile = childProfile;
        this.createdAtUnixSeconds = Math.max(0L, createdAtUnixSeconds);
    }

    public static FamilyBindingInvite create(String familyId, ChildProfile childProfile, long nowUnixSeconds) {
        String safeFamilyId = safe(familyId).trim();
        if (safeFamilyId.isEmpty()) {
            safeFamilyId = "local-family-" + UUID.randomUUID().toString();
        }
        String code = String.format(Locale.US, "%06d", RANDOM.nextInt(1_000_000));
        return new FamilyBindingInvite(safeFamilyId, code, childProfile, nowUnixSeconds);
    }

    public boolean isValid() {
        return !familyId.isEmpty()
                && bindingCode.matches("\\d{6}")
                && childProfile != null
                && childProfile.isValid();
    }

    public static String normalizeBindingCode(String value) {
        return safe(value).replaceAll("\\s+", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
