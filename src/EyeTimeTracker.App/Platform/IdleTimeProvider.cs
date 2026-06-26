using System.Runtime.InteropServices;

namespace EyeTimeTracker.App.Platform;

public sealed class IdleTimeProvider
{
    public TimeSpan GetIdleTime()
    {
        var lastInputInfo = new LastInputInfo
        {
            CbSize = (uint)Marshal.SizeOf<LastInputInfo>()
        };

        if (!GetLastInputInfo(ref lastInputInfo))
        {
            return TimeSpan.Zero;
        }

        var currentTick = unchecked((uint)Environment.TickCount);
        var idleMilliseconds = unchecked(currentTick - lastInputInfo.DwTime);
        return TimeSpan.FromMilliseconds(idleMilliseconds);
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool GetLastInputInfo(ref LastInputInfo plii);

    [StructLayout(LayoutKind.Sequential)]
    private struct LastInputInfo
    {
        public uint CbSize;
        public uint DwTime;
    }
}
