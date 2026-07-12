# Android Family Eye Rules Dispatch v0.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add first-version family rules that parents can set locally and children can receive over the existing family LAN stats sync.

**Architecture:** Keep family rules separate from the existing daily reminder card. Add a small pure Java model for rule defaults, validation, summaries, and disabled-period time matching, then store it in `EyeTimeStore` and include it in `FamilyStatsProtocol` upload responses. Wire the family settings page to open a lightweight rules editor.

**Tech Stack:** Java, Android SDK 36, SharedPreferences JSON state, existing manual Android build script, existing pure Java model tests.

---

### Task 1: Family Rule Model

**Files:**
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyEyeRules.java`
- Modify: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/FamilyModeModelTest.java`

- [ ] Add failing tests for default rules, validation, summary text, and disabled-period matching.
- [ ] Run `FamilyModeModelTest` and confirm the new tests fail because `FamilyEyeRules` does not exist.
- [ ] Implement `FamilyEyeRules` with defaults, clamp behavior, `summaryText()`, and `isInDisabledPeriod()`.
- [ ] Run `FamilyModeModelTest` and confirm it passes.

### Task 2: Local Storage and Sync Protocol

**Files:**
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyStatsProtocol.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyStatsLanServer.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyStatsLanClient.java`
- Modify: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/FamilyModeModelTest.java`

- [ ] Add failing tests for rules JSON round-trip in `EyeTimeStore`.
- [ ] Add failing tests for upload response carrying `FamilyEyeRules`.
- [ ] Implement local read/save helpers in `EyeTimeStore`.
- [ ] Add `FamilyEyeRules` to accepted family stats upload responses.
- [ ] Save received rules on child devices when family stats sync completes.
- [ ] Run `FamilyModeModelTest` and confirm it passes.

### Task 3: Family Settings Rules Page

**Files:**
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyHomeActivity.java`
- Modify: `i18n/source/zh-CN.json`
- Modify: `i18n/source/en-US.json`

- [ ] Enable the rules row for parent devices.
- [ ] Show a family rules screen from the settings page.
- [ ] Let parents toggle continuous-use reminder and disabled-period reminder.
- [ ] Explain on the rules page that disabled periods are soft reminders: they do not lock the phone, but remind and record.
- [ ] Save rules locally and return to the family settings page with the updated summary.

### Task 4: Verification

**Files:**
- Generated: `i18n/generated/android/...`
- Generated: `outputs/android/EyeTimeTrackerAndroid-debug.apk`

- [ ] Regenerate i18n resources through the existing build script.
- [ ] Build the Android APK.
- [ ] Install to the connected test phone if available.
- [ ] Report changed files, verification result, and remaining risks.
