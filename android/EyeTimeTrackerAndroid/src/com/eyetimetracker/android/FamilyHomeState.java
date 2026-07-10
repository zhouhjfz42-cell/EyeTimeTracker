package com.eyetimetracker.android;

public final class FamilyHomeState {
    public final boolean isFamilyMode;
    public final DeviceRole deviceRole;
    public final String childNickname;
    public final String childAgeBand;
    public final boolean hasParentPasscode;
    public final boolean hasBoundChildDevice;
    public final boolean showSettingsEntry;

    private FamilyHomeState(
            boolean isFamilyMode,
            DeviceRole deviceRole,
            String childNickname,
            String childAgeBand,
            boolean hasParentPasscode,
            boolean hasBoundChildDevice,
            boolean showSettingsEntry) {
        this.isFamilyMode = isFamilyMode;
        this.deviceRole = deviceRole == null ? DeviceRole.PERSONAL_DEVICE : deviceRole;
        this.childNickname = safe(childNickname).trim();
        this.childAgeBand = ChildProfile.normalizeAgeBand(childAgeBand);
        this.hasParentPasscode = hasParentPasscode;
        this.hasBoundChildDevice = hasBoundChildDevice;
        this.showSettingsEntry = showSettingsEntry;
    }

    public static FamilyHomeState create(
            ProductMode productMode,
            DeviceRole deviceRole,
            ChildProfile childProfile,
            boolean hasParentPasscode) {
        return create(productMode, deviceRole, childProfile, hasParentPasscode, false);
    }

    public static FamilyHomeState create(
            ProductMode productMode,
            DeviceRole deviceRole,
            ChildProfile childProfile,
            boolean hasParentPasscode,
            boolean hasBoundChildDevice) {
        DeviceRole safeRole = deviceRole == null ? DeviceRole.PERSONAL_DEVICE : deviceRole;
        boolean isFamilyMode = productMode == ProductMode.FAMILY;
        String nickname = childProfile == null ? "" : childProfile.nickname;
        String ageBand = childProfile == null ? ChildProfile.AGE_BAND_UNKNOWN : childProfile.ageBand;
        boolean showSettingsEntry = isFamilyMode
                && (safeRole == DeviceRole.PARENT_DEVICE || safeRole == DeviceRole.CHILD_DEVICE);
        return new FamilyHomeState(isFamilyMode, safeRole, nickname, ageBand, hasParentPasscode, hasBoundChildDevice, showSettingsEntry);
    }

    public boolean hasChildProfile() {
        return !childNickname.isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
