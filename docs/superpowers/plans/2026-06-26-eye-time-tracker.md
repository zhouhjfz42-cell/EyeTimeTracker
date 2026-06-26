# Eye Time Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight Windows tray app that tracks local screen/eye time, reminds once at 5h30m, and shows daily/weekly/monthly totals.

**Architecture:** Split the app into a testable .NET core library and a thin Windows Forms shell. The core owns short-segment accumulation, settings, daily records, persistence, and reminder decisions; the app project owns Windows APIs, tray UI, notifications, and startup integration.

**Tech Stack:** .NET 8, Windows Forms, Win32 idle-time API, Windows Core Audio APIs, JSON local storage, no external NuGet packages.

---

## Current Local Constraint

This machine currently has .NET runtimes but no .NET SDK. Implementation requires installing or providing .NET 8 SDK before build/test commands can run.

Check:

```powershell
dotnet --list-sdks
```

Expected after setup: at least one `8.0.x` SDK line.

## Planned File Structure

- `src/EyeTimeTracker.Core/EyeTimeTracker.Core.csproj` - testable core library.
- `src/EyeTimeTracker.Core/Models/TrackerSettings.cs` - user settings and defaults.
- `src/EyeTimeTracker.Core/Models/DailyRecord.cs` - one day's stored totals.
- `src/EyeTimeTracker.Core/Models/AppState.cs` - persisted settings and daily records.
- `src/EyeTimeTracker.Core/Tracking/ActivitySnapshot.cs` - platform-independent activity state.
- `src/EyeTimeTracker.Core/Tracking/EyeTimeAccumulator.cs` - short-segment counting logic.
- `src/EyeTimeTracker.Core/Reminders/DailyReminderPolicy.cs` - once-per-day reminder decision.
- `src/EyeTimeTracker.Core/Storage/JsonStateStore.cs` - local JSON load/save.
- `src/EyeTimeTracker.App/EyeTimeTracker.App.csproj` - Windows Forms app.
- `src/EyeTimeTracker.App/Program.cs` - WinForms entry point.
- `src/EyeTimeTracker.App/AppPaths.cs` - app-data file locations.
- `src/EyeTimeTracker.App/Platform/IdleTimeProvider.cs` - low-cost Win32 idle detection.
- `src/EyeTimeTracker.App/Platform/AudioActivityProvider.cs` - lightweight audio-output detection.
- `src/EyeTimeTracker.App/Platform/StartupManager.cs` - current-user startup registration.
- `src/EyeTimeTracker.App/Platform/NotificationService.cs` - Windows notification fallback.
- `src/EyeTimeTracker.App/Tracking/TrackingController.cs` - timer loop, persistence flush, reminder call.
- `src/EyeTimeTracker.App/UI/MainForm.cs` - simple statistics/settings window.
- `src/EyeTimeTracker.App/UI/TrayApplicationContext.cs` - tray icon and app lifetime.
- `tests/EyeTimeTracker.Tests/EyeTimeTracker.Tests.csproj` - dependency-free console tests.
- `tests/EyeTimeTracker.Tests/Program.cs` - test runner with built-in assertions.

## Task 0: Tooling And Workspace Preparation

**Files:**
- Create: `src/`
- Create: `tests/`
- Create: `.gitignore`

- [ ] **Step 1: Verify .NET SDK**

Run:

```powershell
dotnet --list-sdks
```

Expected: at least one `8.0.x` SDK. If no SDK appears, install .NET 8 SDK, then rerun the command.

- [ ] **Step 2: Initialize Git if needed**

Run:

```powershell
git rev-parse --is-inside-work-tree
```

Expected: `true`. If it fails with "not a git repository", run:

```powershell
git init
```

- [ ] **Step 3: Create ignore file**

Create `.gitignore`:

```gitignore
bin/
obj/
.vs/
*.user
*.suo
TestResults/
publish/
```

- [ ] **Step 4: Commit workspace prep**

Run:

```powershell
git add .gitignore docs/superpowers/specs/2026-06-26-eye-time-tracker-design.md docs/superpowers/plans/2026-06-26-eye-time-tracker.md
git commit -m "docs: plan eye time tracker"
```

Expected: commit succeeds.

## Task 1: Create Projects

**Files:**
- Create: `src/EyeTimeTracker.Core/EyeTimeTracker.Core.csproj`
- Create: `src/EyeTimeTracker.App/EyeTimeTracker.App.csproj`
- Create: `tests/EyeTimeTracker.Tests/EyeTimeTracker.Tests.csproj`

- [ ] **Step 1: Create core project file**

Create `src/EyeTimeTracker.Core/EyeTimeTracker.Core.csproj`:

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
  </PropertyGroup>
</Project>
```

- [ ] **Step 2: Create app project file**

Create `src/EyeTimeTracker.App/EyeTimeTracker.App.csproj`:

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <OutputType>WinExe</OutputType>
    <TargetFramework>net8.0-windows</TargetFramework>
    <UseWindowsForms>true</UseWindowsForms>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
  </PropertyGroup>
  <ItemGroup>
    <ProjectReference Include="..\EyeTimeTracker.Core\EyeTimeTracker.Core.csproj" />
  </ItemGroup>
</Project>
```

- [ ] **Step 3: Create console test project file**

Create `tests/EyeTimeTracker.Tests/EyeTimeTracker.Tests.csproj`:

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net8.0</TargetFramework>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
  </PropertyGroup>
  <ItemGroup>
    <ProjectReference Include="..\..\src\EyeTimeTracker.Core\EyeTimeTracker.Core.csproj" />
  </ItemGroup>
</Project>
```

- [ ] **Step 4: Verify empty projects restore/build**

Run:

```powershell
dotnet build tests/EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: fails because `Program.cs` does not exist yet, or succeeds after SDK creates implicit entry-point rules. This step verifies SDK/project parsing.

- [ ] **Step 5: Commit project skeleton**

Run:

```powershell
git add src tests
git commit -m "chore: create eye time tracker projects"
```

Expected: commit succeeds.

## Task 2: Core Counting Logic

**Files:**
- Create: `tests/EyeTimeTracker.Tests/Program.cs`
- Create: `src/EyeTimeTracker.Core/Models/TrackerSettings.cs`
- Create: `src/EyeTimeTracker.Core/Models/DailyRecord.cs`
- Create: `src/EyeTimeTracker.Core/Tracking/ActivitySnapshot.cs`
- Create: `src/EyeTimeTracker.Core/Tracking/EyeTimeAccumulator.cs`

- [ ] **Step 1: Write failing tests for short-segment accumulation**

Create `tests/EyeTimeTracker.Tests/Program.cs`:

```csharp
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Tracking;

static void AssertEqual<T>(T expected, T actual, string name)
{
    if (!EqualityComparer<T>.Default.Equals(expected, actual))
        throw new Exception($"{name}: expected {expected}, actual {actual}");
}

static ActivitySnapshot Snapshot(DateTimeOffset now, int idleSeconds, bool audio = false, bool unlocked = true)
{
    return new ActivitySnapshot(now, TimeSpan.FromSeconds(idleSeconds), audio, unlocked, false);
}

static void TestDoesNotBackfillLongIdleGap()
{
    var settings = TrackerSettings.Default;
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 9, 0, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 600), settings);
    acc.Tick(Snapshot(t0.AddMinutes(4), idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddMinutes(7), idleSeconds: 180), settings);
    acc.Tick(Snapshot(t0.AddMinutes(58), idleSeconds: 0), settings);

    AssertEqual(180L, acc.Today.TotalSeconds, nameof(TestDoesNotBackfillLongIdleGap));
}

static void TestIdleThresholdStopsCounting()
{
    var settings = TrackerSettings.Default with { IdleThresholdSeconds = 180, CountAudio = false };
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 10, 0, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddSeconds(10), idleSeconds: 10), settings);
    acc.Tick(Snapshot(t0.AddSeconds(190), idleSeconds: 190), settings);
    acc.Tick(Snapshot(t0.AddSeconds(200), idleSeconds: 200), settings);

    AssertEqual(180L, acc.Today.TotalSeconds, nameof(TestIdleThresholdStopsCounting));
}

static void TestAudioCountsWithoutInput()
{
    var settings = TrackerSettings.Default with { CountAudio = true };
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 11, 0, 0, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 600, audio: true), settings);
    acc.Tick(Snapshot(t0.AddSeconds(30), idleSeconds: 630, audio: true), settings);

    AssertEqual(30L, acc.Today.TotalSeconds, nameof(TestAudioCountsWithoutInput));
}

static void TestDateRolloverStartsNewDay()
{
    var settings = TrackerSettings.Default;
    var acc = new EyeTimeAccumulator(new DateOnly(2026, 6, 26));
    var t0 = new DateTimeOffset(2026, 6, 26, 23, 59, 50, TimeSpan.Zero);

    acc.Tick(Snapshot(t0, idleSeconds: 0), settings);
    acc.Tick(Snapshot(t0.AddSeconds(20), idleSeconds: 20), settings);

    AssertEqual(new DateOnly(2026, 6, 27), acc.Today.Date, nameof(TestDateRolloverStartsNewDay) + " date");
    AssertEqual(0L, acc.Today.TotalSeconds, nameof(TestDateRolloverStartsNewDay) + " seconds");
}

TestDoesNotBackfillLongIdleGap();
TestIdleThresholdStopsCounting();
TestAudioCountsWithoutInput();
TestDateRolloverStartsNewDay();
Console.WriteLine("All tests passed.");
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: compile fails because `TrackerSettings`, `ActivitySnapshot`, and `EyeTimeAccumulator` do not exist.

- [ ] **Step 3: Implement settings and daily record**

Create `src/EyeTimeTracker.Core/Models/TrackerSettings.cs`:

```csharp
namespace EyeTimeTracker.Core.Models;

public sealed record TrackerSettings(
    int IdleThresholdSeconds,
    bool CountAudio,
    int ReminderThresholdSeconds,
    bool StartWithWindows)
{
    public static TrackerSettings Default { get; } = new(
        IdleThresholdSeconds: 180,
        CountAudio: true,
        ReminderThresholdSeconds: 5 * 3600 + 30 * 60,
        StartWithWindows: true);
}
```

Create `src/EyeTimeTracker.Core/Models/DailyRecord.cs`:

```csharp
namespace EyeTimeTracker.Core.Models;

public sealed class DailyRecord
{
    public DateOnly Date { get; set; }
    public long TotalSeconds { get; set; }
    public bool ReminderShown { get; set; }

    public DailyRecord()
    {
    }

    public DailyRecord(DateOnly date)
    {
        Date = date;
    }
}
```

- [ ] **Step 4: Implement activity snapshot and accumulator**

Create `src/EyeTimeTracker.Core/Tracking/ActivitySnapshot.cs`:

```csharp
namespace EyeTimeTracker.Core.Tracking;

public sealed record ActivitySnapshot(
    DateTimeOffset Timestamp,
    TimeSpan IdleTime,
    bool IsAudioActive,
    bool IsSessionUnlocked,
    bool IsSuspended);
```

Create `src/EyeTimeTracker.Core/Tracking/EyeTimeAccumulator.cs`:

```csharp
using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Tracking;

public sealed class EyeTimeAccumulator
{
    private DateTimeOffset? _lastTick;

    public DailyRecord Today { get; private set; }

    public EyeTimeAccumulator(DateOnly today, long initialSeconds = 0, bool reminderShown = false)
    {
        Today = new DailyRecord(today)
        {
            TotalSeconds = initialSeconds,
            ReminderShown = reminderShown
        };
    }

    public bool IsCounting { get; private set; }

    public void Tick(ActivitySnapshot snapshot, TrackerSettings settings)
    {
        var snapshotDate = DateOnly.FromDateTime(snapshot.Timestamp.LocalDateTime);
        if (snapshotDate != Today.Date)
        {
            Today = new DailyRecord(snapshotDate);
            _lastTick = snapshot.Timestamp;
            IsCounting = ShouldCount(snapshot, settings);
            return;
        }

        if (_lastTick is null)
        {
            _lastTick = snapshot.Timestamp;
            IsCounting = ShouldCount(snapshot, settings);
            return;
        }

        var elapsed = snapshot.Timestamp - _lastTick.Value;
        _lastTick = snapshot.Timestamp;

        if (elapsed <= TimeSpan.Zero || elapsed > TimeSpan.FromMinutes(5))
        {
            IsCounting = ShouldCount(snapshot, settings);
            return;
        }

        var countedSeconds = CountableSeconds(snapshot, settings, elapsed);
        IsCounting = countedSeconds > 0 || ShouldCount(snapshot, settings);
        Today.TotalSeconds += countedSeconds;
    }

    private static bool ShouldCount(ActivitySnapshot snapshot, TrackerSettings settings)
    {
        if (!snapshot.IsSessionUnlocked || snapshot.IsSuspended)
            return false;

        var hasRecentInput = snapshot.IdleTime.TotalSeconds <= settings.IdleThresholdSeconds;
        var hasAudio = settings.CountAudio && snapshot.IsAudioActive;
        return hasRecentInput || hasAudio;
    }

    private static long CountableSeconds(ActivitySnapshot snapshot, TrackerSettings settings, TimeSpan elapsed)
    {
        if (!snapshot.IsSessionUnlocked || snapshot.IsSuspended)
            return 0;

        var intervalEnd = snapshot.Timestamp;
        var intervalStart = intervalEnd - elapsed;
        var audioSeconds = settings.CountAudio && snapshot.IsAudioActive
            ? elapsed.TotalSeconds
            : 0;

        var lastInputAt = snapshot.Timestamp - snapshot.IdleTime;
        var activeUntil = lastInputAt + TimeSpan.FromSeconds(settings.IdleThresholdSeconds);
        var inputStart = Max(intervalStart, lastInputAt);
        var inputEnd = Min(intervalEnd, activeUntil);
        var inputSeconds = inputEnd > inputStart ? (inputEnd - inputStart).TotalSeconds : 0;

        return (long)Math.Round(Math.Max(audioSeconds, inputSeconds));
    }

    private static DateTimeOffset Max(DateTimeOffset left, DateTimeOffset right)
    {
        return left >= right ? left : right;
    }

    private static DateTimeOffset Min(DateTimeOffset left, DateTimeOffset right)
    {
        return left <= right ? left : right;
    }
}
```

- [ ] **Step 5: Run tests**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: `All tests passed.`

- [ ] **Step 6: Commit core counting logic**

Run:

```powershell
git add src/EyeTimeTracker.Core tests/EyeTimeTracker.Tests
git commit -m "feat: add short-segment eye time accumulator"
```

Expected: commit succeeds.

## Task 3: Reminder Policy And JSON Storage

**Files:**
- Modify: `tests/EyeTimeTracker.Tests/Program.cs`
- Create: `src/EyeTimeTracker.Core/Models/AppState.cs`
- Create: `src/EyeTimeTracker.Core/Reminders/DailyReminderPolicy.cs`
- Create: `src/EyeTimeTracker.Core/Storage/JsonStateStore.cs`

- [ ] **Step 1: Add failing tests for reminders and storage**

Append to `tests/EyeTimeTracker.Tests/Program.cs` before `Console.WriteLine("All tests passed.");`:

```csharp
static void TestReminderOnlyOncePerDay()
{
    var record = new DailyRecord(new DateOnly(2026, 6, 26)) { TotalSeconds = 19800 };
    var policy = new DailyReminderPolicy();

    AssertEqual(true, policy.ShouldNotify(record, TrackerSettings.Default), "first reminder");
    policy.MarkShown(record);
    AssertEqual(false, policy.ShouldNotify(record, TrackerSettings.Default), "second reminder");
}

static void TestJsonStateRoundTrip()
{
    var path = Path.Combine(Path.GetTempPath(), "eye-time-test-state.json");
    if (File.Exists(path)) File.Delete(path);

    var state = new AppState
    {
        Settings = TrackerSettings.Default with { IdleThresholdSeconds = 120 },
        Records = new List<DailyRecord>
        {
            new(new DateOnly(2026, 6, 26)) { TotalSeconds = 42, ReminderShown = true }
        }
    };

    var store = new JsonStateStore(path);
    store.Save(state);
    var loaded = store.Load();

    AssertEqual(120, loaded.Settings.IdleThresholdSeconds, "settings roundtrip");
    AssertEqual(42L, loaded.Records.Single().TotalSeconds, "record roundtrip");
}

TestReminderOnlyOncePerDay();
TestJsonStateRoundTrip();
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: compile fails because reminder and storage classes do not exist.

- [ ] **Step 3: Implement app state**

Create `src/EyeTimeTracker.Core/Models/AppState.cs`:

```csharp
namespace EyeTimeTracker.Core.Models;

public sealed class AppState
{
    public TrackerSettings Settings { get; set; } = TrackerSettings.Default;
    public List<DailyRecord> Records { get; set; } = new();

    public DailyRecord GetOrCreateRecord(DateOnly date)
    {
        var record = Records.FirstOrDefault(r => r.Date == date);
        if (record is not null)
            return record;

        record = new DailyRecord(date);
        Records.Add(record);
        return record;
    }
}
```

- [ ] **Step 4: Implement reminder policy**

Create `src/EyeTimeTracker.Core/Reminders/DailyReminderPolicy.cs`:

```csharp
using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Reminders;

public sealed class DailyReminderPolicy
{
    public bool ShouldNotify(DailyRecord record, TrackerSettings settings)
    {
        return !record.ReminderShown && record.TotalSeconds >= settings.ReminderThresholdSeconds;
    }

    public void MarkShown(DailyRecord record)
    {
        record.ReminderShown = true;
    }
}
```

- [ ] **Step 5: Implement JSON store**

Create `src/EyeTimeTracker.Core/Storage/JsonStateStore.cs`:

```csharp
using System.Text.Json;
using System.Text.Json.Serialization;
using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Storage;

public sealed class JsonStateStore
{
    private static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter() }
    };

    private readonly string _path;

    public JsonStateStore(string path)
    {
        _path = path;
    }

    public AppState Load()
    {
        try
        {
            if (!File.Exists(_path))
                return new AppState();

            var json = File.ReadAllText(_path);
            return JsonSerializer.Deserialize<AppState>(json, Options) ?? new AppState();
        }
        catch
        {
            return new AppState();
        }
    }

    public void Save(AppState state)
    {
        var directory = Path.GetDirectoryName(_path);
        if (!string.IsNullOrWhiteSpace(directory))
            Directory.CreateDirectory(directory);

        var tempPath = _path + ".tmp";
        File.WriteAllText(tempPath, JsonSerializer.Serialize(state, Options));
        if (File.Exists(_path))
            File.Delete(_path);
        File.Move(tempPath, _path);
    }
}
```

- [ ] **Step 6: Run tests**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: `All tests passed.`

- [ ] **Step 7: Commit persistence and reminder logic**

Run:

```powershell
git add src/EyeTimeTracker.Core tests/EyeTimeTracker.Tests
git commit -m "feat: add reminder policy and local state storage"
```

Expected: commit succeeds.

## Task 4: Windows Platform Adapters

**Files:**
- Create: `src/EyeTimeTracker.App/AppPaths.cs`
- Create: `src/EyeTimeTracker.App/Platform/IdleTimeProvider.cs`
- Create: `src/EyeTimeTracker.App/Platform/AudioActivityProvider.cs`
- Create: `src/EyeTimeTracker.App/Platform/StartupManager.cs`
- Create: `src/EyeTimeTracker.App/Platform/NotificationService.cs`

- [ ] **Step 1: Add app paths**

Create `src/EyeTimeTracker.App/AppPaths.cs`:

```csharp
namespace EyeTimeTracker.App;

public static class AppPaths
{
    public static string AppDataDirectory =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "EyeTimeTracker");

    public static string StateFilePath => Path.Combine(AppDataDirectory, "state.json");
}
```

- [ ] **Step 2: Add idle time provider**

Create `src/EyeTimeTracker.App/Platform/IdleTimeProvider.cs`:

```csharp
using System.Runtime.InteropServices;

namespace EyeTimeTracker.App.Platform;

public sealed class IdleTimeProvider
{
    public TimeSpan GetIdleTime()
    {
        var info = new LASTINPUTINFO
        {
            cbSize = (uint)Marshal.SizeOf<LASTINPUTINFO>()
        };

        if (!GetLastInputInfo(ref info))
            return TimeSpan.Zero;

        var idleMilliseconds = Environment.TickCount64 - info.dwTime;
        return TimeSpan.FromMilliseconds(Math.Max(0, idleMilliseconds));
    }

    [DllImport("user32.dll")]
    private static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);

    [StructLayout(LayoutKind.Sequential)]
    private struct LASTINPUTINFO
    {
        public uint cbSize;
        public uint dwTime;
    }
}
```

- [ ] **Step 3: Add lightweight audio provider**

Create `src/EyeTimeTracker.App/Platform/AudioActivityProvider.cs`:

```csharp
using System.Runtime.InteropServices;

namespace EyeTimeTracker.App.Platform;

public sealed class AudioActivityProvider
{
    public bool IsAudioActive()
    {
        try
        {
            using var enumerator = new ComReleaser<IMMDeviceEnumerator>(
                (IMMDeviceEnumerator)new MMDeviceEnumerator());
            enumerator.Value.GetDefaultAudioEndpoint(EDataFlow.eRender, ERole.eMultimedia, out var device);
            using var deviceReleaser = new ComReleaser<IMMDevice>(device);

            var iid = typeof(IAudioMeterInformation).GUID;
            deviceReleaser.Value.Activate(ref iid, 0, IntPtr.Zero, out var meterObject);
            using var meterReleaser = new ComReleaser<IAudioMeterInformation>((IAudioMeterInformation)meterObject);
            meterReleaser.Value.GetPeakValue(out var peak);
            return peak > 0.01f;
        }
        catch
        {
            return false;
        }
    }

    private sealed class ComReleaser<T> : IDisposable where T : class
    {
        public T Value { get; }

        public ComReleaser(T value)
        {
            Value = value;
        }

        public void Dispose()
        {
            if (Marshal.IsComObject(Value))
                Marshal.ReleaseComObject(Value);
        }
    }

    private enum EDataFlow { eRender, eCapture, eAll }
    private enum ERole { eConsole, eMultimedia, eCommunications }

    [ComImport]
    [Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private sealed class MMDeviceEnumerator
    {
    }

    [ComImport]
    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceEnumerator
    {
        void EnumAudioEndpoints(EDataFlow dataFlow, uint dwStateMask, out object ppDevices);
        void GetDefaultAudioEndpoint(EDataFlow dataFlow, ERole role, out IMMDevice ppEndpoint);
    }

    [ComImport]
    [Guid("D666063F-1587-4E43-81F1-B948E807363F")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDevice
    {
        void Activate(ref Guid iid, uint dwClsCtx, IntPtr pActivationParams, [MarshalAs(UnmanagedType.IUnknown)] out object ppInterface);
    }

    [ComImport]
    [Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioMeterInformation
    {
        void GetPeakValue(out float pfPeak);
    }
}
```

- [ ] **Step 4: Add startup manager**

Create `src/EyeTimeTracker.App/Platform/StartupManager.cs`:

```csharp
using Microsoft.Win32;
using System.Windows.Forms;

namespace EyeTimeTracker.App.Platform;

public sealed class StartupManager
{
    private const string AppName = "EyeTimeTracker";
    private const string RunKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public bool IsEnabled()
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath, false);
        return key?.GetValue(AppName) is string;
    }

    public void SetEnabled(bool enabled)
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath, true)
            ?? Registry.CurrentUser.CreateSubKey(RunKeyPath, true);

        if (enabled)
        {
            var exePath = Application.ExecutablePath;
            key.SetValue(AppName, $"\"{exePath}\"");
        }
        else
        {
            key.DeleteValue(AppName, false);
        }
    }
}
```

- [ ] **Step 5: Add notification service**

Create `src/EyeTimeTracker.App/Platform/NotificationService.cs`:

```csharp
namespace EyeTimeTracker.App.Platform;

public sealed class NotificationService
{
    private readonly NotifyIcon _notifyIcon;

    public NotificationService(NotifyIcon notifyIcon)
    {
        _notifyIcon = notifyIcon;
    }

    public void ShowDailyLimitReminder(TimeSpan total)
    {
        _notifyIcon.BalloonTipTitle = "用眼时间提醒";
        _notifyIcon.BalloonTipText = $"今天电脑用眼时间已达到 {Format(total)}，建议休息一下。";
        _notifyIcon.ShowBalloonTip(5000);
    }

    private static string Format(TimeSpan value)
    {
        return $"{(int)value.TotalHours}小时{value.Minutes:D2}分";
    }
}
```

- [ ] **Step 6: Compile adapters as a library**

Run:

```powershell
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj /p:OutputType=Library
```

Expected: `Build succeeded.` This checks the adapter code before the WinForms entry point is added.

- [ ] **Step 7: Commit platform adapters**

Run:

```powershell
git add src/EyeTimeTracker.App
git commit -m "feat: add windows platform adapters"
```

Expected: commit succeeds.

## Task 5: Tracking Controller

**Files:**
- Create: `src/EyeTimeTracker.App/Tracking/TrackingController.cs`

- [ ] **Step 1: Implement timer controller**

Create `src/EyeTimeTracker.App/Tracking/TrackingController.cs`:

```csharp
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.Core.Models;
using EyeTimeTracker.Core.Reminders;
using EyeTimeTracker.Core.Storage;
using EyeTimeTracker.Core.Tracking;

namespace EyeTimeTracker.App.Tracking;

public sealed class TrackingController : IDisposable
{
    private readonly JsonStateStore _store;
    private readonly IdleTimeProvider _idle;
    private readonly AudioActivityProvider _audio;
    private readonly DailyReminderPolicy _reminders = new();
    private readonly NotificationService _notifications;
    private readonly System.Threading.Timer _timer;
    private readonly object _gate = new();
    private DateTimeOffset _lastSave = DateTimeOffset.MinValue;

    public AppState State { get; private set; }
    public EyeTimeAccumulator Accumulator { get; private set; }

    public event EventHandler? Updated;

    public TrackingController(JsonStateStore store, IdleTimeProvider idle, AudioActivityProvider audio, NotificationService notifications)
    {
        _store = store;
        _idle = idle;
        _audio = audio;
        _notifications = notifications;
        State = _store.Load();

        var today = DateOnly.FromDateTime(DateTime.Now);
        var record = State.GetOrCreateRecord(today);
        Accumulator = new EyeTimeAccumulator(today, record.TotalSeconds, record.ReminderShown);
        _timer = new System.Threading.Timer(_ => Tick(), null, TimeSpan.Zero, TimeSpan.FromSeconds(10));
    }

    public void Tick()
    {
        lock (_gate)
        {
            var now = DateTimeOffset.Now;
            var snapshot = new ActivitySnapshot(
                now,
                _idle.GetIdleTime(),
                State.Settings.CountAudio && _audio.IsAudioActive(),
                SystemInformation.UserInteractive,
                false);

            var beforeDate = Accumulator.Today.Date;
            Accumulator.Tick(snapshot, State.Settings);

            if (Accumulator.Today.Date != beforeDate)
                State.GetOrCreateRecord(Accumulator.Today.Date);

            var record = State.GetOrCreateRecord(Accumulator.Today.Date);
            record.TotalSeconds = Accumulator.Today.TotalSeconds;
            record.ReminderShown = Accumulator.Today.ReminderShown;

            if (_reminders.ShouldNotify(record, State.Settings))
            {
                _notifications.ShowDailyLimitReminder(TimeSpan.FromSeconds(record.TotalSeconds));
                _reminders.MarkShown(record);
                Accumulator.Today.ReminderShown = true;
            }

            if (now - _lastSave >= TimeSpan.FromMinutes(1))
            {
                _store.Save(State);
                _lastSave = now;
            }
        }

        Updated?.Invoke(this, EventArgs.Empty);
    }

    public void SaveNow()
    {
        lock (_gate)
        {
            var record = State.GetOrCreateRecord(Accumulator.Today.Date);
            record.TotalSeconds = Accumulator.Today.TotalSeconds;
            record.ReminderShown = Accumulator.Today.ReminderShown;
            _store.Save(State);
            _lastSave = DateTimeOffset.Now;
        }
    }

    public void Dispose()
    {
        _timer.Dispose();
        SaveNow();
    }
}
```

- [ ] **Step 2: Compile controller as a library**

Run:

```powershell
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj /p:OutputType=Library
```

Expected: `Build succeeded.` This checks the controller before the WinForms entry point is added.

- [ ] **Step 3: Commit tracking controller**

Run:

```powershell
git add src/EyeTimeTracker.App/Tracking
git commit -m "feat: add background tracking controller"
```

Expected: commit succeeds.

## Task 6: Tray App And Main Window

**Files:**
- Create: `src/EyeTimeTracker.App/Program.cs`
- Create: `src/EyeTimeTracker.App/UI/MainForm.cs`
- Create: `src/EyeTimeTracker.App/UI/TrayApplicationContext.cs`

- [ ] **Step 1: Add WinForms entry point**

Create `src/EyeTimeTracker.App/Program.cs`:

```csharp
using EyeTimeTracker.App.UI;

namespace EyeTimeTracker.App;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new TrayApplicationContext());
    }
}
```

- [ ] **Step 2: Add main window**

Create `src/EyeTimeTracker.App/UI/MainForm.cs`:

```csharp
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;

namespace EyeTimeTracker.App.UI;

public sealed class MainForm : Form
{
    private readonly TrackingController _controller;
    private readonly StartupManager _startup;
    private readonly Label _today = new();
    private readonly Label _state = new();
    private readonly Label _week = new();
    private readonly Label _month = new();
    private readonly CheckBox _audio = new() { Text = "音频播放时计入用眼时间", AutoSize = true };
    private readonly CheckBox _startupCheck = new() { Text = "开机自启", AutoSize = true };
    private readonly NumericUpDown _idleMinutes = new() { Minimum = 1, Maximum = 30, Width = 80 };

    public MainForm(TrackingController controller, StartupManager startup)
    {
        _controller = controller;
        _startup = startup;
        Text = "用眼时间统计";
        Width = 420;
        Height = 320;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(16),
            RowCount = 8,
            ColumnCount = 2
        };
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 45));
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 55));

        AddRow(layout, 0, "今天", _today);
        AddRow(layout, 1, "状态", _state);
        AddRow(layout, 2, "本周", _week);
        AddRow(layout, 3, "本月", _month);
        AddRow(layout, 4, "键鼠空闲阈值(分钟)", _idleMinutes);
        layout.Controls.Add(_audio, 0, 5);
        layout.SetColumnSpan(_audio, 2);
        layout.Controls.Add(_startupCheck, 0, 6);
        layout.SetColumnSpan(_startupCheck, 2);

        Controls.Add(layout);

        _idleMinutes.ValueChanged += (_, _) =>
        {
            _controller.State.Settings = _controller.State.Settings with { IdleThresholdSeconds = (int)_idleMinutes.Value * 60 };
            _controller.SaveNow();
        };
        _audio.CheckedChanged += (_, _) =>
        {
            _controller.State.Settings = _controller.State.Settings with { CountAudio = _audio.Checked };
            _controller.SaveNow();
        };
        _startupCheck.CheckedChanged += (_, _) =>
        {
            _controller.State.Settings = _controller.State.Settings with { StartWithWindows = _startupCheck.Checked };
            _startup.SetEnabled(_startupCheck.Checked);
            _controller.SaveNow();
        };
        _controller.Updated += (_, _) => BeginInvoke(UpdateLabels);

        Load += (_, _) =>
        {
            _idleMinutes.Value = Math.Max(1, _controller.State.Settings.IdleThresholdSeconds / 60);
            _audio.Checked = _controller.State.Settings.CountAudio;
            _startupCheck.Checked = _controller.State.Settings.StartWithWindows;
            UpdateLabels();
        };
    }

    private static void AddRow(TableLayoutPanel parent, int row, string label, Control value)
    {
        parent.Add(new Label { Text = label, AutoSize = true, Anchor = AnchorStyles.Left }, 0, row);
        value.Anchor = AnchorStyles.Left;
        parent.Add(value, 1, row);
    }

    private void UpdateLabels()
    {
        var today = _controller.Accumulator.Today.Date;
        var daysSinceMonday = ((int)today.DayOfWeek + 6) % 7;
        _today.Text = Format(_controller.Accumulator.Today.TotalSeconds);
        _state.Text = _controller.Accumulator.IsCounting ? "正在计时" : "暂停计时";
        _week.Text = Format(SumFrom(today.AddDays(-daysSinceMonday)));
        _month.Text = Format(SumFrom(new DateOnly(today.Year, today.Month, 1)));
    }

    private long SumFrom(DateOnly start)
    {
        return _controller.State.Records
            .Where(r => r.Date >= start && r.Date <= _controller.Accumulator.Today.Date)
            .Sum(r => r.TotalSeconds);
    }

    private static string Format(long seconds)
    {
        var value = TimeSpan.FromSeconds(seconds);
        return $"{(int)value.TotalHours}小时{value.Minutes:D2}分";
    }
}
```

- [ ] **Step 3: Add tray application context**

Create `src/EyeTimeTracker.App/UI/TrayApplicationContext.cs`:

```csharp
using EyeTimeTracker.App.Platform;
using EyeTimeTracker.App.Tracking;
using EyeTimeTracker.Core.Storage;

namespace EyeTimeTracker.App.UI;

public sealed class TrayApplicationContext : ApplicationContext
{
    private readonly NotifyIcon _notifyIcon;
    private readonly StartupManager _startup = new();
    private readonly TrackingController _controller;
    private MainForm? _mainForm;

    public TrayApplicationContext()
    {
        _notifyIcon = new NotifyIcon
        {
            Icon = SystemIcons.Application,
            Text = "用眼时间统计",
            Visible = true,
            ContextMenuStrip = BuildMenu()
        };
        _notifyIcon.DoubleClick += (_, _) => ShowMainWindow();

        _controller = new TrackingController(
            new JsonStateStore(AppPaths.StateFilePath),
            new IdleTimeProvider(),
            new AudioActivityProvider(),
            new NotificationService(_notifyIcon));

        _startup.SetEnabled(_controller.State.Settings.StartWithWindows);
    }

    private ContextMenuStrip BuildMenu()
    {
        var menu = new ContextMenuStrip();
        menu.Items.Add("打开", null, (_, _) => ShowMainWindow());
        menu.Items.Add("退出", null, (_, _) => Exit());
        return menu;
    }

    private void ShowMainWindow()
    {
        if (_mainForm is null || _mainForm.IsDisposed)
            _mainForm = new MainForm(_controller, _startup);

        _mainForm.Show();
        _mainForm.WindowState = FormWindowState.Normal;
        _mainForm.Activate();
    }

    private void Exit()
    {
        _controller.Dispose();
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        ExitThread();
    }
}
```

- [ ] **Step 4: Build app**

Run:

```powershell
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj
```

Expected: `Build succeeded.`

- [ ] **Step 5: Run core tests**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: `All tests passed.`

- [ ] **Step 6: Commit UI shell**

Run:

```powershell
git add src/EyeTimeTracker.App
git commit -m "feat: add tray shell and statistics window"
```

Expected: commit succeeds.

## Task 7: Manual Verification And Publish

**Files:**
- No source files unless verification reveals a bug.

- [ ] **Step 1: Run automated verification**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -c Release
```

Expected: tests print `All tests passed.` and build prints `Build succeeded.`

- [ ] **Step 2: Start app manually**

Run:

```powershell
dotnet run --project src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -c Release
```

Expected: tray icon appears, no visible window opens until the tray icon is double-clicked or "打开" is selected.

- [ ] **Step 3: Verify low-impact behavior**

Open Windows Task Manager and observe:

```text
CPU usage should remain near 0% while idle.
Memory should stay modest for a .NET WinForms tray app.
The desktop should not freeze or pulse every 10 seconds.
```

- [ ] **Step 4: Verify local data file**

Check:

```powershell
Get-Content -Encoding UTF8 "$env:LOCALAPPDATA\EyeTimeTracker\state.json"
```

Expected: JSON contains `settings` and at least one daily `records` entry.

- [ ] **Step 5: Publish a local executable**

Run:

```powershell
dotnet publish src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -c Release -r win-x64 --self-contained false -o outputs\EyeTimeTracker
```

Expected: publish output appears under `outputs\EyeTimeTracker`.

- [ ] **Step 6: Commit final verification notes if any files changed**

Run:

```powershell
git status --short
```

Expected: no uncommitted source changes. If source changes were made during verification, commit them with:

```powershell
git add src tests docs
git commit -m "fix: polish eye time tracker verification"
```

## Self-Review Notes

- Spec coverage: the plan covers tray app, startup, short-segment accumulation, 3-minute idle threshold, audio-based counting, one daily reminder, local JSON storage, daily/weekly/monthly statistics, and low-impact sampling.
- Completion scan: no unresolved filler steps remain.
- Type consistency: `TrackerSettings`, `DailyRecord`, `AppState`, `ActivitySnapshot`, `EyeTimeAccumulator`, `DailyReminderPolicy`, and `JsonStateStore` are introduced before later tasks depend on them.
- Known prerequisite: implementation cannot compile on the current machine until .NET 8 SDK is installed or provided.
