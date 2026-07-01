using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Tracking;

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
        Settings = TrackerSettings.Default with
        {
            IdleThresholdSeconds = 240,
            CountAudio = false,
            ReminderThresholdSeconds = 19800,
            StartWithWindows = false,
            RepeatReminder = true
        },
        Records = [savedRecord]
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
TestReminderMessageText();
TestJsonStateRoundTrip();
TestGetOrCreateRecordReusesExistingRecord();
TestMissingJsonReturnsDefaultState();
TestInvalidJsonReturnsDefaultState();
TestOldJsonWithoutRepeatReminderFieldsLoadsSafely();
Console.WriteLine("All tests passed.");
