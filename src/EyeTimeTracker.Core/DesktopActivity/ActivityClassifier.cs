namespace EyeTimeTracker.Core.DesktopActivity;

/// <summary>
/// 平台活动信号 → 已确认有效活动区间、推定中断、不可观察缺口、会话边界。
/// 规则（见改版开发说明 §5）：
/// - 相邻真实输入间隔不超过 idleGrace 时把间隔提交为有效活动；超过则该间隔不提交，暂存尾部撤回；
/// - 无输入超过 idleGrace 进入 IdleCandidate（冻结提醒，不投递）；达到 qualifyingAway 认定推定中断；
/// - 锁屏立即暂停并开始 absence，短锁屏返回仍为原会话，锁屏时间不累计；
/// - 休眠立即结束可观察段，恢复后新建会话；退出／崩溃／采样大缺口记 unknown gap，不补计；
/// - 系统能观察输入、锁屏、休眠，不能确认姿势、起身、走动或远望；后台音乐不单独维持活动（信号层就不该传）。
/// 本类只依赖输入信号序列与注入参数，可用夹具重放。
/// </summary>
public sealed class ActivityClassifier
{
    private readonly DesktopSettings _settings;
    private readonly List<CommittedInterval> _intervals = new();
    private readonly List<InferredBreak> _breaks = new();
    private readonly List<CoverageGapRecord> _gaps = new();
    private readonly List<SessionBoundary> _sessions = new();

    private DateTimeOffset? _lastInputAtUtc;
    private DateTimeOffset? _lockAtUtc;
    private ClassifierState _stateBeforeLock = ClassifierState.Unknown;
    private SessionBoundary? _currentSession;
    private CoverageGapRecord? _openGap;
    private int _breakSequence;

    public ActivityClassifier(DesktopSettings settings)
    {
        _settings = settings ?? throw new ArgumentNullException(nameof(settings));
        Desk = settings.Desk;
    }

    public ClassifierState State { get; private set; } = ClassifierState.Unknown;
    public DeskType Desk { get; private set; }
    public IReadOnlyList<CommittedInterval> Intervals => _intervals;
    public IReadOnlyList<InferredBreak> Breaks => _breaks;
    public IReadOnlyList<CoverageGapRecord> Gaps => _gaps;
    public IReadOnlyList<SessionBoundary> Sessions => _sessions;
    public SessionBoundary? CurrentSession => _currentSession;
    public DateTimeOffset? LastInputAtUtc => _lastInputAtUtc;

    /// <summary>暂存尾部起点（Active 时最后输入时刻）。主数字可显示暂存估计，稳定统计只用 committed。</summary>
    public DateTimeOffset? ProvisionalTailStartUtc =>
        State == ClassifierState.Active ? _lastInputAtUtc : null;

    /// <summary>从持久化快照恢复事件列表与桌型；会话连续性按 unknown gap 语义由调用方处理。</summary>
    public void Restore(
        DeskType desk,
        IEnumerable<CommittedInterval> intervals,
        IEnumerable<InferredBreak> breaks,
        IEnumerable<CoverageGapRecord> gaps,
        IEnumerable<SessionBoundary> sessions,
        ClassifierState lastState = ClassifierState.Unknown)
    {
        Desk = desk;
        _intervals.Clear();
        _intervals.AddRange(intervals);
        _breaks.Clear();
        _breaks.AddRange(breaks);
        _gaps.Clear();
        _gaps.AddRange(gaps);
        _sessions.Clear();
        _sessions.AddRange(sessions);
        _breakSequence = breaks.Count();
        _currentSession = _sessions.LastOrDefault(session => session.EndedAtUtc is null);
        _openGap = _gaps.LastOrDefault(gap => gap.ToUtc is null);
        State = lastState;
    }

    public void Apply(ActivitySignal signal)
    {
        ArgumentNullException.ThrowIfNull(signal);
        ProcessTimeouts(signal.ObservedAtUtc, signal.MonotonicMs);
        switch (signal.Kind)
        {
            case ActivitySignalKind.Input:
                OnInput(signal.ObservedAtUtc);
                break;
            case ActivitySignalKind.LockScreen:
                OnLockScreen(signal.ObservedAtUtc);
                break;
            case ActivitySignalKind.UnlockScreen:
                OnUnlockScreen(signal.ObservedAtUtc);
                break;
            case ActivitySignalKind.Suspend:
                OnSuspend(signal.ObservedAtUtc);
                break;
            case ActivitySignalKind.Resume:
                OnResume(signal.ObservedAtUtc);
                break;
            case ActivitySignalKind.ObservationLost:
                OnObservationLost(signal.ObservedAtUtc);
                break;
            case ActivitySignalKind.DeskProfileChanged:
                OnDeskProfileChanged(signal);
                break;
        }
    }

    /// <summary>采样推进：处理到 atUtc 为止的超时转移。提醒评估在活动变化之后。</summary>
    public void AdvanceTo(DateTimeOffset atUtc, long monotonicMs)
    {
        ProcessTimeouts(atUtc, monotonicMs);
    }

    private void ProcessTimeouts(DateTimeOffset atUtc, long monotonicMs)
    {
        if (State == ClassifierState.Active
            && _lastInputAtUtc is { } lastInput
            && atUtc - lastInput > TimeSpan.FromSeconds(_settings.IdleGraceSeconds))
        {
            // 暂存尾部从未 committed，无需回滚；冻结提醒由调度层读取状态决定
            State = ClassifierState.IdleCandidate;
        }

        if (State == ClassifierState.IdleCandidate
            && _lastInputAtUtc is { } idleSince
            && atUtc - idleSince >= TimeSpan.FromSeconds(_settings.QualifyingAwaySeconds))
        {
            EnterAway(idleSince, idleSince, HealthEvents.SedentaryBreakPayload.ReasonIdleTimeout);
        }

        if (State == ClassifierState.LockAbsence
            && _lockAtUtc is { } lockAt
            && atUtc - lockAt >= TimeSpan.FromSeconds(_settings.QualifyingAwaySeconds))
        {
            EnterAway(lockAt, lockAt, HealthEvents.SedentaryBreakPayload.ReasonLockScreen);
        }
    }

    private void OnInput(DateTimeOffset atUtc)
    {
        CloseOpenGap(atUtc);
        switch (State)
        {
            case ClassifierState.Unknown:
            case ClassifierState.Unavailable:
                StartSession(atUtc);
                break;
            case ClassifierState.Active:
                if (_lastInputAtUtc is { } previous)
                {
                    var gap = atUtc - previous;
                    if (gap <= TimeSpan.FromSeconds(_settings.IdleGraceSeconds))
                    {
                        Commit(previous, atUtc);
                    }
                    // 超过容忍窗口的间隔不提交；暂存尾部一并放弃
                }

                _lastInputAtUtc = atUtc;
                break;
            case ClassifierState.IdleCandidate:
                if (_lastInputAtUtc is { } idleSince
                    && atUtc - idleSince < TimeSpan.FromSeconds(_settings.QualifyingAwaySeconds))
                {
                    // 阈值前返回：保留同一 sessionId，gap 不计活动秒数
                    _lastInputAtUtc = atUtc;
                    State = ClassifierState.Active;
                }
                else
                {
                    ReturnFromAway(atUtc);
                }

                break;
            case ClassifierState.Away:
                ReturnFromAway(atUtc);
                break;
            case ClassifierState.LockAbsence:
                // 锁屏中收到输入按解锁处理（异常序列容错）
                OnUnlockScreen(atUtc);
                if (State == ClassifierState.Active)
                {
                    _lastInputAtUtc = atUtc;
                }
                else if (State == ClassifierState.Unknown)
                {
                    StartSession(atUtc);
                }

                break;
        }
    }

    private void OnLockScreen(DateTimeOffset atUtc)
    {
        if (State is ClassifierState.Active or ClassifierState.IdleCandidate)
        {
            // 锁屏立即撤回尚未确认的尾部并暂停；锁屏时间不累计
            _stateBeforeLock = State;
            _lockAtUtc = atUtc;
            State = ClassifierState.LockAbsence;
        }
    }

    private void OnUnlockScreen(DateTimeOffset atUtc)
    {
        if (State != ClassifierState.LockAbsence || _lockAtUtc is not { } lockAt)
        {
            return;
        }

        if (atUtc - lockAt < TimeSpan.FromSeconds(_settings.QualifyingAwaySeconds))
        {
            State = _stateBeforeLock;
            _lockAtUtc = null;
            return;
        }

        // 长锁屏：先生成推定中断，返回后进入 Away，等下一次输入才确定中断结束
        EnterAway(lockAt, lockAt, HealthEvents.SedentaryBreakPayload.ReasonLockScreen);
        _lockAtUtc = null;
    }

    private void OnSuspend(DateTimeOffset atUtc)
    {
        if (State is ClassifierState.Active or ClassifierState.IdleCandidate or ClassifierState.LockAbsence)
        {
            EndCurrentSession(atUtc, SessionBoundary.ReasonSuspend);
        }

        _lockAtUtc = null;
        _openGap = new CoverageGapRecord(atUtc, HealthEvents.CoverageGapPayload.ReasonSleep);
        _gaps.Add(_openGap);
        State = ClassifierState.Unavailable;
    }

    private void OnResume(DateTimeOffset atUtc)
    {
        CloseOpenGap(atUtc);
        if (State == ClassifierState.Unavailable)
        {
            State = ClassifierState.Unknown;
        }
    }

    private void OnObservationLost(DateTimeOffset atUtc)
    {
        if (_currentSession is not null)
        {
            EndCurrentSession(atUtc, SessionBoundary.ReasonUnknownGap);
        }

        _lockAtUtc = null;
        if (State is ClassifierState.Away or ClassifierState.LockAbsence)
        {
            // 观察丢失期间的离开状态不可确认，保留已生成中断但不补返回时间
        }

        _openGap = new CoverageGapRecord(atUtc, HealthEvents.CoverageGapPayload.ReasonAppClosed);
        _gaps.Add(_openGap);
        State = ClassifierState.Unknown;
    }

    private void OnDeskProfileChanged(ActivitySignal signal)
    {
        if (signal.NewDeskType is { } newDesk && newDesk != Desk)
        {
            // 结束当前分类段并开始新段，不算身体中断；保留会话连续计时
            Desk = newDesk;
        }
    }

    private void StartSession(DateTimeOffset atUtc)
    {
        _currentSession = new SessionBoundary(NewSessionId(), Desk, atUtc);
        _sessions.Add(_currentSession);
        _lastInputAtUtc = atUtc;
        State = ClassifierState.Active;
    }

    private void EndCurrentSession(DateTimeOffset endedAtUtc, string reason)
    {
        if (_currentSession is null)
        {
            return;
        }

        _currentSession.EndedAtUtc = endedAtUtc;
        _currentSession.EndReason = reason;
        _currentSession = null;
    }

    private void EnterAway(DateTimeOffset absenceStartedAtUtc, DateTimeOffset sessionEndAtUtc, string reason)
    {
        var sessionId = _currentSession?.SessionId ?? string.Empty;
        EndCurrentSession(sessionEndAtUtc, SessionBoundary.ReasonInferredAway);
        _breaks.Add(new InferredBreak(
            BreakId: NewBreakId(),
            SessionId: sessionId,
            DeskType: Desk,
            AbsenceStartedAtUtc: absenceStartedAtUtc,
            ThresholdReachedAtUtc: absenceStartedAtUtc + TimeSpan.FromSeconds(_settings.QualifyingAwaySeconds),
            Reason: reason));
        State = ClassifierState.Away;
    }

    private void ReturnFromAway(DateTimeOffset atUtc)
    {
        var openBreak = _breaks.LastOrDefault(b => b.ReturnedAtUtc is null);
        if (openBreak is not null)
        {
            openBreak.ReturnedAtUtc = atUtc;
        }

        StartSession(atUtc);
    }

    private void CloseOpenGap(DateTimeOffset atUtc)
    {
        if (_openGap is not null && _openGap.ToUtc is null)
        {
            _openGap.ToUtc = atUtc;
        }

        _openGap = null;
    }

    private void Commit(DateTimeOffset startUtc, DateTimeOffset endUtc)
    {
        if (_currentSession is null || endUtc <= startUtc)
        {
            return;
        }

        _intervals.Add(new CommittedInterval(_currentSession.SessionId, startUtc, endUtc, Desk));
    }

    private static string NewSessionId() => "session-" + Guid.NewGuid().ToString("N")[..12];
    private string NewBreakId() => "break-" + (++_breakSequence).ToString("D4");
}
