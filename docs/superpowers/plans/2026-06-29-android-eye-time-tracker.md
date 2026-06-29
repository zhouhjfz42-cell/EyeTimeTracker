# Android Eye Time Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a first Android APK that tracks local eye-time using screen-on plus device motion or media playback.

**Architecture:** Use a dependency-free native Java Android app so it can build with the installed Android SDK without Gradle. Keep core decisions and formatting in pure Java classes with host-side tests; Android-specific code is limited to Activity, foreground Service, sensors, audio, notifications, and storage wiring.

**Tech Stack:** Java, Android SDK 36, Activity, Foreground Service, SensorManager, AudioManager, SharedPreferences, JSON, manual aapt2/javac/d8/apksigner build script.

---

## Tasks

- [ ] Create host-side tests for activity decision and duration formatting.
- [ ] Implement pure Java core classes.
- [ ] Implement Android manifest, Activity, foreground Service, local store, and notification reminder.
- [ ] Add manual APK build script.
- [ ] Verify host tests and APK build.
