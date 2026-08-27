package com.eyetimetracker.android;

public final class CoreLogicTest {
    public static void main(String[] args) {
        if (args.length == 1 && "interval".equals(args[0])) {
            runIntervalMergerTests();
            System.out.println("Interval merger tests passed.");
            return;
        }
        if (args.length == 1 && "shared-reminder".equals(args[0])) {
            shouldUseSharedEarlierContinuousReminderStart();
            System.out.println("Shared continuous reminder test passed.");
            return;
        }
        if (args.length == 1 && "continuous-guard".equals(args[0])) {
            shouldKeepContinuousReminderProgressAcrossPeerBaselineChanges();
            System.out.println("Continuous reminder guard test passed.");
            return;
        }

        shouldCountWhenScreenOnAndMotionRecent();
        shouldCountWhenScreenOnAndMediaActive();
        shouldCountWhenScreenOnEvenWithoutMotion();
        shouldPauseWhenScreenOff();
        shouldFormatDurations();
        shouldFormatChartTooltips();
        shouldClassifyTodayToneByFixedHealthyThresholds();
        shouldFormatReminderThresholds();
        shouldFormatReminderAlertText();
        shouldUseFreshHeadsUpReminderNotificationProfile();
        shouldFormatConnectionStatus();
        shouldFormatEyeCareSummary();
        shouldNotifyOnceOrAtRepeatMultiples();
        shouldAlignReminderAfterSettingsChange();
        shouldDisplayReminderCountFromVisibleTotal();
        shouldShowReminderOnEveryActiveDevice();
        shouldUseSharedEarlierContinuousReminderStart();
        shouldKeepContinuousReminderProgressAcrossPeerBaselineChanges();
        shouldHandleContinuousReminderExemptions();
        shouldCreateStableUsageSegmentIds();
        shouldCreateMutableUsageSegmentIds();
        shouldMergeOverlappingSegmentsOnlyOnce();
        shouldUseFixedTenSecondBucketsForArbitraryStartSeconds();
        shouldSplitFixedBucketsAcrossHours();
        shouldKeepExactIntervalSeconds();
        shouldSplitExactIntervalsAcrossHours();
        shouldDeDuplicatePartialIntervalOverlap();
        shouldKeepIntervalSessionWhenGapIsThreeMinutes();
        shouldSplitOverlappingIntervalSourcesEvenly();
        shouldSplitPartialOverlappingIntervalSources();
        shouldConserveOddIntervalOverlapAcrossHours();
        shouldComputeDeviceBreakdown();
        shouldDeDuplicateOverlappingSources();
        shouldCreateSyncSegmentsFromLegacySummaries();
        shouldKeepLegacySummariesWhenDateHasModernSegment();
        shouldSkipLegacySummariesAlreadyStoredAsLegacySegments();
        shouldSubtractModernSegmentsFromLegacyBackfill();
        shouldIgnoreStoredOwnLegacyWhenNormalizingEffectiveSegments();
        shouldBreakContinuousSegmentsAfterThreeMinutes();
        shouldUseSegmentSummaryWhenSegmentsExist();
        shouldSignSyncMessages();
        shouldRejectSyncMessagesWithWrongSecret();
        shouldRejectStaleSyncMessages();
        shouldSendOneJsonRequestAndReadOneJsonResponse();
        shouldStoreSyncClientNetworkErrors();
        shouldReadPcSegmentsFromSyncResponse();
        shouldReadSyncResponseTimestamp();
        shouldReadMutableSegmentCapability();
        shouldReadSyncResponseError();
        shouldMergeSyncSegmentsInOnePass();
        shouldUpdateMutableSyncSegments();
        shouldRecognizePcUnpairedResponse();
        shouldClearPairingWhenPcDoesNotAnswer();
        shouldPrepareLocalFirstDisconnect();
        shouldPairWithPcAndSavePairingInfo();
        shouldApplyDiscoveredAddressOnlyForPairedPc();
        shouldRejectDiscoveredAddressForDifferentPc();
        shouldGenerateSixDigitPairingCode();
        shouldDiscoverPcAndStoreHostAndPort();
        shouldReturnEmptyDiscoveryWhenPcDoesNotAnswer();
        shouldTriggerPeriodicSyncEveryMinute();
        shouldDebounceLocalChangeSyncForThirtySeconds();
        shouldNotPostponeLocalChangeSyncForever();
        shouldSyncEveryTenSecondsWhenReminderIsWithinOneMinute();
        System.out.println("All Android core tests passed.");
    }

    private static void runIntervalMergerTests() {
        shouldKeepExactIntervalSeconds();
        shouldSplitExactIntervalsAcrossHours();
        shouldDeDuplicatePartialIntervalOverlap();
        shouldKeepIntervalSessionWhenGapIsThreeMinutes();
        shouldSplitOverlappingIntervalSourcesEvenly();
        shouldSplitPartialOverlappingIntervalSources();
        shouldConserveOddIntervalOverlapAcrossHours();
    }

    private static void shouldUseSharedEarlierContinuousReminderStart() {
        ReminderRuntimeState local = new ReminderRuntimeState("phone", "android", true, 1_000L);
        ReminderRuntimeState peer = new ReminderRuntimeState("pc", "windows", true, 700L);
        assertEquals(700L, ContinuousReminderBaseline.resolve(local, peer, true), "shared online start");
        assertEquals(1_000L, ContinuousReminderBaseline.resolve(local, peer, false), "local offline start");
        peer.isCounting = false;
        assertEquals(1_000L, ContinuousReminderBaseline.resolve(local, peer, true), "peer idle start");
    }

    private static void shouldKeepContinuousReminderProgressAcrossPeerBaselineChanges() {
        assertEquals(
                2,
                ContinuousReminderGuard.matchingLastStep(1_000L, 1_000L, 2),
                "same session keeps last step");
        assertEquals(
                0,
                ContinuousReminderGuard.matchingLastStep(1_000L, 2_000L, 2),
                "new session starts a new reminder sequence");
        assertEquals(
                false,
                ContinuousReminderGuard.shouldClaim(1_000L, 2, 2),
                "already claimed step is not repeated");
        assertEquals(
                true,
                ContinuousReminderGuard.shouldClaim(1_000L, 3, 2),
                "next step is claimed once reached");
        assertEquals(
                false,
                ContinuousReminderGuard.shouldClaim(1_000L, 2, 3),
                "peer claimed step suppresses local catch-up");
    }

    private static void shouldHandleContinuousReminderExemptions() {
        ReminderExemptionPeriod overnight = ReminderExemptionPeriod.create(22 * 60, 7 * 60 + 30);
        assertEquals(true, overnight.containsMinuteOfDay(23 * 60), "overnight exemption night");
        assertEquals(true, overnight.containsMinuteOfDay(7 * 60 + 29), "overnight exemption morning");
        assertEquals(false, overnight.containsMinuteOfDay(12 * 60), "overnight exemption noon");
        long atNight = java.time.ZonedDateTime.now()
                .withHour(23)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toEpochSecond();
        assertEquals(true, ReminderExemptionPolicy.isExempt(atNight, java.util.Collections.singletonList(overnight)), "exemption policy night");
    }

    private static void shouldCountWhenScreenOnAndMotionRecent() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 20_000L, true, 60_000L, 180_000L);
        assertEquals(true, decision.isCounting(), "motion recent counts");
    }

    private static void shouldCountWhenScreenOnAndMediaActive() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 600_000L, true, 600_000L, 180_000L);
        assertEquals(true, decision.isCounting(), "media counts when screen on");
    }

    private static void shouldCountWhenScreenOnEvenWithoutMotion() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 600_000L, false, 600_000L, 180_000L);
        assertEquals(true, decision.isCounting(), "screen-on time counts without motion");
    }

    private static void shouldPauseWhenScreenOff() {
        ActivityDecision decision = ActivityDecision.evaluate(false, 0L, true, 0L, 180_000L);
        assertEquals(false, decision.isCounting(), "screen off pauses");
    }

    private static void shouldFormatDurations() {
        assertEquals("0分钟", DurationFormatter.format(59), "under one minute floors to zero");
        assertEquals("4分钟", DurationFormatter.format(299), "minutes only");
        assertEquals("1小时05分", DurationFormatter.format(3900), "hours and minutes");
        assertEquals("10小时00分", DurationFormatter.formatMainCard(10L * 3600L), "main card keeps ten hours precise");
        assertEquals("约10小时", DurationFormatter.formatMainCard(10L * 3600L + 30L * 60L), "main card compacts over ten hours without rounding at thirty minutes");
        assertEquals("约10小时", DurationFormatter.formatMainCard(10L * 3600L + 17L * 60L), "main card compacts over ten hours");
        assertEquals("约13小时", DurationFormatter.formatMainCard(12L * 3600L + 31L * 60L), "main card rounds compact duration over thirty minutes");
    }

    private static void shouldFormatChartTooltips() {
        assertEquals("28分钟", DurationFormatter.formatTooltipMinutes(28L * 60L), "formats chart minutes");
        assertEquals("0.5小时", DurationFormatter.formatTooltipHours(30L * 60L), "formats half hour tooltip");
        assertEquals("1小时", DurationFormatter.formatTooltipHours(60L * 60L), "formats full hour tooltip");
        assertEquals("2.5小时", DurationFormatter.formatTooltipHours(150L * 60L), "formats two and half hour tooltip");
    }

    private static void shouldClassifyTodayToneByFixedHealthyThresholds() {
        assertEquals(TodayTone.SAFE, TodayTone.fromSeconds(6L * 3600L), "six hours is still green");
        assertEquals(TodayTone.WARN, TodayTone.fromSeconds(6L * 3600L + 1L), "after six hours is yellow");
        assertEquals(TodayTone.WARN, TodayTone.fromSeconds(8L * 3600L), "eight hours is still yellow");
        assertEquals(TodayTone.DANGER, TodayTone.fromSeconds(8L * 3600L + 1L), "after eight hours is red");
    }

    private static void shouldFormatReminderThresholds() {
        assertEquals(330, ReminderThreshold.clampMinutes(330), "keeps valid reminder minutes");
        assertEquals(1, ReminderThreshold.clampMinutes(-5), "clamps reminder lower bound");
        assertEquals("5小时30分", ReminderThreshold.format(330), "formats reminder value");
        assertEquals("即5小时30分", ReminderThreshold.formatEquivalent(330), "formats equivalent hint");
        assertEquals("反复提醒（每达到时间就提醒一次，一天\n内可能出现多次提醒）", ReminderThreshold.formatRepeatLabel(330), "formats repeat label");
    }

    private static void shouldFormatReminderAlertText() {
        assertEquals("5小时30分", ReminderThreshold.format(330), "formats reminder duration");
        assertEquals("1分钟", ReminderThreshold.format(0), "clamps alert duration");
    }

    private static void shouldFormatConnectionStatus() {
        assertEquals("统计中", ConnectionStatusText.format("统计中", false, "电脑"), "formats disconnected status");
        assertEquals("统计中（已连电脑）", ConnectionStatusText.format("统计中", true, "电脑"), "formats connected status");
        assertEquals("统计中（电脑离线）", ConnectionStatusText.format("统计中", true, false, "电脑"), "formats offline status");
    }

    private static void shouldFormatEyeCareSummary() {
        assertEquals("护眼表现：连续用眼偏多", EyeCareSummaryFormatter.careText(65L * 60L, 0L, 45), "formats continuous pressure");
        DeviceUsageBreakdown breakdown = new DeviceUsageBreakdown(39L, 61L);
        assertEquals("手机占比 61%，建议用大屏或拉远", EyeCareSummaryFormatter.sourceText(breakdown), "formats high phone share");
    }

    private static void shouldUseFreshHeadsUpReminderNotificationProfile() {
        assertEquals("eye_time_tracker_reminders_v3", ReminderNotificationProfile.CHANNEL_ID, "uses fresh reminder channel");
        assertEquals(4, ReminderNotificationProfile.CHANNEL_IMPORTANCE, "uses high importance reminder channel");
        assertEquals(2, ReminderNotificationProfile.NOTIFICATION_PRIORITY, "uses max priority reminder notification");
        assertEquals(false, ReminderNotificationProfile.USE_FULL_SCREEN_INTENT, "banner only, no full screen intent");
        assertEquals(true, ReminderNotificationProfile.ENABLE_SOUND, "uses sound for heads-up reminder");
        assertEquals(true, ReminderNotificationProfile.isLegacyChannelId("eye_time_tracker_reminders_v2"), "knows old reminder channel");
        assertEquals(false, ReminderNotificationProfile.isLegacyChannelId(ReminderNotificationProfile.CHANNEL_ID), "current channel is not legacy");
    }

    private static void shouldNotifyOnceOrAtRepeatMultiples() {
        assertEquals(false, ReminderPolicy.shouldNotify(329L * 60L, 330, false, false, 0), "once policy waits for threshold");
        assertEquals(true, ReminderPolicy.shouldNotify(330L * 60L, 330, false, false, 0), "once policy notifies at threshold");
        assertEquals(true, ReminderPolicy.shouldNotify(660L * 60L, 330, false, true, 1), "old once setting is ignored at next threshold");
        assertEquals(false, ReminderPolicy.shouldNotify(329L * 60L, 330, true, false, 0), "repeat policy waits for first threshold");
        assertEquals(true, ReminderPolicy.shouldNotify(330L * 60L, 330, true, false, 0), "repeat policy notifies at first threshold");
        assertEquals(false, ReminderPolicy.shouldNotify(500L * 60L, 330, true, true, 1), "repeat policy does not notify before next multiple");
        assertEquals(true, ReminderPolicy.shouldNotify(660L * 60L, 330, true, true, 1), "repeat policy notifies at second threshold");
        assertEquals(2, ReminderPolicy.reachedStep(660L * 60L, 330), "repeat step is based on today's total");
    }

    private static void shouldAlignReminderAfterSettingsChange() {
        assertEquals(9, ReminderPolicy.alignedStepAfterSettingsChange(197L * 60L, 20), "aligns to already reached new step");
        assertEquals(0, ReminderPolicy.alignedStepAfterSettingsChange(50L * 60L, 60), "resets when below new threshold");
        assertEquals(false, ReminderPolicy.shouldNotify(197L * 60L, 20, true, true, 9), "does not backfill old repeated reminders");
        assertEquals(true, ReminderPolicy.shouldNotify(200L * 60L, 20, true, true, 9), "notifies at next new boundary");
    }

    private static void shouldDisplayReminderCountFromVisibleTotal() {
        assertEquals(0, ReminderPolicy.displayCount(44L * 60L, 45, false), "display reminder count below threshold");
        assertEquals(2, ReminderPolicy.displayCount(90L * 60L, 45, false), "old once setting is ignored for display count");
        assertEquals(2, ReminderPolicy.displayCount(90L * 60L, 45, true), "display reminder count repeat policy");
    }

    private static void shouldShowReminderOnEveryActiveDevice() {
        ReminderRuntimeState pc = new ReminderRuntimeState("pc", "windows", true, 100L);
        ReminderRuntimeState phone = new ReminderRuntimeState("phone", "android", true, 120L);

        assertEquals(true, ReminderDevicePolicy.shouldShowOnLocalDevice(pc, phone, true), "earlier active device also shows");
        assertEquals(true, ReminderDevicePolicy.shouldShowOnLocalDevice(phone, pc, true), "later active device shows");

        phone.isCounting = false;
        assertEquals(true, ReminderDevicePolicy.shouldShowOnLocalDevice(pc, phone, true), "only active device shows");

        pc.isCounting = false;
        assertEquals(false, ReminderDevicePolicy.shouldShowOnLocalDevice(pc, phone, false), "idle device does not show");
    }

    private static UsageSegment segment(String device, String platform, String source, long startSeconds, long durationSeconds) {
        return new UsageSegment(
                UsageSegmentId.create(device, source, startSeconds, startSeconds + durationSeconds),
                device,
                platform,
                source,
                startSeconds,
                startSeconds + durationSeconds,
                "2026-07-02",
                startSeconds,
                startSeconds + durationSeconds);
    }

    private static void shouldCreateStableUsageSegmentIds() {
        long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 8, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();

        String first = UsageSegmentId.create("phone-1", "android-screen", t0, t0 + 10L);
        String second = UsageSegmentId.create("phone-1", "android-screen", t0, t0 + 10L);

        assertEquals(first, second, "usage segment id is stable");
    }

    private static void shouldCreateMutableUsageSegmentIds() {
        long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 8, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();

        String first = UsageSegmentId.createMutable("phone-1", "android-screen", t0);
        String second = UsageSegmentId.createMutable("phone-1", "android-screen", t0);

        assertEquals(first, second, "mutable segment id is stable");
        assertEquals(true, UsageSegmentId.isMutable(first), "mutable segment id is marked");
    }

    private static void shouldMergeOverlappingSegmentsOnlyOnce() {
        long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 30));
        segments.add(segment("phone", "android", "android-screen", t0, 30));

        DailySummary summary = UsageSegmentMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(30L, summary.totalSeconds, "overlapping device segments count once");
    }

    private static void shouldUseFixedTenSecondBucketsForArbitraryStartSeconds() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 8, 9, 18, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 1));
        segments.add(segment("phone", "android", "android-screen", t0 + 1L, 1));

        DailySummary summary = UsageSegmentMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(10L, summary.totalSeconds, "arbitrary second starts share one fixed bucket");
        assertEquals(10L, summary.hourlySeconds[8], "arbitrary second fixed bucket hourly");
    }

    private static void shouldSplitFixedBucketsAcrossHours() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 8, 59, 58, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 5));

        DailySummary summary = UsageSegmentMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(20L, summary.totalSeconds, "cross-hour fixed buckets total");
        assertEquals(10L, summary.hourlySeconds[8], "cross-hour previous hour");
        assertEquals(10L, summary.hourlySeconds[9], "cross-hour next hour");
    }

    private static void shouldConserveOddIntervalOverlapAcrossHours() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 8, 59, 59, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 3L));
        segments.add(segment("phone", "android", "android-screen", t0, 3L));

        DailySummary summary = UsageIntervalMerger.buildDailySummary("2026-07-02", segments);
        DeviceUsageBreakdown breakdown = UsageIntervalMerger.buildDeviceBreakdown("2026-07-02", segments);

        assertEquals(3L, summary.totalSeconds, "odd overlap total seconds");
        assertEquals(1L, breakdown.pcSeconds, "odd overlap pc seconds");
        assertEquals(2L, breakdown.phoneSeconds, "odd overlap phone seconds");
        assertEquals(summary.totalSeconds, breakdown.pcSeconds + breakdown.phoneSeconds, "odd overlap source total");
        assertEquals(0L, breakdown.pcHourlySeconds[8], "odd overlap previous hour pc");
        assertEquals(1L, breakdown.phoneHourlySeconds[8], "odd overlap previous hour phone");
        assertEquals(1L, breakdown.pcHourlySeconds[9], "odd overlap next hour pc");
        assertEquals(1L, breakdown.phoneHourlySeconds[9], "odd overlap next hour phone");
    }

    private static void shouldKeepExactIntervalSeconds() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 8, 9, 18, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 1L));
        segments.add(segment("phone", "android", "android-screen", t0 + 1L, 1L));

        DailySummary summary = UsageIntervalMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(2L, summary.totalSeconds, "exact seconds total");
        assertEquals(2L, summary.hourlySeconds[8], "exact seconds hourly");
    }

    private static void shouldSplitExactIntervalsAcrossHours() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 8, 59, 58, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = java.util.Collections.singletonList(
                segment("pc", "windows", "pc-input", t0, 5L));

        DailySummary summary = UsageIntervalMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(5L, summary.totalSeconds, "exact cross-hour total");
        assertEquals(2L, summary.hourlySeconds[8], "exact cross-hour previous hour");
        assertEquals(3L, summary.hourlySeconds[9], "exact cross-hour next hour");
    }

    private static void shouldDeDuplicatePartialIntervalOverlap() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 60L));
        segments.add(segment("phone", "android", "android-screen", t0 + 30L, 60L));

        DailySummary summary = UsageIntervalMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(90L, summary.totalSeconds, "partial overlap counts once");
    }

    private static void shouldKeepIntervalSessionWhenGapIsThreeMinutes() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 9, 0, 0, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 60L));
        segments.add(segment("pc", "windows", "pc-input", t0 + 240L, 60L));

        DailySummary summary = UsageIntervalMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(1, summary.sessionSeconds.length, "three-minute gap session count");
        assertEquals(120L, summary.sessionSeconds[0], "three-minute gap session duration");
    }

    private static void shouldSplitOverlappingIntervalSourcesEvenly() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 3600L));
        segments.add(segment("phone", "android", "android-screen", t0, 3600L));

        DeviceUsageBreakdown breakdown = UsageIntervalMerger.buildDeviceBreakdown("2026-07-02", segments);

        assertEquals(1800L, breakdown.pcSeconds, "full overlap pc seconds");
        assertEquals(1800L, breakdown.phoneSeconds, "full overlap phone seconds");
        assertEquals(50, breakdown.pcPercent(), "full overlap pc percent");
        assertEquals(50, breakdown.phonePercent(), "full overlap phone percent");
    }

    private static void shouldSplitPartialOverlappingIntervalSources() {
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 60L));
        segments.add(segment("phone", "android", "android-screen", t0 + 30L, 60L));

        DeviceUsageBreakdown breakdown = UsageIntervalMerger.buildDeviceBreakdown("2026-07-02", segments);

        assertEquals(45L, breakdown.pcSeconds, "partial overlap pc seconds");
        assertEquals(45L, breakdown.phoneSeconds, "partial overlap phone seconds");
    }

    private static void shouldComputeDeviceBreakdown() {
        long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 60));
        segments.add(segment("phone", "android", "android-screen", t0 + 120L, 30));
        int localHour = java.time.Instant.ofEpochSecond(t0)
                .atZone(java.time.ZoneId.systemDefault())
                .getHour();

        DeviceUsageBreakdown breakdown = DeviceUsageBreakdown.build("2026-07-02", segments);

        assertEquals(60L, breakdown.pcSeconds, "device breakdown pc seconds");
        assertEquals(30L, breakdown.phoneSeconds, "device breakdown phone seconds");
        assertEquals(67, breakdown.pcPercent(), "device breakdown pc percent");
        assertEquals(33, breakdown.phonePercent(), "device breakdown phone percent");
        assertEquals(60L, breakdown.pcHourlySeconds[localHour], "device breakdown pc hourly");
        assertEquals(30L, breakdown.phoneHourlySeconds[localHour], "device breakdown phone hourly");
    }

    private static void shouldDeDuplicateOverlappingSources() {
        long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 3600));
        segments.add(segment("phone", "android", "android-screen", t0, 3600));
        int localHour = java.time.Instant.ofEpochSecond(t0)
                .atZone(java.time.ZoneId.systemDefault())
                .getHour();

        DeviceUsageBreakdown breakdown = DeviceUsageBreakdown.build("2026-07-02", segments);

        assertEquals(0L, breakdown.pcSeconds, "overlapping source pc seconds");
        assertEquals(3600L, breakdown.phoneSeconds, "overlapping source phone seconds");
        assertEquals(0, breakdown.pcPercent(), "overlapping source pc percent");
        assertEquals(100, breakdown.phonePercent(), "overlapping source phone percent");
        assertEquals(3600L, breakdown.pcHourlySeconds[localHour] + breakdown.phoneHourlySeconds[localHour], "overlapping source stacked total");
        assertEquals(0L, breakdown.pcHourlySeconds[localHour], "overlapping source pc hourly");
        assertEquals(3600L, breakdown.phoneHourlySeconds[localHour], "overlapping source phone hourly");
    }

    private static void shouldCreateSyncSegmentsFromLegacySummaries() {
        long[] hourly = new long[24];
        hourly[9] = 3600L;
        hourly[10] = 1800L;
        java.util.List<DailySummary> summaries = java.util.Collections.singletonList(
                new DailySummary("2026-07-01", 5400L, hourly, new long[0], 0L, false, 0));

        java.util.List<UsageSegment> segments = LegacyUsageSegments.fromDailySummaries(
                summaries,
                java.util.Collections.emptyList(),
                "phone-test",
                "android");

        assertEquals(2, segments.size(), "legacy sync segment count");
        assertEquals(5400L, segments.get(0).durationSeconds() + segments.get(1).durationSeconds(), "legacy sync segment total");
        assertEquals("legacy-summary", segments.get(0).source, "legacy sync source");
    }

    private static void shouldKeepLegacySummariesWhenDateHasModernSegment() {
        long[] hourly = new long[24];
        hourly[9] = 3600L;
        java.util.List<DailySummary> summaries = java.util.Collections.singletonList(
                new DailySummary("2026-07-02", 3600L, hourly, new long[0], 0L, false, 0));
        java.util.List<UsageSegment> existing = java.util.Collections.singletonList(
                segment("phone-test", "android", "android-screen", 1_783_065_600L, 60L));

        java.util.List<UsageSegment> segments = LegacyUsageSegments.fromDailySummaries(
                summaries,
                existing,
                "phone-test",
                "android");

        assertEquals(1, segments.size(), "modern segment does not hide legacy summary");
        assertEquals(3600L, segments.get(0).durationSeconds(), "legacy summary remains available");
    }

    private static void shouldSkipLegacySummariesAlreadyStoredAsLegacySegments() {
        long[] hourly = new long[24];
        hourly[9] = 3600L;
        java.util.List<DailySummary> summaries = java.util.Collections.singletonList(
                new DailySummary("2026-07-02", 3600L, hourly, new long[0], 0L, false, 0));
        java.util.List<UsageSegment> existing = LegacyUsageSegments.fromDailySummaries(
                summaries,
                java.util.Collections.emptyList(),
                "phone-test",
                "android");

        java.util.List<UsageSegment> segments = LegacyUsageSegments.fromDailySummaries(
                summaries,
                existing,
                "phone-test",
                "android");

        assertEquals(0, segments.size(), "already stored legacy segment is not duplicated");
    }

    private static void shouldSubtractModernSegmentsFromLegacyBackfill() {
        long[] hourly = new long[24];
        hourly[9] = 3600L;
        java.util.List<DailySummary> summaries = java.util.Collections.singletonList(
                new DailySummary("2026-07-02", 3600L, hourly, new long[0], 0L, false, 0));
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 9, 10, 0, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> existing = java.util.Collections.singletonList(
                segment("phone-test", "android", "android-screen", t0, 600L));

        java.util.List<UsageSegment> segments = LegacyUsageSegments.fromDailySummaries(
                summaries,
                existing,
                "phone-test",
                "android");

        assertEquals(1, segments.size(), "legacy backfill subtracts modern segment count");
        assertEquals(3000L, segments.get(0).durationSeconds(), "legacy backfill subtracts modern segment duration");
    }

    private static void shouldIgnoreStoredOwnLegacyWhenNormalizingEffectiveSegments() {
        long[] hourly = new long[24];
        hourly[9] = 600L;
        java.util.List<DailySummary> summaries = java.util.Collections.singletonList(
                new DailySummary("2026-07-02", 600L, hourly, new long[0], 0L, false, 0));
        long t0 = java.time.ZonedDateTime.of(2026, 7, 2, 9, 0, 0, 0, java.time.ZoneId.systemDefault())
                .toEpochSecond();
        java.util.List<UsageSegment> stored = java.util.Collections.singletonList(
                segment("phone-test", "android", LegacyUsageSegments.SOURCE, t0, 3600L));

        java.util.List<UsageSegment> effective = LegacyUsageSegments.normalizeEffectiveSegments(
                stored,
                summaries,
                "phone-test",
                "android");

        assertEquals(1, effective.size(), "own stored legacy is ignored");
        assertEquals(600L, effective.get(0).durationSeconds(), "local summary backfill is used");
    }

    private static void shouldBreakContinuousSegmentsAfterThreeMinutes() {
        long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 9, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 60));
        segments.add(segment("pc", "windows", "pc-input", t0 + 240L, 60));

        DailySummary summary = UsageSegmentMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(2, summary.sessionSeconds.length, "continuous session count");
        assertEquals(60L, summary.sessionSeconds[0], "first continuous session");
        assertEquals(60L, summary.sessionSeconds[1], "second continuous session");
    }

    private static void shouldUseSegmentSummaryWhenSegmentsExist() {
        long[] legacyHourly = new long[24];
        legacyHourly[9] = 3_600L;
        DailySummary legacy = new DailySummary("2026-07-02", 3_600L, legacyHourly, new long[] { 3_600L }, 300L, true, 2);
        DailySummary segmented = new DailySummary("2026-07-02", 120L, false, 0);

        DailySummary summary = DailySummaryReconciler.useSegmentSummaryForSyncedDay(legacy, segmented);

        assertEquals(120L, summary.totalSeconds, "segment summary replaces legacy total");
        assertEquals(0L, summary.hourlySeconds[9], "legacy hourly data does not override synced day");
        assertEquals(true, summary.reminderShown, "legacy reminder state is preserved for synced day");
        assertEquals(2, summary.lastReminderStep, "legacy reminder step is preserved for synced day");
    }

    private static void shouldSignSyncMessages() {
        String first = SyncMessageSigner.sign(SyncMessages.SYNC_REQUEST, 1_788_888_000L, "{\"ok\":true}", "secret");
        String second = SyncMessageSigner.sign(SyncMessages.SYNC_REQUEST, 1_788_888_000L, "{\"ok\":true}", "secret");

        assertEquals(first, second, "sync message signature is stable");
        assertEquals(
                true,
                SyncMessageSigner.verify(SyncMessages.SYNC_REQUEST, 1_788_888_000L, "{\"ok\":true}", "secret", first, 1_788_888_100L),
                "sync message signature verifies");
    }

    private static void shouldRejectSyncMessagesWithWrongSecret() {
        String signature = SyncMessageSigner.sign(SyncMessages.SYNC_REQUEST, 1_788_888_000L, "{}", "secret");

        assertEquals(
                false,
                SyncMessageSigner.verify(SyncMessages.SYNC_REQUEST, 1_788_888_000L, "{}", "wrong", signature, 1_788_888_100L),
                "wrong secret fails");
    }

    private static void shouldRejectStaleSyncMessages() {
        String signature = SyncMessageSigner.sign(SyncMessages.SYNC_REQUEST, 1_788_888_000L, "{}", "secret");

        assertEquals(
                false,
                SyncMessageSigner.verify(SyncMessages.SYNC_REQUEST, 1_788_888_000L, "{}", "secret", signature, 1_788_888_301L),
                "stale sync message fails");
    }

    private static void shouldSendOneJsonRequestAndReadOneJsonResponse() {
        try {
            java.net.ServerSocket server = new java.net.ServerSocket(0);
            int port = server.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try (
                        java.net.Socket socket = server.accept();
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                        java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String request = reader.readLine();
                    assertEquals("{\"type\":\"syncRequest\"}", request, "server receives one JSON request");
                    writer.write("{\"type\":\"syncResponse\",\"accepted\":true}");
                    writer.newLine();
                    writer.flush();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            serverThread.start();

            SyncSettings settings = new SyncSettings();
            settings.isPaired = true;
            settings.peerHost = "127.0.0.1";
            settings.peerPort = port;
            settings.lastSyncUnixSeconds = 1234L;
            AndroidSyncClient client = new AndroidSyncClient(1000, 1000);

            String response = client.sendJson(settings, "{\"type\":\"syncRequest\"}");

            serverThread.join(2000L);
            server.close();
            assertEquals("{\"type\":\"syncResponse\",\"accepted\":true}", response, "client reads one JSON response");
            assertEquals("", settings.lastError, "successful sync clears error");
            assertEquals(1234L, settings.lastSyncUnixSeconds, "client leaves sync cursor to response handling");
        } catch (Exception ex) {
            throw new AssertionError("socket sync test failed", ex);
        }
    }

    private static void shouldStoreSyncClientNetworkErrors() {
        try {
            java.net.ServerSocket server = new java.net.ServerSocket(0);
            int port = server.getLocalPort();
            server.close();

            SyncSettings settings = new SyncSettings();
            settings.isPaired = true;
            settings.peerHost = "127.0.0.1";
            settings.peerPort = port;
            AndroidSyncClient client = new AndroidSyncClient(100, 100);

            String response = client.sendJson(settings, "{\"type\":\"syncRequest\"}");

            assertEquals("", response, "network failure returns empty response");
            assertEquals(false, settings.lastError.isEmpty(), "network failure stores last error");
        } catch (Exception ex) {
            throw new AssertionError("network error test failed", ex);
        }
    }

    private static void shouldReadPcSegmentsFromSyncResponse() {
        String responseJson = "{"
                + "\"Type\":\"syncResponse\","
                + "\"Accepted\":true,"
                + "\"Segments\":[{"
                + "\"SegmentId\":\"pc-segment-1\","
                + "\"DeviceId\":\"pc-1\","
                + "\"Platform\":\"windows\","
                + "\"Source\":\"pc-input\","
                + "\"StartUnixSeconds\":1783000000,"
                + "\"EndUnixSeconds\":1783000030,"
                + "\"LocalDate\":\"2026-07-02\","
                + "\"CreatedAtUnixSeconds\":1783000000,"
                + "\"UpdatedAtUnixSeconds\":1783000030"
                + "}]}";

        java.util.List<UsageSegment> segments = AndroidSyncResponseReader.readSegments(responseJson);

        assertEquals(1, segments.size(), "reads pc segment count");
        assertEquals("pc-segment-1", segments.get(0).segmentId, "reads pc segment id");
        assertEquals("windows", segments.get(0).platform, "reads pc segment platform");
        assertEquals(30L, segments.get(0).durationSeconds(), "reads pc segment duration");
    }

    private static void shouldReadSyncResponseTimestamp() {
        String responseJson = "{\"Type\":\"syncResponse\",\"Accepted\":true,\"TimestampUnixSeconds\":1783000042}";

        assertEquals(1783000042L, AndroidSyncResponseReader.readTimestampUnixSeconds(responseJson), "reads pc sync timestamp");
    }

    private static void shouldReadMutableSegmentCapability() {
        assertEquals(true,
                AndroidSyncResponseReader.supportsMutableSegments("{\"Type\":\"syncResponse\",\"Accepted\":true,\"SupportsMutableSegments\":true}"),
                "reads mutable segment capability");
        assertEquals(false,
                AndroidSyncResponseReader.supportsMutableSegments("{\"Type\":\"syncResponse\",\"Accepted\":true}"),
                "defaults capability to false for old pc");
    }

    private static void shouldReadSyncResponseError() {
        String responseJson = "{\"Type\":\"syncResponse\",\"Accepted\":false,\"Error\":\"PC is not paired.\"}";

        assertEquals("PC is not paired.", AndroidSyncResponseReader.readError(responseJson), "reads sync response error");
    }

    private static void shouldMergeSyncSegmentsInOnePass() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            state.put("segments", new org.json.JSONArray());
            long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
            UsageSegment existing = segment("pc", "windows", "pc-input", t0, 30L);
            UsageSegment added = segment("pc", "windows", "pc-input", t0 + 60L, 30L);

            EyeTimeStore.mergeSegments(state, java.util.Collections.singletonList(existing));
            int changed = EyeTimeStore.mergeSegments(state, java.util.Arrays.asList(existing, added));

            assertEquals(1, changed, "merge only reports newly added segment");
            assertEquals(2, state.getJSONArray("segments").length(), "merge skips duplicate segment");
        } catch (Exception ex) {
            throw new AssertionError("sync segment merge test failed", ex);
        }
    }

    private static void shouldRecognizePcUnpairedResponse() {
        String responseJson = "{\"Type\":\"syncResponse\",\"Accepted\":false,\"Error\":\"PC is not paired.\"}";

        assertEquals(true, AndroidSyncResponseReader.isPeerUnpaired(responseJson), "recognizes pc unpaired response");
    }

    private static void shouldClearPairingWhenPcDoesNotAnswer() {
        assertEquals(false, SyncConnectionState.shouldClearPairingAfterSync("", "Connection refused"), "keeps pairing after pc stops answering");
        assertEquals(true, SyncConnectionState.shouldClearPairingAfterSync("{\"Type\":\"syncResponse\",\"Accepted\":false,\"Error\":\"PC is not paired.\"}", ""), "clears pairing when pc rejects paired sync");
        assertEquals(false, SyncConnectionState.shouldClearPairingAfterSync("{\"Type\":\"syncResponse\",\"Accepted\":true}", ""), "keeps pairing after successful sync");
    }

    private static void shouldPrepareLocalFirstDisconnect() {
        SyncSettings paired = new SyncSettings();
        paired.isPaired = true;
        paired.peerHost = "127.0.0.1";
        paired.peerPort = 17420;
        paired.peerDeviceId = "pc-1";
        paired.sharedSecret = "secret";

        SyncDisconnectPlan plan = SyncDisconnectPlan.create(paired);

        assertEquals(false, plan.localSettings.isPaired, "disconnect local settings unpaired");
        assertEquals(true, plan.peerNotificationSettings.isPaired, "disconnect keeps peer settings for notification");
        assertEquals("127.0.0.1", plan.peerNotificationSettings.peerHost, "disconnect keeps peer host");
    }

    private static void shouldPairWithPcAndSavePairingInfo() {
        try {
            java.net.ServerSocket server = new java.net.ServerSocket(0);
            int port = server.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try (
                        java.net.Socket socket = server.accept();
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                        java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String request = reader.readLine();
                    assertEquals(true, request.contains("\"Type\":\"pairRequest\""), "pair request type");
                    assertEquals(true, request.contains("\"PairingCode\":\"123456\""), "pair request code");
                    writer.write("{\"Type\":\"pairAccept\",\"Accepted\":true,\"DeviceId\":\"pc-1\",\"Platform\":\"windows\",\"SharedSecret\":\"shared-secret\"}");
                    writer.newLine();
                    writer.flush();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            serverThread.start();

            SyncSettings settings = new SyncSettings();
            settings.peerHost = "127.0.0.1";
            settings.peerPort = port;
            settings.lastSyncUnixSeconds = 1783000000L;
            AndroidPairingClient client = new AndroidPairingClient(new AndroidSyncClient(1000, 1000));

            boolean paired = client.pair(settings, "phone-1", "123456");

            serverThread.join(2000L);
            server.close();
            assertEquals(true, paired, "pairing succeeds");
            assertEquals(true, settings.isPaired, "pairing marks settings paired");
            assertEquals("pc-1", settings.peerDeviceId, "pairing stores pc device");
            assertEquals("windows", settings.peerPlatform, "pairing stores pc platform");
            assertEquals("shared-secret", settings.sharedSecret, "pairing stores shared secret");
            assertEquals(0L, settings.lastSyncUnixSeconds, "pairing resets sync cursor for full backfill");
            assertEquals("", settings.lastError, "pairing clears error");
        } catch (Exception ex) {
            throw new AssertionError("pairing test failed", ex);
        }
    }

    private static void shouldApplyDiscoveredAddressOnlyForPairedPc() {
        SyncSettings settings = new SyncSettings();
        settings.isPaired = true;
        settings.peerDeviceId = "pc-1";
        settings.peerHost = "192.168.1.5";
        settings.peerPort = 17420;

        AndroidPcDiscoveryClient.DiscoveryResult discovery = new AndroidPcDiscoveryClient.DiscoveryResult(
                true,
                "192.168.8.23",
                17422,
                "pc-1",
                "windows");

        boolean applied = AndroidSyncEndpointResolver.applyDiscoveredPeer(settings, discovery);

        assertEquals(true, applied, "applies discovered paired pc");
        assertEquals("192.168.8.23", settings.peerHost, "updates discovered host");
        assertEquals(17422, settings.peerPort, "updates discovered port");
    }

    private static void shouldRejectDiscoveredAddressForDifferentPc() {
        SyncSettings settings = new SyncSettings();
        settings.isPaired = true;
        settings.peerDeviceId = "pc-1";
        settings.peerHost = "192.168.1.5";
        settings.peerPort = 17420;

        AndroidPcDiscoveryClient.DiscoveryResult discovery = new AndroidPcDiscoveryClient.DiscoveryResult(
                true,
                "192.168.8.99",
                17422,
                "pc-2",
                "windows");

        boolean applied = AndroidSyncEndpointResolver.applyDiscoveredPeer(settings, discovery);

        assertEquals(false, applied, "rejects different discovered pc");
        assertEquals("192.168.1.5", settings.peerHost, "keeps original host");
        assertEquals(17420, settings.peerPort, "keeps original port");
    }

    private static void shouldGenerateSixDigitPairingCode() {
        String code = PairingCodeGenerator.generate(new java.util.Random(1L));

        assertEquals(6, code.length(), "pairing code length");
        assertEquals(true, code.matches("\\d{6}"), "pairing code digits");
    }

    private static void shouldDiscoverPcAndStoreHostAndPort() {
        try {
            java.net.DatagramSocket server = new java.net.DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1"));
            int port = server.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try {
                    byte[] requestBytes = new byte[1024];
                    java.net.DatagramPacket request = new java.net.DatagramPacket(requestBytes, requestBytes.length);
                    server.receive(request);
                    String requestJson = new String(request.getData(), request.getOffset(), request.getLength(), java.nio.charset.StandardCharsets.UTF_8);
                    assertEquals(true, requestJson.contains("\"Type\":\"discoveryRequest\""), "discovery request type");

                    byte[] responseBytes = "{\"Type\":\"discoveryResponse\",\"DeviceId\":\"pc-1\",\"Platform\":\"windows\",\"Port\":17420}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    java.net.DatagramPacket response = new java.net.DatagramPacket(responseBytes, responseBytes.length, request.getAddress(), request.getPort());
                    server.send(response);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            serverThread.start();

            AndroidPcDiscoveryClient client = new AndroidPcDiscoveryClient(1000);
            AndroidPcDiscoveryClient.DiscoveryResult result = client.discover("127.0.0.1", port);

            serverThread.join(2000L);
            server.close();
            assertEquals(true, result.found, "pc discovery found");
            assertEquals("127.0.0.1", result.host, "pc discovery host");
            assertEquals(17420, result.port, "pc discovery port");
            assertEquals("pc-1", result.deviceId, "pc discovery device");
        } catch (Exception ex) {
            throw new AssertionError("pc discovery test failed", ex);
        }
    }

    private static void shouldReturnEmptyDiscoveryWhenPcDoesNotAnswer() {
        AndroidPcDiscoveryClient client = new AndroidPcDiscoveryClient(50);

        AndroidPcDiscoveryClient.DiscoveryResult result = client.discover("127.0.0.1", 9);

        assertEquals(false, result.found, "pc discovery empty result");
        assertEquals("", result.host, "pc discovery empty host");
        assertEquals(0, result.port, "pc discovery empty port");
    }

    private static void shouldTriggerPeriodicSyncEveryMinute() {
        AndroidSyncTriggerPolicy policy = new AndroidSyncTriggerPolicy();

        assertEquals(true, policy.shouldSyncForServiceTick(1_000L), "first service tick syncs");
        policy.markSyncAttempt(1_000L);
        assertEquals(false, policy.shouldSyncForServiceTick(60_999L), "service tick waits for one minute");
        assertEquals(true, policy.shouldSyncForServiceTick(61_000L), "service tick syncs after one minute");
    }

    private static void shouldDebounceLocalChangeSyncForThirtySeconds() {
        AndroidSyncTriggerPolicy policy = new AndroidSyncTriggerPolicy();

        policy.markLocalChange(10_000L);

        assertEquals(false, policy.shouldSyncForLocalChange(39_999L), "local change waits for debounce");
        assertEquals(true, policy.shouldSyncForLocalChange(40_000L), "local change syncs after debounce");
        policy.markSyncAttempt(40_000L);
        assertEquals(false, policy.shouldSyncForLocalChange(70_000L), "local change clears after sync");
    }

    private static void shouldNotPostponeLocalChangeSyncForever() {
        AndroidSyncTriggerPolicy policy = new AndroidSyncTriggerPolicy();

        policy.markLocalChange(10_000L);
        policy.markLocalChange(20_000L);
        policy.markLocalChange(30_000L);

        assertEquals(true, policy.shouldSyncForLocalChange(40_000L), "later local changes keep first debounce window");
    }

    private static void shouldSyncEveryTenSecondsWhenReminderIsWithinOneMinute() {
        AndroidSyncTriggerPolicy policy = new AndroidSyncTriggerPolicy();

        assertEquals(false, policy.shouldSyncForUpcomingReminder(1_000L, 3_530L, 60, true, true, 0), "does not sync before one minute window");
        assertEquals(true, policy.shouldSyncForUpcomingReminder(2_000L, 3_540L, 60, true, true, 0), "syncs inside one minute window");
        policy.markSyncAttempt(2_000L);
        assertEquals(false, policy.shouldSyncForUpcomingReminder(11_999L, 3_550L, 60, true, true, 0), "waits ten seconds in reminder window");
        assertEquals(true, policy.shouldSyncForUpcomingReminder(12_000L, 3_560L, 60, true, true, 0), "syncs every ten seconds in reminder window");
    }

    private static void shouldUpdateMutableSyncSegments() {
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            state.put("segments", new org.json.JSONArray());
            long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
            String segmentId = UsageSegmentId.createMutable("phone", "android-screen", t0);
            UsageSegment initial = new UsageSegment(segmentId, "phone", "android", "android-screen", t0, t0 + 10L, "2026-07-02", t0, t0 + 10L);
            UsageSegment updated = new UsageSegment(segmentId, "phone", "android", "android-screen", t0, t0 + 20L, "2026-07-02", t0, t0 + 20L);

            EyeTimeStore.mergeSegments(state, java.util.Collections.singletonList(initial));
            int changed = EyeTimeStore.mergeSegments(state, java.util.Collections.singletonList(updated));
            UsageSegment saved = new UsageSegment(
                    state.getJSONArray("segments").getJSONObject(0).getString("segmentId"),
                    state.getJSONArray("segments").getJSONObject(0).getString("deviceId"),
                    state.getJSONArray("segments").getJSONObject(0).getString("platform"),
                    state.getJSONArray("segments").getJSONObject(0).getString("source"),
                    state.getJSONArray("segments").getJSONObject(0).getLong("startUnixSeconds"),
                    state.getJSONArray("segments").getJSONObject(0).getLong("endUnixSeconds"),
                    state.getJSONArray("segments").getJSONObject(0).getString("localDate"),
                    state.getJSONArray("segments").getJSONObject(0).getLong("createdAtUnixSeconds"),
                    state.getJSONArray("segments").getJSONObject(0).getLong("updatedAtUnixSeconds"));

            assertEquals(1, changed, "mutable segment update reports change");
            assertEquals(1, state.getJSONArray("segments").length(), "mutable segment update keeps one item");
            assertEquals(20L, saved.durationSeconds(), "mutable segment update keeps latest end");
        } catch (Exception ex) {
            throw new AssertionError("mutable sync segment update test failed", ex);
        }
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", actual " + actual);
        }
    }
}
