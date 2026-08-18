using System.Security.Cryptography;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.App.Sync;

public sealed class PcSyncCoordinator
{
    private readonly Func<AppState> _loadState;
    private readonly Action<AppState> _saveState;
    private readonly Func<long> _unixClock;

    public PcSyncCoordinator(JsonStateStore stateStore, Func<long>? unixClock = null)
        : this(stateStore.Load, stateStore.Save, unixClock)
    {
    }

    public PcSyncCoordinator(Func<AppState> loadState, Action<AppState> saveState, Func<long>? unixClock = null)
    {
        _loadState = loadState ?? throw new ArgumentNullException(nameof(loadState));
        _saveState = saveState ?? throw new ArgumentNullException(nameof(saveState));
        _unixClock = unixClock ?? (() => DateTimeOffset.UtcNow.ToUnixTimeSeconds());
    }

    public SyncResponse HandleSync(SyncRequest request)
    {
        ArgumentNullException.ThrowIfNull(request);

        var state = _loadState();
        if (!CanAcceptSync(state, request, out var error))
        {
            return RejectSync(state, error);
        }

        MergeSegments(state, request.Segments);
        MergeAppUsageEntries(state, request.AppUsageEntries);
        state.Sync.PeerSupportsMutableSegments = request.SupportsMutableSegments;
        state.Sync.PeerReminderState = CloneReminderState(request.ReminderState);
        state.Settings = MergeSyncedSettings(state.Settings, request.Settings);
        state.Sync.LastSyncUnixSeconds = _unixClock();
        state.Sync.LastError = string.Empty;
        _saveState(state);

        return new SyncResponse
        {
            Accepted = true,
            DeviceId = state.DeviceId,
            Platform = state.Platform,
            Segments = GetLocalSegments(state),
            AppUsageEntries = GetLocalAppUsageEntries(state),
            ReminderState = CloneReminderState(state.Sync.LocalReminderState),
            SupportsMutableSegments = true,
            TimestampUnixSeconds = state.Sync.LastSyncUnixSeconds
        };
    }

    public PairAccept HandlePair(PairRequest request, string expectedCode)
    {
        ArgumentNullException.ThrowIfNull(request);

        var state = _loadState();
        var now = _unixClock();
        if (string.IsNullOrWhiteSpace(expectedCode)
            || request.PairingCode != expectedCode
            || string.IsNullOrWhiteSpace(request.DeviceId))
        {
            state.Sync.LastError = "Pairing code is invalid.";
            _saveState(state);
            return new PairAccept
            {
                Accepted = false,
                DeviceId = state.DeviceId,
                Platform = state.Platform,
                Error = state.Sync.LastError,
                TimestampUnixSeconds = now
            };
        }

        state.Sync = new SyncSettings
        {
            IsPaired = true,
            PeerDeviceId = request.DeviceId,
            PeerPlatform = string.IsNullOrWhiteSpace(request.Platform) ? "android" : request.Platform,
            SharedSecret = CreateSharedSecret(),
            LastSyncUnixSeconds = now,
            LastError = string.Empty
        };
        _saveState(state);

        return new PairAccept
        {
            Accepted = true,
            DeviceId = state.DeviceId,
            Platform = state.Platform,
            SharedSecret = state.Sync.SharedSecret,
            TimestampUnixSeconds = now
        };
    }

    public DiscoveryResponse CreateDiscoveryResponse(int port)
    {
        var state = _loadState();
        return new DiscoveryResponse
        {
            DeviceId = state.DeviceId,
            Platform = state.Platform,
            Port = port
        };
    }

    public DisconnectResponse HandleDisconnect(DisconnectRequest request)
    {
        ArgumentNullException.ThrowIfNull(request);

        var state = _loadState();
        var now = _unixClock();
        if (!state.Sync.IsPaired)
        {
            return new DisconnectResponse
            {
                Accepted = true,
                DeviceId = state.DeviceId,
                Platform = state.Platform,
                TimestampUnixSeconds = now
            };
        }

        if (!string.IsNullOrWhiteSpace(state.Sync.PeerDeviceId)
            && !string.Equals(state.Sync.PeerDeviceId, request.DeviceId, StringComparison.Ordinal))
        {
            state.Sync.LastError = "Disconnect request came from an unknown device.";
            _saveState(state);
            return new DisconnectResponse
            {
                Accepted = false,
                DeviceId = state.DeviceId,
                Platform = state.Platform,
                Error = state.Sync.LastError,
                TimestampUnixSeconds = now
            };
        }

        state.Sync = SyncSettings.Unpaired;
        _saveState(state);
        return new DisconnectResponse
        {
            Accepted = true,
            DeviceId = state.DeviceId,
            Platform = state.Platform,
            TimestampUnixSeconds = now
        };
    }

    private bool CanAcceptSync(AppState state, SyncRequest request, out string error)
    {
        if (!state.Sync.IsPaired || string.IsNullOrWhiteSpace(state.Sync.SharedSecret))
        {
            error = "PC is not paired.";
            return false;
        }

        if (!string.IsNullOrWhiteSpace(state.Sync.PeerDeviceId)
            && !string.Equals(state.Sync.PeerDeviceId, request.DeviceId, StringComparison.Ordinal))
        {
            error = "Sync request came from an unknown device.";
            return false;
        }

        if (!SyncMessageSigner.Verify(
                SyncMessageTypes.SyncRequest,
                request.TimestampUnixSeconds,
                SyncSignatureBody.ForSyncRequest(request),
                state.Sync.SharedSecret,
                request.Signature,
                _unixClock()))
        {
            error = "Sync request signature is invalid.";
            return false;
        }

        error = string.Empty;
        return true;
    }

    private SyncResponse RejectSync(AppState state, string error)
    {
        state.Sync.LastError = error;
        _saveState(state);
        return new SyncResponse
        {
            Accepted = false,
            DeviceId = state.DeviceId,
            Platform = state.Platform,
            Error = error,
            ReminderState = CloneReminderState(state.Sync.LocalReminderState),
            TimestampUnixSeconds = _unixClock()
        };
    }

    private static void MergeSegments(AppState state, IEnumerable<UsageSegment> incomingSegments)
    {
        var existingById = state.Segments
            .Where(segment => !string.IsNullOrWhiteSpace(segment.SegmentId))
            .GroupBy(segment => segment.SegmentId, StringComparer.Ordinal)
            .ToDictionary(group => group.Key, group => group.OrderByDescending(segment => segment.UpdatedAtUnixSeconds).First(), StringComparer.Ordinal);

        foreach (var segment in incomingSegments ?? Array.Empty<UsageSegment>())
        {
            if (!IsUsableSegment(segment))
            {
                continue;
            }

            if (existingById.TryGetValue(segment.SegmentId, out var existing))
            {
                if (segment.UpdatedAtUnixSeconds > existing.UpdatedAtUnixSeconds)
                {
                    CopySegment(existing, segment);
                }

                continue;
            }

            var added = CloneSegment(segment);
            state.Segments.Add(added);
            existingById[added.SegmentId] = added;
        }
    }

    private static bool IsUsableSegment(UsageSegment segment)
    {
        return !string.IsNullOrWhiteSpace(segment.SegmentId)
            && !string.IsNullOrWhiteSpace(segment.DeviceId)
            && segment.EndUnixSeconds > segment.StartUnixSeconds;
    }

    private static List<UsageSegment> GetLocalSegments(AppState state)
    {
        return LegacyUsageSegments.NormalizeEffectiveSegments(
                state.Segments,
                state.Records,
                state.DeviceId,
                state.Platform)
            .Where(segment => string.Equals(segment.DeviceId, state.DeviceId, StringComparison.Ordinal))
            .Select(CloneSegment)
            .ToList();
    }

    private static void MergeAppUsageEntries(AppState state, IEnumerable<AppUsageEntry> incomingEntries)
    {
        state.AppUsageEntries ??= new List<AppUsageEntry>();
        var existingById = state.AppUsageEntries
            .Where(entry => !string.IsNullOrWhiteSpace(entry.EntryId))
            .ToDictionary(entry => entry.EntryId, StringComparer.Ordinal);

        foreach (var incoming in incomingEntries ?? Array.Empty<AppUsageEntry>())
        {
            if (!IsUsableAppUsageEntry(incoming))
            {
                continue;
            }

            var normalized = CloneAppUsageEntry(incoming);
            normalized.EntryId = AppUsageEntryId.For(
                normalized.DeviceId,
                normalized.Platform,
                normalized.Source,
                normalized.AppId,
                normalized.LocalDate);
            if (existingById.TryGetValue(normalized.EntryId, out var existing))
            {
                if (normalized.UpdatedAtUnixSeconds >= existing.UpdatedAtUnixSeconds)
                {
                    existing.AppName = normalized.AppName;
                    existing.IconData = string.Empty;
                    existing.DurationSeconds = normalized.DurationSeconds;
                    existing.UpdatedAtUnixSeconds = normalized.UpdatedAtUnixSeconds;
                }
                continue;
            }

            state.AppUsageEntries.Add(normalized);
            existingById[normalized.EntryId] = normalized;
        }
    }

    private static List<AppUsageEntry> GetLocalAppUsageEntries(AppState state)
    {
        return (state.AppUsageEntries ?? new List<AppUsageEntry>())
            .Where(entry => string.Equals(entry.DeviceId, state.DeviceId, StringComparison.Ordinal)
                && IsUsableAppUsageEntry(entry))
            .Select(CloneAppUsageEntry)
            .ToList();
    }

    private static bool IsUsableAppUsageEntry(AppUsageEntry entry)
    {
        return entry is not null
            && !string.IsNullOrWhiteSpace(entry.DeviceId)
            && !string.IsNullOrWhiteSpace(entry.AppId)
            && entry.LocalDate != default
            && entry.DurationSeconds > 0;
    }

    private static TrackerSettings MergeSyncedSettings(TrackerSettings local, TrackerSettings incoming)
    {
        return incoming with
        {
            StartWithWindows = local.StartWithWindows
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

    private static void CopySegment(UsageSegment destination, UsageSegment source)
    {
        destination.DeviceId = source.DeviceId;
        destination.Platform = source.Platform;
        destination.Source = source.Source;
        destination.StartUnixSeconds = source.StartUnixSeconds;
        destination.EndUnixSeconds = source.EndUnixSeconds;
        destination.LocalDate = source.LocalDate;
        destination.CreatedAtUnixSeconds = source.CreatedAtUnixSeconds;
        destination.UpdatedAtUnixSeconds = source.UpdatedAtUnixSeconds;
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
            DurationSeconds = Math.Max(0, entry.DurationSeconds),
            UpdatedAtUnixSeconds = entry.UpdatedAtUnixSeconds
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
            CurrentSessionStartedUnixSeconds = state.CurrentSessionStartedUnixSeconds
        };
    }

    private static string CreateSharedSecret()
    {
        return Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
    }
}
