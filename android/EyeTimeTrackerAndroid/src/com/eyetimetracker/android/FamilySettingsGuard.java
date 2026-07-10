package com.eyetimetracker.android;

public final class FamilySettingsGuard {
    public enum Action {
        VIEW_CHILD_PROFILE,
        REBIND_CHILD_DEVICE,
        LEAVE_FAMILY_MODE
    }

    private FamilySettingsGuard() {
    }

    public static boolean requiresPasscode(DeviceRole deviceRole, Action action) {
        if (deviceRole != DeviceRole.CHILD_DEVICE || action == null) {
            return false;
        }
        return action == Action.REBIND_CHILD_DEVICE || action == Action.LEAVE_FAMILY_MODE;
    }
}
