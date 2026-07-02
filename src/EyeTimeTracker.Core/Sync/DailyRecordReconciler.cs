using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class DailyRecordReconciler
{
    public static DailyRecord UseSegmentRecordForSyncedDay(DailyRecord? legacy, DailyRecord segmented)
    {
        ArgumentNullException.ThrowIfNull(segmented);
        return Clone(segmented);
    }

    private static DailyRecord Clone(DailyRecord record)
    {
        AppState.NormalizeRecord(record);
        return new DailyRecord(record.Date)
        {
            TotalSeconds = record.TotalSeconds,
            HourlySeconds = (long[])record.HourlySeconds.Clone(),
            SessionSeconds = record.SessionSeconds.ToList(),
            CurrentSessionSeconds = record.CurrentSessionSeconds,
            ReminderShown = record.ReminderShown,
            LastReminderStep = record.LastReminderStep
        };
    }
}
