namespace EyeTimeTracker.Core.DesktopActivity;

/// <summary>
/// 时钟抽象：墙上时钟用于归档与展示，单调时钟用于运行时差值。
/// 系统时钟跳变不能产生负时长；重启后单调时钟不可续用。
/// </summary>
public interface IClock
{
    DateTimeOffset UtcNow { get; }
    long MonotonicMilliseconds { get; }
}

public sealed class SystemClock : IClock
{
    public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;
    public long MonotonicMilliseconds => Environment.TickCount64;
}

/// <summary>测试用手动时钟，由夹具推进。</summary>
public sealed class ManualClock : IClock
{
    public DateTimeOffset UtcNow { get; set; } = new(2026, 9, 29, 9, 0, 0, TimeSpan.Zero);
    public long MonotonicMilliseconds { get; set; }

    public void Advance(TimeSpan delta)
    {
        UtcNow += delta;
        MonotonicMilliseconds += (long)delta.TotalMilliseconds;
    }
}
