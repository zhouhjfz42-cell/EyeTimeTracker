# WiFi 局域网同步 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build first-version WiFi LAN sync for one Windows PC and one Android phone, with phone-as-primary pairing/settings, 10-second usage segments, merged de-duplicated statistics, and local-first behavior when offline.

**Architecture:** Add a shared segment-based data model first, then derive daily/hourly/session statistics from segments, then layer pairing and LAN sync on top. PC hosts an in-app TCP JSON listener while the PC app is running; Android actively connects to PC for pairing and sync. Android remains the source of truth for reminder settings.

**Tech Stack:** .NET WinForms + `System.Text.Json` + `TcpListener`/`TcpClient`; Android Java + `org.json` + `Socket`; existing local JSON/SharedPreferences storage; existing no-dependency test programs.

---

## Scope Rules

- First version supports exactly one PC and one Android phone.
- No cloud sync, login, multi-PC, multi-phone, QR code requirement, or Windows Service.
- Old daily summary data remains visible, but exact cross-device de-duplication starts from newly recorded `UsageSegment` data.
- Do not push or commit unless the user explicitly asks. If commits are later requested, commit after each completed milestone, not every tiny step.

## File Structure

### PC Core

- Create `src/EyeTimeTracker.Core/Models/UsageSegment.cs`
  - Immutable segment model with device/platform/source/start/end/local date metadata.
- Create `src/EyeTimeTracker.Core/Models/SyncDevice.cs`
  - Paired device identity and current sync state.
- Create `src/EyeTimeTracker.Core/Models/SyncSettings.cs`
  - Pairing status, peer endpoint, paired device, sync timestamps, and shared secret.
- Create `src/EyeTimeTracker.Core/Sync/UsageSegmentId.cs`
  - Stable ID generation from device/start/end/source.
- Create `src/EyeTimeTracker.Core/Sync/UsageSegmentMerger.cs`
  - 10-second bucket merge, hourly totals, total seconds, and continuous sessions.
- Create `src/EyeTimeTracker.Core/Sync/SyncMessages.cs`
  - JSON DTOs for pair/sync/reminder messages.
- Create `src/EyeTimeTracker.Core/Sync/SyncMessageSigner.cs`
  - HMAC signing and timestamp validation.
- Modify `src/EyeTimeTracker.Core/Models/AppState.cs`
  - Add `DeviceId`, `Platform`, `Segments`, `Sync`, and normalization.
- Modify `src/EyeTimeTracker.Core/Models/DailyRecord.cs`
  - Keep existing summary fields; add optional `IsLegacyOnly` if needed for old data distinction.
- Modify `src/EyeTimeTracker.Core/Storage/JsonStateStore.cs`
  - Ensure new fields deserialize with defaults.

### PC App

- Modify `src/EyeTimeTracker.App/Tracking/TrackingController.cs`
  - Record local usage segments during ticks.
  - Rebuild daily records from merged segments where segment data exists.
  - Expose sync status and methods for sync services.
- Create `src/EyeTimeTracker.App/Sync/PcSyncServer.cs`
  - In-app TCP listener lifecycle.
- Create `src/EyeTimeTracker.App/Sync/PcPairingCoordinator.cs`
  - PC-side pairing state and code validation.
- Create `src/EyeTimeTracker.App/Sync/PcSyncCoordinator.cs`
  - Apply Android segments/settings and return PC segments.
- Modify `src/EyeTimeTracker.App/UI/MainForm.cs`
  - Add sync status entry and pairing dialog.
  - Make reminder settings read-only when paired.
- Modify `src/EyeTimeTracker.App/UI/StatsForm.cs`
  - Use merged records generated from segments.
- Modify `src/EyeTimeTracker.App/UI/TrayApplicationContext.cs`
  - Start/stop PC sync listener with app lifecycle.

### Android

- Create `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegment.java`
- Create `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegmentId.java`
- Create `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegmentMerger.java`
- Create `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncSettings.java`
- Create `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncMessages.java`
- Create `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncMessageSigner.java`
- Create `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/AndroidSyncClient.java`
- Modify `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
  - Save segments and sync settings.
  - Derive summaries from merged segments.
- Modify `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeService.java`
  - Record segments on tick.
  - Trigger sync with debounce after local changes.
- Modify `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/MainActivity.java`
  - Add sync status/pairing UI.
  - Keep Android reminder setting editable.
- Modify `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/StatsActivity.java`
  - Trigger sync on open and refresh merged statistics.
- Modify `android/EyeTimeTrackerAndroid/AndroidManifest.xml`
  - Add `android.permission.INTERNET`.

### Tests

- Modify `tests/EyeTimeTracker.Tests/Program.cs`
  - Add PC segment/merge/signing/sync-contract tests.
- Modify `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/CoreLogicTest.java`
  - Add Android segment/merge/signing/sync-contract tests.

---

## Milestone 1: Shared Segment Model and Merge Rules

### Task 1: Add PC usage segment model and tests

**Files:**
- Create: `src/EyeTimeTracker.Core/Models/UsageSegment.cs`
- Create: `src/EyeTimeTracker.Core/Sync/UsageSegmentId.cs`
- Modify: `src/EyeTimeTracker.Core/Models/AppState.cs`
- Modify: `src/EyeTimeTracker.Core/Storage/JsonStateStore.cs`
- Test: `tests/EyeTimeTracker.Tests/Program.cs`

- [ ] **Step 1: Write failing PC tests**

Add tests:

```csharp
static void TestUsageSegmentIdIsStable()
{
    var start = new DateTimeOffset(2026, 7, 2, 8, 0, 0, TimeSpan.Zero);
    var end = start.AddSeconds(10);

    var first = UsageSegmentId.Create("pc-1", "pc-input", start, end);
    var second = UsageSegmentId.Create("pc-1", "pc-input", start, end);

    AssertEqual(first, second, nameof(TestUsageSegmentIdIsStable));
}

static void TestAppStateNormalizesSegmentsAndDeviceId()
{
    var state = new AppState();
    AppState.Normalize(state);

    AssertEqual(false, string.IsNullOrWhiteSpace(state.DeviceId), nameof(TestAppStateNormalizesSegmentsAndDeviceId) + " device id");
    AssertEqual("windows", state.Platform, nameof(TestAppStateNormalizesSegmentsAndDeviceId) + " platform");
    AssertEqual(0, state.Segments.Count, nameof(TestAppStateNormalizesSegmentsAndDeviceId) + " segments");
}
```

Call both tests from the bottom of `Program.cs`.

- [ ] **Step 2: Run PC tests and verify they fail**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: compile failure because `UsageSegmentId`, `UsageSegment`, or `AppState.Normalize` does not exist.

- [ ] **Step 3: Implement PC segment model**

Create `UsageSegment.cs`:

```csharp
namespace EyeTimeTracker.Core.Models;

public sealed class UsageSegment
{
    public string SegmentId { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
    public string Platform { get; set; } = string.Empty;
    public string Source { get; set; } = string.Empty;
    public long StartUnixSeconds { get; set; }
    public long EndUnixSeconds { get; set; }
    public DateOnly LocalDate { get; set; }
    public long CreatedAtUnixSeconds { get; set; }
    public long UpdatedAtUnixSeconds { get; set; }

    public long DurationSeconds => Math.Max(0, EndUnixSeconds - StartUnixSeconds);
}
```

Create `UsageSegmentId.cs`:

```csharp
using System.Security.Cryptography;
using System.Text;

namespace EyeTimeTracker.Core.Sync;

public static class UsageSegmentId
{
    public static string Create(string deviceId, string source, DateTimeOffset start, DateTimeOffset end)
    {
        var raw = $"{deviceId}:{source}:{start.ToUnixTimeSeconds()}:{end.ToUnixTimeSeconds()}";
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(raw));
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }
}
```

Modify `AppState`:

```csharp
public string DeviceId { get; set; } = Guid.NewGuid().ToString("N");
public string Platform { get; set; } = "windows";
public List<UsageSegment> Segments { get; set; } = new();
public SyncSettings Sync { get; set; } = SyncSettings.Unpaired;

public static void Normalize(AppState state)
{
    state.Settings ??= TrackerSettings.Default;
    state.Records ??= new List<DailyRecord>();
    state.Segments ??= new List<UsageSegment>();
    state.Sync ??= SyncSettings.Unpaired;
    if (string.IsNullOrWhiteSpace(state.DeviceId))
    {
        state.DeviceId = Guid.NewGuid().ToString("N");
    }
    if (string.IsNullOrWhiteSpace(state.Platform))
    {
        state.Platform = "windows";
    }
    foreach (var record in state.Records)
    {
        NormalizeRecord(record);
    }
}
```

Create `SyncSettings.cs` with at least:

```csharp
namespace EyeTimeTracker.Core.Models;

public sealed class SyncSettings
{
    public static SyncSettings Unpaired => new();
    public bool IsPaired { get; set; }
    public string PeerDeviceId { get; set; } = string.Empty;
    public string PeerPlatform { get; set; } = string.Empty;
    public string SharedSecret { get; set; } = string.Empty;
    public string LastKnownHost { get; set; } = string.Empty;
    public int LastKnownPort { get; set; }
    public long LastSyncUnixSeconds { get; set; }
    public string LastError { get; set; } = string.Empty;
}
```

Modify `JsonStateStore.Normalize` to call `AppState.Normalize(state)`.

- [ ] **Step 4: Run PC tests**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: all tests pass.

### Task 2: Add PC merge and de-duplication logic

**Files:**
- Create: `src/EyeTimeTracker.Core/Sync/UsageSegmentMerger.cs`
- Test: `tests/EyeTimeTracker.Tests/Program.cs`

- [ ] **Step 1: Write failing merge tests**

Add tests:

```csharp
static UsageSegment Segment(string device, string platform, string source, DateTimeOffset start, int seconds)
{
    var end = start.AddSeconds(seconds);
    return new UsageSegment
    {
        SegmentId = UsageSegmentId.Create(device, source, start, end),
        DeviceId = device,
        Platform = platform,
        Source = source,
        StartUnixSeconds = start.ToUnixTimeSeconds(),
        EndUnixSeconds = end.ToUnixTimeSeconds(),
        LocalDate = DateOnly.FromDateTime(start.LocalDateTime),
        CreatedAtUnixSeconds = start.ToUnixTimeSeconds(),
        UpdatedAtUnixSeconds = end.ToUnixTimeSeconds()
    };
}

static void TestSegmentsDeDuplicateOverlappingDevices()
{
    var t0 = new DateTimeOffset(2026, 7, 2, 12, 0, 0, TimeSpan.Zero);
    var segments = new[]
    {
        Segment("pc", "windows", "pc-input", t0, 30),
        Segment("phone", "android", "android-screen", t0, 30)
    };

    var summary = UsageSegmentMerger.BuildDailyRecord(new DateOnly(2026, 7, 2), segments);

    AssertEqual(30L, summary.TotalSeconds, nameof(TestSegmentsDeDuplicateOverlappingDevices));
}

static void TestContinuousBreaksAfterThreeMinutes()
{
    var t0 = new DateTimeOffset(2026, 7, 2, 9, 0, 0, TimeSpan.Zero);
    var segments = new[]
    {
        Segment("pc", "windows", "pc-input", t0, 60),
        Segment("pc", "windows", "pc-input", t0.AddMinutes(4), 60)
    };

    var summary = UsageSegmentMerger.BuildDailyRecord(new DateOnly(2026, 7, 2), segments);

    AssertEqual(2, summary.SessionSeconds.Count, nameof(TestContinuousBreaksAfterThreeMinutes) + " count");
    AssertEqual(60L, summary.SessionSeconds[0], nameof(TestContinuousBreaksAfterThreeMinutes) + " first");
    AssertEqual(60L, summary.SessionSeconds[1], nameof(TestContinuousBreaksAfterThreeMinutes) + " second");
}
```

- [ ] **Step 2: Run PC tests and verify failure**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: compile failure because `UsageSegmentMerger` does not exist.

- [ ] **Step 3: Implement `UsageSegmentMerger`**

Implementation rules:

- Ignore segments whose `EndUnixSeconds <= StartUnixSeconds`.
- Expand each segment to 10-second buckets.
- Bucket start = `floor(startUnix / 10)`.
- Bucket end = `ceil(endUnix / 10)`.
- Use a `SortedSet<long>` for merged bucket IDs.
- `TotalSeconds = bucketCount * 10`.
- `HourlySeconds[hour] += 10` using local time converted from the bucket Unix time.
- Session break if the gap between neighboring bucket starts is greater than 180 seconds.

- [ ] **Step 4: Run PC tests**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: all tests pass.

### Task 3: Add Android segment model and merge tests

**Files:**
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegment.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegmentId.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegmentMerger.java`
- Test: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/CoreLogicTest.java`

- [ ] **Step 1: Write failing Android tests**

Add tests mirroring PC:

```java
private static UsageSegment segment(String device, String platform, String source, long startSeconds, long durationSeconds) {
    return new UsageSegment(
            UsageSegmentId.create(device, source, startSeconds, startSeconds + durationSeconds),
            device,
            platform,
            source,
            startSeconds,
            startSeconds + durationSeconds,
            java.time.LocalDate.of(2026, 7, 2).toString(),
            startSeconds,
            startSeconds + durationSeconds);
}

private static void shouldMergeOverlappingSegmentsOnlyOnce() {
    long t0 = java.time.OffsetDateTime.of(2026, 7, 2, 12, 0, 0, 0, java.time.ZoneOffset.UTC).toEpochSecond();
    java.util.List<UsageSegment> segments = new java.util.ArrayList<>();
    segments.add(segment("pc", "windows", "pc-input", t0, 30));
    segments.add(segment("phone", "android", "android-screen", t0, 30));
    DailySummary summary = UsageSegmentMerger.buildDailySummary("2026-07-02", segments);
    assertEquals(30L, summary.totalSeconds, "overlapping device segments count once");
}
```

- [ ] **Step 2: Compile Android core tests and verify failure**

Run:

```powershell
$out = 'outputs\android-core-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -encoding UTF-8 -d $out android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\*.java android\EyeTimeTrackerAndroid\tests\com\eyetimetracker\android\CoreLogicTest.java
```

Expected: compile failure because segment classes do not exist.

- [ ] **Step 3: Implement Android segment classes**

Implement `UsageSegment` as an immutable final class with public final fields matching the PC model.

Implement `UsageSegmentId.create(...)` using SHA-256 and lowercase hex.

Implement `UsageSegmentMerger.buildDailySummary(date, segments)` with the same 10-second bucket rules as PC.

- [ ] **Step 4: Run Android core tests**

Run:

```powershell
$out = 'outputs\android-core-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -encoding UTF-8 -d $out android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ActivityDecision.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\DailySummary.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\DurationFormatter.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\TodayTone.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderThreshold.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderAlert.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderPolicy.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegment.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegmentId.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegmentMerger.java android\EyeTimeTrackerAndroid\tests\com\eyetimetracker\android\CoreLogicTest.java
java -cp $out com.eyetimetracker.android.CoreLogicTest
```

Expected: `All Android core tests passed.`

---

## Milestone 2: Record Segments Locally on Both Clients

### Task 4: PC tracking writes usage segments

**Files:**
- Modify: `src/EyeTimeTracker.Core/Tracking/EyeTimeAccumulator.cs`
- Modify: `src/EyeTimeTracker.App/Tracking/TrackingController.cs`
- Test: `tests/EyeTimeTracker.Tests/Program.cs`

- [ ] **Step 1: Write failing PC local segment test**

Add a test that ticks a 10-second active interval and asserts one segment exists in saved state after `SaveNow()`.

Use a temp `JsonStateStore`, fake idle/audio providers if existing constructors allow it; otherwise add small test doubles in `Program.cs`.

Expected assertion:

```csharp
AssertEqual(1, saved.Segments.Count, "pc local tick writes one segment");
AssertEqual(10L, saved.Segments[0].DurationSeconds, "pc segment duration");
```

- [ ] **Step 2: Implement segment recording**

Implement this behavior:

- When a tick adds countable seconds, create or extend a local segment.
- Use `pc-media` when audio is active and input is not recent.
- Use `pc-input` otherwise.
- Round start/end to 10-second boundaries.
- Avoid duplicate segment IDs when saving repeatedly.
- Rebuild today’s `DailyRecord` from local segments before returning snapshots.

- [ ] **Step 3: Run PC tests**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Expected: all tests pass.

### Task 5: Android tracking writes usage segments

**Files:**
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeService.java`
- Test: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/CoreLogicTest.java`

- [ ] **Step 1: Add Android store segment APIs**

Add methods:

```java
public synchronized void addSegment(UsageSegment segment)
public synchronized java.util.List<UsageSegment> getSegments(LocalDate start, LocalDate end)
private static JSONObject segmentToJson(UsageSegment segment)
private static UsageSegment segmentFromJson(JSONObject json)
```

- [ ] **Step 2: Update `addSeconds` call path**

Change service tick to call a new store method that records both:

- legacy daily counters for backward compatibility
- a `UsageSegment` for the same interval

For Android first version, use source `android-screen`.

- [ ] **Step 3: Rebuild summaries from segments where available**

`getDay(date)` should:

- If segments exist for that date, return `UsageSegmentMerger.buildDailySummary(...)`.
- If no segments exist, fall back to legacy record fields.

- [ ] **Step 4: Build Android APK**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Expected: APK builds and verifies.

---

## Milestone 3: Sync Contracts, Signing, and Storage

### Task 6: Add shared sync message contracts

**Files:**
- Create: `src/EyeTimeTracker.Core/Sync/SyncMessages.cs`
- Create: `src/EyeTimeTracker.Core/Sync/SyncMessageSigner.cs`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncMessages.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncMessageSigner.java`
- Test: `tests/EyeTimeTracker.Tests/Program.cs`
- Test: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/CoreLogicTest.java`

- [ ] **Step 1: Define message names**

Use exact message type strings:

- `pairRequest`
- `pairAccept`
- `syncRequest`
- `syncResponse`
- `reminderClaim`

- [ ] **Step 2: Add signing tests**

Test:

- Same payload and secret produce same signature.
- Wrong secret fails validation.
- Timestamp older than 5 minutes fails validation.

- [ ] **Step 3: Implement HMAC-SHA256 signing**

PC uses `HMACSHA256`.

Android uses `javax.crypto.Mac` with `HmacSHA256`.

Sign canonical payload string:

```text
type + "\n" + timestampUnixSeconds + "\n" + bodyJson
```

- [ ] **Step 4: Run PC and Android core tests**

Run both existing test commands.

Expected: all pass.

---

## Milestone 4: PC In-App Sync Listener

### Task 7: Add PC sync server

**Files:**
- Create: `src/EyeTimeTracker.App/Sync/PcSyncServer.cs`
- Create: `src/EyeTimeTracker.App/Sync/PcSyncCoordinator.cs`
- Modify: `src/EyeTimeTracker.App/UI/TrayApplicationContext.cs`
- Test: `tests/EyeTimeTracker.Tests/Program.cs`

- [ ] **Step 1: Write server coordinator tests without real sockets**

Test the coordinator directly:

- Apply Android segment, PC state stores it.
- Return PC segment in response.
- Android reminder setting overwrites PC setting.

- [ ] **Step 2: Implement `PcSyncCoordinator`**

Public methods:

```csharp
public SyncResponse HandleSync(SyncRequest request)
public PairAccept HandlePair(PairRequest request, string expectedCode)
```

Rules:

- Reject unpaired sync without valid shared secret.
- Merge incoming segments by `segmentId`.
- Apply Android reminder settings when paired.
- Save state after successful sync.

- [ ] **Step 3: Implement `PcSyncServer`**

Rules:

- Start only when app is running.
- Stop on app exit.
- Listen on default port `17420`; if unavailable, try ports `17421` through `17429`.
- Handle one request per TCP connection.
- Never crash the app on network exception; store last error.

- [ ] **Step 4: Wire lifecycle**

In `TrayApplicationContext`, create and start server after controller creation. Dispose server when app exits.

- [ ] **Step 5: Run PC build**

Run:

```powershell
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -o outputs\verify-pc-wifi-sync
```

Expected: build succeeds.

---

## Milestone 5: Android Sync Client

### Task 8: Add Android sync client

**Files:**
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/AndroidSyncClient.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncSettings.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Modify: `android/EyeTimeTrackerAndroid/AndroidManifest.xml`

- [ ] **Step 1: Add `INTERNET` permission**

Add:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 2: Add sync settings persistence**

Store in SharedPreferences:

- paired state
- peer host
- peer port
- peer device ID
- shared secret
- last sync time
- last sync error

- [ ] **Step 3: Implement socket client**

Rules:

- Connect timeout: 3 seconds.
- Read timeout: 5 seconds.
- Send one JSON message, read one JSON response.
- Network errors update last sync error, but do not affect local tracking.

- [ ] **Step 4: Trigger sync**

Trigger sync:

- when stats page opens
- every 1 minute while service is running
- after local segment changes with at least 30 seconds debounce
- after reminder settings change

- [ ] **Step 5: Build Android APK**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Expected: APK builds and verifies.

---

## Milestone 6: Pairing and Sync UI

### Task 9: PC pairing and status UI

**Files:**
- Modify: `src/EyeTimeTracker.App/UI/MainForm.cs`
- Create or modify: `src/EyeTimeTracker.App/UI/SyncPairingDialog.cs`

- [ ] **Step 1: Add sync card or status row**

Display:

- `同步：未配对`
- `同步：已配对`
- `上次同步：HH:mm`
- `同步失败：原因`

- [ ] **Step 2: Add PC pairing dialog**

Dialog includes:

- 6-digit pairing code input
- PC local IP and port display
- “等待手机连接” status

- [ ] **Step 3: Lock PC reminder settings after pairing**

If paired:

- Reminder card is still visible.
- Clicking shows message: `已与手机同步，请到手机端修改提醒设置。`
- PC does not save local reminder changes.

- [ ] **Step 4: Build PC app**

Run:

```powershell
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -o outputs\verify-pc-sync-ui
```

Expected: build succeeds.

### Task 10: Android pairing and status UI

**Files:**
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/MainActivity.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/StatsActivity.java`

- [ ] **Step 1: Add Android sync status**

Display status near the existing top status/启动 area:

- `未配对`
- `已配对`
- `同步中`
- `上次同步 HH:mm`
- `同步失败`

- [ ] **Step 2: Add pairing sheet/dialog**

Dialog includes:

- Generate 6-digit pairing code.
- Optional PC IP input.
- Optional PC port input.
- Pair button.

- [ ] **Step 3: Trigger sync on stats open**

When `StatsActivity` opens, call sync client in background and refresh on completion.

- [ ] **Step 4: Build Android APK**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Expected: APK builds and verifies.

---

## Milestone 7: Reminder Coordination

### Task 11: Add reminder claim model and merge rules

**Files:**
- Create: `src/EyeTimeTracker.Core/Reminders/ReminderClaim.cs`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ReminderClaim.java`
- Modify: `src/EyeTimeTracker.App/Tracking/TrackingController.cs`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeService.java`

- [ ] **Step 1: Add tests for `reminderKey`**

Expected key format:

```text
yyyy-MM-dd:type:step
```

Examples:

- `2026-07-02:daily:1`
- `2026-07-02:continuous:2`

- [ ] **Step 2: Implement claim persistence**

Each side stores recent reminder claims in local state.

- [ ] **Step 3: Apply device priority**

Rules:

- If only current device active, current device shows reminder.
- If both devices active, Android wins.
- If already claimed by peer, do not show duplicate.
- If offline, current device may show locally; later sync only prevents future duplicates.

- [ ] **Step 4: Run full verification**

Run:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -o outputs\verify-pc-sync-final
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Expected: all pass.

---

## Manual Acceptance Checklist

- [ ] PC and Android both continue local counting when unpaired.
- [ ] Android generates a 6-digit pairing code.
- [ ] PC enters pairing code and waits.
- [ ] Android connects to PC over same WiFi and pairing succeeds.
- [ ] PC app in tray still syncs.
- [ ] PC app fully exited causes Android sync failure but local counting continues.
- [ ] Android reminder setting overwrites PC setting after sync.
- [ ] Two devices recording the same 30 seconds increase merged total by 30 seconds, not 60 seconds.
- [ ] Stats page shows merged totals.
- [ ] Reminder appears on active device; if both active, Android wins.

## Verification Commands

Use these before reporting implementation complete:

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -o outputs\verify-pc-wifi-sync-final
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Android core logic command:

```powershell
$out = 'outputs\android-core-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -encoding UTF-8 -d $out android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ActivityDecision.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\DailySummary.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\DurationFormatter.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\TodayTone.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderThreshold.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderAlert.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderPolicy.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegment.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegmentId.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegmentMerger.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\SyncMessageSigner.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\SyncMessages.java android\EyeTimeTrackerAndroid\tests\com\eyetimetracker\android\CoreLogicTest.java
java -cp $out com.eyetimetracker.android.CoreLogicTest
```

## Self-Review

- The plan starts with segment data and merge logic before networking, so de-duplication can be tested without WiFi.
- The plan keeps Android as the settings authority even though PC hosts the listener.
- The plan explicitly avoids Windows Service and cloud sync in first version.
- The plan includes Android and PC work for every shared feature.
- The plan includes build and test commands for both clients.
- The plan marks old data as visible but not exactly de-duplicable, avoiding impossible historical reconstruction.
