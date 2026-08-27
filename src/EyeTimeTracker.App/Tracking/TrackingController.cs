using System.Windows.Forms;
using EyeTimeTracker.App.Diagnostics;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Sync;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Sync;
using EyeTimeTracker.Core.Tracking;

namespace EyeTimeTracker.App.Tracking;

public sealed class TrackingController : IDisposable
{
    private static readonly TimeSpan TickInterval = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan SaveInterval = TimeSpan.FromMinutes(1);
    private const int PeerOfflineAfterSeconds = 90;

    private readonly object _gate = new();
    private readonly object _saveGate = new();
    private readonly JsonStateStore _stateStore;
    private readonly IdleTimeProvider _idleTimeProvider;
    private readonly AudioActivityProvider _audioActivityProvider;
    private readonly ForegroundAppProvider _foregroundAppProvider;
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
    private int _pendingReminderStep;
    private int _lastContinuousReminderStep;
    private long _continuousReminderSessionStart;
    private bool _disposed;
    private long _statsDataVersion;
    private readonly Dictionary<DateOnly, CachedDailyStats> _dailyStatsCache = new();

    public TrackingController(NotificationService notificationService)
        : this(
            new JsonStateStore(AppPaths.StateFilePath),
            new IdleTimeProvider(),
            new AudioActivityProvider(),
            notificationService,
            null,
            new DailyReminderPolicy())
    {
    }

    public TrackingController(
        JsonStateStore stateStore,
        IdleTimeProvider idleTimeProvider,
        AudioActivityProvider audioActivityProvider,
        NotificationService notificationService,
        ForegroundAppProvider? foregroundAppProvider = null,
        DailyReminderPolicy? reminderPolicy = null)
    {
        _stateStore = stateStore ?? throw new ArgumentNullException(nameof(stateStore));
        _idleTimeProvider = idleTimeProvider ?? throw new ArgumentNullException(nameof(idleTimeProvider));
        _audioActivityProvider = audioActivityProvider ?? throw new ArgumentNullException(nameof(audioActivityProvider));
        _foregroundAppProvider = foregroundAppProvider ?? new ForegroundAppProvider();
        _notificationService = notificationService ?? throw new ArgumentNullException(nameof(notificationService));
        _reminderPolicy = reminderPolicy ?? new DailyReminderPolicy();

        _state = _stateStore.Load();
        var now = DateTimeOffset.Now;
        var today = DateOnly.FromDateTime(now.DateTime);
        _hasPendingImmediateSave = ContinuousSessionRecovery.FinalizeInterruptedSessions(
            _state.Records,
            today,
            _state.LastTrackingStateSavedUnixSeconds,
            now.ToUnixTimeSeconds());
        var record = _state.GetOrCreateRecord(today);
        _accumulator = new EyeTimeAccumulator(record);
        _lastContinuousReminderStep = _state.Settings.ContinuousReminderEnabled
            ? (int)Math.Max(0, record.CurrentSessionSeconds / Math.Max(60, _state.Settings.ContinuousReminderThresholdSeconds))
            : 0;
        _lastSaveAt = now;

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
                var nextSettings = value ?? throw new ArgumentNullException(nameof(value));
                var reminderChanged = _state.Settings.ReminderThresholdSeconds != nextSettings.ReminderThresholdSeconds
                    || _state.Settings.RepeatReminder != nextSettings.RepeatReminder
                    || _state.Settings.ContinuousReminderThresholdSeconds != nextSettings.ContinuousReminderThresholdSeconds
                    || _state.Settings.ContinuousReminderEnabled != nextSettings.ContinuousReminderEnabled
                    || !_state.Settings.ContinuousReminderExemptionPeriods.SequenceEqual(nextSettings.ContinuousReminderExemptionPeriods);
                _state.Settings = nextSettings;
                if (reminderChanged)
                {
                    var record = PersistAccumulatorLocked();
                    _reminderPolicy.AlignAfterSettingsChange(record, _state.Settings);
                    _accumulator.Today.ReminderShown = record.ReminderShown;
                    _accumulator.Today.LastReminderStep = record.LastReminderStep;
                }
            }
        }
    }

    public bool IsPaired
    {
        get
        {
            lock (_gate)
            {
                return _state.Sync.IsPaired;
            }
        }
    }

    public bool IsPeerOnline
    {
        get
        {
            lock (_gate)
            {
                return SyncPeerConnectionState.IsOnline(
                    _state.Sync,
                    DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                    PeerOfflineAfterSeconds);
            }
        }
    }

    public IReadOnlyList<DailyRecord> GetRecordsSnapshot()
    {
        lock (_gate)
        {
            PersistAccumulatorLocked();
            return CreateVisibleRecordsSnapshotLocked();
        }
    }

    public UsageDeviceBreakdown GetDeviceBreakdown(DateOnly date)
    {
        lock (_gate)
        {
            PersistAccumulatorLocked();
            return CloneBreakdown(GetDailyStatsSnapshotLocked(date).Breakdown);
        }
    }

    public IReadOnlyDictionary<DateOnly, DailyStatsSnapshot> GetDailyStatsSnapshots(IEnumerable<DateOnly> dates)
    {
        lock (_gate)
        {
            PersistAccumulatorLocked();
            return GetDailyStatsSnapshotsLocked(dates)
                .ToDictionary(
                    pair => pair.Key,
                    pair => new DailyStatsSnapshot(CloneRecord(pair.Value.Record), CloneBreakdown(pair.Value.Breakdown)));
        }
    }

    public IReadOnlyList<AppUsageEntry> GetAppUsageEntries(DateOnly start, DateOnly end)
    {
        lock (_gate)
        {
            PersistAccumulatorLocked();
            return (_state.AppUsageEntries ?? new List<AppUsageEntry>())
                .Where(entry => entry.LocalDate >= start && entry.LocalDate <= end && entry.DurationSeconds > 0)
                .Select(CloneAppUsageEntry)
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

    public PcSyncCoordinator CreateSyncCoordinator()
    {
        return new PcSyncCoordinator(LoadSyncStateSnapshot, SaveSyncStateSnapshot);
    }

    public void DisconnectSyncPeer()
    {
        StateSaveSnapshot snapshot;
        var now = DateTimeOffset.Now;

        lock (_gate)
        {
            _state.Sync = SyncSettings.Unpaired;
            snapshot = CreateSaveSnapshotLocked(now);
        }

        TrySaveSnapshot(snapshot);
        RaiseUpdated(Current);
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
        var shouldShowReminderStep = 0;
        var dailyReminderSessionSeconds = 0L;
        var dailyReminderThresholdSeconds = 0;
        ContinuousReminderRequest? continuousReminderRequest = null;
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

                var beforeDate = _accumulator.Today.Date;
                var beforeTotalSeconds = _accumulator.Today.TotalSeconds;
                var beforeSessionSeconds = _accumulator.Today.SessionSeconds.ToList();
                var beforeCurrentSessionSeconds = _accumulator.Today.CurrentSessionSeconds;
                _accumulator.Tick(snapshot, _state.Settings);

                if (_accumulator.Today.Date != beforeDate)
                {
                    // 跨午夜：accumulator 内部已 FinishCurrentSession 并切换到新一天
                    // 把前一天的最终 session 状态保存到 state.Records
                    var previousRecord = _state.GetOrCreateRecord(beforeDate);
                    previousRecord.SessionSeconds = beforeCurrentSessionSeconds > 0
                        ? beforeSessionSeconds.Append(beforeCurrentSessionSeconds).ToList()
                        : beforeSessionSeconds;
                    previousRecord.CurrentSessionSeconds = 0;
                    InvalidateStatsCacheLocked(beforeDate);
                }

                var countedSeconds = Math.Max(0, _accumulator.Today.TotalSeconds - beforeTotalSeconds);
                if (countedSeconds > 0)
                {
                    AddUsageSegmentLocked(snapshot, _state.Settings, countedSeconds);
                    AddAppUsageLocked(snapshot, countedSeconds);
                    InvalidateStatsCacheLocked(_accumulator.Today.Date);
                }

                var record = PersistAccumulatorLocked();
                _state.Sync.LocalReminderState = CreateLocalReminderStateLocked();

                var reminderRecord = CreateReminderRecordLocked(record.Date, record);
                if (_reminderPolicy.ShouldNotify(reminderRecord, _state.Settings))
                {
                    _reminderPolicy.MarkShown(reminderRecord, _state.Settings);
                    record.ReminderShown = true;
                    record.LastReminderStep = reminderRecord.LastReminderStep;
                    _accumulator.Today.ReminderShown = true;
                    _accumulator.Today.LastReminderStep = reminderRecord.LastReminderStep;
                    _hasPendingImmediateSave = true;
                    _pendingReminderNotification = ReminderDevicePolicy.ShouldPcShowReminder(
                        _state.Sync,
                        now.ToUnixTimeSeconds(),
                        PeerOfflineAfterSeconds);
                    _pendingReminderStep = _pendingReminderNotification ? record.LastReminderStep : 0;
                    dailyReminderSessionSeconds = record.CurrentSessionSeconds;
                    dailyReminderThresholdSeconds = _state.Settings.ReminderThresholdSeconds;
                }

                continuousReminderRequest = ShouldShowContinuousReminderLocked(record, now);

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
                    shouldShowReminderStep = _pendingReminderStep;
                    _pendingReminderStep = 0;
                }

                TryShowDailyReminder(shouldShowReminderStep);
                ReminderDiagnosticLog.Record(
                    "累计用眼提醒",
                    triggeredAt: DateTimeOffset.Now,
                    sessionSeconds: dailyReminderSessionSeconds,
                    thresholdSeconds: dailyReminderThresholdSeconds,
                    step: shouldShowReminderStep);
            }

            if (continuousReminderRequest is not null)
            {
                TryShowContinuousReminder(continuousReminderRequest);
            }

            if (saveIsImmediate && saveSucceeded)
            {
                lock (_gate)
                {
                    _hasPendingImmediateSave = false;
                }
            }
        }
        catch (Exception ex)
        {
            update = null;
            try
            {
                ReminderDiagnosticLog.RecordEvent("TimerTickError", DateTimeOffset.Now, null, ex.ToString());
            }
            catch
            {
                // 诊断日志不能干扰计时器
            }
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
        record.HourlySeconds = (long[])_accumulator.Today.HourlySeconds.Clone();
        record.SessionSeconds = _accumulator.Today.SessionSeconds.ToList();
        record.CurrentSessionSeconds = _accumulator.Today.CurrentSessionSeconds;
        record.ReminderShown = _accumulator.Today.ReminderShown;
        record.LastReminderStep = _accumulator.Today.LastReminderStep;
        return record;
    }

    private void AddUsageSegmentLocked(ActivitySnapshot snapshot, TrackerSettings settings, long countedSeconds)
    {
        var intervalEnd = snapshot.Timestamp;
        var intervalStart = intervalEnd.AddSeconds(-countedSeconds);
        var source = settings.CountAudio
            && snapshot.IsAudioActive
            && snapshot.IdleTime.TotalSeconds > settings.IdleThresholdSeconds
                ? "pc-media"
                : "pc-input";
        var canUseMutableSegments = _state.Sync.IsPaired && _state.Sync.PeerSupportsMutableSegments;
        var segment = canUseMutableSegments
            ? UsageSegmentFactory.CreateMutable(
                _state.DeviceId,
                _state.Platform,
                source,
                intervalStart,
                intervalEnd)
            : UsageSegmentFactory.Create(
                _state.DeviceId,
                _state.Platform,
                source,
                intervalStart,
                intervalEnd);

        if (canUseMutableSegments && UsageSegmentCompactor.TryExtendMutableSegment(_state.Segments, segment))
        {
            return;
        }

        _state.Segments.Add(segment);
    }

    private void AddAppUsageLocked(ActivitySnapshot snapshot, long countedSeconds)
    {
        ForegroundAppSnapshot? foreground;
        try
        {
            foreground = _foregroundAppProvider.GetCurrent();
        }
        catch (Exception)
        {
            foreground = null;
        }

        if (foreground is null || string.IsNullOrWhiteSpace(foreground.AppId))
        {
            return;
        }

        var date = DateOnly.FromDateTime(snapshot.Timestamp.LocalDateTime);
        var entryId = AppUsageEntryId.For(_state.DeviceId, _state.Platform, "pc", foreground.AppId, date);
        var entry = _state.AppUsageEntries.FirstOrDefault(existing => existing.EntryId == entryId);
        if (entry is null)
        {
            entry = new AppUsageEntry
            {
                EntryId = entryId,
                DeviceId = _state.DeviceId,
                Platform = _state.Platform,
                Source = "pc",
                AppId = foreground.AppId,
                AppName = foreground.AppName,
                IconData = string.Empty,
                LocalDate = date
            };
            _state.AppUsageEntries.Add(entry);
        }

        entry.AppName = string.IsNullOrWhiteSpace(foreground.AppName) ? entry.AppName : foreground.AppName;
        entry.IconData = string.Empty;
        entry.DurationSeconds += countedSeconds;
        entry.UpdatedAtUnixSeconds = snapshot.Timestamp.ToUnixTimeSeconds();
    }

    private StateSaveSnapshot CreateSaveSnapshotLocked(DateTimeOffset savedAt)
    {
        _state.LastTrackingStateSavedUnixSeconds = savedAt.ToUnixTimeSeconds();
        return new StateSaveSnapshot(CloneStateLocked(), savedAt, ++_nextSaveVersion);
    }

    private AppState LoadSyncStateSnapshot()
    {
        lock (_gate)
        {
            PersistAccumulatorLocked();
            _state.Sync.LocalReminderState = CreateLocalReminderStateLocked();
            return CloneStateLocked();
        }
    }

    private void SaveSyncStateSnapshot(AppState syncedState)
    {
        ArgumentNullException.ThrowIfNull(syncedState);
        StateSaveSnapshot snapshot;
        var now = DateTimeOffset.Now;

        lock (_gate)
        {
            AppState.Normalize(syncedState);
            _state.Settings = syncedState.Settings;
            _state.Segments = syncedState.Segments
                .Select(CloneSegment)
                .ToList();
            _state.AppUsageEntries = syncedState.AppUsageEntries
                .Select(CloneAppUsageEntry)
                .ToList();
            _state.Sync = CloneSyncSettings(syncedState.Sync);
            _state.Sync.LocalReminderState = CreateLocalReminderStateLocked();
            InvalidateStatsCacheLocked();
            snapshot = CreateSaveSnapshotLocked(now);
        }

        TrySaveSnapshot(snapshot);
        RaiseUpdated(Current);
    }

    private AppState CloneStateLocked()
    {
        return new AppState
        {
            DeviceId = _state.DeviceId,
            Platform = _state.Platform,
            StartWithWindowsDefaultApplied = _state.StartWithWindowsDefaultApplied,
            LastTrackingStateSavedUnixSeconds = _state.LastTrackingStateSavedUnixSeconds,
            Settings = _state.Settings,
            Records = _state.Records
                .Select(CloneRecord)
                .ToList(),
            Segments = _state.Segments
                .Select(CloneSegment)
                .ToList(),
            AppUsageEntries = _state.AppUsageEntries
                .Select(CloneAppUsageEntry)
                .ToList(),
            Sync = CloneSyncSettings(_state.Sync)
        };
    }

    private DailyRecord CreateReminderRecordLocked(DateOnly date, DailyRecord fallback)
    {
        var visible = CreateVisibleRecordsSnapshotLocked()
            .FirstOrDefault(record => record.Date == date);
        if (visible is null)
        {
            visible = CloneRecord(fallback);
        }

        visible.ReminderShown = fallback.ReminderShown;
        visible.LastReminderStep = fallback.LastReminderStep;
        return visible;
    }

    private List<DailyRecord> CreateVisibleRecordsSnapshotLocked()
    {
        var dates = _state.Records
            .Where(record => record.Date != default)
            .Select(record => record.Date)
            .ToHashSet();
        foreach (var segment in _state.Segments)
        {
            foreach (var date in SegmentDates(segment))
            {
                dates.Add(date);
            }
        }

        return GetDailyStatsSnapshotsLocked(dates)
            .Values
            .Select(snapshot => CloneRecord(snapshot.Record))
            .OrderBy(record => record.Date)
            .ToList();
    }

    private Dictionary<DateOnly, DailyStatsSnapshot> GetDailyStatsSnapshotsLocked(IEnumerable<DateOnly> dates)
    {
        var requestedDates = (dates ?? Array.Empty<DateOnly>())
            .Where(date => date != default)
            .Distinct()
            .ToList();
        var result = new Dictionary<DateOnly, DailyStatsSnapshot>();
        var missingDates = new List<DateOnly>();

        foreach (var date in requestedDates)
        {
            if (_dailyStatsCache.TryGetValue(date, out var cached) && cached.Version == _statsDataVersion)
            {
                result[date] = new DailyStatsSnapshot(CloneRecord(cached.Record), CloneBreakdown(cached.Breakdown));
            }
            else
            {
                missingDates.Add(date);
            }
        }

        if (missingDates.Count == 0)
        {
            return result;
        }

        var effectiveSegments = CreateEffectiveSegmentsLocked();
        var segmentsByDate = IndexSegmentsByDate(effectiveSegments, missingDates);
        foreach (var date in missingDates)
        {
            var snapshot = BuildDailyStatsSnapshotLocked(
                date,
                segmentsByDate.TryGetValue(date, out var segments) ? segments : Array.Empty<UsageSegment>());
            _dailyStatsCache[date] = new CachedDailyStats(_statsDataVersion, CloneRecord(snapshot.Record), CloneBreakdown(snapshot.Breakdown));
            result[date] = snapshot;
        }

        return result;
    }

    private DailyStatsSnapshot GetDailyStatsSnapshotLocked(DateOnly date)
    {
        return GetDailyStatsSnapshotsLocked(new[] { date })[date];
    }

    private DailyStatsSnapshot BuildDailyStatsSnapshotLocked(DateOnly date, IReadOnlyList<UsageSegment> dateSegments)
    {
        var existing = _state.Records.FirstOrDefault(record => record.Date == date);
        var record = dateSegments.Count > 0
            ? DailyRecordReconciler.UseSegmentRecordForSyncedDay(existing, UsageSegmentMerger.BuildDailyRecord(date, dateSegments))
            : existing is null ? new DailyRecord(date) : CloneRecord(existing);
        var breakdown = UsageDeviceBreakdown.Build(date, dateSegments);
        return new DailyStatsSnapshot(record, breakdown);
    }

    private static Dictionary<DateOnly, List<UsageSegment>> IndexSegmentsByDate(IEnumerable<UsageSegment> segments, IEnumerable<DateOnly> dates)
    {
        var result = dates
            .Where(date => date != default)
            .Distinct()
            .ToDictionary(date => date, _ => new List<UsageSegment>());
        foreach (var segment in segments)
        {
            foreach (var date in SegmentDates(segment))
            {
                if (result.TryGetValue(date, out var list))
                {
                    list.Add(segment);
                }
            }
        }

        return result;
    }

    private static IEnumerable<DateOnly> SegmentDates(UsageSegment segment)
    {
        if (segment.EndUnixSeconds <= segment.StartUnixSeconds)
        {
            yield break;
        }

        var startDate = DateOnly.FromDateTime(DateTimeOffset.FromUnixTimeSeconds(segment.StartUnixSeconds).LocalDateTime);
        var endDate = DateOnly.FromDateTime(DateTimeOffset.FromUnixTimeSeconds(segment.EndUnixSeconds - 1).LocalDateTime);
        for (var date = startDate; date <= endDate; date = date.AddDays(1))
        {
            yield return date;
        }
    }

    private void InvalidateStatsCacheLocked(DateOnly? date = null)
    {
        if (date is null)
        {
            _statsDataVersion++;
            _dailyStatsCache.Clear();
            return;
        }

        _dailyStatsCache.Remove(date.Value);
    }

    private List<UsageSegment> CreateEffectiveSegmentsLocked()
    {
        return LegacyUsageSegments.NormalizeEffectiveSegments(
            _state.Segments,
            _state.Records,
            _state.DeviceId,
            _state.Platform);
    }

    private static DailyRecord CloneRecord(DailyRecord record)
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

    private static UsageSegment CloneSegment(UsageSegment segment)
    {
        return new UsageSegment
        {
            SegmentId = segment.SegmentId,
            DeviceId = segment.DeviceId,
            Platform = segment.Platform,
            Source = segment.Source,
            StartUnixSeconds = segment.StartUnixSeconds,
            EndUnixSeconds = segment.EndUnixSeconds,
            LocalDate = segment.LocalDate,
            CreatedAtUnixSeconds = segment.CreatedAtUnixSeconds,
            UpdatedAtUnixSeconds = segment.UpdatedAtUnixSeconds
        };
    }

    private static UsageDeviceBreakdown CloneBreakdown(UsageDeviceBreakdown breakdown)
    {
        return new UsageDeviceBreakdown(
            breakdown.PcSeconds,
            breakdown.PhoneSeconds,
            (long[])breakdown.PcHourlySeconds.Clone(),
            (long[])breakdown.PhoneHourlySeconds.Clone());
    }

    private static AppUsageEntry CloneAppUsageEntry(AppUsageEntry entry)
    {
        return new AppUsageEntry
        {
            EntryId = entry.EntryId,
            DeviceId = entry.DeviceId,
            Platform = entry.Platform,
            Source = entry.Source,
            AppId = entry.AppId,
            AppName = entry.AppName,
            IconData = string.Empty,
            LocalDate = entry.LocalDate,
            DurationSeconds = entry.DurationSeconds,
            UpdatedAtUnixSeconds = entry.UpdatedAtUnixSeconds
        };
    }

    private static SyncSettings CloneSyncSettings(SyncSettings sync)
    {
        return new SyncSettings
        {
            IsPaired = sync.IsPaired,
            PeerDeviceId = sync.PeerDeviceId,
            PeerPlatform = sync.PeerPlatform,
            SharedSecret = sync.SharedSecret,
            LastKnownHost = sync.LastKnownHost,
            LastKnownPort = sync.LastKnownPort,
            LastSyncUnixSeconds = sync.LastSyncUnixSeconds,
            LastError = sync.LastError,
            PeerSupportsMutableSegments = sync.PeerSupportsMutableSegments,
            LocalReminderState = CloneReminderState(sync.LocalReminderState),
            PeerReminderState = CloneReminderState(sync.PeerReminderState)
        };
    }

    private ReminderRuntimeState CreateLocalReminderStateLocked()
    {
        var previous = _state.Sync.LocalReminderState;
        return new ReminderRuntimeState
        {
            DeviceId = _state.DeviceId,
            Platform = _state.Platform,
            IsCounting = _accumulator.IsCounting,
            CurrentSessionStartedUnixSeconds = _accumulator.CurrentSessionStartedAt?.ToUnixTimeSeconds() ?? 0,
            ContinuousClaimSessionStartedUnixSeconds = previous?.ContinuousClaimSessionStartedUnixSeconds ?? 0,
            ContinuousClaimLastStep = previous?.ContinuousClaimLastStep ?? 0
        };
    }

    private static ReminderRuntimeState CloneReminderState(ReminderRuntimeState? state)
    {
        if (state is null)
        {
            return new ReminderRuntimeState();
        }

        return new ReminderRuntimeState
        {
            DeviceId = state.DeviceId,
            Platform = state.Platform,
            IsCounting = state.IsCounting,
            CurrentSessionStartedUnixSeconds = state.CurrentSessionStartedUnixSeconds,
            ContinuousClaimSessionStartedUnixSeconds = state.ContinuousClaimSessionStartedUnixSeconds,
            ContinuousClaimLastStep = state.ContinuousClaimLastStep
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

    private void TryShowDailyReminder(int reminderStep)
    {
        try
        {
            _notificationService.ShowDailyReminder(Settings, reminderStep);
        }
        catch (Exception)
        {
        }
    }

    private ContinuousReminderRequest? ShouldShowContinuousReminderLocked(DailyRecord record, DateTimeOffset now)
    {
        if (!_accumulator.IsCounting || !_state.Settings.ContinuousReminderEnabled)
        {
            _lastContinuousReminderStep = 0;
            _continuousReminderSessionStart = 0;
            return null;
        }

        var localState = CreateLocalReminderStateLocked();
        var peerOnline = SyncPeerConnectionState.IsOnline(
            _state.Sync,
            now.ToUnixTimeSeconds(),
            PeerOfflineAfterSeconds);
        var sessionStarted = ContinuousReminderBaseline.Resolve(
            localState,
            _state.Sync.PeerReminderState,
            peerOnline,
            now.ToUnixTimeSeconds());
        if (sessionStarted <= 0)
        {
            _lastContinuousReminderStep = 0;
            _continuousReminderSessionStart = 0;
            return null;
        }

        if (_continuousReminderSessionStart == 0)
        {
            _continuousReminderSessionStart = sessionStarted;
        }
        else if (sessionStarted > _continuousReminderSessionStart)
        {
            // A later start means a new local/shared episode. Reset its reminder steps.
            _lastContinuousReminderStep = 0;
            _continuousReminderSessionStart = sessionStarted;
        }
        else if (sessionStarted < _continuousReminderSessionStart)
        {
            // A peer may reveal an earlier start for the same episode. Keep the steps
            // already shown so the earlier baseline cannot cause a duplicate alert.
            _continuousReminderSessionStart = sessionStarted;
        }

        // 合并双端已提醒次数：同一段共享会话里，任何一端弹过的次数另一端不再重复弹
        var localClaim = _state.Sync.LocalReminderState;
        if (localClaim is not null
            && localClaim.ContinuousClaimSessionStartedUnixSeconds == sessionStarted)
        {
            _lastContinuousReminderStep = Math.Max(_lastContinuousReminderStep, localClaim.ContinuousClaimLastStep);
        }

        var peerState = _state.Sync.PeerReminderState;
        if (peerState is not null
            && peerState.ContinuousClaimSessionStartedUnixSeconds == sessionStarted)
        {
            _lastContinuousReminderStep = Math.Max(_lastContinuousReminderStep, peerState.ContinuousClaimLastStep);
        }

        var thresholdSeconds = Math.Max(60, _state.Settings.ContinuousReminderThresholdSeconds);
        var sessionSeconds = Math.Max(0, now.ToUnixTimeSeconds() - sessionStarted);
        var step = (int)Math.Max(0, sessionSeconds / thresholdSeconds);
        if (step <= 0 || step <= _lastContinuousReminderStep
            || ContinuousReminderExemptionPolicy.IsExempt(now, _state.Settings.ContinuousReminderExemptionPeriods))
        {
            return null;
        }

        _lastContinuousReminderStep = step;
        var claimState = _state.Sync.LocalReminderState ??= new ReminderRuntimeState();
        claimState.ContinuousClaimSessionStartedUnixSeconds = sessionStarted;
        claimState.ContinuousClaimLastStep = step;
        _hasPendingImmediateSave = true;
        var requestId = Guid.NewGuid().ToString("N")[..12];
        ReminderDiagnosticLog.Record(
            "连续用眼提醒",
            now,
            sessionSeconds,
            thresholdSeconds,
            step);
        ReminderDiagnosticLog.RecordEvent(
            "连续用眼提醒/达到阈值",
            now,
            requestId,
            $"连续={sessionSeconds}秒;阈值={thresholdSeconds}秒;第{step}次");
        return new ContinuousReminderRequest(requestId, sessionSeconds, thresholdSeconds, step);
    }

    private void TryShowContinuousReminder(ContinuousReminderRequest request)
    {
        try
        {
            ReminderDiagnosticLog.RecordEvent(
                "连续用眼提醒/请求显示",
                DateTimeOffset.Now,
                request.RequestId);
            _notificationService.ShowContinuousReminder(request.RequestId, request.ThresholdSeconds);
        }
        catch (Exception exception)
        {
            ReminderDiagnosticLog.RecordEvent(
                "连续用眼提醒/请求显示失败",
                DateTimeOffset.Now,
                request.RequestId,
                $"{exception.GetType().Name}: {exception.Message}");
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

public sealed record DailyStatsSnapshot(DailyRecord Record, UsageDeviceBreakdown Breakdown);

internal sealed record CachedDailyStats(long Version, DailyRecord Record, UsageDeviceBreakdown Breakdown);

internal sealed record StateSaveSnapshot(AppState State, DateTimeOffset SavedAt, long Version);

internal sealed record ContinuousReminderRequest(
    string RequestId,
    long SessionSeconds,
    int ThresholdSeconds,
    int Step);
