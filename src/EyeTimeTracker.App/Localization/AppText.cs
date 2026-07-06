using System.Text.Json;

namespace EyeTimeTracker.App.Localization;

public static class AppText
{
    private const string DefaultLocale = "zh-CN";
    private static readonly Lazy<IReadOnlyDictionary<string, string>> Messages = new(LoadMessages);

    public static string Get(string key)
    {
        if (string.IsNullOrWhiteSpace(key))
        {
            return string.Empty;
        }

        return Messages.Value.TryGetValue(key, out var value) ? value : key;
    }

    public static string Format(string key, params (string Name, object? Value)[] values)
    {
        var text = Get(key);
        foreach (var (name, value) in values)
        {
            if (string.IsNullOrWhiteSpace(name))
            {
                continue;
            }

            text = text.Replace("{" + name + "}", Convert.ToString(value) ?? string.Empty, StringComparison.Ordinal);
        }

        return text;
    }

    private static IReadOnlyDictionary<string, string> LoadMessages()
    {
        var path = FindLocaleFile(DefaultLocale);
        if (string.IsNullOrEmpty(path))
        {
            return new Dictionary<string, string>();
        }

        var json = File.ReadAllText(path);
        return JsonSerializer.Deserialize<Dictionary<string, string>>(json) ?? new Dictionary<string, string>();
    }

    private static string FindLocaleFile(string locale)
    {
        var relativePath = Path.Combine("i18n", "source", locale + ".json");

        foreach (var root in CandidateRoots())
        {
            var path = Path.Combine(root, relativePath);
            if (File.Exists(path))
            {
                return path;
            }
        }

        return string.Empty;
    }

    private static IEnumerable<string> CandidateRoots()
    {
        yield return AppContext.BaseDirectory;
        yield return Directory.GetCurrentDirectory();

        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null)
        {
            yield return directory.FullName;
            directory = directory.Parent;
        }
    }
}
