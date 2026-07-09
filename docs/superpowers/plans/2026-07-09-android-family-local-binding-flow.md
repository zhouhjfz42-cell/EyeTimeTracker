# Android Family Local Binding Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do not commit unless the user explicitly asks.

**Goal:** Replace the current symmetric family setup with a parent-created, child-bound local flow: parent devices create child profiles and passcodes; child devices join an existing profile by binding code/scan path and cannot edit child name or age.

**Architecture:** Keep the current local-only Android Java implementation. Add a small local binding model that can represent a pending invite and a completed child-device binding without accounts or cloud sync. The parent flow creates the family data and shows a waiting/binding-code screen; the child flow accepts a binding code and stores itself as `DeviceRole.CHILD_DEVICE` with the bound child profile.

**Tech Stack:** Android native Java views, existing `EyeTimeStore` JSON persistence, existing i18n source/generated resource flow, existing manual Android APK build script.

---

## Scope

This plan updates the already-started Round 1 Android family mode. It does not add account login, cloud sync, payment, app usage statistics, app limits, real QR scanning, or remote parent reports.

The binding code in this round is local/demo-grade. It exists to make the product path correct and to prevent the child setup from owning child-profile creation. The future cloud version can replace code validation with server-backed family-space binding while keeping the same user-facing flow.

## Files

- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilySetupDraft.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilySetupActivity.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyHomeActivity.java`
- Modify: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/FamilyModeModelTest.java`
- Modify: `i18n/source/zh-CN.json`
- Modify: `i18n/source/en-US.json`
- Generated: `i18n/generated/android/values/strings.xml`
- Generated: `i18n/generated/android/values-en/strings.xml`
- Generated: `i18n/generated/dotnet/zh-CN.json`
- Generated: `i18n/generated/dotnet/en-US.json`
- Optional create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyBindingInvite.java`
- Optional create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyBindingDraft.java`

---

## Task 1: Local Binding Model

**Intent:** Store enough local state for the parent to create a one-time binding code and for the child flow to consume a code without creating its own child profile.

- [ ] Add a failing model test in `FamilyModeModelTest.java`.

Test behavior:

```java
private static void shouldCreateAndConsumeFamilyBindingInvite() {
    JSONObject state = new JSONObject();
    ChildProfile child = new ChildProfile("local-child-1", "mumu", "3-6", 1783000800L, 1783000800L);
    FamilyBindingInvite invite = FamilyBindingInvite.create("local-family-1", child, 1783000800L);

    EyeTimeStore.writeFamilyBindingInvite(state, invite);
    FamilyBindingInvite saved = EyeTimeStore.readFamilyBindingInvite(state);

    assertEquals(invite.bindingCode, saved.bindingCode, "binding code round trips");
    assertEquals("local-child-1", saved.childProfile.childId, "invite keeps child id");
    assertEquals("mumu", saved.childProfile.nickname, "invite keeps child nickname");
    assertEquals(true, EyeTimeStore.consumeFamilyBindingCode(state, invite.bindingCode), "correct code consumes invite");
    assertEquals(DeviceRole.CHILD_DEVICE, EyeTimeStore.readDeviceRole(state), "child device role is saved after consume");
    assertEquals(ProductMode.FAMILY, EyeTimeStore.readProductMode(state), "family mode is saved after consume");
    assertEquals("local-child-1", EyeTimeStore.readActiveChildId(state), "active child id is saved after consume");
}
```

- [ ] Run the model test and confirm it fails because `FamilyBindingInvite` and binding store methods do not exist.

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Expected: compile/test failure references missing binding model symbols.

- [ ] Implement `FamilyBindingInvite`.

Shape:

```java
public final class FamilyBindingInvite {
    public final String familyId;
    public final String bindingCode;
    public final ChildProfile childProfile;
    public final long createdAtUnixSeconds;

    public static FamilyBindingInvite create(String familyId, ChildProfile childProfile, long nowUnixSeconds) {
        String safeFamilyId = familyId == null || familyId.trim().isEmpty()
                ? "local-family-" + java.util.UUID.randomUUID().toString()
                : familyId.trim();
        String code = String.format(java.util.Locale.US, "%06d", Math.abs(java.util.UUID.randomUUID().hashCode()) % 1000000);
        return new FamilyBindingInvite(safeFamilyId, code, childProfile, nowUnixSeconds);
    }
}
```

- [ ] Add `EyeTimeStore` JSON read/write helpers:

Methods:

```java
static FamilyBindingInvite readFamilyBindingInvite(JSONObject state)
static void writeFamilyBindingInvite(JSONObject state, FamilyBindingInvite invite) throws JSONException
static boolean consumeFamilyBindingCode(JSONObject state, String code) throws JSONException
static String readActiveChildId(JSONObject state)
```

Storage fields:

```json
{
  "familyId": "local-family-...",
  "pendingFamilyBindingInvite": {
    "bindingCode": "428916",
    "createdAtUnixSeconds": 1783000800,
    "childProfile": {}
  }
}
```

- [ ] Run model tests and confirm the new binding test passes.

---

## Task 2: Split Parent and Child Setup Drafts

**Intent:** Parent setup creates child profile/passcode; child setup binds to an existing profile and never asks for child name or age.

- [ ] Add failing tests:

```java
private static void shouldCreateParentSetupDraftWithChildProfile() {
    FamilySetupDraft draft = FamilySetupDraft.createParent("mumu", "3-6", "2468", 1783000900L);
    assertEquals(DeviceRole.PARENT_DEVICE, draft.deviceRole, "parent draft uses parent role");
    assertEquals("mumu", draft.childProfile.nickname, "parent draft owns child nickname");
    assertEquals("3-6", draft.childProfile.ageBand, "parent draft owns child age band");
}

private static void shouldCreateChildBindingDraftWithoutChildProfileInput() {
    FamilyBindingDraft draft = FamilyBindingDraft.create("428916");
    assertEquals("428916", draft.bindingCode, "child binding keeps entered code");
}
```

- [ ] Refactor `FamilySetupDraft`:

Keep parent creation in `FamilySetupDraft.createParent(...)`. Remove the child-role path from profile creation logic.

- [ ] Add `FamilyBindingDraft`:

```java
public final class FamilyBindingDraft {
    public final String bindingCode;

    public static FamilyBindingDraft create(String bindingCode) {
        String normalized = bindingCode == null ? "" : bindingCode.replaceAll("\\s+", "");
        if (!normalized.matches("\\d{6}")) {
            throw new IllegalArgumentException("binding code must be 6 digits");
        }
        return new FamilyBindingDraft(normalized);
    }
}
```

- [ ] Run model tests and confirm parent draft and child binding draft pass.

---

## Task 3: Update Family Setup UI Flow

**Intent:** Match the approved HTML mockup: one role selection, then separate parent and child paths.

- [ ] Modify `FamilySetupActivity` role step:

Parent option text:

```text
我是家长
创建孩子档案，之后添加孩子设备。
```

Child option text:

```text
这是孩子设备
加入家长已经创建好的档案。
```

- [ ] Parent path screens:

1. Child profile and 4-digit passcode.
2. Binding-code waiting screen.
3. After local save, open `FamilyHomeActivity`.

Parent save behavior:

```java
store.saveProductMode(ProductMode.FAMILY);
store.saveDeviceRole(DeviceRole.PARENT_DEVICE);
store.saveChildProfiles(Collections.singletonList(draft.childProfile), draft.childProfile.childId);
store.saveParentPasscode(draft.passcode);
store.saveFamilyBindingInvite(FamilyBindingInvite.create(familyId, draft.childProfile, now));
```

- [ ] Child path screens:

1. Binding code input with secondary “扫码加入” button disabled or non-functional in this round.
2. Confirmation card: `将加入 / 沐沐的家庭护眼 / 孩子设备`.
3. On valid local code, save child device role and open `FamilyHomeActivity`.

Child save behavior:

```java
FamilyBindingDraft draft = FamilyBindingDraft.create(bindingCodeInput);
boolean consumed = store.consumeFamilyBindingCode(draft.bindingCode);
if (!consumed) {
    showToast(getString(R.string.family_binding_code_invalid));
    return;
}
startActivity(new Intent(this, FamilyHomeActivity.class));
finish();
```

- [ ] If local invite is missing on the same device, show a friendly message:

```text
请在家长手机上生成绑定码后再加入。
```

This keeps the first local round honest: same-device demo works, real cross-device binding waits for cloud/local-network binding.

---

## Task 4: Update Family Home Copy

**Intent:** Keep parent and child home pages aligned with the approved home design.

- [ ] Parent home remains adult-like and shows `设置` in the top right.
- [ ] Child home remains adult-like and does not show `设置`.
- [ ] Parent note:

```text
家庭护眼已启用。需要修改孩子档案、保护密码或规则时，进入右上角设置。
```

- [ ] Child note:

```text
这台设备已加入家庭护眼。家庭设置由家长管理。
```

- [ ] Family settings must not include a duplicate reminder row.

---

## Task 5: i18n and Build

**Intent:** Keep all user-facing copy in the existing resource pipeline.

- [ ] Add new keys to `i18n/source/zh-CN.json` and `i18n/source/en-US.json`.

Required Chinese keys include:

```json
{
  "family_setup_role_parent_title": "我是家长",
  "family_setup_role_parent_desc": "创建孩子档案，之后添加孩子设备。",
  "family_setup_role_child_title": "这是孩子设备",
  "family_setup_role_child_desc": "加入家长已经创建好的档案。",
  "family_binding_waiting_title": "在孩子设备上加入",
  "family_binding_waiting_desc": "让孩子设备扫描二维码，或输入下面的数字码。",
  "family_binding_waiting_status": "等待孩子设备加入...",
  "family_binding_child_title": "输入家长手机上的绑定码",
  "family_binding_child_desc": "加入后，这台设备会按家长设置记录和提醒。",
  "family_binding_scan": "扫码加入",
  "family_binding_join": "加入家庭护眼",
  "family_binding_code_invalid": "绑定码不正确或已失效"
}
```

- [ ] Regenerate resources:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File i18n\tools\Update-I18nResources.ps1
```

- [ ] Build APK:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Expected: exit code 0 and APK signature verification v2/v3 true.

---

## Task 6: Device Verification

**Intent:** Verify on the two named phones.

- [ ] Install APK to 小米 15 (`660005a0`) and verify parent flow:

Expected:

- Family entry opens parent/child role selection.
- Parent path creates child profile and passcode.
- Parent sees binding-code waiting screen.
- Parent family home shows `家长手机` and `设置`.

- [ ] Install APK to 红米 K40 (`efe1f62`) and verify child flow:

Expected:

- Child path asks for binding code, not child name or age.
- Child home shows `孩子设备`.
- Child home does not show `设置`.
- Statistics page button remains in the adult-home position.

If true cross-device binding is not available in this local round, record that limitation explicitly in the final report and keep the UI copy from implying cloud sync is finished.

---

## Risks

- The current Android project uses hand-written Java views, so the setup activity can grow large. Keep helper methods small and do not restructure unrelated screens.
- Without cloud or LAN binding, real cross-device code validation cannot be complete. The UI can express the intended product flow, but the final report must call out that binding is still local/demo-grade.
- The child-device flow must not silently create a new child profile from child-entered data; that would reintroduce the product bug this plan fixes.
