using System.Net;
using System.Net.Sockets;
using System.Text.Json;
using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.App.Sync;

public sealed class PcSyncServer : IDisposable
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    private readonly object _gate = new();
    private readonly PcSyncCoordinator _coordinator;
    private readonly int _firstPort;
    private readonly int _lastPort;
    private readonly Func<string> _expectedPairingCode;
    private readonly Action _pairingAccepted;

    private TcpListener? _listener;
    private CancellationTokenSource? _cancellation;
    private Task? _acceptLoop;
    private bool _disposed;

    public PcSyncServer(
        PcSyncCoordinator coordinator,
        int firstPort = 17420,
        int lastPort = 17429,
        Func<string>? expectedPairingCode = null,
        Action? pairingAccepted = null)
    {
        if (lastPort < firstPort)
        {
            throw new ArgumentOutOfRangeException(nameof(lastPort), "Last port must be greater than or equal to first port.");
        }

        _coordinator = coordinator ?? throw new ArgumentNullException(nameof(coordinator));
        _firstPort = firstPort;
        _lastPort = lastPort;
        _expectedPairingCode = expectedPairingCode ?? (() => string.Empty);
        _pairingAccepted = pairingAccepted ?? (() => { });
    }

    public int Port { get; private set; }

    public string LastError { get; private set; } = string.Empty;

    public void Start()
    {
        lock (_gate)
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            if (_listener is not null)
            {
                return;
            }

            _listener = BindFirstAvailablePort();
            _cancellation = new CancellationTokenSource();
            _acceptLoop = Task.Run(() => AcceptLoopAsync(_cancellation.Token));
        }
    }

    public void Dispose()
    {
        Task? acceptLoop;
        CancellationTokenSource? cancellation;
        lock (_gate)
        {
            if (_disposed)
            {
                return;
            }

            _disposed = true;
            cancellation = _cancellation;
            acceptLoop = _acceptLoop;
            cancellation?.Cancel();
            _listener?.Stop();
            _listener = null;
            Port = 0;
        }

        try
        {
            acceptLoop?.Wait(TimeSpan.FromSeconds(1));
        }
        catch (AggregateException)
        {
        }

        cancellation?.Dispose();
    }

    private TcpListener BindFirstAvailablePort()
    {
        Exception? lastException = null;
        for (var port = _firstPort; port <= _lastPort; port++)
        {
            try
            {
                var listener = new TcpListener(IPAddress.Any, port);
                listener.Start();
                Port = port;
                LastError = string.Empty;
                return listener;
            }
            catch (SocketException ex)
            {
                lastException = ex;
                LastError = ex.Message;
            }
        }

        throw new InvalidOperationException($"No sync port is available from {_firstPort} to {_lastPort}.", lastException);
    }

    private async Task AcceptLoopAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            TcpClient? client = null;
            try
            {
                var listener = _listener;
                if (listener is null)
                {
                    return;
                }

                client = await listener.AcceptTcpClientAsync(cancellationToken).ConfigureAwait(false);
                var acceptedClient = client;
                _ = Task.Run(() => HandleClientAsync(acceptedClient, cancellationToken), cancellationToken);
                client = null;
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch (ObjectDisposedException)
            {
                return;
            }
            catch (Exception ex)
            {
                RecordError(ex);
            }
            finally
            {
                client?.Dispose();
            }
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken cancellationToken)
    {
        try
        {
            client.ReceiveTimeout = 5000;
            client.SendTimeout = 5000;
            using (client)
            using (var stream = client.GetStream())
            using (var reader = new StreamReader(stream))
            using (var writer = new StreamWriter(stream) { AutoFlush = true })
            {
                var requestJson = await reader.ReadLineAsync(cancellationToken).ConfigureAwait(false);
                if (string.IsNullOrWhiteSpace(requestJson))
                {
                    return;
                }

                var responseJson = HandleRequestJson(requestJson);
                await writer.WriteLineAsync(responseJson.AsMemory(), cancellationToken).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception ex)
        {
            RecordError(ex);
        }
    }

    private string HandleRequestJson(string requestJson)
    {
        try
        {
            using var document = JsonDocument.Parse(requestJson);
            if (!document.RootElement.TryGetProperty(nameof(SyncRequest.Type), out var typeElement))
            {
                return SerializeRejectedSyncResponse("Sync request is missing a type.");
            }

            return typeElement.GetString() switch
            {
                SyncMessageTypes.DiscoveryRequest => JsonSerializer.Serialize(_coordinator.CreateDiscoveryResponse(Port), SerializerOptions),
                SyncMessageTypes.SyncRequest => HandleSyncRequestJson(requestJson),
                SyncMessageTypes.PairRequest => HandlePairRequestJson(requestJson),
                SyncMessageTypes.DisconnectRequest => HandleDisconnectRequestJson(requestJson),
                _ => SerializeRejectedSyncResponse("Sync request type is not supported.")
            };
        }
        catch (JsonException ex)
        {
            RecordError(ex);
            return SerializeRejectedSyncResponse("Sync request JSON is invalid.");
        }
    }

    private string HandleSyncRequestJson(string requestJson)
    {
        var request = JsonSerializer.Deserialize<SyncRequest>(requestJson, SerializerOptions);
        if (request is null)
        {
            return SerializeRejectedSyncResponse("Sync request JSON is invalid.");
        }

        var response = _coordinator.HandleSync(request);
        return JsonSerializer.Serialize(response, SerializerOptions);
    }

    private string HandleDisconnectRequestJson(string requestJson)
    {
        var request = JsonSerializer.Deserialize<DisconnectRequest>(requestJson, SerializerOptions);
        if (request is null)
        {
            return JsonSerializer.Serialize(new DisconnectResponse
            {
                Accepted = false,
                Error = "Disconnect request JSON is invalid.",
                TimestampUnixSeconds = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
            }, SerializerOptions);
        }

        var response = _coordinator.HandleDisconnect(request);
        return JsonSerializer.Serialize(response, SerializerOptions);
    }

    private string HandlePairRequestJson(string requestJson)
    {
        var request = JsonSerializer.Deserialize<PairRequest>(requestJson, SerializerOptions);
        if (request is null)
        {
            return JsonSerializer.Serialize(new PairAccept
            {
                Accepted = false,
                Error = "Pair request JSON is invalid.",
                TimestampUnixSeconds = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
            }, SerializerOptions);
        }

        var response = _coordinator.HandlePair(request, _expectedPairingCode());
        if (response.Accepted)
        {
            _pairingAccepted();
        }

        return JsonSerializer.Serialize(response, SerializerOptions);
    }

    private string SerializeRejectedSyncResponse(string error)
    {
        LastError = error;
        return JsonSerializer.Serialize(new SyncResponse
        {
            Accepted = false,
            Error = error,
            TimestampUnixSeconds = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
        }, SerializerOptions);
    }

    private void RecordError(Exception ex)
    {
        LastError = ex.Message;
    }
}
