using System.Windows.Forms;
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.Core.DesktopActivity;
using EyeTimeTracker.Core.DesktopReminders;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Sync;
using Microsoft.Win32;

namespace EyeTimeTracker.App.Tracking;

/// <summary>
/// 新版桌面采集控制器：真实平台信号 → ActivityClassifier → 版本化存储。
/// 只按输入信号计时（音频不再单独维持桌面活动），本机独立运行，不依赖手机共享会话。
/// 对旧页面保留兼容数据面（records / stats / settings），页面在 P0-4 重写。
/// 提醒调度在 P0-3 接入，本控制器不触发任何提醒。
/// </summary>
public sealed class DesktopTrackingController : IDisposable
{
    private static readonly TimeSpan SaveInterval = TimeSpan.FromMinutes(1);

    private readonly object _gate = new();
    private readonly IdleTimeProvider _idleTimeProvider;
    private readonly DesktopStateStore _store;
    private readonly System.Threading.Timer _timer;
    private ActivityClassifier _classifier = null!;
    private DesktopSettings _settings = DesktopSettings.Default;
    private bool _hasCompletedOnboarding;
    private ReminderScheduler _scheduler = null!;
    private ReminderSchedulerState _reminderState = new();
    private readonly List<ReminderInstance> _reminderInstances = new();
    private readonly DesktopReminderDispatcher? _reminderDispatcher;
    private string _deviceId = "desktop-local";
    private bool _startWithWindows = true;
    private DateTimeOffset _lastReportedInputAtUtc = DateTimeOffset.MinValue;
    private DateTimeOffset _lastSaveAtUtc = DateTimeOffset.MinValue;
    private bool _lastInteractive = true;
    private int _tickInProgress;
    private bool _disposed;

    public DesktopTrackingController(DesktopReminderDispatcher? reminderDispatcher = null)
        : this(new IdleTimeProvider(), new DesktopStateStore(AppPaths.DesktopStateFilePath), reminderDispatcher)
    {
    }

    public DesktopTrackingController(
        IdleTimeProvider idleTimeProvider,
        DesktopStateStore store,
        DesktopReminderDispatcher? reminderDispatcher = null)
    {
        _idleTimeProvider = idleTimeProvider ?? throw new ArgumentNullException(nameof(idleTimeProvider));
        _store = store ?? throw new ArgumentNullException(nameof(store));
        _reminderDispatcher = reminderDispatcher;

        DesktopStateMigrator.EnsureLegacyBackup(AppPaths.StateFilePath, AppPaths.DesktopStateFilePath);

        var snapshot = _store.Load();
        _settings = snapshot?.Settings ?? DesktopSettings.Default;
        _startWithWindows = snapshot?.StartWithWindows ?? true;
        _hasCompletedOnboarding = snapshot?.HasCompletedOnboarding ?? false;
        _classifier = new ActivityClassifier(_settings);
        if (snapshot is not null)
        {
            _classifier.Restore(
                snapshot.Settings.Desk,
                snapshot.Intervals,
                snapshot.Breaks,
                snapshot.Gaps,
                snapshot.Sessions);
            // 重启后单调时钟不可续用：上次仍在进行的会话按 unknown gap 结束，不补计
            if (snapshot.LastClassifierState is ClassifierState.Active or ClassifierState.IdleCandidate or ClassifierState.LockAbsence
                && snapshot.SavedAtUtc > DateTimeOffset.MinValue)
            {
                _classifier.Apply(new ActivitySignal(ActivitySignalKind.ObservationLost, snapshot.SavedAtUtc, 0L));
            }

            _reminderState = snapshot.ReminderState ?? new ReminderSchedulerState();
            _reminderInstances.AddRange(snapshot.ReminderInstances);
            _deviceId = string.IsNullOrWhiteSpace(snapshot.DeviceId)
                ? _deviceId
                : snapshot.DeviceId;
            // 崩溃恢复：Dispatching 中的实例记为投递失败（中断），不重复播音
            if (ReminderScheduler.MarkInterruptedDispatches(_reminderInstances, DateTimeOffset.UtcNow) > 0)
            {
                SaveSnapshotLocked();
            }
        }

        _scheduler = new ReminderScheduler(_deviceId);

        _lastInteractive = SystemInformation.UserInteractive;
        // 启动前最后一次输入不生成信号：Unknown 起步，不补计关闭程序期间时间（§5.2）
        _lastReportedInputAtUtc = DateTimeOffset.UtcNow - _idleTimeProvider.GetIdleTime();
        SystemEvents.SessionSwitch += OnSessionSwitch;
        SystemEvents.PowerModeChanged += OnPowerModeChanged;
        _timer = new System.Threading.Timer(OnTimerTick, null, TimeSpan.Zero, TimeSpan.FromSeconds(_settings.ActivitySamplingSeconds));
    }

    public event EventHandler<TrackingUpdatedEventArgs>? Updated;

    public TrackingUpdatedEventArgs Current
    {
        get
        {
            lock (_gate)
            {
                return CreateUpdateLocked();
            }
        }
    }

    public bool IsPaired => false;
    public bool IsPeerOnline => false;
    public ClassifierState State => _classifier.State;
    public DesktopSettings DesktopSettings => _settings;
    public bool HasCompletedOnboarding => _hasCompletedOnboarding;

    public List<ReminderInstance> GetReminderInstancesSnapshot()
    {
        lock (_gate)
        {
            return _reminderInstances.ToList();
        }
    }

    public (long SessionActiveSeconds, DateTimeOffset? SessionStartedAtUtc, DateTimeOffset? ProvisionalTailStartUtc) CurrentSessionInfo()
    {
        lock (_gate)
        {
            var session = _classifier.CurrentSession;
            if (session is null)
            {
                return (0L, null, null);
            }

            var seconds = SessionAnalyzer.CommittedSeconds(
                _classifier.Intervals.Where(interval => interval.SessionId == session.SessionId));
            return (seconds, session.StartedAtUtc, _classifier.ProvisionalTailStartUtc);
        }
    }

    public ResponseEvaluation GetResponseRateSnapshot(DateOnly date)
    {
        lock (_gate)
        {
            var metrics = DailyAggregator.Aggregate(
                date,
                _classifier.Intervals.ToList(),
                _classifier.Sessions.ToList(),
                _classifier.Breaks.ToList(),
                DeliveredMovementOpportunitiesLocked(),
                _classifier.Gaps.ToList(),
                DateTimeOffset.UtcNow,
                TimeZoneInfo.Local);
            return metrics.ResponseRate;
        }
    }

    /// <summary>按当地日期聚合的每日指标（口径 §7），供记录页与主页今日概览使用。</summary>
    public DailyMetrics GetDailyMetrics(DateOnly date)
    {
        lock (_gate)
        {
            return DailyAggregator.Aggregate(
                date,
                _classifier.Intervals.ToList(),
                _classifier.Sessions.ToList(),
                _classifier.Breaks.ToList(),
                DeliveredMovementOpportunitiesLocked(),
                _classifier.Gaps.ToList(),
                DateTimeOffset.UtcNow,
                TimeZoneInfo.Local);
        }
    }

    /// <summary>页面只读明细快照：committed 区间、会话、中断事件、不可观察缺口。</summary>
    public (List<CommittedInterval> Intervals, List<SessionBoundary> Sessions, List<InferredBreak> Breaks, List<CoverageGapRecord> Gaps) GetDayDetailSnapshot()
    {
        lock (_gate)
        {
            return (
                _classifier.Intervals.ToList(),
                _classifier.Sessions.ToList(),
                _classifier.Breaks.ToList(),
                _classifier.Gaps.ToList());
        }
    }

    /// <summary>
    /// 更新桌面提醒设置（提醒页保存）。间隔或开关变化时 bump policyVersion，
    /// 新间隔从修改时刻开始，不追补旧点。识别参数与桌型不允许经此修改（桌型走 SetDesk）。
    /// </summary>
    public void UpdateDesktopSettings(Func<DesktopSettings, DesktopSettings> update)
    {
        ArgumentNullException.ThrowIfNull(update);
        lock (_gate)
        {
            var next = update(_settings) with
            {
                Desk = _settings.Desk,
                ActivitySamplingSeconds = _settings.ActivitySamplingSeconds,
                IdleGraceSeconds = _settings.IdleGraceSeconds,
                QualifyingAwaySeconds = _settings.QualifyingAwaySeconds
            };
            var schedulingChanged = next.EyeEnabled != _settings.EyeEnabled
                || next.MovementEnabled != _settings.MovementEnabled
                || next.EyeIntervalSeconds != _settings.EyeIntervalSeconds
                || next.MovementIntervalSeconds != _settings.MovementIntervalSeconds;
            _settings = next with
            {
                PolicyVersion = schedulingChanged
                    ? $"desktop-v1-{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}"
                    : _settings.PolicyVersion
            };
            SaveSnapshotLocked();
        }

        RaiseUpdated();
    }

    /// <summary>删除本机桌面活动数据（记录、提醒历史、统计），保留桌型与提醒设置。</summary>
    public void ResetActivityData()
    {
        lock (_gate)
        {
            _classifier = new ActivityClassifier(_settings);
            _reminderState = new ReminderSchedulerState();
            _reminderInstances.Clear();
            _lastReportedInputAtUtc = DateTimeOffset.UtcNow - _idleTimeProvider.GetIdleTime();
            SaveSnapshotLocked();
        }

        RaiseUpdated();
    }

    public (long NextEyeDueActiveSeconds, long NextMovementDueActiveSeconds) NextDueActiveSeconds()
    {
        lock (_gate)
        {
            var (seconds, _, _) = CurrentSessionInfo();
            return (
                _reminderState.EyeCycleBaseActiveSeconds + _settings.EyeIntervalSeconds - seconds,
                _reminderState.MovementCycleBaseActiveSeconds + _settings.MovementIntervalSeconds - seconds);
        }
    }

    public void CompleteOnboarding(DeskType desk)
    {
        _hasCompletedOnboarding = true;
        SetDesk(desk);
    }

    public TrackerSettings Settings
    {
        get
        {
            lock (_gate)
            {
                return new TrackerSettings(
                    IdleThresholdSeconds: _settings.QualifyingAwaySeconds,
                    CountAudio: false,
                    ReminderThresholdSeconds: _settings.MovementIntervalSeconds,
                    StartWithWindows: _startWithWindows,
                    RepeatReminder: true)
                {
                    ContinuousReminderEnabled = _settings.EyeEnabled,
                    ContinuousReminderThresholdSeconds = _settings.EyeIntervalSeconds
                };
            }
        }

        set
        {
            ArgumentNullException.ThrowIfNull(value);
            lock (_gate)
            {
                _startWithWindows = value.StartWithWindows;
                var reminderParamsChanged = _settings.EyeEnabled != value.ContinuousReminderEnabled
                    || _settings.EyeIntervalSeconds != Math.Max(60, value.ContinuousReminderThresholdSeconds)
                    || _settings.MovementIntervalSeconds != Math.Max(60, value.ReminderThresholdSeconds);
                _settings = _settings with
                {
                    EyeEnabled = value.ContinuousReminderEnabled,
                    EyeIntervalSeconds = Math.Max(60, value.ContinuousReminderThresholdSeconds),
                    MovementIntervalSeconds = Math.Max(60, value.ReminderThresholdSeconds),
                    PolicyVersion = reminderParamsChanged
                        ? $"desktop-v1-{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}"
                        : _settings.PolicyVersion
                };
                SaveSnapshotLocked();
            }

            RaiseUpdated();
        }
    }

    public void SaveNow()
    {
        lock (_gate)
        {
            SaveSnapshotLocked();
        }
    }

    public void DisconnectSyncPeer()
    {
        // 手机线冻结：本机独立运行，无对端可断开
    }

    public List<DailyRecord> GetRecordsSnapshot()
    {
        lock (_gate)
        {
            return BuildRecordsLocked();
        }
    }

    public Dictionary<DateOnly, DailyStatsSnapshot> GetDailyStatsSnapshots(IEnumerable<DateOnly> dates)
    {
        ArgumentNullException.ThrowIfNull(dates);
        lock (_gate)
        {
            var requested = dates.ToHashSet();
            return BuildRecordsLocked()
                .Where(record => requested.Contains(record.Date))
                .ToDictionary(
                    record => record.Date,
                    record => new DailyStatsSnapshot(
                        record,
                        new UsageDeviceBreakdown(record.TotalSeconds, 0L, record.HourlySeconds, null)));
        }
    }

    public UsageDeviceBreakdown GetDeviceBreakdown(DateOnly date)
    {
        lock (_gate)
        {
            var metrics = DailyAggregator.Aggregate(
                date,
                _classifier.Intervals.ToList(),
                _classifier.Sessions.ToList(),
                _classifier.Breaks.ToList(),
                DeliveredMovementOpportunitiesLocked(),
                _classifier.Gaps.ToList(),
                DateTimeOffset.UtcNow,
                TimeZoneInfo.Local);
            return new UsageDeviceBreakdown(metrics.DesktopActiveSeconds, 0L);
        }
    }

    public List<AppUsageEntry> GetAppUsageEntries(DateOnly start, DateOnly end)
    {
        // 新定位不含应用使用排行；旧页面在 P0-4 重写前显示空榜
        return new List<AppUsageEntry>();
    }

    private void OnTimerTick(object? state)
    {
        if (Interlocked.Exchange(ref _tickInProgress, 1) == 1)
        {
            return;
        }

        try
        {
            var now = DateTimeOffset.Now;
            var nowUtc = now.ToUniversalTime();
            var monotonic = Environment.TickCount64;
            var idle = _idleTimeProvider.GetIdleTime();
            var interactive = SystemInformation.UserInteractive;

            lock (_gate)
            {
                if (_disposed)
                {
                    return;
                }

                var lastInputUtc = nowUtc - idle;
                if (lastInputUtc > _lastReportedInputAtUtc)
                {
                    _lastReportedInputAtUtc = lastInputUtc;
                    _classifier.Apply(new ActivitySignal(ActivitySignalKind.Input, lastInputUtc, monotonic));
                }

                if (interactive != _lastInteractive)
                {
                    _lastInteractive = interactive;
                    _classifier.Apply(new ActivitySignal(
                        interactive ? ActivitySignalKind.UnlockScreen : ActivitySignalKind.LockScreen,
                        nowUtc,
                        monotonic));
                }

                _classifier.AdvanceTo(nowUtc, monotonic);
                EvaluateRemindersLocked(nowUtc, now);
                if (nowUtc - _lastSaveAtUtc >= SaveInterval)
                {
                    SaveSnapshotLocked();
                }
            }

            RaiseUpdated();
        }
        catch (Exception)
        {
            // 采样异常不能打断采集循环
        }
        finally
        {
            Interlocked.Exchange(ref _tickInProgress, 0);
        }
    }

    private void OnSessionSwitch(object sender, SessionSwitchEventArgs e)
    {
        lock (_gate)
        {
            if (_disposed)
            {
                return;
            }

            var nowUtc = DateTimeOffset.UtcNow;
            var kind = e.Reason is SessionSwitchReason.SessionLock or SessionSwitchReason.SessionLogoff
                ? ActivitySignalKind.LockScreen
                : ActivitySignalKind.UnlockScreen;
            _lastInteractive = kind == ActivitySignalKind.UnlockScreen;
            _classifier.Apply(new ActivitySignal(kind, nowUtc, Environment.TickCount64));
        }

        RaiseUpdated();
    }

    private void OnPowerModeChanged(object sender, PowerModeChangedEventArgs e)
    {
        ActivitySignalKind? kind = e.Mode switch
        {
            PowerModes.Suspend => ActivitySignalKind.Suspend,
            PowerModes.Resume => ActivitySignalKind.Resume,
            _ => null
        };
        if (kind is null)
        {
            return;
        }

        lock (_gate)
        {
            if (_disposed)
            {
                return;
            }

            _classifier.Apply(new ActivitySignal(kind.Value, DateTimeOffset.UtcNow, Environment.TickCount64));
        }

        RaiseUpdated();
    }

    public bool RemindersPaused
    {
        get
        {
            lock (_gate)
            {
                return _reminderState.PausedUntilUtc is { } until && DateTimeOffset.UtcNow < until;
            }
        }
    }

    public void PauseReminders()
    {
        lock (_gate)
        {
            _reminderState.PausedUntilUtc =
                DateTimeOffset.UtcNow + TimeSpan.FromMinutes(DesktopSettings.DefaultPauseReminderMinutes);
            SaveSnapshotLocked();
        }

        RaiseUpdated();
    }

    public void ResumeReminders()
    {
        lock (_gate)
        {
            _reminderState.PausedUntilUtc = null;
            SaveSnapshotLocked();
        }

        RaiseUpdated();
    }

    private void EvaluateRemindersLocked(DateTimeOffset nowUtc, DateTimeOffset localNow)
    {
        var currentSession = _classifier.CurrentSession;
        var sessionActiveSeconds = currentSession is null
            ? 0L
            : SessionAnalyzer.CommittedSeconds(
                _classifier.Intervals.Where(interval => interval.SessionId == currentSession.SessionId));
        var newInstances = _scheduler.Evaluate(
            _classifier.State,
            currentSession?.SessionId,
            sessionActiveSeconds,
            _reminderState,
            _settings,
            nowUtc,
            localNow);
        if (newInstances.Count == 0)
        {
            return;
        }

        _reminderInstances.AddRange(newInstances);
        SaveSnapshotLocked();

        if (_reminderDispatcher is null)
        {
            return;
        }

        // 合并组按组投递；抑制实例只落盘不投递
        foreach (var group in newInstances
            .Where(instance => instance.Status == ReminderInstance.StatusDispatching)
            .GroupBy(instance => instance.DeliveryGroupId ?? instance.ReminderId))
        {
            _reminderDispatcher.Dispatch(group.ToList(), _settings);
        }

        SaveSnapshotLocked();
    }

    public void SetDesk(DeskType desk)
    {
        lock (_gate)
        {
            _settings = _settings with { Desk = desk };
            _classifier.Apply(new ActivitySignal(
                ActivitySignalKind.DeskProfileChanged,
                DateTimeOffset.UtcNow,
                Environment.TickCount64,
                desk));
            SaveSnapshotLocked();
        }

        RaiseUpdated();
    }

    private TrackingUpdatedEventArgs CreateUpdateLocked()
    {
        var today = DateOnly.FromDateTime(DateTime.Now);
        var metrics = DailyAggregator.Aggregate(
            today,
            _classifier.Intervals.ToList(),
            _classifier.Sessions.ToList(),
            _classifier.Breaks.ToList(),
            DeliveredMovementOpportunitiesLocked(),
            _classifier.Gaps.ToList(),
            DateTimeOffset.UtcNow,
            TimeZoneInfo.Local);
        var isCounting = _classifier.State is ClassifierState.Active or ClassifierState.IdleCandidate;
        return new TrackingUpdatedEventArgs(today, metrics.DesktopActiveSeconds, false, isCounting);
    }

    private List<DeliveredOpportunity> DeliveredMovementOpportunitiesLocked()
    {
        // 只对实际投递成功的活动提醒创建中断率机会
        return _reminderInstances
            .Where(instance => instance.ReminderType == "movement"
                && instance.Status == ReminderInstance.StatusDelivered
                && instance.DispatchAtUtc is not null)
            .Select(instance => new DeliveredOpportunity(instance.ReminderId, instance.DispatchAtUtc!.Value))
            .ToList();
    }

    private List<DailyRecord> BuildRecordsLocked()
    {
        var intervals = _classifier.Intervals.ToList();
        var sessions = _classifier.Sessions.ToList();
        var dates = intervals
            .Select(interval => DateOnly.FromDateTime(interval.StartUtc.ToLocalTime().Date))
            .Append(DateOnly.FromDateTime(DateTime.Now))
            .Distinct()
            .OrderBy(date => date)
            .ToList();

        var records = new List<DailyRecord>();
        foreach (var date in dates)
        {
            var metrics = DailyAggregator.Aggregate(
                date,
                intervals,
                sessions,
                _classifier.Breaks.ToList(),
                DeliveredMovementOpportunitiesLocked(),
                _classifier.Gaps.ToList(),
                DateTimeOffset.UtcNow,
                TimeZoneInfo.Local);
            var record = new DailyRecord(date)
            {
                TotalSeconds = metrics.DesktopActiveSeconds,
                SessionSeconds = sessions
                    .Where(session => DateOnly.FromDateTime(session.StartedAtUtc.ToLocalTime().Date) == date)
                    .Select(session => SessionAnalyzer.CommittedSeconds(
                        intervals.Where(interval => interval.SessionId == session.SessionId)))
                    .Where(seconds => seconds > 0)
                    .ToList(),
                CurrentSessionSeconds = _classifier.CurrentSession is { } current && date == DateOnly.FromDateTime(DateTime.Now)
                    ? SessionAnalyzer.CommittedSeconds(intervals.Where(interval => interval.SessionId == current.SessionId))
                    : 0L
            };
            foreach (var interval in intervals)
            {
                AddIntervalToHourly(record, interval);
            }

            records.Add(record);
        }

        return records;
    }

    private static void AddIntervalToHourly(DailyRecord record, CommittedInterval interval)
    {
        var localStart = interval.StartUtc.ToLocalTime();
        var localEnd = interval.EndUtc.ToLocalTime();
        if (DateOnly.FromDateTime(localStart.Date) != record.Date)
        {
            return;
        }

        var cursor = localStart;
        while (cursor < localEnd)
        {
            var hourEnd = new DateTime(cursor.Year, cursor.Month, cursor.Day, cursor.Hour, 0, 0).AddHours(1);
            var segmentEnd = hourEnd < localEnd ? hourEnd : localEnd;
            record.HourlySeconds[cursor.Hour] += (long)(segmentEnd - cursor).TotalSeconds;
            cursor = segmentEnd;
        }
    }

    private void SaveSnapshotLocked()
    {
        _lastSaveAtUtc = DateTimeOffset.UtcNow;
        _store.Save(new DesktopStateSnapshot
        {
            HasCompletedOnboarding = _hasCompletedOnboarding,
            Settings = _settings,
            StartWithWindows = _startWithWindows,
            StartedTrackingAtUtc = _classifier.Sessions.FirstOrDefault()?.StartedAtUtc,
            SavedAtUtc = _lastSaveAtUtc,
            DeviceId = _deviceId,
            Sessions = _classifier.Sessions.ToList(),
            Intervals = _classifier.Intervals.ToList(),
            Breaks = _classifier.Breaks.ToList(),
            Gaps = _classifier.Gaps.ToList(),
            LastClassifierState = _classifier.State,
            ReminderState = _reminderState,
            ReminderInstances = _reminderInstances.ToList()
        });
    }

    private void RaiseUpdated()
    {
        TrackingUpdatedEventArgs update;
        lock (_gate)
        {
            update = CreateUpdateLocked();
        }

        var handler = Updated;
        if (handler is null)
        {
            return;
        }

        foreach (var callback in handler.GetInvocationList())
        {
            try
            {
                ((EventHandler<TrackingUpdatedEventArgs>)callback)(this, update);
            }
            catch (Exception)
            {
            }
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
            SystemEvents.SessionSwitch -= OnSessionSwitch;
            SystemEvents.PowerModeChanged -= OnPowerModeChanged;
            _timer.Dispose();
            SaveSnapshotLocked();
        }
    }
}
