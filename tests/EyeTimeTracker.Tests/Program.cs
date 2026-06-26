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
    var record = new DailyRecord(new DateOnly(2026, 6, 26))
    {
        TotalSeconds = 19800
    };
    var policy = new DailyReminderPolicy();

    AssertEqual(true, policy.ShouldNotify(record, settings), nameof(TestReminderOnlyOncePerDay) + " first");

    policy.MarkShown(record);

    AssertEqual(false, policy.ShouldNotify(record, settings), nameof(TestReminderOnlyOncePerDay) + " after shown");
}

static void TestJsonStateRoundTrip()
{
    var path = Path.Combine(Path.GetTempPath(), "eye-time-tracker-tests", $"{Guid.NewGuid()}.json");
    var store = new JsonStateStore(path);
    var state = new AppState
    {
        Settings = TrackerSettings.Default with
        {
            IdleThresholdSeconds = 240,
            CountAudio = false,
            ReminderThresholdSeconds = 19800,
            StartWithWindows = false
        },
        Today = new DailyRecord(new DateOnly(2026, 6, 26))
        {
            TotalSeconds = 12345,
            ReminderShown = true
        }
    };

    store.Save(state);
    var loaded = store.Load();

    AssertEqual(state.Settings, loaded.Settings, nameof(TestJsonStateRoundTrip) + " settings");
    AssertEqual(state.Today.Date, loaded.Today.Date, nameof(TestJsonStateRoundTrip) + " date");
    AssertEqual(state.Today.TotalSeconds, loaded.Today.TotalSeconds, nameof(TestJsonStateRoundTrip) + " seconds");
    AssertEqual(state.Today.ReminderShown, loaded.Today.ReminderShown, nameof(TestJsonStateRoundTrip) + " reminder shown");
}

static void TestInvalidJsonReturnsDefaultState()
{
    var path = Path.Combine(Path.GetTempPath(), "eye-time-tracker-tests", $"{Guid.NewGuid()}.json");
    Directory.CreateDirectory(Path.GetDirectoryName(path)!);
    File.WriteAllText(path, "{ invalid json");
    var store = new JsonStateStore(path);

    var loaded = store.Load();

    AssertEqual(TrackerSettings.Default, loaded.Settings, nameof(TestInvalidJsonReturnsDefaultState) + " settings");
    AssertEqual(DateOnly.FromDateTime(DateTime.Today), loaded.Today.Date, nameof(TestInvalidJsonReturnsDefaultState) + " date");
    AssertEqual(0L, loaded.Today.TotalSeconds, nameof(TestInvalidJsonReturnsDefaultState) + " seconds");
    AssertEqual(false, loaded.Today.ReminderShown, nameof(TestInvalidJsonReturnsDefaultState) + " reminder shown");
}

TestDoesNotBackfillLongIdleGap();
TestIdleThresholdStopsCounting();
TestAudioCountsWithoutInput();
TestSparseAudioDoesNotBackfillElapsed();
TestFractionalTicksAreTruncated();
TestDateRolloverStartsNewDay();
TestReminderOnlyOncePerDay();
TestJsonStateRoundTrip();
TestInvalidJsonReturnsDefaultState();
Console.WriteLine("All tests passed.");
