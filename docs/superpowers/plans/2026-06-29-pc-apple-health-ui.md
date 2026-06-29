# PC Apple Health UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the approved Apple Health-style visual direction to the Windows PC main window without changing tracking behavior or stored data.

**Architecture:** Keep the existing WinForms app and `MainForm` event/data flow. Replace the default table-heavy visual layout with a cleaner hierarchy: large today total, compact metric cards, low-noise status, and rounded action buttons.

**Tech Stack:** C# WinForms, existing .NET project structure, existing build/publish flow.

---

### Task 1: Restyle PC Main Window

**Files:**
- Modify: `src/EyeTimeTracker.App/UI/MainForm.cs`

- [ ] Replace the fixed default-gray layout with a light Apple Health-style surface.
- [ ] Show today's total as the primary large value.
- [ ] Show yesterday, week, and month as compact secondary cards.
- [ ] Move status into a small indicator row using a green/gray dot and text.
- [ ] Keep reset display, idle threshold, audio counting, and startup settings available.
- [ ] Use rounded custom-drawn buttons/panels where WinForms defaults look outdated.

### Task 2: Verify PC Build

**Files:**
- Read: `src/EyeTimeTracker.App/EyeTimeTracker.App.csproj`
- Read: `tests/EyeTimeTracker.Tests/EyeTimeTracker.Tests.csproj`

- [ ] Run the existing test project.
- [ ] Build or publish the PC app.
- [ ] Confirm no tracking/data behavior files changed unexpectedly.

### Task 3: Commit

**Files:**
- Modify: `docs/superpowers/plans/2026-06-29-pc-apple-health-ui.md`
- Modify: `src/EyeTimeTracker.App/UI/MainForm.cs`

- [ ] Review the diff.
- [ ] Commit the PC UI change.
