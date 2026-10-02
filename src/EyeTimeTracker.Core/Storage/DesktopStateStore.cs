using System.Text.Json;
using EyeTimeTracker.Core.DesktopActivity;

namespace EyeTimeTracker.Core.Storage;

/// <summary>desktop-state.json 读写：原子替换，坏文件回退空快照（不影响采集重启）。</summary>
public sealed class DesktopStateStore
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        WriteIndented = false
    };

    private readonly string _path;

    public DesktopStateStore(string path)
    {
        _path = path;
    }

    public string Path => _path;

    public bool Exists() => File.Exists(_path);

    public DesktopStateSnapshot? Load()
    {
        if (!File.Exists(_path))
        {
            return null;
        }

        try
        {
            var json = File.ReadAllText(_path);
            return JsonSerializer.Deserialize<DesktopStateSnapshot>(json, SerializerOptions);
        }
        catch (Exception ex) when (ex is JsonException or NotSupportedException or IOException)
        {
            return null;
        }
    }

    public void Save(DesktopStateSnapshot snapshot)
    {
        ArgumentNullException.ThrowIfNull(snapshot);
        var directory = System.IO.Path.GetDirectoryName(_path);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }

        var tempPath = System.IO.Path.Combine(
            string.IsNullOrEmpty(directory) ? "." : directory,
            $"{System.IO.Path.GetFileName(_path)}.{Guid.NewGuid():N}.tmp");
        try
        {
            var json = JsonSerializer.Serialize(snapshot, SerializerOptions);
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
}
