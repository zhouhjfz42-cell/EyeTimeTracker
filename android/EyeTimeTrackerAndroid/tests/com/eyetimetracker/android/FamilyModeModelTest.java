package com.eyetimetracker.android;

public final class FamilyModeModelTest {
    public static void main(String[] args) {
        shouldDefaultToPersonalFamilyModeState();
        shouldSaveFamilyModeState();
        shouldRoundTripChildProfiles();
        shouldStoreParentPasscodeWithoutPlaintext();
        shouldVerifyParentPasscode();
        shouldUseRandomSaltForParentPasscode();
        shouldBuildFamilySetupDraftForSaving();
        shouldRejectFamilySetupDraftWithoutNickname();
        shouldBuildFamilyHomeStateForParentDevice();
        shouldHideFamilySettingsEntryOnChildDevice();
        System.out.println("All Android family mode model tests passed.");
    }

    private static void shouldDefaultToPersonalFamilyModeState() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();

            assertEquals(ProductMode.PERSONAL, EyeTimeStore.readProductMode(state), "default product mode is personal");
            assertEquals(DeviceRole.PERSONAL_DEVICE, EyeTimeStore.readDeviceRole(state), "default device role is personal device");
        } catch (Exception ex) {
            throw new AssertionError("family mode defaults test failed", ex);
        }
    }

    private static void shouldSaveFamilyModeState() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();

            EyeTimeStore.writeProductMode(state, ProductMode.FAMILY);
            EyeTimeStore.writeDeviceRole(state, DeviceRole.PARENT_DEVICE);

            assertEquals(ProductMode.FAMILY, EyeTimeStore.readProductMode(state), "saves family product mode");
            assertEquals(DeviceRole.PARENT_DEVICE, EyeTimeStore.readDeviceRole(state), "saves parent device role");
            assertEquals("family", state.getString("productMode"), "stores product mode value");
            assertEquals("parent_device", state.getString("deviceRole"), "stores device role value");
        } catch (Exception ex) {
            throw new AssertionError("family mode save test failed", ex);
        }
    }

    private static void shouldRoundTripChildProfiles() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            ChildProfile older = new ChildProfile("local-child-1", "child-one", "7-9", 1783000000L, 1783000001L);
            ChildProfile younger = new ChildProfile("local-child-2", "child-two", "", 1783000002L, 1783000003L);

            EyeTimeStore.writeChildProfiles(state, java.util.Arrays.asList(older, younger), "local-child-2");
            java.util.List<ChildProfile> profiles = EyeTimeStore.readChildProfiles(state);

            assertEquals(2, profiles.size(), "reads child profile count");
            assertEquals("local-child-1", profiles.get(0).childId, "reads first child id");
            assertEquals("child-one", profiles.get(0).nickname, "reads first child nickname");
            assertEquals("7-9", profiles.get(0).ageBand, "reads first child age band");
            assertEquals("local-child-2", EyeTimeStore.readActiveChildId(state), "reads active child id");
            assertEquals("unknown", profiles.get(1).ageBand, "blank age band becomes unknown");
        } catch (Exception ex) {
            throw new AssertionError("child profile round trip test failed", ex);
        }
    }

    private static void shouldStoreParentPasscodeWithoutPlaintext() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            ParentPasscode passcode = EyeTimeStore.createParentPasscode(
                    "135790",
                    new byte[] { 1, 3, 5, 7, 9, 11, 13, 15 },
                    1783000100L);

            EyeTimeStore.writeParentPasscode(state, passcode);
            ParentPasscode saved = EyeTimeStore.readParentPasscode(state);

            assertEquals(true, EyeTimeStore.hasParentPasscode(state), "parent passcode exists after save");
            assertEquals(1783000100L, saved.updatedAtUnixSeconds, "stores passcode updated time");
            assertEquals(false, state.getString("parentPasscodeHash").isEmpty(), "stores passcode hash");
            assertEquals(false, state.getString("parentPasscodeSalt").isEmpty(), "stores passcode salt");
            assertEquals(false, "135790".equals(state.getString("parentPasscodeHash")), "does not store plaintext as hash");
            assertEquals(false, "135790".equals(state.getString("parentPasscodeSalt")), "does not store plaintext as salt");
        } catch (Exception ex) {
            throw new AssertionError("parent passcode storage test failed", ex);
        }
    }

    private static void shouldVerifyParentPasscode() {
        try {
            ParentPasscode passcode = EyeTimeStore.createParentPasscode(
                    "246810",
                    new byte[] { 2, 4, 6, 8, 10, 12, 14, 16 },
                    1783000200L);

            assertEquals(true, EyeTimeStore.verifyParentPasscode(passcode, "246810"), "correct parent passcode verifies");
            assertEquals(false, EyeTimeStore.verifyParentPasscode(passcode, "246811"), "wrong parent passcode fails");
            assertEquals(false, EyeTimeStore.verifyParentPasscode(null, "246810"), "missing parent passcode fails");
        } catch (Exception ex) {
            throw new AssertionError("parent passcode verify test failed", ex);
        }
    }

    private static void shouldUseRandomSaltForParentPasscode() {
        try {
            ParentPasscode first = EyeTimeStore.createParentPasscode("112233", 1783000300L);
            ParentPasscode second = EyeTimeStore.createParentPasscode("112233", 1783000301L);

            assertEquals(false, first.salt.equals(second.salt), "same passcode uses different salts");
            assertEquals(false, first.hash.equals(second.hash), "same passcode creates different hashes");
        } catch (Exception ex) {
            throw new AssertionError("parent passcode random salt test failed", ex);
        }
    }

    private static void shouldBuildFamilySetupDraftForSaving() {
        try {
            FamilySetupDraft draft = FamilySetupDraft.create(
                    DeviceRole.PARENT_DEVICE,
                    " 小明 ",
                    "",
                    "2468",
                    1783000400L);

            assertEquals(ProductMode.FAMILY, draft.productMode, "setup draft switches to family mode");
            assertEquals(DeviceRole.PARENT_DEVICE, draft.deviceRole, "setup draft stores selected role");
            assertEquals("小明", draft.childProfile.nickname, "setup draft trims nickname");
            assertEquals(ChildProfile.AGE_BAND_UNKNOWN, draft.childProfile.ageBand, "blank age band becomes unknown");
            assertEquals("2468", draft.passcode, "setup draft stores passcode for hashing");
        } catch (Exception ex) {
            throw new AssertionError("family setup draft save test failed", ex);
        }
    }

    private static void shouldRejectFamilySetupDraftWithoutNickname() {
        try {
            FamilySetupDraft.create(DeviceRole.PARENT_DEVICE, " ", "7-9", "2468", 1783000500L);
            throw new AssertionError("blank nickname should be rejected");
        } catch (IllegalArgumentException expected) {
        } catch (Exception ex) {
            throw new AssertionError("family setup draft validation test failed", ex);
        }
    }

    private static void shouldBuildFamilyHomeStateForParentDevice() {
        try {
            ChildProfile child = new ChildProfile("local-child-1", "child-one", "7-9", 1783000600L, 1783000600L);
            FamilyHomeState state = FamilyHomeState.create(ProductMode.FAMILY, DeviceRole.PARENT_DEVICE, child, true);

            assertEquals(true, state.isFamilyMode, "family home detects family mode");
            assertEquals("child-one", state.childNickname, "family home reads child nickname");
            assertEquals("7-9", state.childAgeBand, "family home reads child age band");
            assertEquals(DeviceRole.PARENT_DEVICE, state.deviceRole, "family home reads parent role");
            assertEquals(true, state.showSettingsEntry, "parent device can open family settings");
            assertEquals(true, state.hasParentPasscode, "family home reads passcode state");
        } catch (Exception ex) {
            throw new AssertionError("family home parent state test failed", ex);
        }
    }

    private static void shouldHideFamilySettingsEntryOnChildDevice() {
        try {
            ChildProfile child = new ChildProfile("local-child-1", "child-one", "", 1783000700L, 1783000700L);
            FamilyHomeState state = FamilyHomeState.create(ProductMode.FAMILY, DeviceRole.CHILD_DEVICE, child, false);

            assertEquals(true, state.isFamilyMode, "child device still uses family mode");
            assertEquals(ChildProfile.AGE_BAND_UNKNOWN, state.childAgeBand, "missing age band is unknown");
            assertEquals(false, state.showSettingsEntry, "child device does not show settings entry");
            assertEquals(false, state.hasParentPasscode, "family home reads missing passcode state");
        } catch (Exception ex) {
            throw new AssertionError("family home child state test failed", ex);
        }
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", actual " + actual);
        }
    }
}
