namespace EyeTimeTracker.Core.Models;

public sealed class DailyRecord
{
    public DateOnly Date { get; set; }
    public long TotalSeconds { get; set; }
    public bool ReminderShown { get; set; }
    public int LastReminderStep { get; set; }

    public DailyRecord()
    {
    }

    public DailyRecord(DateOnly date)
    {
        Date = date;
    }
}
