# Android Family LAN Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a child Android phone join a family profile created on a parent Android phone over the same WiFi network by entering the 6-digit binding code.

**Architecture:** Reuse the existing Java socket style from Android PC sync, but keep this feature Android-to-Android and local-only. The parent binding screen starts a temporary LAN binding service with UDP discovery and one TCP join endpoint; the child binding screen discovers the parent service, sends the binding code, and saves the returned family profile as a child device.

**Tech Stack:** Android native Java views, `ServerSocket`, `DatagramSocket`, existing `EyeTimeStore` JSON persistence, existing i18n source/generated resources, existing APK build script.

---

## Scope

This round only makes the approved parent/child setup flow real on two phones in the same LAN:

- 小米 15 (`660005a0`) can act as parent device.
- 红米 K40 (`efe1f62`) can act as child device.
- Parent creates child profile and 6-digit binding code.
- Child enters that code and joins the parent-created profile.

Out of scope:

- Account login.
- Cloud sync.
- Payment.
- Rule editing/downlink.
- App usage statistics.
- Long-running parent report sync.
- Real QR scanning. The visible scan button remains a placeholder.

## User-Facing Flow

Parent phone:

1. User enters family setup as parent.
2. User fills child nickname, optional age band, and 4-digit protection password.
3. App saves parent family state and starts a temporary LAN binding service.
4. App shows the binding code and status: waiting, joined, or failed.
5. When the child joins, parent can enter the family home.

Child phone:

1. User enters family setup as child device.
2. User enters the 6-digit binding code shown on the parent phone.
3. App searches the same WiFi for a parent binding service.
4. App sends the binding code.
5. If accepted, app saves family mode, child device role, family ID, and child profile, then opens the family home.
6. If not found or rejected, app shows a plain retryable error.

## Files

- Modify: `android/EyeTimeTrackerAndroid/AndroidManifest.xml`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncMessages.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilySetupActivity.java`
- Modify: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/FamilyModeModelTest.java`
- Modify: `i18n/source/zh-CN.json`
- Modify: `i18n/source/en-US.json`
- Generated: `i18n/generated/android/values/strings.xml`
- Generated: `i18n/generated/android/values-en/strings.xml`
- Generated: `i18n/generated/dotnet/zh-CN.json`
- Generated: `i18n/generated/dotnet/en-US.json`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyBindingProtocol.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyBindingLanServer.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyBindingLanClient.java`

---

## Task 1: LAN Binding Protocol Model

**Intent:** Define small request/response JSON helpers before touching sockets.

**Files:**

- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyBindingProtocol.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncMessages.java`
- Modify: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/FamilyModeModelTest.java`

- [ ] **Step 1: Add protocol constants to `SyncMessages.java`**

Add:

```java
public static final String FAMILY_BINDING_DISCOVERY_REQUEST = "familyBindingDiscoveryRequest";
public static final String FAMILY_BINDING_DISCOVERY_RESPONSE = "familyBindingDiscoveryResponse";
public static final String FAMILY_BINDING_JOIN_REQUEST = "familyBindingJoinRequest";
public static final String FAMILY_BINDING_JOIN_RESPONSE = "familyBindingJoinResponse";
```

- [ ] **Step 2: Add failing protocol test**

In `FamilyModeModelTest.java`, add:

```java
private static void shouldBuildAndParseFamilyBindingJoinResponse() throws Exception {
    ChildProfile child = new ChildProfile("local-child-1", "mumu", "7-9", 1783001000L, 1783001000L);
    FamilyBindingInvite invite = new FamilyBindingInvite("local-family-1", "428916", child, 1783001000L);

    String responseJson = FamilyBindingProtocol.buildJoinResponse(true, "", invite);
    FamilyBindingProtocol.JoinResponse response = FamilyBindingProtocol.parseJoinResponse(responseJson);

    assertEquals(true, response.accepted, "accepted response parses");
    assertEquals("local-family-1", response.familyId, "family id parses");
    assertEquals("local-child-1", response.childProfile.childId, "child id parses");
    assertEquals("mumu", response.childProfile.nickname, "child nickname parses");
}
```

Call it from `main`.

- [ ] **Step 3: Run model test and confirm it fails**

Use the existing manual model test command. Expected failure: `FamilyBindingProtocol` does not exist.

- [ ] **Step 4: Implement `FamilyBindingProtocol`**

Create a pure Java helper:

```java
package com.eyetimetracker.android;

import org.json.JSONException;
import org.json.JSONObject;

public final class FamilyBindingProtocol {
    public static final int DEFAULT_DISCOVERY_PORT = 17429;

    public static String buildDiscoveryRequest(String deviceId) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Type", SyncMessages.FAMILY_BINDING_DISCOVERY_REQUEST);
        json.put("DeviceId", safe(deviceId));
        json.put("Platform", "android");
        return json.toString();
    }

    public static String buildDiscoveryResponse(String deviceId, int port) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Type", SyncMessages.FAMILY_BINDING_DISCOVERY_RESPONSE);
        json.put("DeviceId", safe(deviceId));
        json.put("Platform", "android");
        json.put("Port", port);
        return json.toString();
    }

    public static String buildJoinRequest(String deviceId, String bindingCode) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Type", SyncMessages.FAMILY_BINDING_JOIN_REQUEST);
        json.put("DeviceId", safe(deviceId));
        json.put("Platform", "android");
        json.put("BindingCode", FamilyBindingInvite.normalizeBindingCode(bindingCode));
        json.put("TimestampUnixSeconds", System.currentTimeMillis() / 1000L);
        return json.toString();
    }

    public static JoinRequest parseJoinRequest(String jsonText) throws JSONException {
        JSONObject json = new JSONObject(jsonText == null ? "" : jsonText);
        if (!SyncMessages.FAMILY_BINDING_JOIN_REQUEST.equals(json.optString("Type", ""))) {
            return new JoinRequest("", "");
        }
        return new JoinRequest(
                json.optString("DeviceId", ""),
                json.optString("BindingCode", ""));
    }

    public static String buildJoinResponse(boolean accepted, String error, FamilyBindingInvite invite) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Type", SyncMessages.FAMILY_BINDING_JOIN_RESPONSE);
        json.put("Accepted", accepted);
        json.put("Error", safe(error));
        if (accepted && invite != null && invite.isValid()) {
            json.put("FamilyId", invite.familyId);
            json.put("ChildProfile", childProfileToJson(invite.childProfile));
        }
        json.put("TimestampUnixSeconds", System.currentTimeMillis() / 1000L);
        return json.toString();
    }

    public static JoinResponse parseJoinResponse(String jsonText) throws JSONException {
        JSONObject json = new JSONObject(jsonText == null ? "" : jsonText);
        if (!SyncMessages.FAMILY_BINDING_JOIN_RESPONSE.equals(json.optString("Type", ""))) {
            return JoinResponse.rejected("Invalid response.");
        }
        boolean accepted = json.optBoolean("Accepted", false);
        JSONObject childJson = json.optJSONObject("ChildProfile");
        ChildProfile child = childJson == null ? null : childProfileFromJson(childJson);
        return new JoinResponse(
                accepted,
                json.optString("Error", ""),
                json.optString("FamilyId", ""),
                child);
    }

    public static JSONObject childProfileToJson(ChildProfile profile) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("childId", profile.childId);
        json.put("nickname", profile.nickname);
        json.put("ageBand", profile.ageBand);
        json.put("createdAtUnixSeconds", profile.createdAtUnixSeconds);
        json.put("updatedAtUnixSeconds", profile.updatedAtUnixSeconds);
        return json;
    }

    public static ChildProfile childProfileFromJson(JSONObject json) {
        return new ChildProfile(
                json.optString("childId", ""),
                json.optString("nickname", ""),
                json.optString("ageBand", ChildProfile.AGE_BAND_UNKNOWN),
                json.optLong("createdAtUnixSeconds", 0L),
                json.optLong("updatedAtUnixSeconds", 0L));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class JoinRequest {
        public final String deviceId;
        public final String bindingCode;

        public JoinRequest(String deviceId, String bindingCode) {
            this.deviceId = safe(deviceId);
            this.bindingCode = FamilyBindingInvite.normalizeBindingCode(bindingCode);
        }
    }

    public static final class JoinResponse {
        public final boolean accepted;
        public final String error;
        public final String familyId;
        public final ChildProfile childProfile;

        public JoinResponse(boolean accepted, String error, String familyId, ChildProfile childProfile) {
            this.accepted = accepted;
            this.error = safe(error);
            this.familyId = safe(familyId);
            this.childProfile = childProfile;
        }

        public static JoinResponse rejected(String error) {
            return new JoinResponse(false, error, "", null);
        }
    }

    private FamilyBindingProtocol() {
    }
}
```

- [ ] **Step 5: Run model test**

Expected: `All Android family mode model tests passed.`

---

## Task 2: Store Remote Child Binding

**Intent:** Let child devices save a family profile returned by another phone without requiring a local pending invite.

**Files:**

- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Modify: `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/FamilyModeModelTest.java`

- [ ] **Step 1: Add failing store test**

Add:

```java
private static void shouldSaveRemoteFamilyChildBinding() throws Exception {
    JSONObject state = new JSONObject();
    ChildProfile child = new ChildProfile("local-child-9", "nini", "10-12", 1783001100L, 1783001100L);

    EyeTimeStore.writeRemoteFamilyChildBinding(state, "local-family-9", child);

    assertEquals(ProductMode.FAMILY, EyeTimeStore.readProductMode(state), "remote binding saves family mode");
    assertEquals(DeviceRole.CHILD_DEVICE, EyeTimeStore.readDeviceRole(state), "remote binding saves child role");
    assertEquals("local-child-9", EyeTimeStore.readActiveChildId(state), "remote binding saves active child");
    assertEquals("nini", EyeTimeStore.readChildProfiles(state).get(0).nickname, "remote binding saves child profile");
    assertEquals(true, EyeTimeStore.readFamilyBindingInvite(state) == null, "remote binding does not leave pending invite");
}
```

- [ ] **Step 2: Run model test and confirm it fails**

Expected failure: `writeRemoteFamilyChildBinding` does not exist.

- [ ] **Step 3: Add store methods**

Add public method:

```java
public synchronized void saveRemoteFamilyChildBinding(String familyId, ChildProfile childProfile) {
    if (childProfile == null || !childProfile.isValid()) {
        return;
    }
    try {
        JSONObject state = loadState();
        writeRemoteFamilyChildBinding(state, familyId, childProfile);
        saveState(state);
    } catch (JSONException ex) {
        throw new IllegalStateException("Failed to save remote family child binding.", ex);
    }
}
```

Add static helper:

```java
static void writeRemoteFamilyChildBinding(JSONObject state, String familyId, ChildProfile childProfile) throws JSONException {
    if (state == null || childProfile == null || !childProfile.isValid()) {
        return;
    }
    writeProductMode(state, ProductMode.FAMILY);
    writeDeviceRole(state, DeviceRole.CHILD_DEVICE);
    writeChildProfiles(state, java.util.Collections.singletonList(childProfile), childProfile.childId);
    state.put(FAMILY_ID, familyId == null ? "" : familyId.trim());
    state.put(PENDING_FAMILY_BINDING_INVITE, null);
}
```

- [ ] **Step 4: Run model test**

Expected: `All Android family mode model tests passed.`

---

## Task 3: Parent LAN Binding Server

**Intent:** Parent binding screen starts a temporary local service that can answer discovery and join requests.

**Files:**

- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyBindingLanServer.java`
- Modify: `android/EyeTimeTrackerAndroid/AndroidManifest.xml`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilySetupActivity.java`

- [ ] **Step 1: Add permission**

In `AndroidManifest.xml`, add:

```xml
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

This helps Android receive local discovery packets on some WiFi stacks.

- [ ] **Step 2: Implement `FamilyBindingLanServer`**

Use one `ServerSocket` for TCP join and one `DatagramSocket` for UDP discovery. The server accepts only the current invite code.

Core behavior:

```java
public final class FamilyBindingLanServer {
    public interface Listener {
        void onStarted(int port);
        void onJoined(String childDeviceId);
        void onError(String message);
    }

    public void start(FamilyBindingInvite invite, String deviceId, Listener listener) {
        if (invite == null || !invite.isValid()) {
            if (listener != null) {
                listener.onError("Binding invite is missing.");
            }
            return;
        }
        stop();
        // Create the TCP ServerSocket first, then start the UDP discovery loop with that port.
    }

    public void stop() {
        // Close UDP and TCP sockets and interrupt worker threads.
    }
}
```

TCP join handling:

```java
FamilyBindingProtocol.JoinRequest request = FamilyBindingProtocol.parseJoinRequest(requestJson);
if (!invite.bindingCode.equals(request.bindingCode)) {
    return FamilyBindingProtocol.buildJoinResponse(false, "Binding code is incorrect.", null);
}
listener.onJoined(request.deviceId);
return FamilyBindingProtocol.buildJoinResponse(true, "", invite);
```

UDP discovery handling:

```java
if (SyncMessages.FAMILY_BINDING_DISCOVERY_REQUEST.equals(type)) {
    String response = FamilyBindingProtocol.buildDiscoveryResponse(deviceId, tcpPort);
    socket.send(new DatagramPacket(responseBytes, responseBytes.length, packet.getAddress(), packet.getPort()));
}
```

- [ ] **Step 3: Wire parent screen lifecycle**

In `FamilySetupActivity`:

- Add `FamilyBindingLanServer bindingServer;`
- Start server when rendering parent binding screen with a valid invite.
- Stop server in `onDestroy`.
- Update waiting status when server starts or child joins.
- Keep the “进入家庭护眼首页” button available.

- [ ] **Step 4: Build APK**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Expected: exit code 0.

---

## Task 4: Child LAN Binding Client

**Intent:** Child phone discovers the parent phone and joins by binding code.

**Files:**

- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyBindingLanClient.java`
- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilySetupActivity.java`
- Modify: `i18n/source/zh-CN.json`
- Modify: `i18n/source/en-US.json`

- [ ] **Step 1: Implement `FamilyBindingLanClient`**

Client behavior:

```java
public final class FamilyBindingLanClient {
    public JoinResult join(String deviceId, String bindingCode) {
        // Discover parent service by UDP, then send one TCP join request.
        return JoinResult.failed("Parent phone not found.");
    }
}
```

Discovery:

- Send `FamilyBindingProtocol.buildDiscoveryRequest(deviceId)` to `255.255.255.255:17429`.
- Wait up to 3 seconds for `familyBindingDiscoveryResponse`.
- Read host from UDP packet address and port from JSON.

Join:

- Open TCP socket to discovered host/port.
- Send `FamilyBindingProtocol.buildJoinRequest(deviceId, bindingCode)` plus newline.
- Read one-line response.
- Parse with `FamilyBindingProtocol.parseJoinResponse`.

Result:

```java
public static final class JoinResult {
    public final boolean success;
    public final String error;
    public final String familyId;
    public final ChildProfile childProfile;
}
```

- [ ] **Step 2: Update child button behavior**

In `FamilySetupActivity.joinFamilyByCode()`:

- First try local `store.consumeFamilyBindingCode(draft.bindingCode)` for same-device development.
- If local consume fails, run `FamilyBindingLanClient.join(store.getDeviceId(), draft.bindingCode)` on a background thread.
- On success, call `store.saveRemoteFamilyChildBinding(result.familyId, result.childProfile)`.
- Then open `FamilyHomeActivity`.
- On failure, show a plain retryable toast.

- [ ] **Step 3: Add user-facing strings**

Chinese:

```json
"family.binding.searching": "正在查找家长手机",
"family.binding.joined": "已加入家庭护眼",
"family.binding.notFound": "没有找到家长手机。请确认两台手机在同一 WiFi，并停留在绑定码页面。"
```

English:

```json
"family.binding.searching": "Looking for the parent phone",
"family.binding.joined": "Joined Family Eye Care",
"family.binding.notFound": "Parent phone not found. Keep both phones on the same WiFi and leave the parent phone on the binding code screen."
```

- [ ] **Step 4: Regenerate i18n resources**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File i18n\tools\Update-I18nResources.ps1
```

Expected: resources generated.

---

## Task 5: Verification and Device Install

**Intent:** Verify the feature in code and on the two named phones.

- [ ] **Step 1: Run model tests**

Run the existing `FamilyModeModelTest` manual command.

Expected:

```text
All Android family mode model tests passed.
```

- [ ] **Step 2: Build APK**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Expected:

- Exit code 0.
- APK exists at `outputs/android/EyeTimeTrackerAndroid-debug.apk`.
- v2/v3 signature verification true.

- [ ] **Step 3: Install to connected devices**

Run:

```powershell
adb devices
adb -s 660005a0 install -r outputs\android\EyeTimeTrackerAndroid-debug.apk
adb -s efe1f62 install -r outputs\android\EyeTimeTrackerAndroid-debug.apk
```

If only one phone is connected, install to the connected phone and record which verification was blocked.

- [ ] **Step 4: Manual device verification**

On 小米 15:

- Open app.
- Enter family setup as parent if not already set.
- Generate binding code and stay on binding page.

On 红米 K40:

- Open app.
- Enter family setup as child device.
- Enter the code shown on 小米 15.
- Confirm the app opens family home as child device.
- Confirm child home has the stats page button in the adult-home position.
- Confirm child home has no settings entry.

## Risks

- Some WiFi routers block broadcast packets. If discovery fails but both phones are on the same WiFi, the next fallback should be showing parent IP address and allowing manual IP entry, but that fallback is not part of this round.
- Android OEM background/network policies can interrupt the temporary server if the parent leaves the binding page. This round keeps the server tied to the visible binding page.
- This is not cloud binding. It proves the product flow and local family handoff, but formal v1 still needs account, family space, cloud sync, parent reports, and rule downlink.
