using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Tracking;

public sealed class EyeTimeAccumulator
{
    private static readonly TimeSpan MaxCountableElapsed = TimeSpan.FromMinutes(5);
    private static readonly TimeSpan MaxNormalTickElapsed = TimeSpan.FromSeconds(30);
    private DateTimeOffset? _lastTick;

    public EyeTimeAccumulator(DateOnly today, long initialSeconds = 0, bool reminderShown = false, int lastReminderStep = 0)
        : this(new DailyRecord(today)
        {
            TotalSeconds = initialSeconds,
            ReminderShown = reminderShown,
            LastReminderStep = lastReminderStep
        })
    {
    }

    public EyeTimeAccumulator(DailyRecord initialRecord)
    {
        AppState.NormalizeRecord(initialRecord);
        Today = new DailyRecord(initialRecord.Date)
        {
            TotalSeconds = initialRecord.TotalSeconds,
            HourlySeconds = (long[])initialRecord.HourlySeconds.Clone(),
            SessionSeconds = initialRecord.SessionSeconds.ToList(),
            CurrentSessionSeconds = initialRecord.CurrentSessionSeconds,
            ReminderShown = initialRecord.ReminderShown,
            LastReminderStep = initialRecord.LastReminderStep
        };
    }

    public DailyRecord Today { get; private set; }
    public bool IsCounting { get; private set; }

    public void Tick(ActivitySnapshot snapshot, TrackerSettings settings)
    {
        var snapshotDate = DateOnly.FromDateTime(snapshot.Timestamp.DateTime);
        if (snapshotDate != Today.Date)
        {
            FinishCurrentSession();
            Today = new DailyRecord(snapshotDate);
            _lastTick = snapshot.Timestamp;
            IsCounting = ShouldCount(snapshot, settings);
            return;
        }

        if (_lastTick is null)
        {
            _lastTick = snapshot.Timestamp;
            IsCounting = ShouldCount(snapshot, settings);
            return;
        }

        var elapsed = snapshot.Timestamp - _lastTick.Value;
        _lastTick = snapshot.Timestamp;

        if (elapsed <= TimeSpan.Zero || elapsed > MaxCountableElapsed)
        {
            IsCounting = ShouldCount(snapshot, settings);
            return;
        }

        var countedSeconds = CountableSeconds(snapshot, settings, elapsed);
        Today.TotalSeconds += countedSeconds;
        AddCountedSeconds(snapshot.Timestamp, countedSeconds);
        IsCounting = countedSeconds > 0 || ShouldCount(snapshot, settings);
        if (!IsCounting)
        {
            FinishCurrentSession();
        }
    }

    private void AddCountedSeconds(DateTimeOffset timestamp, long countedSeconds)
    {
        if (countedSeconds <= 0)
        {
            return;
        }

        var hour = Math.Clamp(timestamp.DateTime.Hour, 0, 23);
        Today.HourlySeconds[hour] += countedSeconds;
        Today.CurrentSessionSeconds += countedSeconds;
    }

    private void FinishCurrentSession()
    {
        if (Today.CurrentSessionSeconds <= 0)
        {
            return;
        }

        Today.SessionSeconds.Add(Today.CurrentSessionSeconds);
        Today.CurrentSessionSeconds = 0;
    }

    private static bool ShouldCount(ActivitySnapshot snapshot, TrackerSettings settings)
    {
        if (!snapshot.IsSessionUnlocked || snapshot.IsSuspended)
        {
            return false;
        }

        var hasRecentInput = snapshot.IdleTime.TotalSeconds <= settings.IdleThresholdSeconds;
        var hasAudio = settings.CountAudio && snapshot.IsAudioActive;
        return hasRecentInput || hasAudio;
    }

    private static long CountableSeconds(ActivitySnapshot snapshot, TrackerSettings settings, TimeSpan elapsed)
    {
        if (!snapshot.IsSessionUnlocked || snapshot.IsSuspended)
        {
            return 0;
        }

        var intervalEnd = snapshot.Timestamp;
        var intervalStart = intervalEnd - elapsed;

        var audioSeconds = settings.CountAudio && snapshot.IsAudioActive && elapsed <= MaxNormalTickElapsed
            ? elapsed.TotalSeconds
            : 0;

        var recentInputSeconds = snapshot.IdleTime.TotalSeconds <= settings.IdleThresholdSeconds && elapsed <= MaxNormalTickElapsed
            ? elapsed.TotalSeconds
            : 0;

        var lastInputAt = snapshot.Timestamp - snapshot.IdleTime;
        var activeUntil = lastInputAt + TimeSpan.FromSeconds(settings.IdleThresholdSeconds);
        var inputStart = Max(intervalStart, lastInputAt);
        var inputEnd = Min(intervalEnd, activeUntil);
        var inputSeconds = inputEnd > inputStart ? (inputEnd - inputStart).TotalSeconds : 0;

        return (long)Math.Floor(Math.Max(audioSeconds, Math.Max(recentInputSeconds, inputSeconds)));
    }

    private static DateTimeOffset Max(DateTimeOffset left, DateTimeOffset right)
    {
        return left >= right ? left : right;
    }

    private static DateTimeOffset Min(DateTimeOffset left, DateTimeOffset right)
    {
        return left <= right ? left : right;
    }
}
