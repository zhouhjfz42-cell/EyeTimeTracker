using System.Diagnostics;
using System.Runtime.InteropServices;

namespace EyeTimeTracker.App.Platform;

public sealed class ForegroundAppProvider
{
    public ForegroundAppSnapshot? GetCurrent()
    {
        var window = GetForegroundWindow();
        if (window == IntPtr.Zero)
        {
            return null;
        }

        _ = GetWindowThreadProcessId(window, out var processId);
        if (processId == 0)
        {
            return null;
        }

        try
        {
            using var process = Process.GetProcessById((int)processId);
            var processName = process.ProcessName;
            var exePath = TryGetExecutablePath(process);
            var displayName = TryGetDisplayName(exePath);
            if (string.IsNullOrWhiteSpace(displayName))
            {
                displayName = processName;
            }

            return new ForegroundAppSnapshot(
                string.IsNullOrWhiteSpace(exePath) ? processName : exePath,
                displayName);
        }
        catch (Exception)
        {
            return null;
        }
    }

    private static string TryGetExecutablePath(Process process)
    {
        try
        {
            return process.MainModule?.FileName ?? string.Empty;
        }
        catch (Exception)
        {
            return string.Empty;
        }
    }

    private static string TryGetDisplayName(string exePath)
    {
        if (string.IsNullOrWhiteSpace(exePath))
        {
            return string.Empty;
        }

        try
        {
            var info = FileVersionInfo.GetVersionInfo(exePath);
            return !string.IsNullOrWhiteSpace(info.FileDescription)
                ? info.FileDescription
                : info.ProductName ?? string.Empty;
        }
        catch (Exception)
        {
            return string.Empty;
        }
    }

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
}

public sealed record ForegroundAppSnapshot(string AppId, string AppName);
