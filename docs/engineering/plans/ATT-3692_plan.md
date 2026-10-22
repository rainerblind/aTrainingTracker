# Stage 3 Implementation Plan: ATT-3692 - Remove dead sensor pairing code from ControlTrackingScreen, ControlTrackingViewModel, TrackingTabsScreen, and MainActivityWithNavigation

**Ticket**: [ATT-3692](https://rainerblind.atlassian.net/browse/ATT-3692)  
**Sub-task**: `TBD` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Branch**: `feature/ATT-3692`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Architectural Overview (SWE.2)

This ticket removes dead, unreachable ANT+ and Bluetooth LE sensor pairing code from the Control Tracking cockpit and `MainActivityWithNavigation` (`REQ-UI-393`), completing the architectural clean-up after `ATT-2791` (`REQ-UI-301`) moved all sensor pairing exclusively to **My Sensors** (`DevicesTabbedContainerFragment` / `PairingProtocolBottomSheet` / `DeviceTypeSelectionDialog`).

### Layer Decomposition
* **UI Layer (`PairingButtons.kt`, `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`)**:
  - Delete `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt`.
  - Remove unused pairing parameters (`isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection`) and the unreachable `selectingProtocol?.let { DeviceTypeSelectionDialog(...) }` block from `ControlTrackingScreen.kt` (and all 5 `@Preview` functions).
  - Remove `selectingProtocol` collection, `ControlNavigation.ToPairing` handling, and pairing arguments from `TrackingTabsScreen.kt`.
* **ViewModel Layer (`ControlTrackingViewModel.kt`)**:
  - Remove `ControlNavigation.ToPairing`.
  - Remove `_selectingProtocol`, `selectingProtocol`, `isAntProperlyInstalled()`, `isBluetoothSupported()`, `onPairingClicked()`, `onDeviceTypeSelected()`, and `onCancelDeviceTypeSelection()`.
* **Activity Layer (`MainActivityWithNavigation.kt`)**:
  - Remove `fun startPairing(protocol: Protocol, deviceType: DeviceType?)`.

### UI Consistency (Rule 23 / `docs/design_guidelines.md` Section 5)
* **Closest Existing Reference Screen**: `ControlTrackingScreen.kt` (`ATT-2791` / `REQ-UI-301`) and `DevicesTabbedContainerFragment.kt` (**My Sensors**).
* **Visual Impact**: Zero visual change to rendered layouts — only unreachable parameters, dead state flows, and an unreferenced composable file (`PairingButtons.kt`) are removed.

---

## 2. Atomic Step-by-Step Implementation Plan

### Step 1: Delete `PairingButtons.kt` & Update `ComposeSafePainterContractTest.kt` (`REQ-UI-393.1`)
* **Target Files**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt` (delete via `git rm`)
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/ComposeSafePainterContractTest.kt`
* **Changes**:
  * Delete `PairingButtons.kt`.
  * Remove `"com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt"` from `targetFiles` in `ComposeSafePainterContractTest.kt`.

### Step 2: Clean `ControlTrackingScreen.kt` (`REQ-UI-393.2`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`
* **Changes**:
  * Remove unused imports `com.atrainingtracker.banalservice.Protocol` and `com.atrainingtracker.banalservice.ui.devices.DeviceTypeSelectionDialog`.
  * Remove parameters `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, and `onCancelDeviceTypeSelection` from `ControlTrackingScreen` and from all 5 `@Preview` composables.
  * Remove the `selectingProtocol?.let { protocol -> DeviceTypeSelectionDialog(...) }` block at lines 463–470.

### Step 3: Clean `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt` & `MainActivityWithNavigation.kt` (`REQ-UI-393.3`, `REQ-UI-393.4`)
* **Target Files**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
* **Changes**:
  * In `ControlTrackingViewModel.kt`: remove `ToPairing` from `ControlNavigation`, remove `_selectingProtocol` / `selectingProtocol`, remove `isAntProperlyInstalled()`, `isBluetoothSupported()`, `onPairingClicked()`, `onDeviceTypeSelected()`, `onCancelDeviceTypeSelection()`, and unused imports (`BANALService`, `MutableStateFlow`, `asStateFlow`).
  * In `TrackingTabsScreen.kt`: remove `val selectingProtocol by controlViewModel.selectingProtocol.collectAsState()`, remove `is ControlNavigation.ToPairing -> { ... }`, and remove the 6 dead arguments from `ControlTrackingScreen(...)`.
  * In `MainActivityWithNavigation.kt`: remove `fun startPairing(protocol: Protocol, deviceType: DeviceType?)` (lines 908–921).

### Step 4: Add `ControlTrackingDeadPairingRemovalTest.kt` (`TST-UI-356`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingDeadPairingRemovalTest.kt`
* **Changes**:
  * Create unit and contract tests verifying all 4 sub-requirements of `REQ-UI-393` and 9-locale parity of `protocol_ant_plus` / `protocol_bluetooth`.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingDeadPairingRemovalTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ComposeSafePainterContractTest"
  ```

---

## 3. System Invariants & Non-Regression Guarantees

1. **Active Control Tracking Sensor Discovery Intact**: `hasPairedRemoteDevices`, `remoteDevices`, `onSearchClicked`, `ControlNavigation.ToEditDevice`, and `ControlNavigation.ShowRecoveryDialog` remain 100% untouched.
2. **My Sensors Pairing Flow Intact**: `DevicesTabbedContainerFragment`, `PairingProtocolBottomSheet`, and `DeviceTypeSelectionDialog` remain 100% untouched.
3. **Gate 3 Pre-Check Compliance (Rule 4)**: No production code is modified before `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>` exits with code `0`.

---

## 4. Requirement-to-Implementation Traceability Matrix

| Requirement ID | Planned Artifact / Component | Verification Test |
| :--- | :--- | :--- |
| `REQ-UI-393.1` | Delete `PairingButtons.kt`, update `ComposeSafePainterContractTest.kt` | `ControlTrackingDeadPairingRemovalTest`, `ComposeSafePainterContractTest` |
| `REQ-UI-393.2` | `ControlTrackingScreen.kt` | `ControlTrackingDeadPairingRemovalTest` |
| `REQ-UI-393.3` | `ControlTrackingViewModel.kt` | `ControlTrackingDeadPairingRemovalTest` |
| `REQ-UI-393.4` | `TrackingTabsScreen.kt`, `MainActivityWithNavigation.kt` | `ControlTrackingDeadPairingRemovalTest` |
