namespace EyeTimeTracker.Core.Models;

public sealed class AppState
{
    public TrackerSettings Settings { get; set; } = TrackerSettings.Default;
    public List<DailyRecord> Records { get; set; } = new();

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
}
