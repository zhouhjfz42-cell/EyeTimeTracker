using EyeTimeTracker.App.Sync;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Formatting;
using EyeTimeTracker.Core.Reminders;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Sync;
using EyeTimeTracker.Core.Tracking;
using System.Net;
using System.Net.Sockets;
using System.Text.Json;

static void AssertEqual<T>(T expected, T actual, string name)
{
    if (!EqualityComparer<T>.Default.Equals(expected, actual))
    {
        throw new Exception($"{name}: expected {expected}, actual {actual}");
    }
}

static ActivitySnapshot Snapshot(DateTimeOffset now, int idleSeconds, bool audio = false, bool unlocked = true)
{
    return new ActivitySnapshot(now, TimeSpan.FromSeconds(idleSeconds), audio, unlocked, false);
}

static ActivitySnapshot SnapshotWithIdleTime(DateTimeOffset now, TimeSpan idleTime, bool audio = false, bool unlocked = true)
{
    return new ActivitySnapshot(now, idleTime, audio, unlocked, false);
}

static void TestDoesNotBackfillLongIdleGap()
{
    var settings = TrackerSettings.Default;
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 9, 0, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 600), settings);
    acc.Tick(Snapshot(t0.AddMinutes(4), idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddMinutes(7), idleSeconds: 180), settings);
    acc.Tick(Snapshot(t0.AddMinutes(58), idleSeconds: 0), settings);

    AssertEqual(180L, acc.Today.TotalSeconds, nameof(TestDoesNotBackfillLongIdleGap));
}

static void TestIdleThresholdStopsCounting()
{
    var settings = TrackerSettings.Default with { IdleThresholdSeconds = 180, CountAudio = false };
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 10, 0, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddSeconds(10), idleSeconds: 10), settings);
    acc.Tick(Snapshot(t0.AddSeconds(190), idleSeconds: 190), settings);
    acc.Tick(Snapshot(t0.AddSeconds(200), idleSeconds: 200), settings);

    AssertEqual(180L, acc.Today.TotalSeconds, nameof(TestIdleThresholdStopsCounting));
}

static void TestContinuousRecentInputCountsEachNormalTick()
{
    var settings = TrackerSettings.Default with { IdleThresholdSeconds = 180, CountAudio = false };
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 10, 30, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 0), settings);
    for (var seconds = 10; seconds <= 300; seconds += 10)
    {
        acc.Tick(Snapshot(t0.AddSeconds(seconds), idleSeconds: 0), settings);
    }

    AssertEqual(300L, acc.Today.TotalSeconds, nameof(TestContinuousRecentInputCountsEachNormalTick));
}

static void TestHourlyAndSessionStatsAreRecorded()
{
    var settings = TrackerSettings.Default with { IdleThresholdSeconds = 180, CountAudio = false };
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 10, 0, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddSeconds(10), idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddSeconds(20), idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddMinutes(4), idleSeconds: 240), settings);
    acc.Tick(Snapshot(t0.AddMinutes(4).AddSeconds(10), idleSeconds: 250), settings);

    AssertEqual(180L, acc.Today.HourlySeconds[10], nameof(TestHourlyAndSessionStatsAreRecorded) + " hourly");
    AssertEqual(1, acc.Today.SessionSeconds.Count, nameof(TestHourlyAndSessionStatsAreRecorded) + " session count");
    AssertEqual(180L, acc.Today.SessionSeconds[0], nameof(TestHourlyAndSessionStatsAreRecorded) + " session seconds");
    AssertEqual(0L, acc.Today.CurrentSessionSeconds, nameof(TestHourlyAndSessionStatsAreRecorded) + " current session");
}

static void TestAudioCountsWithoutInput()
{
    var settings = TrackerSettings.Default with { CountAudio = true };
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 11, 0, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 600, audio: true), settings);
    acc.Tick(Snapshot(t0.AddSeconds(30), idleSeconds: 630, audio: true), settings);

    AssertEqual(30L, acc.Today.TotalSeconds, nameof(TestAudioCountsWithoutInput));
}

static void TestSparseAudioDoesNotBackfillElapsed()
{
    var settings = TrackerSettings.Default with { CountAudio = true };
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 12, 0, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 600, audio: false), settings);
    acc.Tick(Snapshot(t0.AddMinutes(4).AddSeconds(59), idleSeconds: 899, audio: true), settings);

    AssertEqual(0L, acc.Today.TotalSeconds, nameof(TestSparseAudioDoesNotBackfillElapsed));
}

static void TestFractionalTicksAreTruncated()
{
    var settings = TrackerSettings.Default with { CountAudio = false };
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 13, 0, 0, TimeSpan.Zero);

    acc.Tick(SnapshotWithIdleTime(t0, TimeSpan.Zero), settings);
    acc.Tick(SnapshotWithIdleTime(t0.AddMilliseconds(600), TimeSpan.FromMilliseconds(600)), settings);
    acc.Tick(SnapshotWithIdleTime(t0.AddMilliseconds(1200), TimeSpan.FromMilliseconds(1200)), settings);

    AssertEqual(0L, acc.Today.TotalSeconds, nameof(TestFractionalTicksAreTruncated));
}

static void TestDateRolloverStartsNewDay()
{
    var settings = TrackerSettings.Default;
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 23, 59, 50, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddSeconds(20), idleSeconds: 20), settings);

    AssertEqual(new DateOnly(2026, 6, 27), acc.Today.Date, nameof(TestDateRolloverStartsNewDay) + " date");
    AssertEqual(0L, acc.Today.TotalSeconds, nameof(TestDateRolloverStartsNewDay) + " seconds");
}

static void TestReminderOnlyOncePerDay()
{
    var settings = TrackerSettings.Default with { ReminderThresholdSeconds = 19800 };
    var dailyRecord = new DailyRecord(new DateOnly(2026, 6, 26))
    {
        TotalSeconds = 19800
    };
    var policy = new DailyReminderPolicy();

    AssertEqual(true, policy.ShouldNotify(dailyRecord, settings), nameof(TestReminderOnlyOncePerDay) + " first");

    policy.MarkShown(dailyRecord, settings);

    AssertEqual(false, policy.ShouldNotify(dailyRecord, settings), nameof(TestReminderOnlyOncePerDay) + " after shown");
}

static void TestReminderRepeatsAtThresholdMultiples()
{
    var settings = TrackerSettings.Default with
    {
        ReminderThresholdSeconds = 19800,
        RepeatReminder = true
    };
    var dailyRecord = new DailyRecord(new DateOnly(2026, 6, 26))
    {
        TotalSeconds = 19800
    };
    var policy = new DailyReminderPolicy();

    AssertEqual(true, policy.ShouldNotify(dailyRecord, settings), nameof(TestReminderRepeatsAtThresholdMultiples) + " first");

    policy.MarkShown(dailyRecord, settings);

    AssertEqual(true, dailyRecord.ReminderShown, nameof(TestReminderRepeatsAtThresholdMultiples) + " shown");
    AssertEqual(1, dailyRecord.LastReminderStep, nameof(TestReminderRepeatsAtThresholdMultiples) + " first step");

    dailyRecord.TotalSeconds = 30000;
    AssertEqual(false, policy.ShouldNotify(dailyRecord, settings), nameof(TestReminderRepeatsAtThresholdMultiples) + " before second");

    dailyRecord.TotalSeconds = 39600;
    AssertEqual(true, policy.ShouldNotify(dailyRecord, settings), nameof(TestReminderRepeatsAtThresholdMultiples) + " second");

    policy.MarkShown(dailyRecord, settings);

    AssertEqual(2, dailyRecord.LastReminderStep, nameof(TestReminderRepeatsAtThresholdMultiples) + " second step");
}

static void TestReminderThresholdMinutesAndDisplay()
{
    AssertEqual(19800, ReminderThreshold.FromMinutes(330), nameof(TestReminderThresholdMinutesAndDisplay) + " seconds");
    AssertEqual(330, ReminderThreshold.ToMinutes(19800), nameof(TestReminderThresholdMinutesAndDisplay) + " minutes");
    AssertEqual("5\u5c0f\u65f630\u5206", ReminderThreshold.Format(19800), nameof(TestReminderThresholdMinutesAndDisplay) + " hours");
    AssertEqual("45\u5206\u949f", ReminderThreshold.Format(2700), nameof(TestReminderThresholdMinutesAndDisplay) + " minutes only");
    AssertEqual("\uff08\u53735\u5c0f\u65f630\u5206\uff09", ReminderThreshold.FormatEquivalent(19800), nameof(TestReminderThresholdMinutesAndDisplay) + " equivalent");
    AssertEqual("\u53cd\u590d\u63d0\u9192\uff08\u5f53\u5929\u5185\u6bcf330\u5206\u949f\u63d0\u9192\u4e00\u6b21\uff09", ReminderThreshold.FormatRepeatLabel(330), nameof(TestReminderThresholdMinutesAndDisplay) + " repeat label");
}

static void TestTodayToneThresholds()
{
    AssertEqual(TodayTone.Safe, TodayTonePolicy.FromSeconds(6L * 3600L), nameof(TestTodayToneThresholds) + " six hours");
    AssertEqual(TodayTone.Warn, TodayTonePolicy.FromSeconds(6L * 3600L + 1L), nameof(TestTodayToneThresholds) + " over six hours");
    AssertEqual(TodayTone.Warn, TodayTonePolicy.FromSeconds(8L * 3600L), nameof(TestTodayToneThresholds) + " eight hours");
    AssertEqual(TodayTone.Danger, TodayTonePolicy.FromSeconds(8L * 3600L + 1L), nameof(TestTodayToneThresholds) + " over eight hours");
}

static void TestChartValueFormatting()
{
    AssertEqual("0分钟", ChartValueFormatter.FormatMinutes(59), nameof(TestChartValueFormatting) + " zero minutes");
    AssertEqual("28分钟", ChartValueFormatter.FormatMinutes(28 * 60), nameof(TestChartValueFormatting) + " minute tooltip");
    AssertEqual("0.5小时", ChartValueFormatter.FormatCompactHours(30 * 60), nameof(TestChartValueFormatting) + " half hour");
    AssertEqual("1小时", ChartValueFormatter.FormatCompactHours(60 * 60), nameof(TestChartValueFormatting) + " full hour");
    AssertEqual("2.5小时", ChartValueFormatter.FormatCompactHours(150 * 60), nameof(TestChartValueFormatting) + " two and half hours");
}

static void TestConnectionStatusFormatting()
{
    AssertEqual("统计中", ConnectionStatusFormatter.Format("统计中", false, "手机"), nameof(TestConnectionStatusFormatting) + " disconnected");
    AssertEqual("统计中（已连接手机）", ConnectionStatusFormatter.Format("统计中", true, "手机"), nameof(TestConnectionStatusFormatting) + " connected");
    AssertEqual("统计中（手机暂时离线）", ConnectionStatusFormatter.Format("统计中", true, false, "手机"), nameof(TestConnectionStatusFormatting) + " temporarily offline");
}

static void TestSyncPeerConnectionState()
{
    AssertEqual(false, SyncPeerConnectionState.IsOnline(SyncSettings.Unpaired, 1_783_000_000, 90), nameof(TestSyncPeerConnectionState) + " unpaired");
    AssertEqual(true, SyncPeerConnectionState.IsOnline(new SyncSettings { IsPaired = true, LastSyncUnixSeconds = 1_782_999_950 }, 1_783_000_000, 90), nameof(TestSyncPeerConnectionState) + " recent");
    AssertEqual(false, SyncPeerConnectionState.IsOnline(new SyncSettings { IsPaired = true, LastSyncUnixSeconds = 1_782_999_800 }, 1_783_000_000, 90), nameof(TestSyncPeerConnectionState) + " stale");
}

static void TestReminderMessageText()
{
    AssertEqual("\u7528\u773c\u63d0\u9192", ReminderMessage.Title, nameof(TestReminderMessageText) + " title");
    AssertEqual("\u4eca\u5929\u7684\u5c4f\u5e55\u4f7f\u7528\u65f6\u95f4\u5df2\u8fbe\u52305\u5c0f\u65f630\u5206\uff0c\u5efa\u8bae\u4f11\u606f\u4e00\u4e0b\u773c\u775b\u3002", ReminderMessage.Body(19800), nameof(TestReminderMessageText) + " body");
    AssertEqual("\u4eca\u5929\u7684\u5c4f\u5e55\u4f7f\u7528\u65f6\u95f4\u5df2\u7ecf\u7b2c2\u6b21\u8fbe\u5230330\u5206\u949f\u4e86\uff0c\u5efa\u8bae\u4f11\u606f\u4e00\u4e0b\u773c\u775b\u3002", ReminderMessage.Body(19800, true, 2), nameof(TestReminderMessageText) + " repeat body");
    AssertEqual("\u4eca\u5929\u7684\u5c4f\u5e55\u4f7f\u7528\u65f6\u95f4\u5df2\u8fbe\u52305\u5c0f\u65f630\u5206\uff0c\u5efa\u8bae\u4f11\u606f\u4e00\u4e0b\u773c\u775b\u3002", ReminderMessage.Body(19800, false, 2), nameof(TestReminderMessageText) + " once body");
}

static void TestJsonStateRoundTrip()
{
    var path = Path.Combine(Path.GetTempPath(), "eye-time-tracker-tests", $"{Guid.NewGuid()}.json");
    var store = new JsonStateStore(path);
    var savedRecord = new DailyRecord(new DateOnly(2026, 6, 26))
    {
        TotalSeconds = 12345,
        HourlySeconds = Enumerable.Range(0, 24).Select(index => (long)index).ToArray(),
        SessionSeconds = new List<long> { 120, 240 },
        CurrentSessionSeconds = 60,
        ReminderShown = true,
        LastReminderStep = 1
    };
    var state = new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Settings = TrackerSettings.Default with
        {
            IdleThresholdSeconds = 240,
            CountAudio = false,
            ReminderThresholdSeconds = 19800,
            StartWithWindows = false,
            RepeatReminder = true
        },
        Records = [savedRecord],
        Segments =
        [
            Segment("pc-test", "windows", "pc-input", new DateTimeOffset(2026, 6, 26, 9, 0, 0, TimeSpan.Zero), 10)
        ],
        Sync = new SyncSettings
        {
            IsPaired = true,
            PeerDeviceId = "phone-test",
            PeerPlatform = "android",
            SharedSecret = "secret",
            LastKnownHost = "192.168.1.2",
            LastKnownPort = 17420,
            LastSyncUnixSeconds = 123456,
            LastError = "none"
        }
    };

    store.Save(state);
    var loaded = store.Load();
    var loadedRecord = loaded.Records[0];

    AssertEqual(state.Settings, loaded.Settings, nameof(TestJsonStateRoundTrip) + " settings");
    AssertEqual(1, loaded.Records.Count, nameof(TestJsonStateRoundTrip) + " records count");
    AssertEqual(savedRecord.Date, loadedRecord.Date, nameof(TestJsonStateRoundTrip) + " date");
    AssertEqual(savedRecord.TotalSeconds, loadedRecord.TotalSeconds, nameof(TestJsonStateRoundTrip) + " seconds");
    AssertEqual(23L, loadedRecord.HourlySeconds[23], nameof(TestJsonStateRoundTrip) + " hourly");
    AssertEqual(2, loadedRecord.SessionSeconds.Count, nameof(TestJsonStateRoundTrip) + " session count");
    AssertEqual(60L, loadedRecord.CurrentSessionSeconds, nameof(TestJsonStateRoundTrip) + " current session");
    AssertEqual(savedRecord.ReminderShown, loadedRecord.ReminderShown, nameof(TestJsonStateRoundTrip) + " reminder shown");
    AssertEqual(savedRecord.LastReminderStep, loadedRecord.LastReminderStep, nameof(TestJsonStateRoundTrip) + " reminder step");
    AssertEqual("pc-test", loaded.DeviceId, nameof(TestJsonStateRoundTrip) + " device");
    AssertEqual("windows", loaded.Platform, nameof(TestJsonStateRoundTrip) + " platform");
    AssertEqual(1, loaded.Segments.Count, nameof(TestJsonStateRoundTrip) + " segments count");
    AssertEqual(10L, loaded.Segments[0].DurationSeconds, nameof(TestJsonStateRoundTrip) + " segment duration");
    AssertEqual(true, loaded.Sync.IsPaired, nameof(TestJsonStateRoundTrip) + " sync paired");
    AssertEqual("phone-test", loaded.Sync.PeerDeviceId, nameof(TestJsonStateRoundTrip) + " sync peer");
}

static void TestGetOrCreateRecordReusesExistingRecord()
{
    var state = new AppState();
    var date = new DateOnly(2026, 6, 26);

    var first = state.GetOrCreateRecord(date);
    first.TotalSeconds = 42;
    var second = state.GetOrCreateRecord(date);
    var nextDay = state.GetOrCreateRecord(date.AddDays(1));

    AssertEqual(true, ReferenceEquals(first, second), nameof(TestGetOrCreateRecordReusesExistingRecord) + " same reference");
    AssertEqual(2, state.Records.Count, nameof(TestGetOrCreateRecordReusesExistingRecord) + " records count");
    AssertEqual(42L, second.TotalSeconds, nameof(TestGetOrCreateRecordReusesExistingRecord) + " reused seconds");
    AssertEqual(date.AddDays(1), nextDay.Date, nameof(TestGetOrCreateRecordReusesExistingRecord) + " new date");
}

static void TestMissingJsonReturnsDefaultState()
{
    var path = Path.Combine(Path.GetTempPath(), "eye-time-tracker-tests", $"{Guid.NewGuid()}.json");
    var store = new JsonStateStore(path);

    var loaded = store.Load();

    AssertEqual(TrackerSettings.Default, loaded.Settings, nameof(TestMissingJsonReturnsDefaultState) + " settings");
    AssertEqual(0, loaded.Records.Count, nameof(TestMissingJsonReturnsDefaultState) + " records count");
}

static void TestInvalidJsonReturnsDefaultState()
{
    var path = Path.Combine(Path.GetTempPath(), "eye-time-tracker-tests", $"{Guid.NewGuid()}.json");
    Directory.CreateDirectory(Path.GetDirectoryName(path)!);
    File.WriteAllText(path, "{ invalid json");
    var store = new JsonStateStore(path);

    var loaded = store.Load();

    AssertEqual(TrackerSettings.Default, loaded.Settings, nameof(TestInvalidJsonReturnsDefaultState) + " settings");
    AssertEqual(0, loaded.Records.Count, nameof(TestInvalidJsonReturnsDefaultState) + " records count");
}

static void TestOldJsonWithoutRepeatReminderFieldsLoadsSafely()
{
    var path = Path.Combine(Path.GetTempPath(), "eye-time-tracker-tests", $"{Guid.NewGuid()}.json");
    Directory.CreateDirectory(Path.GetDirectoryName(path)!);
    File.WriteAllText(path, """
        {
          "Settings": {
            "IdleThresholdSeconds": 180,
            "CountAudio": true,
            "ReminderThresholdSeconds": 19800,
            "StartWithWindows": true
          },
          "Records": [
            {
              "Date": "2026-06-26",
              "TotalSeconds": 19800,
              "ReminderShown": true
            }
          ]
        }
        """);
    var store = new JsonStateStore(path);

    var loaded = store.Load();
    var record = loaded.Records[0];

    AssertEqual(false, loaded.Settings.RepeatReminder, nameof(TestOldJsonWithoutRepeatReminderFieldsLoadsSafely) + " repeat default");
    AssertEqual(0, record.LastReminderStep, nameof(TestOldJsonWithoutRepeatReminderFieldsLoadsSafely) + " step default");
    AssertEqual(19800L, record.TotalSeconds, nameof(TestOldJsonWithoutRepeatReminderFieldsLoadsSafely) + " seconds");
    AssertEqual(true, record.ReminderShown, nameof(TestOldJsonWithoutRepeatReminderFieldsLoadsSafely) + " shown");
}

static void TestUsageSegmentIdIsStable()
{
    var start = new DateTimeOffset(2026, 7, 2, 8, 0, 0, TimeSpan.Zero);
    var end = start.AddSeconds(10);

    var first = UsageSegmentId.Create("pc-1", "pc-input", start, end);
    var second = UsageSegmentId.Create("pc-1", "pc-input", start, end);

    AssertEqual(first, second, nameof(TestUsageSegmentIdIsStable));
}

static void TestAppStateNormalizesSegmentsAndDeviceId()
{
    var state = new AppState();
    AppState.Normalize(state);

    AssertEqual(false, string.IsNullOrWhiteSpace(state.DeviceId), nameof(TestAppStateNormalizesSegmentsAndDeviceId) + " device id");
    AssertEqual("windows", state.Platform, nameof(TestAppStateNormalizesSegmentsAndDeviceId) + " platform");
    AssertEqual(0, state.Segments.Count, nameof(TestAppStateNormalizesSegmentsAndDeviceId) + " segments");
}

static UsageSegment Segment(string device, string platform, string source, DateTimeOffset start, int seconds)
{
    var end = start.AddSeconds(seconds);
    return new UsageSegment
    {
        SegmentId = UsageSegmentId.Create(device, source, start, end),
        DeviceId = device,
        Platform = platform,
        Source = source,
        StartUnixSeconds = start.ToUnixTimeSeconds(),
        EndUnixSeconds = end.ToUnixTimeSeconds(),
        LocalDate = DateOnly.FromDateTime(start.LocalDateTime),
        CreatedAtUnixSeconds = start.ToUnixTimeSeconds(),
        UpdatedAtUnixSeconds = end.ToUnixTimeSeconds()
    };
}

static JsonStateStore SeedState(AppState state)
{
    var path = Path.Combine(Path.GetTempPath(), "eye-time-tracker-tests", $"{Guid.NewGuid()}.json");
    var store = new JsonStateStore(path);
    store.Save(state);
    return store;
}

static (int FirstPort, int SecondPort, TcpListener ReservedFirstPort) ReserveAvailablePortPair()
{
    for (var port = 20000; port < 30000; port++)
    {
        TcpListener? first = null;
        TcpListener? second = null;
        try
        {
            first = new TcpListener(IPAddress.Any, port);
            first.Start();
            second = new TcpListener(IPAddress.Any, port + 1);
            second.Start();
            second.Stop();
            return (port, port + 1, first);
        }
        catch (SocketException)
        {
            first?.Stop();
            second?.Stop();
        }
    }

    throw new InvalidOperationException("Could not reserve adjacent TCP ports for the test.");
}

static int FindAvailablePort()
{
    var listener = new TcpListener(IPAddress.Loopback, 0);
    listener.Start();
    var port = ((IPEndPoint)listener.LocalEndpoint).Port;
    listener.Stop();
    return port;
}

static int FindAvailableUdpPort()
{
    using var client = new UdpClient(new IPEndPoint(IPAddress.Loopback, 0));
    return ((IPEndPoint)client.Client.LocalEndPoint!).Port;
}

static void TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments()
{
    var t0 = new DateTimeOffset(2026, 7, 2, 12, 0, 0, TimeSpan.Zero);
    var pcSegment = Segment("pc-test", "windows", "pc-input", t0, 20);
    var androidSegment = Segment("phone-test", "android", "android-screen", t0.AddMinutes(1), 30);
    var store = SeedState(new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Settings = TrackerSettings.Default,
        Segments = [pcSegment],
        Sync = new SyncSettings
        {
            IsPaired = true,
            PeerDeviceId = "phone-test",
            PeerPlatform = "android",
            SharedSecret = "shared-secret"
        }
    });
    var coordinator = new PcSyncCoordinator(store, () => 1_783_000_000);

    var response = coordinator.HandleSync(new SyncRequest
    {
        DeviceId = "phone-test",
        Platform = "android",
        Segments = [androidSegment],
        Settings = TrackerSettings.Default with
        {
            ReminderThresholdSeconds = 2400,
            RepeatReminder = true
        }
    });
    var saved = store.Load();

    AssertEqual(true, response.Accepted, nameof(TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments) + " accepted");
    AssertEqual(2, saved.Segments.Count, nameof(TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments) + " saved count");
    AssertEqual(true, saved.Segments.Any(segment => segment.SegmentId == androidSegment.SegmentId), nameof(TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments) + " android saved");
    AssertEqual(1, response.Segments.Count, nameof(TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments) + " response count");
    AssertEqual(pcSegment.SegmentId, response.Segments[0].SegmentId, nameof(TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments) + " response segment");
    AssertEqual(2400, saved.Settings.ReminderThresholdSeconds, nameof(TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments) + " settings");
    AssertEqual(true, saved.Settings.RepeatReminder, nameof(TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments) + " repeat");
    AssertEqual(1_783_000_000L, saved.Sync.LastSyncUnixSeconds, nameof(TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments) + " last sync");
}

static void TestPcSyncCoordinatorRejectsUnpairedSync()
{
    var store = SeedState(new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Sync = SyncSettings.Unpaired
    });
    var coordinator = new PcSyncCoordinator(store, () => 1_783_000_000);

    var response = coordinator.HandleSync(new SyncRequest
    {
        DeviceId = "phone-test",
        Platform = "android",
        Segments = [Segment("phone-test", "android", "android-screen", new DateTimeOffset(2026, 7, 2, 12, 0, 0, TimeSpan.Zero), 30)]
    });
    var saved = store.Load();

    AssertEqual(false, response.Accepted, nameof(TestPcSyncCoordinatorRejectsUnpairedSync) + " rejected");
    AssertEqual(0, saved.Segments.Count, nameof(TestPcSyncCoordinatorRejectsUnpairedSync) + " no merge");
    AssertEqual(false, string.IsNullOrWhiteSpace(response.Error), nameof(TestPcSyncCoordinatorRejectsUnpairedSync) + " error");
}

static void TestPcSyncCoordinatorCanUseInMemoryStateGateway()
{
    var t0 = new DateTimeOffset(2026, 7, 2, 12, 0, 0, TimeSpan.Zero);
    var androidSegment = Segment("phone-test", "android", "android-screen", t0, 30);
    var current = new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Settings = TrackerSettings.Default,
        Sync = new SyncSettings
        {
            IsPaired = true,
            PeerDeviceId = "phone-test",
            PeerPlatform = "android",
            SharedSecret = "shared-secret"
        }
    };
    var coordinator = new PcSyncCoordinator(() => current, state => current = state, () => 1_783_000_000);

    var response = coordinator.HandleSync(new SyncRequest
    {
        DeviceId = "phone-test",
        Platform = "android",
        Segments = [androidSegment],
        Settings = TrackerSettings.Default with
        {
            ReminderThresholdSeconds = 3600
        }
    });

    AssertEqual(true, response.Accepted, nameof(TestPcSyncCoordinatorCanUseInMemoryStateGateway) + " accepted");
    AssertEqual(1, current.Segments.Count, nameof(TestPcSyncCoordinatorCanUseInMemoryStateGateway) + " saved count");
    AssertEqual(androidSegment.SegmentId, current.Segments[0].SegmentId, nameof(TestPcSyncCoordinatorCanUseInMemoryStateGateway) + " segment");
    AssertEqual(3600, current.Settings.ReminderThresholdSeconds, nameof(TestPcSyncCoordinatorCanUseInMemoryStateGateway) + " settings");
}

static void TestPcSyncCoordinatorPairsWithCorrectCode()
{
    var store = SeedState(new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Sync = SyncSettings.Unpaired
    });
    var coordinator = new PcSyncCoordinator(store, () => 1_783_000_000);

    var response = coordinator.HandlePair(new PairRequest
    {
        PairingCode = "123456",
        DeviceId = "phone-test",
        Platform = "android",
        TimestampUnixSeconds = 1_783_000_000
    }, "123456");
    var saved = store.Load();

    AssertEqual(true, response.Accepted, nameof(TestPcSyncCoordinatorPairsWithCorrectCode) + " accepted");
    AssertEqual(true, saved.Sync.IsPaired, nameof(TestPcSyncCoordinatorPairsWithCorrectCode) + " saved paired");
    AssertEqual("phone-test", saved.Sync.PeerDeviceId, nameof(TestPcSyncCoordinatorPairsWithCorrectCode) + " peer device");
    AssertEqual("android", saved.Sync.PeerPlatform, nameof(TestPcSyncCoordinatorPairsWithCorrectCode) + " peer platform");
    AssertEqual(false, string.IsNullOrWhiteSpace(saved.Sync.SharedSecret), nameof(TestPcSyncCoordinatorPairsWithCorrectCode) + " saved secret");
    AssertEqual(saved.Sync.SharedSecret, response.SharedSecret, nameof(TestPcSyncCoordinatorPairsWithCorrectCode) + " response secret");
}

static void TestPcSyncCoordinatorRejectsWrongPairingCode()
{
    var store = SeedState(new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Sync = SyncSettings.Unpaired
    });
    var coordinator = new PcSyncCoordinator(store, () => 1_783_000_000);

    var response = coordinator.HandlePair(new PairRequest
    {
        PairingCode = "000000",
        DeviceId = "phone-test",
        Platform = "android",
        TimestampUnixSeconds = 1_783_000_000
    }, "123456");
    var saved = store.Load();

    AssertEqual(false, response.Accepted, nameof(TestPcSyncCoordinatorRejectsWrongPairingCode) + " rejected");
    AssertEqual(false, saved.Sync.IsPaired, nameof(TestPcSyncCoordinatorRejectsWrongPairingCode) + " not paired");
    AssertEqual(true, string.IsNullOrWhiteSpace(saved.Sync.SharedSecret), nameof(TestPcSyncCoordinatorRejectsWrongPairingCode) + " no secret");
}

static void TestPcSyncCoordinatorDisconnectsKnownPeer()
{
    var store = SeedState(new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Sync = new SyncSettings
        {
            IsPaired = true,
            PeerDeviceId = "phone-test",
            PeerPlatform = "android",
            SharedSecret = "shared-secret",
            LastSyncUnixSeconds = 1_783_000_000
        }
    });
    var coordinator = new PcSyncCoordinator(store, () => 1_783_000_100);

    var response = coordinator.HandleDisconnect(new DisconnectRequest
    {
        DeviceId = "phone-test",
        Platform = "android",
        TimestampUnixSeconds = 1_783_000_100
    });
    var saved = store.Load();

    AssertEqual(true, response.Accepted, nameof(TestPcSyncCoordinatorDisconnectsKnownPeer) + " accepted");
    AssertEqual(false, saved.Sync.IsPaired, nameof(TestPcSyncCoordinatorDisconnectsKnownPeer) + " unpaired");
    AssertEqual(string.Empty, saved.Sync.PeerDeviceId, nameof(TestPcSyncCoordinatorDisconnectsKnownPeer) + " peer cleared");
    AssertEqual(string.Empty, saved.Sync.SharedSecret, nameof(TestPcSyncCoordinatorDisconnectsKnownPeer) + " secret cleared");
}

static void TestPcSyncCoordinatorCreatesDiscoveryResponse()
{
    const string test = "TestPcSyncCoordinatorCreatesDiscoveryResponse";
    var current = new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows"
    };
    var coordinator = new PcSyncCoordinator(() => current, state => current = state);

    var response = coordinator.CreateDiscoveryResponse(17421);

    AssertEqual(SyncMessageTypes.DiscoveryResponse, response.Type, test + " type");
    AssertEqual("pc-test", response.DeviceId, test + " device");
    AssertEqual("windows", response.Platform, test + " platform");
    AssertEqual(17421, response.Port, test + " port");
}

static void TestPcPairingCodeProviderReturnsCodeBeforeExpiry()
{
    var now = new DateTimeOffset(2026, 7, 2, 10, 0, 0, TimeSpan.Zero);
    var pairingCodes = new PcPairingCodeProvider(() => now);

    pairingCodes.AllowCode("246810");
    now = now.AddMinutes(4).AddSeconds(59);

    AssertEqual("246810", pairingCodes.GetExpectedCode(), nameof(TestPcPairingCodeProviderReturnsCodeBeforeExpiry));
}

static void TestPcPairingCodeProviderRejectsCodeAfterExpiry()
{
    var now = new DateTimeOffset(2026, 7, 2, 10, 0, 0, TimeSpan.Zero);
    var pairingCodes = new PcPairingCodeProvider(() => now);

    pairingCodes.AllowCode("246810");
    now = now.AddMinutes(5).AddSeconds(1);

    AssertEqual(string.Empty, pairingCodes.GetExpectedCode(), nameof(TestPcPairingCodeProviderRejectsCodeAfterExpiry));
}

static void TestPcPairingCodeProviderAcceptsTrimmedSixDigitCode()
{
    var pairingCodes = new PcPairingCodeProvider();

    var accepted = pairingCodes.TryAllowCode(" 246810 ", out var error);

    AssertEqual(true, accepted, nameof(TestPcPairingCodeProviderAcceptsTrimmedSixDigitCode) + " accepted");
    AssertEqual(string.Empty, error, nameof(TestPcPairingCodeProviderAcceptsTrimmedSixDigitCode) + " error");
    AssertEqual("246810", pairingCodes.GetExpectedCode(), nameof(TestPcPairingCodeProviderAcceptsTrimmedSixDigitCode) + " code");
}

static void TestPcPairingCodeProviderRejectsNonSixDigitCode()
{
    var pairingCodes = new PcPairingCodeProvider();

    var accepted = pairingCodes.TryAllowCode("24A810", out var error);

    AssertEqual(false, accepted, nameof(TestPcPairingCodeProviderRejectsNonSixDigitCode) + " accepted");
    AssertEqual(false, string.IsNullOrWhiteSpace(error), nameof(TestPcPairingCodeProviderRejectsNonSixDigitCode) + " error");
    AssertEqual(string.Empty, pairingCodes.GetExpectedCode(), nameof(TestPcPairingCodeProviderRejectsNonSixDigitCode) + " code");
}

static void TestPcSyncServerUsesNextPortWhenDefaultIsBusy()
{
    var (firstPort, secondPort, reservedFirstPort) = ReserveAvailablePortPair();
    var store = SeedState(new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Sync = new SyncSettings
        {
            IsPaired = true,
            PeerDeviceId = "phone-test",
            PeerPlatform = "android",
            SharedSecret = "shared-secret"
        }
    });
    using var server = new PcSyncServer(new PcSyncCoordinator(store), firstPort, secondPort);

    try
    {
        server.Start();

        AssertEqual(secondPort, server.Port, "TestPcSyncServerUsesNextPortWhenDefaultIsBusy");
    }
    finally
    {
        reservedFirstPort.Stop();
    }
}

static void TestPcSyncServerHandlesOneJsonSyncRequest()
{
    var port = FindAvailablePort();
    var t0 = new DateTimeOffset(2026, 7, 2, 12, 0, 0, TimeSpan.Zero);
    var pcSegment = Segment("pc-test", "windows", "pc-input", t0, 20);
    var androidSegment = Segment("phone-test", "android", "android-screen", t0.AddMinutes(1), 30);
    var store = SeedState(new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Settings = TrackerSettings.Default,
        Segments = [pcSegment],
        Sync = new SyncSettings
        {
            IsPaired = true,
            PeerDeviceId = "phone-test",
            PeerPlatform = "android",
            SharedSecret = "shared-secret"
        }
    });
    using var server = new PcSyncServer(new PcSyncCoordinator(store, () => 1_783_000_000), port, port);
    server.Start();

    using var client = new TcpClient();
    client.Connect(IPAddress.Loopback, server.Port);
    client.ReceiveTimeout = 5000;
    client.SendTimeout = 5000;
    using var stream = client.GetStream();
    using var writer = new StreamWriter(stream) { AutoFlush = true };
    using var reader = new StreamReader(stream);
    writer.WriteLine(JsonSerializer.Serialize(new SyncRequest
    {
        DeviceId = "phone-test",
        Platform = "android",
        Segments = [androidSegment],
        Settings = TrackerSettings.Default with
        {
            ReminderThresholdSeconds = 1800
        }
    }));

    var responseJson = reader.ReadLine();
    var response = JsonSerializer.Deserialize<SyncResponse>(responseJson!);
    var saved = store.Load();

    AssertEqual(true, response?.Accepted, nameof(TestPcSyncServerHandlesOneJsonSyncRequest) + " accepted");
    AssertEqual(pcSegment.SegmentId, response!.Segments[0].SegmentId, nameof(TestPcSyncServerHandlesOneJsonSyncRequest) + " pc segment");
    AssertEqual(true, saved.Segments.Any(segment => segment.SegmentId == androidSegment.SegmentId), nameof(TestPcSyncServerHandlesOneJsonSyncRequest) + " android saved");
    AssertEqual(1800, saved.Settings.ReminderThresholdSeconds, nameof(TestPcSyncServerHandlesOneJsonSyncRequest) + " settings");
}

static void TestPcSyncServerAcceptsPairRequestWithCurrentCode()
{
    var port = FindAvailablePort();
    var now = new DateTimeOffset(2026, 7, 2, 10, 0, 0, TimeSpan.Zero);
    var pairingCodes = new PcPairingCodeProvider(() => now);
    pairingCodes.AllowCode("246810");
    var store = SeedState(new AppState
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Sync = SyncSettings.Unpaired
    });
    using var server = new PcSyncServer(
        new PcSyncCoordinator(store, () => 1_783_000_000),
        port,
        port,
        () => pairingCodes.GetExpectedCode(),
        pairingCodes.Clear);
    server.Start();

    using var client = new TcpClient();
    client.Connect(IPAddress.Loopback, server.Port);
    client.ReceiveTimeout = 5000;
    client.SendTimeout = 5000;
    using var stream = client.GetStream();
    using var writer = new StreamWriter(stream) { AutoFlush = true };
    using var reader = new StreamReader(stream);
    writer.WriteLine(JsonSerializer.Serialize(new PairRequest
    {
        PairingCode = "246810",
        DeviceId = "phone-test",
        Platform = "android",
        TimestampUnixSeconds = 1_783_000_000
    }));

    var responseJson = reader.ReadLine();
    var response = JsonSerializer.Deserialize<PairAccept>(responseJson!);
    var saved = store.Load();

    AssertEqual(true, response?.Accepted, nameof(TestPcSyncServerAcceptsPairRequestWithCurrentCode) + " accepted");
    AssertEqual(true, saved.Sync.IsPaired, nameof(TestPcSyncServerAcceptsPairRequestWithCurrentCode) + " saved paired");
    AssertEqual("phone-test", saved.Sync.PeerDeviceId, nameof(TestPcSyncServerAcceptsPairRequestWithCurrentCode) + " peer device");
    AssertEqual(false, string.IsNullOrWhiteSpace(response!.SharedSecret), nameof(TestPcSyncServerAcceptsPairRequestWithCurrentCode) + " secret");
    AssertEqual(string.Empty, pairingCodes.GetExpectedCode(), nameof(TestPcSyncServerAcceptsPairRequestWithCurrentCode) + " code cleared");
}

static void TestPcDiscoveryServerRespondsWithSyncPort()
{
    var discoveryPort = FindAvailableUdpPort();
    using var server = new PcDiscoveryServer(() => new DiscoveryResponse
    {
        DeviceId = "pc-test",
        Platform = "windows",
        Port = 17420
    }, discoveryPort);
    server.Start();

    using var client = new UdpClient();
    client.Client.ReceiveTimeout = 3000;
    var requestJson = JsonSerializer.Serialize(new DiscoveryRequest());
    var requestBytes = System.Text.Encoding.UTF8.GetBytes(requestJson);
    client.Send(requestBytes, requestBytes.Length, new IPEndPoint(IPAddress.Loopback, discoveryPort));
    var endpoint = new IPEndPoint(IPAddress.Any, 0);
    var responseBytes = client.Receive(ref endpoint);
    var responseJson = System.Text.Encoding.UTF8.GetString(responseBytes);
    var response = JsonSerializer.Deserialize<DiscoveryResponse>(responseJson);

    AssertEqual("pc-test", response?.DeviceId, nameof(TestPcDiscoveryServerRespondsWithSyncPort) + " device");
    AssertEqual("windows", response!.Platform, nameof(TestPcDiscoveryServerRespondsWithSyncPort) + " platform");
    AssertEqual(17420, response.Port, nameof(TestPcDiscoveryServerRespondsWithSyncPort) + " port");
}

static void TestSegmentsDeDuplicateOverlappingDevices()
{
    var t0 = new DateTimeOffset(2026, 7, 2, 12, 0, 0, TimeSpan.Zero);
    var segments = new[]
    {
        Segment("pc", "windows", "pc-input", t0, 30),
        Segment("phone", "android", "android-screen", t0, 30)
    };

    var summary = UsageSegmentMerger.BuildDailyRecord(new DateOnly(2026, 7, 2), segments);

    AssertEqual(30L, summary.TotalSeconds, nameof(TestSegmentsDeDuplicateOverlappingDevices));
}

static void TestUsageSegmentFactoryCreatesExpectedSegment()
{
    var start = new DateTimeOffset(2026, 7, 2, 8, 0, 0, TimeSpan.Zero);
    var end = start.AddSeconds(10);

    var segment = UsageSegmentFactory.Create("pc-1", "windows", "pc-input", start, end);

    AssertEqual("pc-1", segment.DeviceId, nameof(TestUsageSegmentFactoryCreatesExpectedSegment) + " device");
    AssertEqual("windows", segment.Platform, nameof(TestUsageSegmentFactoryCreatesExpectedSegment) + " platform");
    AssertEqual("pc-input", segment.Source, nameof(TestUsageSegmentFactoryCreatesExpectedSegment) + " source");
    AssertEqual(10L, segment.DurationSeconds, nameof(TestUsageSegmentFactoryCreatesExpectedSegment) + " duration");
    AssertEqual(new DateOnly(2026, 7, 2), segment.LocalDate, nameof(TestUsageSegmentFactoryCreatesExpectedSegment) + " date");
}

static void TestContinuousBreaksAfterThreeMinutes()
{
    var t0 = new DateTimeOffset(2026, 7, 2, 9, 0, 0, TimeSpan.Zero);
    var segments = new[]
    {
        Segment("pc", "windows", "pc-input", t0, 60),
        Segment("pc", "windows", "pc-input", t0.AddMinutes(4), 60)
    };

    var summary = UsageSegmentMerger.BuildDailyRecord(new DateOnly(2026, 7, 2), segments);

    AssertEqual(2, summary.SessionSeconds.Count, nameof(TestContinuousBreaksAfterThreeMinutes) + " count");
    AssertEqual(60L, summary.SessionSeconds[0], nameof(TestContinuousBreaksAfterThreeMinutes) + " first");
    AssertEqual(60L, summary.SessionSeconds[1], nameof(TestContinuousBreaksAfterThreeMinutes) + " second");
}

static void TestSyncMessageSigningIsStable()
{
    var first = SyncMessageSigner.Sign(SyncMessageTypes.SyncRequest, 1_788_888_000, "{\"ok\":true}", "secret");
    var second = SyncMessageSigner.Sign(SyncMessageTypes.SyncRequest, 1_788_888_000, "{\"ok\":true}", "secret");

    AssertEqual(first, second, nameof(TestSyncMessageSigningIsStable));
    AssertEqual(
        true,
        SyncMessageSigner.Verify(SyncMessageTypes.SyncRequest, 1_788_888_000, "{\"ok\":true}", "secret", first, 1_788_888_100),
        nameof(TestSyncMessageSigningIsStable) + " verify");
}

static void TestSyncMessageSigningRejectsWrongSecret()
{
    var signature = SyncMessageSigner.Sign(SyncMessageTypes.SyncRequest, 1_788_888_000, "{}", "secret");

    AssertEqual(
        false,
        SyncMessageSigner.Verify(SyncMessageTypes.SyncRequest, 1_788_888_000, "{}", "wrong", signature, 1_788_888_100),
        nameof(TestSyncMessageSigningRejectsWrongSecret));
}

static void TestSyncMessageSigningRejectsStaleTimestamp()
{
    var signature = SyncMessageSigner.Sign(SyncMessageTypes.SyncRequest, 1_788_888_000, "{}", "secret");

    AssertEqual(
        false,
        SyncMessageSigner.Verify(SyncMessageTypes.SyncRequest, 1_788_888_000, "{}", "secret", signature, 1_788_888_301),
        nameof(TestSyncMessageSigningRejectsStaleTimestamp));
}

TestDoesNotBackfillLongIdleGap();
TestIdleThresholdStopsCounting();
TestContinuousRecentInputCountsEachNormalTick();
TestHourlyAndSessionStatsAreRecorded();
TestAudioCountsWithoutInput();
TestSparseAudioDoesNotBackfillElapsed();
TestFractionalTicksAreTruncated();
TestDateRolloverStartsNewDay();
TestReminderOnlyOncePerDay();
TestReminderRepeatsAtThresholdMultiples();
TestReminderThresholdMinutesAndDisplay();
TestTodayToneThresholds();
TestChartValueFormatting();
TestConnectionStatusFormatting();
TestSyncPeerConnectionState();
TestReminderMessageText();
TestJsonStateRoundTrip();
TestGetOrCreateRecordReusesExistingRecord();
TestMissingJsonReturnsDefaultState();
TestInvalidJsonReturnsDefaultState();
TestOldJsonWithoutRepeatReminderFieldsLoadsSafely();
TestUsageSegmentIdIsStable();
TestAppStateNormalizesSegmentsAndDeviceId();
TestPcSyncCoordinatorAppliesAndroidSegmentsAndReturnsPcSegments();
TestPcSyncCoordinatorRejectsUnpairedSync();
TestPcSyncCoordinatorCanUseInMemoryStateGateway();
TestPcSyncCoordinatorPairsWithCorrectCode();
TestPcSyncCoordinatorRejectsWrongPairingCode();
TestPcSyncCoordinatorDisconnectsKnownPeer();
TestPcSyncCoordinatorCreatesDiscoveryResponse();
TestPcPairingCodeProviderReturnsCodeBeforeExpiry();
TestPcPairingCodeProviderRejectsCodeAfterExpiry();
TestPcPairingCodeProviderAcceptsTrimmedSixDigitCode();
TestPcPairingCodeProviderRejectsNonSixDigitCode();
TestPcSyncServerUsesNextPortWhenDefaultIsBusy();
TestPcSyncServerHandlesOneJsonSyncRequest();
TestPcSyncServerAcceptsPairRequestWithCurrentCode();
TestPcDiscoveryServerRespondsWithSyncPort();
TestSegmentsDeDuplicateOverlappingDevices();
TestUsageSegmentFactoryCreatesExpectedSegment();
TestContinuousBreaksAfterThreeMinutes();
TestSyncMessageSigningIsStable();
TestSyncMessageSigningRejectsWrongSecret();
TestSyncMessageSigningRejectsStaleTimestamp();
Console.WriteLine("All tests passed.");
