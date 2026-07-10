package com.eyetimetracker.android;

public final class FamilyModeModelTest {
    public static void main(String[] args) {
        shouldDefaultToPersonalFamilyModeState();
        shouldSaveFamilyModeState();
        shouldRoundTripChildProfiles();
        shouldStoreParentPasscodeWithoutPlaintext();
        shouldVerifyParentPasscode();
        shouldUseRandomSaltForParentPasscode();
        shouldCreateAndConsumeFamilyBindingInvite();
        shouldBuildAndParseFamilyBindingJoinResponse();
        shouldSaveRemoteFamilyChildBinding();
        shouldSaveParentChildDeviceJoinStatus();
        shouldMergeFamilyChildUsageSegmentsSeparately();
        shouldSummarizeFamilyChildUsageSegments();
        shouldBuildAndParseFamilyStatsUploadRequest();
        shouldBuildAndParseFamilyStatsUploadResponseWithReminderSettings();
        shouldBuildFamilyChildStatsSeries();
        shouldBuildFamilyChildHomeStatsInOnePass();
        shouldLeaveFamilyModeLocally();
        shouldCreateParentSetupDraftWithChildProfile();
        shouldCreateChildBindingDraftWithoutChildProfileInput();
        shouldBuildFamilySetupDraftForSaving();
        shouldRejectFamilySetupDraftWithoutNickname();
        shouldBuildFamilyHomeStateForParentDevice();
        shouldReadFamilyHomeStateFromLoadedStoreState();
        shouldShowLimitedFamilySettingsEntryOnChildDevice();
        shouldAutoOpenFamilyHomeOnlyForChildDevice();
        shouldStartTrackerServiceForEveryMainEntryRoute();
        shouldRequirePasscodeForChildProtectedSettingsActions();
        shouldHideJoinAsChildEntryAfterParentRoleIsConfirmed();
        shouldBlockSecondChildBindingInOneToOneFamilyMode();
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

    private static void shouldCreateAndConsumeFamilyBindingInvite() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            ChildProfile child = new ChildProfile("local-child-1", "mumu", "3-6", 1783000800L, 1783000800L);
            FamilyBindingInvite invite = FamilyBindingInvite.create("local-family-1", child, 1783000800L);

            EyeTimeStore.writeFamilyBindingInvite(state, invite);
            FamilyBindingInvite saved = EyeTimeStore.readFamilyBindingInvite(state);

            assertEquals(invite.bindingCode, saved.bindingCode, "binding code round trips");
            assertEquals("local-child-1", saved.childProfile.childId, "invite keeps child id");
            assertEquals("mumu", saved.childProfile.nickname, "invite keeps child nickname");
            assertEquals(true, EyeTimeStore.consumeFamilyBindingCode(state, invite.bindingCode), "correct code consumes invite");
            assertEquals(DeviceRole.CHILD_DEVICE, EyeTimeStore.readDeviceRole(state), "child device role is saved after consume");
            assertEquals(ProductMode.FAMILY, EyeTimeStore.readProductMode(state), "family mode is saved after consume");
            assertEquals("local-child-1", EyeTimeStore.readActiveChildId(state), "active child id is saved after consume");
            assertEquals(true, EyeTimeStore.readFamilyBindingInvite(state) == null, "invite is cleared after consume");
        } catch (Exception ex) {
            throw new AssertionError("family binding invite consume test failed", ex);
        }
    }

    private static void shouldBuildAndParseFamilyBindingJoinResponse() {
        try {
            ChildProfile child = new ChildProfile("local-child-1", "mumu", "7-9", 1783001000L, 1783001000L);
            FamilyBindingInvite invite = new FamilyBindingInvite("local-family-1", "428916", child, 1783001000L);
            ParentPasscode passcode = EyeTimeStore.createParentPasscode(
                    "2468",
                    new byte[] { 2, 4, 6, 8, 10, 12, 14, 16 },
                    1783001001L);

            String responseJson = FamilyBindingProtocol.buildJoinResponse(true, "", invite, passcode);
            FamilyBindingProtocol.JoinResponse response = FamilyBindingProtocol.parseJoinResponse(responseJson);

            assertEquals(true, response.accepted, "accepted response parses");
            assertEquals("local-family-1", response.familyId, "family id parses");
            assertEquals("local-child-1", response.childProfile.childId, "child id parses");
            assertEquals("mumu", response.childProfile.nickname, "child nickname parses");
            assertEquals(true, EyeTimeStore.verifyParentPasscode(response.parentPasscode, "2468"), "parent passcode parses");
        } catch (Exception ex) {
            throw new AssertionError("family binding protocol join response test failed", ex);
        }
    }

    private static void shouldSaveRemoteFamilyChildBinding() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            ChildProfile child = new ChildProfile("local-child-9", "nini", "10-12", 1783001100L, 1783001100L);
            ParentPasscode passcode = EyeTimeStore.createParentPasscode(
                    "1357",
                    new byte[] { 1, 3, 5, 7, 9, 11, 13, 15 },
                    1783001101L);

            EyeTimeStore.writeRemoteFamilyChildBinding(state, "local-family-9", child, passcode);

            assertEquals(ProductMode.FAMILY, EyeTimeStore.readProductMode(state), "remote binding saves family mode");
            assertEquals(DeviceRole.CHILD_DEVICE, EyeTimeStore.readDeviceRole(state), "remote binding saves child role");
            assertEquals("local-child-9", EyeTimeStore.readActiveChildId(state), "remote binding saves active child");
            assertEquals("nini", EyeTimeStore.readChildProfiles(state).get(0).nickname, "remote binding saves child profile");
            assertEquals(true, EyeTimeStore.readFamilyBindingInvite(state) == null, "remote binding does not leave pending invite");
            assertEquals(true, EyeTimeStore.verifyParentPasscode(EyeTimeStore.readParentPasscode(state), "1357"), "remote binding saves passcode verifier");
        } catch (Exception ex) {
            throw new AssertionError("remote family child binding test failed", ex);
        }
    }

    private static void shouldSaveParentChildDeviceJoinStatus() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();

            EyeTimeStore.writeFamilyChildDeviceBinding(state, "child-device-1", 1783001200L);

            assertEquals(true, EyeTimeStore.hasFamilyChildDeviceBinding(state), "parent records joined child device");
            assertEquals("child-device-1", state.getString("familyChildDeviceId"), "stores joined child device id");
            assertEquals(1783001200L, state.optLong("familyChildDeviceJoinedAtUnixSeconds", 0L), "stores joined time");
        } catch (Exception ex) {
            throw new AssertionError("parent child device join status test failed", ex);
        }
    }

    private static void shouldMergeFamilyChildUsageSegmentsSeparately() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            UsageSegment childSegment = segment("child-segment-1", "child-device-1", "2026-07-10", 3600L, 60L);
            UsageSegment localParentSegment = segment("parent-segment-1", "parent-device", "2026-07-10", 3600L, 120L);

            EyeTimeStore.mergeSegments(state, java.util.Collections.singletonList(localParentSegment));
            int changed = EyeTimeStore.mergeFamilyChildSegments(state, java.util.Arrays.asList(childSegment, childSegment));

            assertEquals(1, changed, "deduplicates family child segments");
            assertEquals(1, EyeTimeStore.readFamilyChildSegments(state, java.time.LocalDate.parse("2026-07-10"), java.time.LocalDate.parse("2026-07-10")).size(), "reads family child segment count");
            assertEquals(1, EyeTimeStore.readSegments(state, java.time.LocalDate.parse("2026-07-10"), java.time.LocalDate.parse("2026-07-10")).size(), "keeps parent local segments separate");
        } catch (Exception ex) {
            throw new AssertionError("family child usage segment storage test failed", ex);
        }
    }

    private static void shouldSummarizeFamilyChildUsageSegments() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            java.time.LocalDate today = java.time.LocalDate.parse("2026-07-10");
            EyeTimeStore.mergeFamilyChildSegments(state, java.util.Arrays.asList(
                    segment("child-today-1", "child-device-1", "2026-07-10", 3600L, 30L),
                    segment("child-yesterday-1", "child-device-1", "2026-07-09", 3600L, 60L)));

            assertEquals(30L, EyeTimeStore.sumFamilyChildDay(state, today), "summarizes family child today");
            assertEquals(60L, EyeTimeStore.sumFamilyChildDay(state, today.minusDays(1)), "summarizes family child yesterday");
            assertEquals(90L, EyeTimeStore.sumFamilyChildRange(state, today.minusDays(1), today), "summarizes family child range");
        } catch (Exception ex) {
            throw new AssertionError("family child usage summary test failed", ex);
        }
    }

    private static void shouldBuildAndParseFamilyStatsUploadRequest() {
        try {
            UsageSegment segment = segment("child-segment-1", "child-device-1", "2026-07-10", 3600L, 30L);

            String json = FamilyStatsProtocol.buildUploadRequest(
                    "local-family-1",
                    "local-child-1",
                    "child-device-1",
                    java.util.Collections.singletonList(segment));
            FamilyStatsProtocol.UploadRequest request = FamilyStatsProtocol.parseUploadRequest(json);

            assertEquals(true, request.valid, "family stats upload request parses");
            assertEquals("local-family-1", request.familyId, "family stats upload keeps family id");
            assertEquals("local-child-1", request.childId, "family stats upload keeps child id");
            assertEquals("child-device-1", request.childDeviceId, "family stats upload keeps child device id");
            assertEquals(1, request.segments.size(), "family stats upload keeps segments");
            assertEquals("child-segment-1", request.segments.get(0).segmentId, "family stats upload keeps segment id");
        } catch (Exception ex) {
            throw new AssertionError("family stats protocol upload request test failed", ex);
        }
    }

    private static void shouldBuildAndParseFamilyStatsUploadResponseWithReminderSettings() {
        try {
            String json = FamilyStatsProtocol.buildUploadResponse(true, "", 2, 45, true);
            FamilyStatsProtocol.UploadResponse response = FamilyStatsProtocol.parseUploadResponse(json);

            assertEquals(true, response.accepted, "family stats upload response is accepted");
            assertEquals(2, response.changedSegments, "family stats upload response keeps changed count");
            assertEquals(45, response.reminderMinutes, "family stats upload response keeps reminder minutes");
            assertEquals(true, response.repeatReminder, "family stats upload response keeps repeat reminder");
            assertEquals(true, response.hasReminderSettings, "family stats upload response marks reminder settings present");

            ParentPasscode passcode = EyeTimeStore.createParentPasscode(
                    "2468",
                    new byte[] { 2, 4, 6, 8, 10, 12, 14, 16 },
                    1783002000L);
            FamilyStatsProtocol.UploadResponse responseWithPasscode = FamilyStatsProtocol.parseUploadResponse(
                    FamilyStatsProtocol.buildUploadResponse(true, "", 0, 30, false, passcode));
            assertEquals(true, EyeTimeStore.verifyParentPasscode(responseWithPasscode.parentPasscode, "2468"), "family stats upload response keeps parent passcode");
        } catch (Exception ex) {
            throw new AssertionError("family stats protocol upload response reminder test failed", ex);
        }
    }

    private static void shouldBuildFamilyChildStatsSeries() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            java.time.LocalDate today = java.time.LocalDate.parse("2026-07-10");
            EyeTimeStore.mergeFamilyChildSegments(state, java.util.Arrays.asList(
                    segment("child-today-1", "child-device-1", "2026-07-10", 3600L, 30L),
                    segment("child-yesterday-1", "child-device-1", "2026-07-09", 3600L, 60L)));

            DailySummary todaySummary = EyeTimeStore.buildFamilyChildDay(state, today);
            java.util.List<DailySummary> days = EyeTimeStore.buildFamilyChildDays(state, today.minusDays(1), today);
            DeviceUsageBreakdown breakdown = EyeTimeStore.buildFamilyChildDeviceBreakdown(state, today);

            assertEquals(30L, todaySummary.totalSeconds, "family child day uses child segments");
            assertEquals(2, days.size(), "family child days keeps requested range");
            assertEquals(60L, days.get(0).totalSeconds, "family child days keeps first day");
            assertEquals(30L, days.get(1).totalSeconds, "family child days keeps second day");
            assertEquals(30L, breakdown.phoneSeconds, "family child device breakdown uses child phone segment");
        } catch (Exception ex) {
            throw new AssertionError("family child stats series test failed", ex);
        }
    }

    private static void shouldBuildFamilyChildHomeStatsInOnePass() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            java.time.LocalDate today = java.time.LocalDate.parse("2026-07-10");
            EyeTimeStore.mergeFamilyChildSegments(state, java.util.Arrays.asList(
                    segment("child-home-today", "child-device-1", "2026-07-10", 3600L, 600L),
                    segment("child-home-yesterday", "child-device-1", "2026-07-09", 3600L, 1800L),
                    segment("child-home-week", "child-device-1", "2026-07-08", 3600L, 600L)));

            HomeStatsSnapshot snapshot = EyeTimeStore.buildFamilyChildHomeStats(state, today);

            assertEquals(600L, snapshot.todaySeconds, "family child home stats today");
            assertEquals(1800L, snapshot.yesterdaySeconds, "family child home stats yesterday");
            assertEquals(3000L, snapshot.weekSeconds, "family child home stats week");
            assertEquals(3000L, snapshot.monthSeconds, "family child home stats month");
        } catch (Exception ex) {
            throw new AssertionError("family child home stats test failed", ex);
        }
    }

    private static void shouldLeaveFamilyModeLocally() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            ChildProfile child = new ChildProfile("local-child-9", "nini", "10-12", 1783001100L, 1783001100L);
            EyeTimeStore.writeRemoteFamilyChildBinding(state, "local-family-9", child);
            EyeTimeStore.writeFamilyChildDeviceBinding(state, "child-device-1", 1783001200L);

            EyeTimeStore.writeLeaveFamilyMode(state);

            assertEquals(ProductMode.PERSONAL, EyeTimeStore.readProductMode(state), "leave family mode restores personal mode");
            assertEquals(DeviceRole.PERSONAL_DEVICE, EyeTimeStore.readDeviceRole(state), "leave family mode restores personal role");
            assertEquals("", EyeTimeStore.readActiveChildId(state), "leave family mode clears active child");
            assertEquals(0, EyeTimeStore.readChildProfiles(state).size(), "leave family mode clears child profile");
            assertEquals(false, EyeTimeStore.hasFamilyChildDeviceBinding(state), "leave family mode clears joined child device");
            assertEquals(true, EyeTimeStore.readFamilyBindingInvite(state) == null, "leave family mode clears pending invite");
        } catch (Exception ex) {
            throw new AssertionError("leave family mode test failed", ex);
        }
    }

    private static void shouldCreateParentSetupDraftWithChildProfile() {
        try {
            FamilySetupDraft draft = FamilySetupDraft.createParent("mumu", "3-6", "2468", 1783000900L);

            assertEquals(ProductMode.FAMILY, draft.productMode, "parent draft switches to family mode");
            assertEquals(DeviceRole.PARENT_DEVICE, draft.deviceRole, "parent draft uses parent role");
            assertEquals("mumu", draft.childProfile.nickname, "parent draft owns child nickname");
            assertEquals("3-6", draft.childProfile.ageBand, "parent draft owns child age band");
            assertEquals("2468", draft.passcode, "parent draft stores passcode");
        } catch (Exception ex) {
            throw new AssertionError("parent setup draft test failed", ex);
        }
    }

    private static void shouldCreateChildBindingDraftWithoutChildProfileInput() {
        try {
            FamilyBindingDraft draft = FamilyBindingDraft.create("428 916");

            assertEquals("428916", draft.bindingCode, "child binding keeps normalized code");
        } catch (Exception ex) {
            throw new AssertionError("child binding draft test failed", ex);
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
            FamilyHomeState state = FamilyHomeState.create(ProductMode.FAMILY, DeviceRole.PARENT_DEVICE, child, true, true);

            assertEquals(true, state.isFamilyMode, "family home detects family mode");
            assertEquals("child-one", state.childNickname, "family home reads child nickname");
            assertEquals("7-9", state.childAgeBand, "family home reads child age band");
            assertEquals(DeviceRole.PARENT_DEVICE, state.deviceRole, "family home reads parent role");
            assertEquals(true, state.showSettingsEntry, "parent device can open family settings");
            assertEquals(true, state.hasParentPasscode, "family home reads passcode state");
            assertEquals(true, state.hasBoundChildDevice, "family home reads joined child device state");
        } catch (Exception ex) {
            throw new AssertionError("family home parent state test failed", ex);
        }
    }

    private static void shouldReadFamilyHomeStateFromLoadedStoreState() {
        try {
            org.json.JSONObject raw = new org.json.JSONObject();
            ChildProfile child = new ChildProfile("local-child-1", "child-one", "7-9", 1783000600L, 1783000600L);
            EyeTimeStore.writeProductMode(raw, ProductMode.FAMILY);
            EyeTimeStore.writeDeviceRole(raw, DeviceRole.PARENT_DEVICE);
            EyeTimeStore.writeChildProfiles(raw, java.util.Collections.singletonList(child), child.childId);
            EyeTimeStore.writeFamilyChildDeviceBinding(raw, "child-device-1", 1783000700L);
            EyeTimeStore.writeParentPasscode(raw, EyeTimeStore.createParentPasscode(
                    "1357",
                    new byte[] { 1, 3, 5, 7, 9, 11, 13, 15 },
                    1783000800L));

            FamilyHomeState state = EyeTimeStore.readFamilyHomeState(raw);

            assertEquals(true, state.isFamilyMode, "loaded family home state uses family mode");
            assertEquals(DeviceRole.PARENT_DEVICE, state.deviceRole, "loaded family home state keeps role");
            assertEquals("child-one", state.childNickname, "loaded family home state keeps child");
            assertEquals(true, state.hasParentPasscode, "loaded family home state keeps passcode");
            assertEquals(true, state.hasBoundChildDevice, "loaded family home state keeps bound child");
        } catch (Exception ex) {
            throw new AssertionError("family home loaded state test failed", ex);
        }
    }

    private static void shouldShowLimitedFamilySettingsEntryOnChildDevice() {
        try {
            ChildProfile child = new ChildProfile("local-child-1", "child-one", "", 1783000700L, 1783000700L);
            FamilyHomeState state = FamilyHomeState.create(ProductMode.FAMILY, DeviceRole.CHILD_DEVICE, child, false);

            assertEquals(true, state.isFamilyMode, "child device still uses family mode");
            assertEquals(ChildProfile.AGE_BAND_UNKNOWN, state.childAgeBand, "missing age band is unknown");
            assertEquals(true, state.showSettingsEntry, "child device can open limited family settings");
            assertEquals(false, state.hasParentPasscode, "family home reads missing passcode state");
        } catch (Exception ex) {
            throw new AssertionError("family home child state test failed", ex);
        }
    }

    private static void shouldAutoOpenFamilyHomeOnlyForChildDevice() {
        try {
            ChildProfile child = new ChildProfile("local-child-1", "child-one", "7-9", 1783000600L, 1783000600L);

            assertEquals(true, MainEntryRoute.shouldOpenFamilyHomeOnLaunch(ProductMode.FAMILY, DeviceRole.CHILD_DEVICE, child), "child device opens family home on launch");
            assertEquals(false, MainEntryRoute.shouldOpenFamilyHomeOnLaunch(ProductMode.FAMILY, DeviceRole.PARENT_DEVICE, child), "parent device stays on personal home on launch");
            assertEquals(false, MainEntryRoute.shouldOpenFamilyHomeOnLaunch(ProductMode.PERSONAL, DeviceRole.CHILD_DEVICE, child), "personal mode stays on personal home on launch");
            assertEquals(false, MainEntryRoute.shouldOpenFamilyHomeOnLaunch(ProductMode.FAMILY, DeviceRole.CHILD_DEVICE, null), "missing child profile stays on personal home on launch");
        } catch (Exception ex) {
            throw new AssertionError("main entry route test failed", ex);
        }
    }

    private static void shouldStartTrackerServiceForEveryMainEntryRoute() {
        try {
            assertEquals(true, MainEntryRoute.shouldStartTrackerServiceOnLaunch(ProductMode.PERSONAL, DeviceRole.PERSONAL_DEVICE, false), "personal mode starts tracker service");
            assertEquals(true, MainEntryRoute.shouldStartTrackerServiceOnLaunch(ProductMode.FAMILY, DeviceRole.PARENT_DEVICE, true), "parent family mode starts tracker service");
            assertEquals(true, MainEntryRoute.shouldStartTrackerServiceOnLaunch(ProductMode.FAMILY, DeviceRole.CHILD_DEVICE, true), "child family auto route starts tracker service");
        } catch (Exception ex) {
            throw new AssertionError("main entry service startup test failed", ex);
        }
    }

    private static void shouldRequirePasscodeForChildProtectedSettingsActions() {
        try {
            assertEquals(true, FamilySettingsGuard.requiresPasscode(DeviceRole.CHILD_DEVICE, FamilySettingsGuard.Action.REBIND_CHILD_DEVICE), "child rebind requires passcode");
            assertEquals(true, FamilySettingsGuard.requiresPasscode(DeviceRole.CHILD_DEVICE, FamilySettingsGuard.Action.LEAVE_FAMILY_MODE), "child leave requires passcode");
            assertEquals(false, FamilySettingsGuard.requiresPasscode(DeviceRole.CHILD_DEVICE, FamilySettingsGuard.Action.VIEW_CHILD_PROFILE), "child read-only profile does not require passcode");
            assertEquals(false, FamilySettingsGuard.requiresPasscode(DeviceRole.PARENT_DEVICE, FamilySettingsGuard.Action.REBIND_CHILD_DEVICE), "parent rebind is not protected by child guard");
        } catch (Exception ex) {
            throw new AssertionError("family settings guard test failed", ex);
        }
    }

    private static void shouldHideJoinAsChildEntryAfterParentRoleIsConfirmed() {
        try {
            assertEquals(false, FamilySettingsPolicy.showJoinAsChildEntry(DeviceRole.PARENT_DEVICE), "parent settings hides child join entry");
            assertEquals(false, FamilySettingsPolicy.showJoinAsChildEntry(DeviceRole.CHILD_DEVICE), "child settings hides child join entry");
            assertEquals(true, FamilySettingsPolicy.showJoinAsChildEntry(DeviceRole.PERSONAL_DEVICE), "personal setup may join as child");
        } catch (Exception ex) {
            throw new AssertionError("family settings policy test failed", ex);
        }
    }

    private static void shouldBlockSecondChildBindingInOneToOneFamilyMode() {
        try {
            assertEquals(true, FamilySettingsPolicy.canOpenAddChildDeviceBinding(DeviceRole.PARENT_DEVICE, false), "parent may bind first child device");
            assertEquals(false, FamilySettingsPolicy.canOpenAddChildDeviceBinding(DeviceRole.PARENT_DEVICE, true), "parent cannot bind a second child device yet");
            assertEquals(false, FamilySettingsPolicy.canOpenAddChildDeviceBinding(DeviceRole.CHILD_DEVICE, false), "child device cannot open parent binding page");
            assertEquals(false, FamilySettingsPolicy.canOpenAddChildDeviceBinding(DeviceRole.PERSONAL_DEVICE, false), "personal device must complete family setup first");
        } catch (Exception ex) {
            throw new AssertionError("family one-to-one binding policy test failed", ex);
        }
    }

    private static UsageSegment segment(String id, String deviceId, String date, long startOffsetSeconds, long durationSeconds) {
        long startUnixSeconds = java.time.LocalDate.parse(date)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toEpochSecond()
                + startOffsetSeconds;
        long endUnixSeconds = startUnixSeconds + durationSeconds;
        return new UsageSegment(
                id,
                deviceId,
                "android",
                "android-screen",
                startUnixSeconds,
                endUnixSeconds,
                date,
                startUnixSeconds,
                endUnixSeconds);
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", actual " + actual);
        }
    }
}
