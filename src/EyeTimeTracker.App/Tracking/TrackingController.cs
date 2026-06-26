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
    private readonly JsonStateStore _stateStore;
    private readonly IdleTimeProvider _idleTimeProvider;
    private readonly AudioActivityProvider _audioActivityProvider;
    private readonly NotificationService _notificationService;
    private readonly DailyReminderPolicy _reminderPolicy;
    private readonly System.Threading.Timer _timer;

    private AppState _state;
    private EyeTimeAccumulator _accumulator;
    private DateTimeOffset _lastSaveAt;
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
        _accumulator = new EyeTimeAccumulator(today, record.TotalSeconds, record.ReminderShown);
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

    public void SaveNow()
    {
        lock (_gate)
        {
            PersistAccumulatorLocked();
            SaveLocked(DateTimeOffset.Now);
        }
    }

    public void Dispose()
    {
        lock (_gate)
        {
            if (_disposed)
            {
                return;
            }

            _disposed = true;
            _timer.Dispose();
            PersistAccumulatorLocked();
            SaveLocked(DateTimeOffset.Now);
        }
    }

    private void OnTimerTick(object? state)
    {
        TrackingUpdatedEventArgs? update = null;

        if (!Monitor.TryEnter(_gate))
        {
            return;
        }

        try
        {
            if (_disposed)
            {
                return;
            }

            var now = DateTimeOffset.Now;
            var snapshot = new ActivitySnapshot(
                now,
                _idleTimeProvider.GetIdleTime(),
                _audioActivityProvider.IsAudioActive(),
                SystemInformation.UserInteractive,
                false);

            _accumulator.Tick(snapshot, _state.Settings);
            var record = PersistAccumulatorLocked();

            if (_reminderPolicy.ShouldNotify(record, _state.Settings))
            {
                _notificationService.ShowDailyReminder();
                _reminderPolicy.MarkShown(record);
                _accumulator.Today.ReminderShown = true;
            }

            if (now - _lastSaveAt >= SaveInterval)
            {
                SaveLocked(now);
            }

            update = CreateUpdateLocked(record);
        }
        catch (Exception)
        {
            update = null;
        }
        finally
        {
            Monitor.Exit(_gate);
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
        return record;
    }

    private void SaveLocked(DateTimeOffset savedAt)
    {
        _stateStore.Save(_state);
        _lastSaveAt = savedAt;
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
