using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Tracking;

public static class ContinuousSessionRecovery
{
    public const long ResumeGraceSeconds = 180;

    public static bool FinalizeInterruptedSessions(
        IEnumerable<DailyRecord> records,
        DateOnly resumedDate,
        long lastStateSavedUnixSeconds,
        long resumedAtUnixSeconds)
    {
        ArgumentNullException.ThrowIfNull(records);

        var canResumeToday = CanResumeCurrentSession(lastStateSavedUnixSeconds, resumedAtUnixSeconds);
        var changed = false;

        foreach (var record in records)
        {
            AppState.NormalizeRecord(record);
            if (record.CurrentSessionSeconds <= 0
                || (record.Date == resumedDate && canResumeToday))
            {
                continue;
            }

            record.SessionSeconds.Add(record.CurrentSessionSeconds);
            record.CurrentSessionSeconds = 0;
            changed = true;
        }

        return changed;
    }

    public static bool CanResumeCurrentSession(long lastStateSavedUnixSeconds, long resumedAtUnixSeconds)
    {
        return lastStateSavedUnixSeconds > 0
            && resumedAtUnixSeconds >= lastStateSavedUnixSeconds
            && resumedAtUnixSeconds - lastStateSavedUnixSeconds <= ResumeGraceSeconds;
    }
}
