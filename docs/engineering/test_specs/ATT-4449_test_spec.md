# Stage 2: Requirement & Test Specification - ATT-4449: [Bug] [High] Granting Bluetooth scan permission after selecting Bluetooth via '+' FAB cancels pairing flow and stays on Known/Paired tab

**Ticket**: [ATT-4449](https://rainerblind.atlassian.net/browse/ATT-4449)  
**Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-PRI-006` (*Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement*), `REQ-CON-019` (*Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture*)  
**Test Spec ID**: `TST-PRI-005`, `TST-CON-011`  
**Branch**: `feature/ATT-4449`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-PRI-006, REQ-CON-019)

### 1.1 Problem Statement & Rationale

In `aTrainingTracker`, discovery and pairing of wireless sensors occurs in `DevicesTabbedScreen.kt`. On Android 12+ (API 31+), discovery of Bluetooth Low Energy (BLE) peripherals requires the runtime permission `android.permission.BLUETOOTH_SCAN`.

During physical device evaluation on Android 12+ with fresh installs or revoked permissions, two severe defects prevent users from discovering and pairing BLE sensors:

1. **Symptom 1 (Pairing Flow Cancelled After Granting Scan Permission)**:
   When an athlete views `DevicesTabbedScreen` without `BLUETOOTH_SCAN` permission (e.g., on the *Known* or *Paired* sensors tab), taps the `+` FAB, and selects *Bluetooth* (`Protocol.BLUETOOTH_LE`):
   - The app stores `pendingPairingProtocol = Protocol.BLUETOOTH_LE` and launches `bluetoothScanLauncher.launch(...)`.
   - When the athlete grants the permission in the system dialog, `bluetoothScanLauncher` sets `hasBluetoothScanPermission = true` and `selectedPairingProtocol = Protocol.BLUETOOTH_LE`.
   - Because `DisposableEffect(hasBluetoothScanPermission)` in `DevicesTabbedScreen.kt` was keyed on `hasBluetoothScanPermission`, the `false -> true` state transition caused Jetpack Compose to immediately dispose of the previous effect for key `false`.
   - The `onDispose` block executed `selectedPairingProtocol = null`, silently destroying the pairing state before `DeviceTypeSelectionDialog` could render.
   - The athlete was left stranded on the *Known* / *Paired* tab without dialog presentation, without scan dispatch, and without pager navigation to the *Available* tab (Page 0).

2. **Symptom 2 (Zero BLE Scan Results Without Location Permission)**:
   In `app/src/main/AndroidManifest.xml`, `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />` was declared without `android:usesPermissionFlags="neverForLocation"`.
   - Under Android 12+ (API 31+), omitting `neverForLocation` causes the Android framework's Bluetooth service to require runtime `ACCESS_FINE_LOCATION` to deliver BLE scan results.
   - If runtime location is not held, `BluetoothLeScanner` silently discards 100% of incoming BLE scan advertisements, yielding zero discovered sensors.

---

### 1.2 Functional & Architectural Requirements

The system SHALL refine `REQ-PRI-006` (`REQ-PRI-006.3`) and `REQ-CON-019` (`REQ-CON-019.2`) with the following architectural clauses:

1. **Decoupled Screen Teardown Lifecycle (`DevicesTabbedScreen.kt`, `REQ-PRI-006.3`)**:
   - `DevicesTabbedScreen.kt` SHALL isolate screen teardown cleanup strictly within `DisposableEffect(Unit)`:
     ```kotlin
     DisposableEffect(Unit) {
         onDispose {
             tabViewModel.stopSearching()
             showProtocolSheet = false
             selectedPairingProtocol = null
         }
     }
     ```
   - Modifying runtime permission state (`hasBluetoothScanPermission`) SHALL NOT trigger `onDispose` and SHALL NOT reset `selectedPairingProtocol` or `showProtocolSheet`.

2. **Reactive Search Activation on Permission Grant (`DevicesTabbedScreen.kt`, `REQ-PRI-006.3`)**:
   - `DevicesTabbedScreen.kt` SHALL reactively observe permission state changes via `LaunchedEffect(hasBluetoothScanPermission)`:
     ```kotlin
     LaunchedEffect(hasBluetoothScanPermission) {
         if (hasBluetoothScanPermission) {
             tabViewModel.startSearching()
         }
     }
     ```
   - When `hasBluetoothScanPermission` transitions from `false` to `true`, `tabViewModel.startSearching()` SHALL be dispatched without destroying active pairing dialog state.

3. **Pairing Flow Progression Contract (`DevicesTabbedScreen.kt`, `REQ-PRI-006.3`)**:
   - When `bluetoothScanLauncher` completes with granted permissions:
     - `pendingPairingProtocol` SHALL be assigned to `selectedPairingProtocol`.
     - `DeviceTypeSelectionDialog(protocol = selectedPairingProtocol)` SHALL be rendered immediately.
     - When the athlete selects a sensor type (`chosenDeviceType`):
       - `tabViewModel.startPairingScan(protocol, chosenDeviceType)` SHALL be invoked.
       - `pagerState.animateScrollToPage(0)` SHALL scroll to the *Available* tab.

4. **Manifest `neverForLocation` Declaration (`AndroidManifest.xml`, `REQ-CON-019.2`)**:
   - `app/src/main/AndroidManifest.xml` SHALL declare `android:usesPermissionFlags="neverForLocation"` and `tools:targetApi="s"` on `android.permission.BLUETOOTH_SCAN`:
     ```xml
     <uses-permission
         android:name="android.permission.BLUETOOTH_SCAN"
         android:usesPermissionFlags="neverForLocation"
         tools:targetApi="s" />
     ```
   - This asserts to the Android 12+ framework that Bluetooth scanning is used strictly for sensor telemetry (heart rate, power, cadence, speed) and never to determine physical location, permitting unrestricted BLE advertisement delivery without requiring runtime location permissions.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Granting Scan Permission Opens Device Type Dialog**:
  * *Given* `DevicesTabbedScreen` open on Android 12+ (API 31+) with `hasBluetoothScanPermission == false`,
  * *When* the athlete taps the `+` FAB, selects *Bluetooth*, and grants `BLUETOOTH_SCAN` in the system dialog,
  * *Then* `hasBluetoothScanPermission` becomes `true`, `selectedPairingProtocol` remains `Protocol.BLUETOOTH_LE`, `DisposableEffect(Unit)` does NOT dispose, and `DeviceTypeSelectionDialog` renders immediately.

* **Criterion 2: Sensor Type Selection Initiates Scan and Pager Scroll**:
  * *Given* `DeviceTypeSelectionDialog` open with `Protocol.BLUETOOTH_LE`,
  * *When* the athlete selects a device type (e.g., `DeviceType.HRM`),
  * *Then* `tabViewModel.startPairingScan(Protocol.BLUETOOTH_LE, DeviceType.HRM)` is invoked and the pager scrolls smoothly to Page 0 (*Available* tab).

* **Criterion 3: Screen Exit Cleans Up Resources**:
  * *Given* `DevicesTabbedScreen` active with sensor search running,
  * *When* the athlete navigates away from the screen,
  * *Then* `DisposableEffect(Unit)` executes `onDispose`, invoking `tabViewModel.stopSearching()`, setting `showProtocolSheet = false`, and resetting `selectedPairingProtocol = null`.

* **Criterion 4: Manifest Declaration Delivers BLE Advertisements Without Location**:
  * *Given* an Android 12+ device running `aTrainingTracker` with granted `BLUETOOTH_SCAN` and WITHOUT `ACCESS_FINE_LOCATION`,
  * *When* scanning for BLE sensors,
  * *Then* incoming BLE advertisements are delivered to `BTSearchForNewDevicesEngine` without framework suppression.

* **Criterion 5: Pre-Android 12 Backward Compatibility**:
  * *Given* a device running Android 8.0–11 (API 26–30),
  * *When* `DevicesTabbedScreen` is opened and Bluetooth is selected,
  * *Then* scan permission checks return `true` immediately and pairing proceeds without runtime dialog friction.

---

### 1.4 System Invariants

1. **Resource Conservation**: Bluetooth radio scanning MUST NOT start on API 31+ without `BLUETOOTH_SCAN` permission, and MUST be stopped via `tabViewModel.stopSearching()` whenever leaving `DevicesTabbedScreen`.
2. **Location Tracking Separation**: GPS tracking in `TrackerService` via `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION` MUST remain completely independent of sensor discovery permission flags.
3. **Idiomatic Compose Lifecycles**: Side effects driven by state (`LaunchedEffect`) MUST NOT be multiplexed into component teardown effects (`DisposableEffect`).
4. **Clean-Room Test Regression**: 100% full-suite unit test pass rate (`./gradlew testDebugUnitTest`).

---

### 1.5. Requirement Archaeology & Chesterton's Fence Audit

#### Requirement 1: `REQ-PRI-006` (`DevicesTabbedScreen.kt` Permission Gating)
* **Original Requirement ID & Target**: `REQ-PRI-006.3` (*Just-in-Time Bluetooth Permissions for Sensor Discovery*) targeting `DevicesTabbedScreen.kt`.
* **Historical Origin & Commit Trace**: Added in Sprint 2026-43.1 under `ATT-3998` (commit `21f5c90e`).
* **Root Reason for Existing Formulation**: ATT-3998 introduced JIT permission gating in `DevicesTabbedScreen.kt` to prevent the screen from initiating auto-search without holding `BLUETOOTH_SCAN` on API 31+. The developer placed `tabViewModel.startSearching()` inside `DisposableEffect(hasBluetoothScanPermission)` with cleanup in `onDispose`. However, placing screen cleanup (`selectedPairingProtocol = null`) inside an effect keyed on a mutable permission state caused Compose to treat a permission elevation as a screen teardown, creating the regression where `selectedPairingProtocol` was destroyed.
* **Preservation of Core Invariants**: The core invariant that sensor search only runs when permission is granted (`tabViewModel.startSearching()`) and stops when leaving the screen (`tabViewModel.stopSearching()`) is 100% preserved. Decoupling screen disposal (`DisposableEffect(Unit)`) from permission elevation (`LaunchedEffect(hasBluetoothScanPermission)`) eliminates the state wipe while strictly maintaining permission gating.

#### Requirement 2: `REQ-CON-019` (`AndroidManifest.xml` `neverForLocation` Attribute)
* **Original Requirement ID & Target**: `REQ-CON-019.2` (*Unsuppressed BLE Scan Result Delivery*) targeting `AndroidManifest.xml`.
* **Historical Origin & Commit Trace**: Formulated in Sprint 2026-41.4 under `ATT-2773` (commit `67451a9`).
* **Root Reason for Existing Formulation**: In ATT-2773, `neverForLocation` was removed based on an erroneous assumption that asserting `neverForLocation` caused Android 12+ to drop scan results when `ScanFilter` was null. In reality, omitting `neverForLocation` is what causes the Android OS Bluetooth stack to require runtime `ACCESS_FINE_LOCATION` for all BLE scans. Because `aTrainingTracker` uses BLE scanning strictly for sensor telemetry (heart rate, power, cadence, speed) and never to locate the athlete geographically, declaring `neverForLocation` is standard Android architectural best practice and restores BLE scanning when location permission is not held.
* **Preservation of Core Invariants**: GPS tracking and outdoor route recording via `ACCESS_FINE_LOCATION` remain untouched. BLE scanning functions with standard `BLUETOOTH_SCAN` privileges.

---

## 2. Test Specification (TST-PRI-005, TST-CON-011)

### Test Case 1: Source Contract Verification for Decoupled Lifecycles (`TST-PRI-005.7`)
* **Scope**: Source Contract & Architecture Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedPermissionContractTest.kt`
* **Preconditions**: `DevicesTabbedScreen.kt` exists in source tree.
* **Action**: Parse and assert source structure:
  - Assert presence of `DisposableEffect(Unit)` for lifecycle teardown.
  - Assert presence of `LaunchedEffect(hasBluetoothScanPermission)` for reactive search start.
  - Assert absence of `DisposableEffect(hasBluetoothScanPermission)`.
* **Expected Result**: Lifecycle teardown is strictly decoupled from dynamic permission state.

### Test Case 2: Manifest `neverForLocation` and `tools:targetApi` Declaration (`TST-CON-011.7`)
* **Scope**: Manifest Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedPermissionContractTest.kt`
* **Preconditions**: `app/src/main/AndroidManifest.xml` exists.
* **Action**: Parse `AndroidManifest.xml` XML contents:
  - Assert `uses-permission android:name="android.permission.BLUETOOTH_SCAN"` contains `android:usesPermissionFlags="neverForLocation"`.
  - Assert `tools:targetApi="s"` is declared.
* **Expected Result**: Manifest attributes comply with Android 12+ location exemption standards.

### Test Case 3: Permission Elevation Pairing State Retention Simulation (`TST-PRI-005.8`)
* **Scope**: State Machine Simulation Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedPermissionContractTest.kt`
* **Preconditions**: Initial permission state is `false`, `pendingPairingProtocol = Protocol.BLUETOOTH_LE`, `selectedPairingProtocol = null`.
* **Action**: Simulate permission launcher callback:
  - `hasBluetoothScanPermission` transitions to `true`.
  - `pendingPairingProtocol` transfers to `selectedPairingProtocol`.
  - Simulate screen disposal effect with key `Unit` (no disposal invocation) vs. key `hasBluetoothScanPermission` (invokes disposal).
* **Expected Result**: Under decoupled `Unit` lifecycle, `selectedPairingProtocol` retains `Protocol.BLUETOOTH_LE` and is NOT wiped to `null`.

### Test Case 4: 9-Language Localization Audit (`TST-PRI-005.9`)
* **Scope**: Localization Parity Test
* **Goal**: Verify existing sensor discovery strings across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`):
  - `bluetooth_permission_scan_required`
  - `grant_permission`
* **Expected Result**: 100% parity across all 9 locales, zero missing entries.

### Test Case 5: Clean-Room Full Regression Suite (`TST-PRI-005.10`, `REQ-PRO-001`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.
* **Expected Result**: Zero test failures, zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRI-005.7` | Contract | `DevicesTabbedScreen.kt` | `REQ-PRI-006.3` | Planned (ATT-4449) |
| `TST-CON-011.7` | Contract | `AndroidManifest.xml` | `REQ-CON-019.2` | Planned (ATT-4449) |
| `TST-PRI-005.8` | Unit | State Retention Simulation | `REQ-PRI-006.3` | Planned (ATT-4449) |
| `TST-PRI-005.9` | Localization | `TranslationParityTest` | `REQ-PRI-006.6`, `REQ-UI-106` | Planned (ATT-4449) |
| `TST-PRI-005.10`| Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Planned (ATT-4449) |
