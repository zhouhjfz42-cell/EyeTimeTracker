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
            return existing;
        }

        var record = new DailyRecord(date);
        Records.Add(record);
        return record;
    }
}
