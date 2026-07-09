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
        if (deviceRole != null && deviceRole != DeviceRole.PARENT_DEVICE) {
            throw new IllegalArgumentException("Family setup draft only creates parent-owned child profiles.");
        }
        return createParent(childNickname, ageBand, passcode, nowUnixSeconds);
    }

    public static FamilySetupDraft createParent(
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
                DeviceRole.PARENT_DEVICE,
                childProfile,
                safePasscode);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
