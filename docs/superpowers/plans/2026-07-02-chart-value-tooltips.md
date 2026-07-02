# Chart Value Tooltips Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add compact value hints for the three statistics charts on PC and Android.

**Architecture:** Keep all existing chart layout and statistics calculations. Add shared value formatting, then add hit detection inside the existing PC WinForms chart controls and Android custom `View` classes. PC shows a tooltip on mouse hover; Android shows a small in-chart value bubble on tap.

**Tech Stack:** C# WinForms for PC, Java Android custom views for Android, existing local tests and build scripts.

---

### Task 1: Shared Value Format

**Files:**
- Create: `src/EyeTimeTracker.Core/Formatting/ChartValueFormatter.cs`
- Modify: `tests/EyeTimeTracker.Tests/Program.cs`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/DurationFormatter.java`
- Modify: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/CoreLogicTest.java`

- [ ] Add a formatter that converts seconds to minute text for the 24H chart, such as `28分钟`.
- [ ] Add a formatter that converts seconds to compact hour text for the week and month charts, such as `0.5小时`, `1小时`, `2.5小时`.
- [ ] Write tests before implementation.
- [ ] Reuse this formatter in chart tooltips.

### Task 2: PC Hover Tooltips

**Files:**
- Modify: `src/EyeTimeTracker.App/UI/StatsForm.cs`

- [ ] In `HourlyHeatChart`, store each drawn hour bar hit region and value.
- [ ] On mouse move, show a WinForms tooltip with only the value text.
- [ ] In the weekly chart drawing code, store each daily bar hit region and value.
- [ ] In `MonthTrendChart`, store each point hit region and value.
- [ ] Hide the tooltip when hovering over empty chart areas.

### Task 3: Android Tap Tooltips

**Files:**
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/StatsActivity.java`

- [ ] In the 24H chart view, store each hour bar hit region and value.
- [ ] In the weekly chart view, store each daily bar hit region and value.
- [ ] In the month trend view, store each point hit region and value.
- [ ] On tap, show a compact rounded value bubble near the tapped element.
- [ ] Hide or replace the bubble when tapping another element or empty area.

### Task 4: Verification

**Commands:**
- `dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj`
- `dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -o outputs\verify-pc-chart-tooltips`
- `powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1`

**Expected Result:** All commands exit with code 0.
