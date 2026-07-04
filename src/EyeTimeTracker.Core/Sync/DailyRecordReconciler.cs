using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Sync;

public static class DailyRecordReconciler
{
    public static DailyRecord UseSegmentRecordForSyncedDay(DailyRecord? legacy, DailyRecord segmented)
    {
        ArgumentNullException.ThrowIfNull(segmented);
        var result = Clone(segmented);
        if (legacy is not null)
        {
            AppState.NormalizeRecord(legacy);
            result.ReminderShown = legacy.ReminderShown;
            result.LastReminderStep = legacy.LastReminderStep;
        }

        return result;
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
