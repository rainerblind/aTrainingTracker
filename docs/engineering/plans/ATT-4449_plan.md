# Stage 3: Implementation Plan - ATT-4449: [Bug] [High] Granting Bluetooth scan permission after selecting Bluetooth via '+' FAB cancels pairing flow and stays on Known/Paired tab

**Ticket**: [ATT-4449](https://rainerblind.atlassian.net/browse/ATT-4449)  
**Sub-task**: [ATT-4510](https://rainerblind.atlassian.net/browse/ATT-4510) (`[Impl-Plan]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-PRI-006` (`REQ-PRI-006.3`), `REQ-CON-019` (`REQ-CON-019.2`)  
**Test Mapping**: `TST-PRI-005` (`TST-PRI-005.7`, `TST-PRI-005.8`, `TST-PRI-005.9`, `TST-PRI-005.10`), `TST-CON-011` (`TST-CON-011.7`)  
**Branch**: `feature/ATT-4449`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Description & Background

In `aTrainingTracker`, wireless sensor discovery and pairing take place in `DevicesTabbedScreen.kt`. Under Android 12+ (API 31+), discovery of Bluetooth Low Energy (BLE) peripherals requires the runtime permission `android.permission.BLUETOOTH_SCAN`.

During physical device evaluation on Android 12+ with fresh installs or revoked permissions, two severe defects prevent athletes from discovering and pairing BLE sensors:

1. **Symptom 1 (Pairing Flow Cancelled on Permission Grant)**:
   When an athlete views `DevicesTabbedScreen` without `BLUETOOTH_SCAN` permission (e.g., on the *Known* or *Paired* sensors tab), taps the floating action button (`+` FAB), and selects *Bluetooth* (`Protocol.BLUETOOTH_LE`):
   - The app stores `pendingPairingProtocol = Protocol.BLUETOOTH_LE` and triggers `bluetoothScanLauncher.launch(...)`.
   - When the athlete grants `BLUETOOTH_SCAN` in the system dialog, `bluetoothScanLauncher` sets `hasBluetoothScanPermission = true` and `selectedPairingProtocol = Protocol.BLUETOOTH_LE`.
   - Because `DisposableEffect(hasBluetoothScanPermission)` in `DevicesTabbedScreen.kt` was keyed on `hasBluetoothScanPermission`, the `false -> true` state transition caused Jetpack Compose to immediately dispose of the previous effect for key `false`.
   - The `onDispose` block executed `selectedPairingProtocol = null`, silently destroying the pairing state before `DeviceTypeSelectionDialog` could render.
   - The athlete was left stranded on the *Known* / *Paired* tab without dialog presentation, without scan dispatch, and without pager navigation to the *Available* tab (Page 0).

2. **Symptom 2 (Zero BLE Scan Results Without Location Permission)**:
   In `app/src/main/AndroidManifest.xml`, `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />` was declared without `android:usesPermissionFlags="neverForLocation"`.
   - Under Android 12+ (API 31+), omitting `neverForLocation` causes the Android framework's Bluetooth service to require runtime `ACCESS_FINE_LOCATION` to deliver BLE scan results.
   - If runtime location is not held, `BluetoothLeScanner` silently discards 100% of incoming BLE scan advertisements, yielding zero discovered sensors.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRI-006` (*Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement*)
  * `REQ-PRI-006.3`: Decoupled screen teardown lifecycle (`DisposableEffect(Unit)`) from permission elevation (`LaunchedEffect(hasBluetoothScanPermission)`), ensuring pairing state is preserved when permission is granted.
* **Requirement**: `REQ-CON-019` (*Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture*)
  * `REQ-CON-019.2`: Manifest `neverForLocation` declaration on `BLUETOOTH_SCAN` (`android:usesPermissionFlags="neverForLocation"` and `tools:targetApi="s"`), ensuring unsuppressed BLE scan result delivery without requiring location permissions.
* **Test Mapping**: `TST-PRI-005` (`TST-PRI-005.7`, `TST-PRI-005.8`, `TST-PRI-005.9`, `TST-PRI-005.10`), `TST-CON-011` (`TST-CON-011.7`)
  * `TST-PRI-005.7`: Source contract test in `DevicesTabbedPermissionContractTest.kt` asserting presence of `DisposableEffect(Unit)` and `LaunchedEffect(hasBluetoothScanPermission)` and absence of `DisposableEffect(hasBluetoothScanPermission)`.
  * `TST-CON-011.7`: Manifest contract test in `DevicesTabbedPermissionContractTest.kt` verifying `android:usesPermissionFlags="neverForLocation"` and `tools:targetApi="s"`.
  * `TST-PRI-005.8`: State machine simulation test in `DevicesTabbedPermissionContractTest.kt` asserting `selectedPairingProtocol` retention across permission elevation.
  * `TST-PRI-005.9`: 9-language localization audit verifying 100% translation parity.
  * `TST-PRI-005.10`: Full clean-room test suite regression execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Regression**: All existing sensor pairing, filtering, and connection tests must remain 100% green.
2. **Resource Conservation**: Bluetooth scanning must not start on API 31+ without `BLUETOOTH_SCAN` permission, and must be stopped via `tabViewModel.stopSearching()` whenever leaving `DevicesTabbedScreen`.
3. **Location Tracking Independence**: Outdoor GPS tracking in `TrackerService` via `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION` remains completely independent of sensor discovery permission flags.
4. **Idiomatic Compose Lifecycles**: Component teardown effects (`DisposableEffect(Unit)`) must never be multiplexed with state-driven side effects (`LaunchedEffect(hasBluetoothScanPermission)`).
5. **Code Lock Until Gate 3 Passes (Rule 4)**: No production source code in `app/src/main/...` shall be created or edited until `check-gate` passes on this plan.
6. **Parent Human Gate Invariance (Rule 3)**: Terminal completion of parent ticket `ATT-4449` is strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

```
+--------------------------------------------------------------------------------------------------+
| app/src/main/AndroidManifest.xml                                                                |
+--------------------------------------------------------------------------------------------------+
| <uses-permission                                                                                |
|     android:name="android.permission.BLUETOOTH_SCAN"                                            |
|     android:usesPermissionFlags="neverForLocation"                                              |
|     tools:targetApi="s" />                                                                       |
+--------------------------------------------------------------------------------------------------+
                                                 |
                                                 v enables BLE scan delivery without fine location
+--------------------------------------------------------------------------------------------------+
| DevicesTabbedScreen.kt (Jetpack Compose Lifecycle Decoupling)                                    |
+--------------------------------------------------------------------------------------------------+
|                                                                                                  |
| // 1. Screen Teardown Lifecycle (Constant Key 'Unit'):                                            |
| DisposableEffect(Unit) {                                                                         |
|     onDispose {                                                                                  |
|         tabViewModel.stopSearching()                                                             |
|         showProtocolSheet = false                                                                |
|         selectedPairingProtocol = null                                                           |
|     }                                                                                            |
| }                                                                                                |
|                                                                                                  |
| // 2. Reactive Search Trigger (Dynamic Key 'hasBluetoothScanPermission'):                        |
| LaunchedEffect(hasBluetoothScanPermission) {                                                     |
|     if (hasBluetoothScanPermission) {                                                            |
|         tabViewModel.startSearching()                                                            |
|     }                                                                                            |
| }                                                                                                |
|                                                                                                  |
| // 3. Pairing Flow State Retention:                                                              |
| bluetoothScanLauncher callback:                                                                  |
|   hasBluetoothScanPermission = true                                                              |
|   selectedPairingProtocol = protocolToPair  --> DisposableEffect(Unit) does NOT dispose!        |
|                                             --> selectedPairingProtocol preserved!              |
|                                             --> DeviceTypeSelectionDialog renders!               |
+--------------------------------------------------------------------------------------------------+
```

### UI Consistency (Rule 23 — Mandatory UI Check)
* **Reference screen / component**: `DevicesTabbedScreen.kt` and `DeviceTypeSelectionDialog.kt`.
* **Reused components**: Existing Compose dialogs (`DeviceTypeSelectionDialog`), bottom sheets (`PairingProtocolBottomSheet`), and view models (`DevicesTabbedViewModel`).
* **Theme tokens**: Existing Material 3 theme colors (`MaterialTheme.colorScheme`), typography, and padding tokens.
* **New one-off styles & justification**: None. No new visual styles or UI elements are introduced; the changes strictly fix Compose effect lifecycles and manifest permission attributes to restore existing UI dialog transitions.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Manifest Declaration (`app/src/main/AndroidManifest.xml`)
* **File**: `app/src/main/AndroidManifest.xml`
* **Changes**: Add `android:usesPermissionFlags="neverForLocation"` and `tools:targetApi="s"` to `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />`.
* **Before**:
  ```xml
  <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
  ```
* **After**:
  ```xml
  <uses-permission
      android:name="android.permission.BLUETOOTH_SCAN"
      android:usesPermissionFlags="neverForLocation"
      tools:targetApi="s" />
  ```

### Step 2: Compose Effect Decoupling (`DevicesTabbedScreen.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`
* **Changes**: Decouple lines 501–511 from `DisposableEffect(hasBluetoothScanPermission)` into `DisposableEffect(Unit)` and `LaunchedEffect(hasBluetoothScanPermission)`.
* **Before**:
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
* **After**:
  ```kotlin
  // Screen lifecycle cleanup: stop search and dismiss pairing sheets when leaving screen
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

### Step 3: Contract & Unit Verification Suite Update (`DevicesTabbedPermissionContractTest.kt`)
* **File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedPermissionContractTest.kt`
* **Changes**:
  1. In `testDevicesTabbedScreen_sourceContract_verifiesBluetoothScanGating`, update assertion 4 to check for `DisposableEffect(Unit)` and `LaunchedEffect(hasBluetoothScanPermission)`, and assert `!content.contains("DisposableEffect(hasBluetoothScanPermission)")`.
  2. Add `testManifestDeclaration_bluetoothScanHasNeverForLocation()` to parse `AndroidManifest.xml` and verify `neverForLocation` and `tools:targetApi="s"` are declared on `BLUETOOTH_SCAN`.
  3. Add `testPairingFlow_permissionElevationPreservesSelectedPairingProtocol()` to verify state retention logic when permission changes from `false` to `true`.

### Step 4: Targeted Unit Test Verification
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedPermissionContractTest"
  ```
* **Expected Result**: 100% pass across all tests in `DevicesTabbedPermissionContractTest`.

### Step 5: Living Documentation Verification
* **Command**:
  ```bash
  python3 tools/verify_requirement_governance.py
  ```
* **Expected Result**: Exit code 0 (`PASS`).

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction (Step 4), followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: All changes are localized to git branch `feature/ATT-4449`. A single branch revert (`git checkout main` or `git reset --hard`) safely removes all edits without impacting production branches.
