# Stage 1 Analysis: ATT-4449 - [Bug] [High] Granting Bluetooth scan permission after selecting Bluetooth via '+' FAB cancels pairing flow and stays on Known/Paired tab

**Ticket**: [ATT-4449](https://rainerblind.atlassian.net/browse/ATT-4449)  
**Sub-task**: [ATT-4508](https://rainerblind.atlassian.net/browse/ATT-4508) (`[Analysis]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Branch**: `feature/ATT-4449`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation

In `aTrainingTracker`, users configure and pair wireless sensors via the tabbed Sensors screen (`DevicesTabbedScreen.kt`). On Android 12+ (API 31+), discovery of Bluetooth Low Energy (BLE) peripherals requires the runtime permission `android.permission.BLUETOOTH_SCAN`.

During physical device evaluation on Android 12+ with fresh installs or revoked permissions, two severe defects prevent users from discovering and pairing BLE sensors:

### Symptom 1: Pairing Flow Cancelled After Granting Bluetooth Scan Permission
When the athlete is viewing `DevicesTabbedScreen` (for example, on the *Known* or *Paired* sensors tab) and does not yet hold the `BLUETOOTH_SCAN` permission:
1. The athlete taps the floating action button (`+` FAB), which opens `PairingProtocolBottomSheet`.
2. The athlete selects *Bluetooth* (`Protocol.BLUETOOTH_LE`).
3. Because `hasBluetoothScanPermission == false`, the app saves the target protocol (`pendingPairingProtocol = Protocol.BLUETOOTH_LE`) and triggers the system permission launcher (`bluetoothScanLauncher.launch(...)`) requesting `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`.
4. The Android OS presents the runtime permission dialog ("Allow aTrainingTracker to find, connect to, and determine the relative position of nearby devices?").
5. The athlete taps **Allow** / **Grant**.
6. The permission callback fires, sets `hasBluetoothScanPermission = true`, and sets `selectedPairingProtocol = Protocol.BLUETOOTH_LE` so that `DeviceTypeSelectionDialog` should open to let the athlete choose the sensor type (Heart Rate, Power, Speed/Cadence).
7. **Observed Defect**: Instead of opening `DeviceTypeSelectionDialog`, the pairing flow is immediately cancelled. `DeviceTypeSelectionDialog` never appears, `tabViewModel.startPairingScan(...)` is never called, the pager does not animate to Page 0 (*Available* tab), and the screen remains static on whichever tab the athlete was viewing (*Known* or *Paired*).

### Symptom 2: BLE Scan Finds Zero Sensors on Android 12+ Unless Location Permission is Granted
Even after granting `BLUETOOTH_SCAN` ("Nearby devices"), BLE sensor discovery (`BTSearchForNewDevicesEngine.java`) fails to discover any nearby BLE peripherals unless the athlete has also separately granted `ACCESS_FINE_LOCATION`.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Failure Mechanism 1: `DisposableEffect(hasBluetoothScanPermission)` Effect Key Mutation Lifecycle Bug

In `DevicesTabbedScreen.kt`, line 118:
```kotlin
val bluetoothScanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
) { _ ->
    val granted = checkHasBluetoothScan()
    hasBluetoothScanPermission = granted
    if (granted) {
        tabViewModel.startSearching()
        pendingPairingProtocol?.let { protocolToPair ->
            pendingPairingProtocol = null
            selectedPairingProtocol = protocolToPair
        }
    }
}
```

And lines 501–511:
```kotlin
// Keep searching active while displaying tabs (only when scan permission is held)
DisposableEffect(hasBluetoothScanPermission) {
    if (hasBluetoothScanPermission) {
        tabViewModel.startSearching()
    }
    onDispose {
        tabViewModel.stopSearching()
        showProtocolSheet = false
        selectedPairingProtocol = null
    }
}
```

The bug arises directly from the key passed to `DisposableEffect`:
1. When `DevicesTabbedScreen` is initially composed while `hasBluetoothScanPermission == false`, `DisposableEffect` executes for key `false`. Because `hasBluetoothScanPermission` is false, it does not invoke `tabViewModel.startSearching()`. Its `onDispose` block is registered.
2. The athlete opens `PairingProtocolBottomSheet` and selects Bluetooth. `pendingPairingProtocol` is assigned `Protocol.BLUETOOTH_LE`, and `bluetoothScanLauncher.launch(...)` is invoked.
3. The athlete grants `BLUETOOTH_SCAN`.
4. In the activity result callback:
   - `hasBluetoothScanPermission` becomes `true`.
   - `pendingPairingProtocol` is retrieved and assigned to `selectedPairingProtocol = Protocol.BLUETOOTH_LE`.
5. Modifying `hasBluetoothScanPermission` and `selectedPairingProtocol` schedules a recomposition.
6. **The Critical Lifecycle Flaw**: In the Jetpack Compose runtime lifecycle, when the key of a `DisposableEffect(key)` changes (`false -> true`), Compose **must dispose of the previous effect before launching the new effect**. Compose invokes the `onDispose` lambda of key `false` immediately during recomposition.
7. The `onDispose` lambda executes:
   ```kotlin
   tabViewModel.stopSearching()
   showProtocolSheet = false
   selectedPairingProtocol = null // <--- SILENTLY DESTROYS THE PAIRING STATE!
   ```
8. Following the disposal of key `false`, the effect block for key `true` runs and calls `tabViewModel.startSearching()`.
9. However, by the time the composable evaluates line 554:
   ```kotlin
   selectedPairingProtocol?.let { protocolToPair ->
       DeviceTypeSelectionDialog(...)
   }
   ```
   `selectedPairingProtocol` has already been reset to `null` by the `onDispose` callback of the previous key!
10. Consequently:
    - `DeviceTypeSelectionDialog` never renders.
    - The athlete is never prompted to select a sensor type (`DeviceType.HRM`, `BIKE_POWER`, etc.).
    - `tabViewModel.startPairingScan(protocolToPair, chosenDeviceType)` is never triggered.
    - `pagerState.animateScrollToPage(0)` is never triggered.
    - The athlete remains stranded on the current tab (*Known* or *Paired*).

Screen teardown cleanup (`selectedPairingProtocol = null`, `showProtocolSheet = false`, `tabViewModel.stopSearching()`) was erroneously coupled to the dynamic permission state `hasBluetoothScanPermission` instead of the screen's lifecycle (`DisposableEffect(Unit)`).

---

### 2.2 Failure Mechanism 2: Missing `android:usesPermissionFlags="neverForLocation"` in `AndroidManifest.xml`

In `app/src/main/AndroidManifest.xml` lines 40–41:
```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

Under Android 12+ (API 31+ / Android S), `android.permission.BLUETOOTH_SCAN` is by default treated as a permission that can derive user physical location (for example, through BLE beacon trilateration).

According to Android platform specifications:
- If an app declares `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />` without `android:usesPermissionFlags="neverForLocation"`, the Android framework's `BluetoothLeScanner` **requires that the app also holds runtime `ACCESS_FINE_LOCATION`**. If `ACCESS_FINE_LOCATION` is not granted, the system Bluetooth service silently drops 100% of incoming BLE scan advertisements, and `ScanCallback.onScanResult` is never invoked.
- When an app declares:
  ```xml
  <uses-permission
      android:name="android.permission.BLUETOOTH_SCAN"
      android:usesPermissionFlags="neverForLocation"
      tools:targetApi="s" />
  ```
  the app strongly asserts to the Android OS that it does not use Bluetooth scan results to derive physical location. On Android 12+, this allows `BluetoothLeScanner` to deliver scan results for all filtered BLE sensors (such as Heart Rate Monitors, Cycling Power meters, Speed/Cadence sensors) **purely with `BLUETOOTH_SCAN` permission**, without requiring runtime location permissions.

Because `neverForLocation` was omitted in `AndroidManifest.xml`, BLE sensor discovery on Android 12+ silently yielded zero discovered sensors unless the user had already granted full location permissions.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Decouple Screen Teardown from Permission Trigger in `DevicesTabbedScreen.kt`**:
     - Separate screen disposal cleanup (`DisposableEffect(Unit) { onDispose { tabViewModel.stopSearching(); showProtocolSheet = false; selectedPairingProtocol = null } }`) from the permission trigger (`LaunchedEffect(hasBluetoothScanPermission) { if (hasBluetoothScanPermission) tabViewModel.startSearching() }`).
     - Guarantee that a `false -> true` permission transition does not execute `onDispose` and preserves `selectedPairingProtocol`.
  2. **Pairing Dialog Progression**:
     - Verify that granting `BLUETOOTH_SCAN` from the `+` FAB immediately opens `DeviceTypeSelectionDialog`, and selecting a sensor type starts `tabViewModel.startPairingScan(protocol, deviceType)` and scrolls to Page 0 (*Available* tab).
  3. **Declare `neverForLocation` on `BLUETOOTH_SCAN` in `AndroidManifest.xml`**:
     - Add `android:usesPermissionFlags="neverForLocation"` and `tools:targetApi="s"` to `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />`.
  4. **Update Contract & Behavior Tests**:
     - Update `DevicesTabbedPermissionContractTest.kt` to reflect the decoupled `DisposableEffect(Unit)` and `LaunchedEffect(hasBluetoothScanPermission)` pattern and verify the manifest declaration.
  5. **Synchronize Living Documentation**:
     - Update `docs/requirements.md` (`REQ-PRI-006` and `REQ-CON-019`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No modifications to ANT+ discovery, ANT+ status sheets, or ANT radio services.
  2. No changes to `ControlTrackingScreen.kt` workout start permission rationales or location cascades.
  3. No changes to database schemas, `DevicesDatabaseManager`, or GATT connection logic in `MyBTLEDevice`.
  4. No redesign of `DeviceTypeSelectionDialog` UI or device type categorization.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### 4.1 Requirement `REQ-PRI-006` (`DevicesTabbedScreen.kt` Permission Gating)
* **Original Requirement ID & Target**: `REQ-PRI-006.3` (*Just-in-Time Bluetooth Permissions for Sensor Discovery*) targeting `DevicesTabbedScreen.kt`.
* **Historical Origin & Commit Trace**: Added in Sprint 2026-43.1 under `ATT-3998` (commit `21f5c90e`).
* **Root Reason for Existing Formulation**: ATT-3998 introduced JIT permission gating in `DevicesTabbedScreen.kt` to prevent the screen from initiating auto-search without holding `BLUETOOTH_SCAN` on API 31+. The developer placed `tabViewModel.startSearching()` inside `DisposableEffect(hasBluetoothScanPermission)` with cleanup in `onDispose`. However, placing screen cleanup (`selectedPairingProtocol = null`) inside an effect keyed on a mutable permission state caused Compose to treat a permission elevation as a screen teardown, creating the regression where `selectedPairingProtocol` was destroyed.
* **Preservation of Core Invariants**: The core invariant that sensor search only runs when permission is granted (`tabViewModel.startSearching()`) and stops when leaving the screen (`tabViewModel.stopSearching()`) is 100% preserved. Decoupling screen disposal (`DisposableEffect(Unit)`) from permission elevation (`LaunchedEffect(hasBluetoothScanPermission)`) eliminates the state wipe while strictly maintaining permission gating.

### 4.2 Requirement `REQ-CON-019` (`AndroidManifest.xml` `neverForLocation` Attribute)
* **Original Requirement ID & Target**: `REQ-CON-019.2` (*Unsuppressed BLE Scan Result Delivery*) targeting `AndroidManifest.xml`.
* **Historical Origin & Commit Trace**: Formulated in Sprint 2026-41.4 under `ATT-2773` (commit `67451a9`).
* **Root Reason for Existing Formulation**: In ATT-2773, `neverForLocation` was removed based on an erroneous assumption that asserting `neverForLocation` caused Android 12+ to drop scan results when `ScanFilter` was null. In reality, omitting `neverForLocation` is what causes the Android OS Bluetooth stack to require runtime `ACCESS_FINE_LOCATION` for all BLE scans. Because `aTrainingTracker` uses BLE scanning strictly for sensor telemetry (heart rate, power, cadence, speed) and never to locate the athlete geographically, declaring `neverForLocation` is standard Android architectural best practice and restores BLE scanning when location permission is not held.
* **Preservation of Core Invariants**: GPS tracking and outdoor route recording via `ACCESS_FINE_LOCATION` remain untouched. BLE scanning functions with standard `BLUETOOTH_SCAN` privileges.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 `app/src/main/AndroidManifest.xml`
Declare `neverForLocation` and `tools:targetApi="s"`:
```xml
<uses-permission
    android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />
```

### 5.2 `DevicesTabbedScreen.kt`
Replace lines 501–511:
```kotlin
// BEFORE (Buggy): Keyed on dynamic permission state
DisposableEffect(hasBluetoothScanPermission) {
    if (hasBluetoothScanPermission) {
        tabViewModel.startSearching()
    }
    onDispose {
        tabViewModel.stopSearching()
        showProtocolSheet = false
        selectedPairingProtocol = null
    }
}
```
With decoupled screen lifecycle and permission triggers:
```kotlin
// AFTER (Fixed): Screen lifecycle cleanup on exit
DisposableEffect(Unit) {
    onDispose {
        tabViewModel.stopSearching()
        showProtocolSheet = false
        selectedPairingProtocol = null
    }
}

// Reactively start searching whenever scan permission is granted
LaunchedEffect(hasBluetoothScanPermission) {
    if (hasBluetoothScanPermission) {
        tabViewModel.startSearching()
    }
}
```

When the athlete grants `BLUETOOTH_SCAN`:
1. `bluetoothScanLauncher` sets `hasBluetoothScanPermission = true` and `selectedPairingProtocol = Protocol.BLUETOOTH_LE`.
2. `DisposableEffect(Unit)` does **not** re-execute and does **not** call `onDispose`.
3. `selectedPairingProtocol` remains intact (`Protocol.BLUETOOTH_LE`).
4. `LaunchedEffect(hasBluetoothScanPermission)` executes and triggers `tabViewModel.startSearching()`.
5. `DeviceTypeSelectionDialog` renders with `protocol = Protocol.BLUETOOTH_LE`.
6. Athlete selects a sensor type -> `tabViewModel.startPairingScan(protocol, chosenDeviceType)` starts and `pagerState.animateScrollToPage(0)` smoothly scrolls to the *Available* tab.
7. When the athlete navigates away from `DevicesTabbedScreen`, `DisposableEffect(Unit)` disposes, stopping search and clearing pairing sheets.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Sensor search MUST NOT start on Android 12+ without `BLUETOOTH_SCAN` permission.
  2. When leaving `DevicesTabbedScreen`, `tabViewModel.stopSearching()` MUST execute to release Bluetooth radio resources.
  3. Pre-Android 12 (API < 31) platforms MUST continue functioning without regression.
  4. Location-based tracking (GPS recording in `TrackerService`) MUST remain completely unaffected.
  5. 100% full clean-room unit test suite pass rate.

* **Risk Rating**: **LOW**
  - **Justification**: The fix consists of:
    1. A single manifest attribute (`neverForLocation`) that aligns with Android platform standards.
    2. Decoupling a Compose `DisposableEffect(Unit)` from a `LaunchedEffect(hasBluetoothScanPermission)`, which is standard idiomatic Compose practice for separating screen teardown from state-driven side effects.
  - No database schemas, background services, or network communication pathways are modified.
