using System.Text.Json;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Sync;

namespace EyeTimeTracker.Core.Storage;

public sealed class JsonStateStore
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        WriteIndented = false,
        Converters = { new UsageSegmentJsonConverter() }
    };

    // 最近 2 天的 segment 保持原样（增量同步按 ID 比对），更早的稳定段压缩成时间区间段
    private const long RecentRawSegmentSeconds = 2 * 24 * 60 * 60;

    private readonly string _path;
    private readonly Func<long> _unixClock;

    public JsonStateStore(string path)
        : this(path, null)
    {
    }

    public JsonStateStore(string path, Func<long>? unixClock)
    {
        _path = path;
        _unixClock = unixClock ?? (() => DateTimeOffset.UtcNow.ToUnixTimeSeconds());
    }

    public AppState Load()
    {
        if (!File.Exists(_path))
        {
            return Normalize(new AppState());
        }

        try
        {
            var json = File.ReadAllText(_path);
            var state = JsonSerializer.Deserialize<AppState>(json, SerializerOptions);
            return Normalize(state);
        }
        catch (Exception ex) when (ex is JsonException or NotSupportedException or IOException)
        {
            return new AppState();
        }
    }

    public void Save(AppState state)
    {
        var directory = Path.GetDirectoryName(_path);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }

        var tempPath = Path.Combine(
            string.IsNullOrEmpty(directory) ? "." : directory,
            $"{Path.GetFileName(_path)}.{Guid.NewGuid():N}.tmp");

        try
        {
            state.Segments = SegmentStorageCompactor.CompactForStorage(
                state.Segments,
                _unixClock() - RecentRawSegmentSeconds);
            var json = JsonSerializer.Serialize(state, SerializerOptions);
            File.WriteAllText(tempPath, json);

            if (File.Exists(_path))
            {
                File.Replace(tempPath, _path, null);
            }
            else
            {
                File.Move(tempPath, _path);
            }
        }
        finally
        {
            if (File.Exists(tempPath))
            {
                File.Delete(tempPath);
            }
        }
    }

    private static AppState Normalize(AppState? state)
    {
        if (state is null)
        {
            return new AppState();
        }

        AppState.Normalize(state);

        return state;
    }
}
