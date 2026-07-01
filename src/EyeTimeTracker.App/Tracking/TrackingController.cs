using System.Windows.Forms;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Tracking;

namespace EyeTimeTracker.App.Tracking;

public sealed class TrackingController : IDisposable
{
    private static readonly TimeSpan TickInterval = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan SaveInterval = TimeSpan.FromMinutes(1);

    private readonly object _gate = new();
    private readonly object _saveGate = new();
    private readonly JsonStateStore _stateStore;
    private readonly IdleTimeProvider _idleTimeProvider;
    private readonly AudioActivityProvider _audioActivityProvider;
    private readonly NotificationService _notificationService;
    private readonly DailyReminderPolicy _reminderPolicy;
    private readonly System.Threading.Timer _timer;

    private AppState _state;
    private EyeTimeAccumulator _accumulator;
    private DateTimeOffset _lastSaveAt;
    private long _nextSaveVersion;
    private long _lastSavedVersion;
    private int _tickInProgress;
    private bool _hasPendingImmediateSave;
    private bool _pendingReminderNotification;
    private bool _disposed;

    public TrackingController(NotificationService notificationService)
        : this(
            new JsonStateStore(AppPaths.StateFilePath),
            new IdleTimeProvider(),
            new AudioActivityProvider(),
            notificationService,
            new DailyReminderPolicy())
    {
    }

    public TrackingController(
        JsonStateStore stateStore,
        IdleTimeProvider idleTimeProvider,
        AudioActivityProvider audioActivityProvider,
        NotificationService notificationService,
        DailyReminderPolicy? reminderPolicy = null)
    {
        _stateStore = stateStore ?? throw new ArgumentNullException(nameof(stateStore));
        _idleTimeProvider = idleTimeProvider ?? throw new ArgumentNullException(nameof(idleTimeProvider));
        _audioActivityProvider = audioActivityProvider ?? throw new ArgumentNullException(nameof(audioActivityProvider));
        _notificationService = notificationService ?? throw new ArgumentNullException(nameof(notificationService));
        _reminderPolicy = reminderPolicy ?? new DailyReminderPolicy();

        _state = _stateStore.Load();
        var today = DateOnly.FromDateTime(DateTime.Now);
        var record = _state.GetOrCreateRecord(today);
        _accumulator = new EyeTimeAccumulator(today, record.TotalSeconds, record.ReminderShown, record.LastReminderStep);
        _lastSaveAt = DateTimeOffset.Now;

        _timer = new System.Threading.Timer(OnTimerTick, null, TimeSpan.Zero, TickInterval);
    }

    public event EventHandler<TrackingUpdatedEventArgs>? Updated;

    public TrackingUpdatedEventArgs Current
    {
        get
        {
            lock (_gate)
            {
                return CreateUpdateLocked(_accumulator.Today);
            }
        }
    }

    public TrackerSettings Settings
    {
        get
        {
            lock (_gate)
            {
                return _state.Settings;
            }
        }
        set
        {
            lock (_gate)
            {
                _state.Settings = value ?? throw new ArgumentNullException(nameof(value));
            }
        }
    }

    public IReadOnlyList<DailyRecord> GetRecordsSnapshot()
    {
        lock (_gate)
        {
            PersistAccumulatorLocked();
            return _state.Records
                .Select(record => new DailyRecord(record.Date)
                {
                    TotalSeconds = record.TotalSeconds,
                    ReminderShown = record.ReminderShown,
                    LastReminderStep = record.LastReminderStep
                })
                .ToList();
        }
    }

    public void SaveNow()
    {
        StateSaveSnapshot snapshot;
        var now = DateTimeOffset.Now;

        lock (_gate)
        {
            PersistAccumulatorLocked();
            snapshot = CreateSaveSnapshotLocked(now);
        }

        TrySaveSnapshot(snapshot);
    }

    public void Dispose()
    {
        StateSaveSnapshot snapshot;
        var now = DateTimeOffset.Now;

        lock (_gate)
        {
            if (_disposed)
            {
                return;
            }

            _disposed = true;
            _timer.Dispose();
            PersistAccumulatorLocked();
            snapshot = CreateSaveSnapshotLocked(now);
        }

        TrySaveSnapshot(snapshot);
    }

    private void OnTimerTick(object? state)
    {
        if (Interlocked.Exchange(ref _tickInProgress, 1) == 1)
        {
            return;
        }

        TrackingUpdatedEventArgs? update = null;
        StateSaveSnapshot? snapshotToSave = null;
        var shouldShowReminder = false;
        var saveIsImmediate = false;

        try
        {
            var now = DateTimeOffset.Now;
            var snapshot = new ActivitySnapshot(
                now,
                _idleTimeProvider.GetIdleTime(),
                _audioActivityProvider.IsAudioActive(),
                SystemInformation.UserInteractive,
                false);

            lock (_gate)
            {
                if (_disposed)
                {
                    return;
                }

                _accumulator.Tick(snapshot, _state.Settings);
                var record = PersistAccumulatorLocked();

                if (_reminderPolicy.ShouldNotify(record, _state.Settings))
                {
                    _reminderPolicy.MarkShown(record, _state.Settings);
                    _accumulator.Today.ReminderShown = true;
                    _accumulator.Today.LastReminderStep = record.LastReminderStep;
                    _hasPendingImmediateSave = true;
                    _pendingReminderNotification = true;
                }

                if (_hasPendingImmediateSave)
                {
                    snapshotToSave = CreateSaveSnapshotLocked(now);
                    shouldShowReminder = _pendingReminderNotification;
                    saveIsImmediate = true;
                }
                else if (now - _lastSaveAt >= SaveInterval)
                {
                    snapshotToSave = CreateSaveSnapshotLocked(now);
                }

                update = CreateUpdateLocked(record);
            }

            var saveSucceeded = TrySaveSnapshot(snapshotToSave);

            if (shouldShowReminder && saveSucceeded)
            {
                lock (_gate)
                {
                    _pendingReminderNotification = false;
                }

                TryShowDailyReminder();
            }

            if (saveIsImmediate && saveSucceeded)
            {
                lock (_gate)
                {
                    _hasPendingImmediateSave = false;
                }
            }
        }
        catch (Exception)
        {
            update = null;
        }
        finally
        {
            Interlocked.Exchange(ref _tickInProgress, 0);
        }

        if (update is not null)
        {
            RaiseUpdated(update);
        }
    }

    private DailyRecord PersistAccumulatorLocked()
    {
        var record = _state.GetOrCreateRecord(_accumulator.Today.Date);
        record.TotalSeconds = _accumulator.Today.TotalSeconds;
        record.ReminderShown = _accumulator.Today.ReminderShown;
        record.LastReminderStep = _accumulator.Today.LastReminderStep;
        return record;
    }

    private StateSaveSnapshot CreateSaveSnapshotLocked(DateTimeOffset savedAt)
    {
        return new StateSaveSnapshot(CloneStateLocked(), savedAt, ++_nextSaveVersion);
    }

    private AppState CloneStateLocked()
    {
        return new AppState
        {
            Settings = _state.Settings,
            Records = _state.Records
                .Select(record => new DailyRecord(record.Date)
                {
                    TotalSeconds = record.TotalSeconds,
                    ReminderShown = record.ReminderShown,
                    LastReminderStep = record.LastReminderStep
                })
                .ToList()
        };
    }

    private bool TrySaveSnapshot(StateSaveSnapshot? snapshot)
    {
        if (snapshot is null)
        {
            return false;
        }

        lock (_saveGate)
        {
            if (snapshot.Version <= _lastSavedVersion)
            {
                return true;
            }

            try
            {
                _stateStore.Save(snapshot.State);
            }
            catch (Exception)
            {
                return false;
            }

            _lastSavedVersion = snapshot.Version;

            lock (_gate)
            {
                _lastSaveAt = snapshot.SavedAt;
            }

            return true;
        }
    }

    private void TryShowDailyReminder()
    {
        try
        {
            _notificationService.ShowDailyReminder();
        }
        catch (Exception)
        {
        }
    }

    private TrackingUpdatedEventArgs CreateUpdateLocked(DailyRecord record)
    {
        return new TrackingUpdatedEventArgs(
            record.Date,
            record.TotalSeconds,
            record.ReminderShown,
            _accumulator.IsCounting);
    }

    private void RaiseUpdated(TrackingUpdatedEventArgs update)
    {
        var handler = Updated;
        if (handler is null)
        {
            return;
        }

        var callbacks = handler.GetInvocationList();
        ThreadPool.QueueUserWorkItem(static state =>
        {
            var (controller, args, invocationList) =
                ((TrackingController Controller, TrackingUpdatedEventArgs Args, Delegate[] InvocationList))state!;

            foreach (var callback in invocationList)
            {
                try
                {
                    ((EventHandler<TrackingUpdatedEventArgs>)callback)(controller, args);
                }
                catch (Exception)
                {
                }
            }
        }, (this, update, callbacks));
    }
}

public sealed record TrackingUpdatedEventArgs(
    DateOnly Date,
    long TotalSeconds,
    bool ReminderShown,
    bool IsCounting);

internal sealed record StateSaveSnapshot(AppState State, DateTimeOffset SavedAt, long Version);
