package com.eyetimetracker.android;

public final class CoreLogicTest {
    public static void main(String[] args) {
        shouldCountWhenScreenOnAndMotionRecent();
        shouldCountWhenScreenOnAndMediaActive();
        shouldPauseWhenScreenOff();
        shouldPauseWhenScreenOnButIdleAndNoMedia();
        shouldFormatDurations();
        shouldFormatChartTooltips();
        shouldClassifyTodayToneByFixedHealthyThresholds();
        shouldFormatReminderThresholds();
        shouldFormatReminderAlertText();
        shouldFormatConnectionStatus();
        shouldNotifyOnceOrAtRepeatMultiples();
        shouldDisplayReminderCountFromVisibleTotal();
        shouldCreateStableUsageSegmentIds();
        shouldMergeOverlappingSegmentsOnlyOnce();
        shouldComputeDeviceBreakdown();
        shouldCapHourlySourceStackAtOneHour();
        shouldBreakContinuousSegmentsAfterThreeMinutes();
        shouldUseSegmentSummaryWhenSegmentsExist();
        shouldSignSyncMessages();
        shouldRejectSyncMessagesWithWrongSecret();
        shouldRejectStaleSyncMessages();
        shouldSendOneJsonRequestAndReadOneJsonResponse();
        shouldStoreSyncClientNetworkErrors();
        shouldReadPcSegmentsFromSyncResponse();
        shouldReadSyncResponseError();
        shouldRecognizePcUnpairedResponse();
        shouldClearPairingWhenPcDoesNotAnswer();
        shouldPairWithPcAndSavePairingInfo();
        shouldGenerateSixDigitPairingCode();
        shouldDiscoverPcAndStoreHostAndPort();
        shouldReturnEmptyDiscoveryWhenPcDoesNotAnswer();
        shouldTriggerPeriodicSyncEveryMinute();
        shouldDebounceLocalChangeSyncForThirtySeconds();
        shouldNotPostponeLocalChangeSyncForever();
        System.out.println("All Android core tests passed.");
    }

    private static void shouldCountWhenScreenOnAndMotionRecent() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 20_000L, true, 60_000L, 180_000L);
        assertEquals(true, decision.isCounting(), "motion recent counts");
    }

    private static void shouldCountWhenScreenOnAndMediaActive() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 600_000L, true, 600_000L, 180_000L);
        assertEquals(true, decision.isCounting(), "media counts when screen on");
    }

    private static void shouldPauseWhenScreenOff() {
        ActivityDecision decision = ActivityDecision.evaluate(false, 0L, true, 0L, 180_000L);
        assertEquals(false, decision.isCounting(), "screen off pauses");
    }

    private static void shouldPauseWhenScreenOnButIdleAndNoMedia() {
        ActivityDecision decision = ActivityDecision.evaluate(true, 600_000L, false, 600_000L, 180_000L);
        assertEquals(false, decision.isCounting(), "idle without media pauses");
    }

    private static void shouldFormatDurations() {
        assertEquals("0分钟", DurationFormatter.format(59), "under one minute floors to zero");
        assertEquals("4分钟", DurationFormatter.format(299), "minutes only");
        assertEquals("1小时05分", DurationFormatter.format(3900), "hours and minutes");
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
        assertEquals("反复提醒（当天内每330分钟提醒一次）", ReminderThreshold.formatRepeatLabel(330), "formats repeat label");
    }

    private static void shouldFormatReminderAlertText() {
        assertEquals("用眼提醒", ReminderAlert.title(), "formats reminder title");
        assertEquals("今天用眼时间已达到 5小时30分，建议休息一下眼睛。", ReminderAlert.message(330), "formats reminder message");
        assertEquals("今天用眼时间已经第2次达到330分钟了，建议休息一下眼睛。", ReminderAlert.message(330, true, 2), "formats repeat reminder message");
        assertEquals("今天用眼时间已达到 5小时30分，建议休息一下眼睛。", ReminderAlert.message(330, false, 2), "formats once reminder message");
    }

    private static void shouldFormatConnectionStatus() {
        assertEquals("统计中", ConnectionStatusText.format("统计中", false, "电脑"), "formats disconnected status");
        assertEquals("统计中（已连接电脑）", ConnectionStatusText.format("统计中", true, "电脑"), "formats connected status");
        assertEquals("统计中（电脑暂时离线）", ConnectionStatusText.format("统计中", true, false, "电脑"), "formats temporarily offline status");
    }

    private static void shouldNotifyOnceOrAtRepeatMultiples() {
        assertEquals(false, ReminderPolicy.shouldNotify(329L * 60L, 330, false, false, 0), "once policy waits for threshold");
        assertEquals(true, ReminderPolicy.shouldNotify(330L * 60L, 330, false, false, 0), "once policy notifies at threshold");
        assertEquals(false, ReminderPolicy.shouldNotify(660L * 60L, 330, false, true, 1), "once policy only notifies once per day");
        assertEquals(false, ReminderPolicy.shouldNotify(329L * 60L, 330, true, false, 0), "repeat policy waits for first threshold");
        assertEquals(true, ReminderPolicy.shouldNotify(330L * 60L, 330, true, false, 0), "repeat policy notifies at first threshold");
        assertEquals(false, ReminderPolicy.shouldNotify(500L * 60L, 330, true, true, 1), "repeat policy does not notify before next multiple");
        assertEquals(true, ReminderPolicy.shouldNotify(660L * 60L, 330, true, true, 1), "repeat policy notifies at second threshold");
        assertEquals(2, ReminderPolicy.reachedStep(660L * 60L, 330), "repeat step is based on today's total");
    }

    private static void shouldDisplayReminderCountFromVisibleTotal() {
        assertEquals(0, ReminderPolicy.displayCount(44L * 60L, 45, false), "display reminder count below threshold");
        assertEquals(1, ReminderPolicy.displayCount(90L * 60L, 45, false), "display reminder count once policy");
        assertEquals(2, ReminderPolicy.displayCount(90L * 60L, 45, true), "display reminder count repeat policy");
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

    private static void shouldMergeOverlappingSegmentsOnlyOnce() {
        long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 30));
        segments.add(segment("phone", "android", "android-screen", t0, 30));

        DailySummary summary = UsageSegmentMerger.buildDailySummary("2026-07-02", segments);

        assertEquals(30L, summary.totalSeconds, "overlapping device segments count once");
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

    private static void shouldCapHourlySourceStackAtOneHour() {
        long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
        java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
        segments.add(segment("pc", "windows", "pc-input", t0, 3600));
        segments.add(segment("phone", "android", "android-screen", t0, 3600));
        int localHour = java.time.Instant.ofEpochSecond(t0)
                .atZone(java.time.ZoneId.systemDefault())
                .getHour();

        DeviceUsageBreakdown breakdown = DeviceUsageBreakdown.build("2026-07-02", segments);

        assertEquals(3600L, breakdown.pcHourlySeconds[localHour] + breakdown.phoneHourlySeconds[localHour], "hourly source stack caps at one hour");
        assertEquals(1800L, breakdown.pcHourlySeconds[localHour], "hourly source pc scales down");
        assertEquals(1800L, breakdown.phoneHourlySeconds[localHour], "hourly source phone scales down");
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
        assertEquals(false, summary.reminderShown, "legacy reminder state does not override synced day");
        assertEquals(0, summary.lastReminderStep, "legacy reminder step does not override synced day");
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
            AndroidSyncClient client = new AndroidSyncClient(1000, 1000);

            String response = client.sendJson(settings, "{\"type\":\"syncRequest\"}");

            serverThread.join(2000L);
            server.close();
            assertEquals("{\"type\":\"syncResponse\",\"accepted\":true}", response, "client reads one JSON response");
            assertEquals("", settings.lastError, "successful sync clears error");
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

    private static void shouldReadSyncResponseError() {
        String responseJson = "{\"Type\":\"syncResponse\",\"Accepted\":false,\"Error\":\"PC is not paired.\"}";

        assertEquals("PC is not paired.", AndroidSyncResponseReader.readError(responseJson), "reads sync response error");
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
            AndroidPairingClient client = new AndroidPairingClient(new AndroidSyncClient(1000, 1000));

            boolean paired = client.pair(settings, "phone-1", "123456");

            serverThread.join(2000L);
            server.close();
            assertEquals(true, paired, "pairing succeeds");
            assertEquals(true, settings.isPaired, "pairing marks settings paired");
            assertEquals("pc-1", settings.peerDeviceId, "pairing stores pc device");
            assertEquals("windows", settings.peerPlatform, "pairing stores pc platform");
            assertEquals("shared-secret", settings.sharedSecret, "pairing stores shared secret");
            assertEquals("", settings.lastError, "pairing clears error");
        } catch (Exception ex) {
            throw new AssertionError("pairing test failed", ex);
        }
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

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", actual " + actual);
        }
    }
}
