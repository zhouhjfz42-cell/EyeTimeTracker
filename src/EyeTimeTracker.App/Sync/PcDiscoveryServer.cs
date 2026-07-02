using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.App.Sync;

public sealed class PcDiscoveryServer : IDisposable
{
    public const int DefaultDiscoveryPort = 17419;

    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    private readonly object _gate = new();
    private readonly Func<DiscoveryResponse> _createResponse;
    private readonly int _port;
    private UdpClient? _client;
    private CancellationTokenSource? _cancellation;
    private Task? _listenLoop;
    private bool _disposed;

    public PcDiscoveryServer(Func<DiscoveryResponse> createResponse, int port = DefaultDiscoveryPort)
    {
        _createResponse = createResponse ?? throw new ArgumentNullException(nameof(createResponse));
        _port = port;
    }

    public string LastError { get; private set; } = string.Empty;

    public void Start()
    {
        lock (_gate)
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            if (_client is not null)
            {
                return;
            }

            _client = new UdpClient(new IPEndPoint(IPAddress.Any, _port))
            {
                EnableBroadcast = true
            };
            _cancellation = new CancellationTokenSource();
            _listenLoop = Task.Run(() => ListenLoopAsync(_cancellation.Token));
            LastError = string.Empty;
        }
    }

    public void Dispose()
    {
        Task? listenLoop;
        CancellationTokenSource? cancellation;
        lock (_gate)
        {
            if (_disposed)
            {
                return;
            }

            _disposed = true;
            cancellation = _cancellation;
            listenLoop = _listenLoop;
            cancellation?.Cancel();
            _client?.Dispose();
            _client = null;
        }

        try
        {
            listenLoop?.Wait(TimeSpan.FromSeconds(1));
        }
        catch (AggregateException)
        {
        }

        cancellation?.Dispose();
    }

    private async Task ListenLoopAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                var client = _client;
                if (client is null)
                {
                    return;
                }

                var result = await client.ReceiveAsync(cancellationToken).ConfigureAwait(false);
                if (!IsDiscoveryRequest(result.Buffer))
                {
                    continue;
                }

                var response = _createResponse();
                var responseJson = JsonSerializer.Serialize(response, SerializerOptions);
                var responseBytes = Encoding.UTF8.GetBytes(responseJson);
                await client.SendAsync(responseBytes, result.RemoteEndPoint, cancellationToken).ConfigureAwait(false);
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
                LastError = ex.Message;
            }
        }
    }

    private static bool IsDiscoveryRequest(byte[] bytes)
    {
        try
        {
            var json = Encoding.UTF8.GetString(bytes);
            var request = JsonSerializer.Deserialize<DiscoveryRequest>(json, SerializerOptions);
            return string.Equals(request?.Type, SyncMessageTypes.DiscoveryRequest, StringComparison.Ordinal);
        }
        catch (JsonException)
        {
            return false;
        }
    }
}
