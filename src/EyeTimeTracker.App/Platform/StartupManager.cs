using Microsoft.Win32;
using System.Windows.Forms;

namespace EyeTimeTracker.App.Platform;

public sealed class StartupManager
{
    private const string AppName = "EyeTimeTracker";
    private const string RunKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public bool IsEnabled()
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath, writable: false);
        var value = key?.GetValue(AppName) as string;

        if (string.IsNullOrWhiteSpace(value))
        {
            return false;
        }

        return string.Equals(value, StartupCommand, StringComparison.OrdinalIgnoreCase)
            || string.Equals(value.Trim('"'), Application.ExecutablePath, StringComparison.OrdinalIgnoreCase);
    }

    public void SetEnabled(bool enabled)
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath, writable: true)
            ?? Registry.CurrentUser.CreateSubKey(RunKeyPath, writable: true);

        if (key is null)
        {
            return;
        }

        if (enabled)
        {
            key.SetValue(AppName, StartupCommand, RegistryValueKind.String);
        }
        else
        {
            key.DeleteValue(AppName, throwOnMissingValue: false);
        }
    }

    private static string StartupCommand => $"\"{Application.ExecutablePath}\"";
}
