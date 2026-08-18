namespace EyeTimeTracker.App.Diagnostics;

public static class ReminderDiagnosticLog
{
    private const int MaxEntries = 100;
    private static readonly object Gate = new();

    public static void Record(
        string reminderType,
        DateTimeOffset triggeredAt,
        long sessionSeconds,
        int thresholdSeconds,
        int step)
    {
        WriteLine(string.Join(
            " | ",
            triggeredAt.ToLocalTime().ToString("yyyy-MM-dd HH:mm:ss"),
            reminderType,
            $"连续={Math.Max(0, sessionSeconds)}秒",
            $"阈值={Math.Max(0, thresholdSeconds)}秒",
            $"第{Math.Max(0, step)}次"));
    }

    public static void RecordEvent(
        string eventName,
        DateTimeOffset occurredAt,
        string? requestId = null,
        string? details = null)
    {
        var parts = new List<string>
        {
            occurredAt.ToLocalTime().ToString("yyyy-MM-dd HH:mm:ss.fff"),
            eventName
        };

        if (!string.IsNullOrWhiteSpace(requestId))
        {
            parts.Add($"请求={Clean(requestId, 40)}");
        }

        if (!string.IsNullOrWhiteSpace(details))
        {
            parts.Add($"详情={Clean(details, 240)}");
        }

        WriteLine(string.Join(" | ", parts));
    }

    private static void WriteLine(string line)
    {
        try
        {
            lock (Gate)
            {
                var directory = Path.GetDirectoryName(AppPaths.ReminderDiagnosticLogPath);
                if (!string.IsNullOrWhiteSpace(directory))
                {
                    Directory.CreateDirectory(directory);
                }

                var entries = File.Exists(AppPaths.ReminderDiagnosticLogPath)
                    ? File.ReadAllLines(AppPaths.ReminderDiagnosticLogPath).TakeLast(MaxEntries - 1).ToList()
                    : new List<string>();
                entries.Add(line);
                File.WriteAllLines(AppPaths.ReminderDiagnosticLogPath, entries);
            }
        }
        catch (IOException)
        {
            // Diagnostics must never interfere with the reminder itself.
        }
        catch (UnauthorizedAccessException)
        {
            // Diagnostics must never interfere with the reminder itself.
        }
    }

    private static string Clean(string value, int maxLength)
    {
        var cleaned = value.Replace('\r', ' ').Replace('\n', ' ').Trim();
        return cleaned.Length <= maxLength ? cleaned : cleaned[..maxLength];
    }
}
