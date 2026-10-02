using EyeTimeTracker.Core.DesktopActivity;
using EyeTimeTracker.Core.Storage;

/// <summary>P0-2 迁移与版本化存储测试。</summary>
static class DesktopMigrationTests
{
    private static void Check<T>(T expected, T actual, string name)
    {
        if (!EqualityComparer<T>.Default.Equals(expected, actual))
        {
            throw new Exception($"{name}: expected {expected}, actual {actual}");
        }
    }

    public static void RunAll()
    {
        LegacyBackupIsCreatedOnceAndIdempotent();
        NoBackupWhenDesktopStateExists();
        StoreRoundtripPreservesEvents();
        Console.WriteLine("  desktop migration tests passed.");
    }

    private static void LegacyBackupIsCreatedOnceAndIdempotent()
    {
        var name = nameof(LegacyBackupIsCreatedOnceAndIdempotent);
        var dir = Directory.CreateDirectory(Path.Combine(Path.GetTempPath(), "desktop-migration-tests", Guid.NewGuid().ToString("N")));
        var legacyPath = Path.Combine(dir.FullName, "state.json");
        var desktopPath = Path.Combine(dir.FullName, "desktop-state.json");
        File.WriteAllText(legacyPath, "{\"deviceId\":\"legacy\"}");

        Check(true, DesktopStateMigrator.EnsureLegacyBackup(legacyPath, desktopPath), name + " first backup created");
        Check(true, File.Exists(Path.Combine(dir.FullName, DesktopStateMigrator.LegacyBackupFileName)), name + " backup file exists");
        Check(false, DesktopStateMigrator.EnsureLegacyBackup(legacyPath, desktopPath), name + " second run does not repeat");
        Check("{\"deviceId\":\"legacy\"}", File.ReadAllText(legacyPath), name + " legacy untouched");
    }

    private static void NoBackupWhenDesktopStateExists()
    {
        var name = nameof(NoBackupWhenDesktopStateExists);
        var dir = Directory.CreateDirectory(Path.Combine(Path.GetTempPath(), "desktop-migration-tests", Guid.NewGuid().ToString("N")));
        var legacyPath = Path.Combine(dir.FullName, "state.json");
        var desktopPath = Path.Combine(dir.FullName, "desktop-state.json");
        File.WriteAllText(legacyPath, "{}");
        File.WriteAllText(desktopPath, "{}");

        Check(false, DesktopStateMigrator.EnsureLegacyBackup(legacyPath, desktopPath), name + " migrated already");
        Check(false, File.Exists(Path.Combine(dir.FullName, DesktopStateMigrator.LegacyBackupFileName)), name + " no backup file");
    }

    private static void StoreRoundtripPreservesEvents()
    {
        var name = nameof(StoreRoundtripPreservesEvents);
        var path = Path.Combine(Path.GetTempPath(), "desktop-migration-tests", $"{Guid.NewGuid():N}.json");
        var store = new DesktopStateStore(path);
        var start = new DateTimeOffset(2026, 9, 29, 9, 0, 0, TimeSpan.Zero);
        var session = new SessionBoundary("session-1", DeskType.Ordinary, start)
        {
            EndedAtUtc = start.AddMinutes(20),
            EndReason = SessionBoundary.ReasonInferredAway
        };
        var snapshot = new DesktopStateSnapshot
        {
            Settings = DesktopSettings.Default with { Desk = DeskType.Ordinary },
            StartWithWindows = false,
            StartedTrackingAtUtc = start,
            SavedAtUtc = start.AddHours(1),
            Sessions = new List<SessionBoundary> { session },
            Intervals = new List<CommittedInterval>
            {
                new("session-1", start, start.AddMinutes(8), DeskType.Ordinary),
                new("session-1", start.AddMinutes(10), start.AddMinutes(18), DeskType.Ordinary)
            },
            Breaks = new List<InferredBreak>
            {
                new("break-1", "session-1", DeskType.Ordinary, start.AddMinutes(18), start.AddMinutes(20), "idle_timeout")
                {
                    ReturnedAtUtc = start.AddMinutes(25)
                }
            },
            Gaps = new List<CoverageGapRecord>
            {
                new(start.AddMinutes(30), "sleep") { ToUtc = start.AddMinutes(40) }
            },
            LastClassifierState = ClassifierState.Away
        };

        store.Save(snapshot);
        var loaded = store.Load() ?? throw new Exception(name + ": load returned null");

        Check(DesktopStateSnapshot.CurrentSchemaVersion, loaded.SchemaVersion, name + " schema version");
        Check(DesktopStateSnapshot.CurrentMigrationVersion, loaded.MigrationVersion, name + " migration version");
        Check(DeskType.Ordinary, loaded.Settings.Desk, name + " desk");
        Check(false, loaded.StartWithWindows, name + " start with windows");
        Check(1, loaded.Sessions.Count, name + " sessions");
        Check(SessionBoundary.ReasonInferredAway, loaded.Sessions[0].EndReason ?? "", name + " session end reason");
        Check(2, loaded.Intervals.Count, name + " intervals");
        Check(480L, loaded.Intervals[0].DurationSeconds, name + " interval seconds");
        Check(1, loaded.Breaks.Count, name + " breaks");
        Check(start.AddMinutes(25), loaded.Breaks[0].ReturnedAtUtc, name + " break returned");
        Check(1, loaded.Gaps.Count, name + " gaps");
        Check("sleep", loaded.Gaps[0].Reason, name + " gap reason");
        Check(ClassifierState.Away, loaded.LastClassifierState, name + " classifier state");
    }
}
