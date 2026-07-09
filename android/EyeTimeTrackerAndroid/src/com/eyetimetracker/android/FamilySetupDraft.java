package com.eyetimetracker.android;

public final class FamilySetupDraft {
    public final ProductMode productMode;
    public final DeviceRole deviceRole;
    public final ChildProfile childProfile;
    public final String passcode;

    private FamilySetupDraft(ProductMode productMode, DeviceRole deviceRole, ChildProfile childProfile, String passcode) {
        this.productMode = productMode;
        this.deviceRole = deviceRole;
        this.childProfile = childProfile;
        this.passcode = passcode;
    }

    public static FamilySetupDraft create(
            DeviceRole deviceRole,
            String childNickname,
            String ageBand,
            String passcode,
            long nowUnixSeconds) {
        String nickname = safe(childNickname).trim();
        if (nickname.isEmpty()) {
            throw new IllegalArgumentException("Child nickname is required.");
        }
        String safePasscode = safe(passcode).trim();
        if (!safePasscode.matches("\\d{4}")) {
            throw new IllegalArgumentException("Protection passcode must be 4 digits.");
        }
        long timestamp = Math.max(0L, nowUnixSeconds);
        ChildProfile childProfile = new ChildProfile(
                "local-child-" + timestamp,
                nickname,
                ageBand,
                timestamp,
                timestamp);
        return new FamilySetupDraft(
                ProductMode.FAMILY,
                deviceRole == null ? DeviceRole.PARENT_DEVICE : deviceRole,
                childProfile,
                safePasscode);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
