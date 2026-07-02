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
        state.Settings = request.Settings;
        state.Sync.LastSyncUnixSeconds = _unixClock();
        state.Sync.LastError = string.Empty;
        _saveState(state);

        return new SyncResponse
        {
            Accepted = true,
            DeviceId = state.DeviceId,
            Platform = state.Platform,
            Segments = GetLocalSegmentsSince(state, request.SinceUnixSeconds),
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

    private static bool CanAcceptSync(AppState state, SyncRequest request, out string error)
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
            TimestampUnixSeconds = _unixClock()
        };
    }

    private static void MergeSegments(AppState state, IEnumerable<UsageSegment> incomingSegments)
    {
        var knownSegmentIds = state.Segments
            .Where(segment => !string.IsNullOrWhiteSpace(segment.SegmentId))
            .Select(segment => segment.SegmentId)
            .ToHashSet(StringComparer.Ordinal);

        foreach (var segment in incomingSegments)
        {
            if (!IsUsableSegment(segment) || !knownSegmentIds.Add(segment.SegmentId))
            {
                continue;
            }

            state.Segments.Add(CloneSegment(segment));
        }
    }

    private static bool IsUsableSegment(UsageSegment segment)
    {
        return !string.IsNullOrWhiteSpace(segment.SegmentId)
            && !string.IsNullOrWhiteSpace(segment.DeviceId)
            && segment.EndUnixSeconds > segment.StartUnixSeconds;
    }

    private static List<UsageSegment> GetLocalSegmentsSince(AppState state, long sinceUnixSeconds)
    {
        return state.Segments
            .Where(segment => string.Equals(segment.DeviceId, state.DeviceId, StringComparison.Ordinal))
            .Where(segment => sinceUnixSeconds <= 0 || segment.UpdatedAtUnixSeconds > sinceUnixSeconds)
            .Select(CloneSegment)
            .ToList();
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

    private static string CreateSharedSecret()
    {
        return Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
    }
}
