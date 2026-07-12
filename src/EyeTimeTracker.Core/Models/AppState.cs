namespace EyeTimeTracker.Core.Models;

public sealed class AppState
{
    public string DeviceId { get; set; } = Guid.NewGuid().ToString("N");
    public string Platform { get; set; } = "windows";
    public bool StartWithWindowsDefaultApplied { get; set; }
    public TrackerSettings Settings { get; set; } = TrackerSettings.Default;
    public List<DailyRecord> Records { get; set; } = new();
    public List<UsageSegment> Segments { get; set; } = new();
    public List<AppUsageEntry> AppUsageEntries { get; set; } = new();
    public SyncSettings Sync { get; set; } = SyncSettings.Unpaired;

    public DailyRecord GetOrCreateRecord(DateOnly date)
    {
        var existing = Records.FirstOrDefault(record => record.Date == date);
        if (existing is not null)
        {
            NormalizeRecord(existing);
            return existing;
        }

        var record = new DailyRecord(date);
        Records.Add(record);
        return record;
    }

    public static void NormalizeRecord(DailyRecord record)
    {
        if (record.HourlySeconds is null || record.HourlySeconds.Length != 24)
        {
            var normalized = new long[24];
            if (record.HourlySeconds is not null)
            {
                Array.Copy(record.HourlySeconds, normalized, Math.Min(24, record.HourlySeconds.Length));
            }

            record.HourlySeconds = normalized;
        }

        record.SessionSeconds ??= new List<long>();
        if (record.CurrentSessionSeconds < 0)
        {
            record.CurrentSessionSeconds = 0;
        }
    }

    public static void Normalize(AppState state)
    {
        state.Settings ??= TrackerSettings.Default;
        if (!state.StartWithWindowsDefaultApplied)
        {
            state.Settings = state.Settings with { StartWithWindows = true };
            state.StartWithWindowsDefaultApplied = true;
        }

        state.Records ??= new List<DailyRecord>();
        state.Segments ??= new List<UsageSegment>();
        state.AppUsageEntries ??= new List<AppUsageEntry>();
        state.Sync ??= SyncSettings.Unpaired;

        if (string.IsNullOrWhiteSpace(state.DeviceId))
        {
            state.DeviceId = Guid.NewGuid().ToString("N");
        }

        if (string.IsNullOrWhiteSpace(state.Platform))
        {
            state.Platform = "windows";
        }

        foreach (var record in state.Records)
        {
            NormalizeRecord(record);
        }

        foreach (var entry in state.AppUsageEntries)
        {
            entry.EntryId = AppUsageEntryId.For(
                entry.DeviceId,
                entry.Platform,
                entry.Source,
                entry.AppId,
                entry.LocalDate);
            if (entry.DurationSeconds < 0)
            {
                entry.DurationSeconds = 0;
            }
        }
    }
}

public static class AppUsageEntryId
{
    public static string For(string deviceId, string platform, string source, string appId, DateOnly date)
    {
        return string.Join(
            ":",
            "app",
            Safe(deviceId),
            Safe(platform),
            Safe(source),
            Safe(appId),
            date == default ? string.Empty : date.ToString("yyyy-MM-dd"));
    }

    private static string Safe(string? value)
    {
        return string.IsNullOrWhiteSpace(value) ? "_" : value.Trim().ToLowerInvariant();
    }
}
