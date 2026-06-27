using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Tracking;

public sealed class EyeTimeAccumulator
{
    private static readonly TimeSpan MaxCountableElapsed = TimeSpan.FromMinutes(5);
    private static readonly TimeSpan MaxNormalTickElapsed = TimeSpan.FromSeconds(30);
    private DateTimeOffset? _lastTick;

    public EyeTimeAccumulator(DateOnly today, long initialSeconds = 0, bool reminderShown = false)
    {
        Today = new DailyRecord(today)
        {
            TotalSeconds = initialSeconds,
            ReminderShown = reminderShown
        };
    }

    public DailyRecord Today { get; private set; }
    public bool IsCounting { get; private set; }

    public void Tick(ActivitySnapshot snapshot, TrackerSettings settings)
    {
        var snapshotDate = DateOnly.FromDateTime(snapshot.Timestamp.Date);
        if (snapshotDate != Today.Date)
        {
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
        IsCounting = countedSeconds > 0 || ShouldCount(snapshot, settings);
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
