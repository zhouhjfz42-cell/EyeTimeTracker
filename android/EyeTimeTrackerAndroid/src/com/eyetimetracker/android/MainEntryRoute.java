package com.eyetimetracker.android;

public final class MainEntryRoute {
    private MainEntryRoute() {
    }

    public static boolean shouldOpenFamilyHomeOnLaunch(
            ProductMode productMode,
            DeviceRole deviceRole,
            ChildProfile childProfile) {
        return shouldOpenFamilyHomeOnLaunch(productMode, deviceRole, childProfile != null && childProfile.isValid());
    }

    public static boolean shouldOpenFamilyHomeOnLaunch(
            ProductMode productMode,
            DeviceRole deviceRole,
            boolean hasChildProfile) {
        return productMode == ProductMode.FAMILY
                && deviceRole == DeviceRole.CHILD_DEVICE
                && hasChildProfile;
    }
}
