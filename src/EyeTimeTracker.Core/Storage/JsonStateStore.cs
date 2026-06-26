using System.Text.Json;
using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Storage;

public sealed class JsonStateStore
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        WriteIndented = true
    };

    private readonly string _path;

    public JsonStateStore(string path)
    {
        _path = path;
    }

    public AppState Load()
    {
        if (!File.Exists(_path))
        {
            return new AppState();
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

        state.Settings ??= TrackerSettings.Default;
        state.Records ??= new List<DailyRecord>();
        return state;
    }
}
