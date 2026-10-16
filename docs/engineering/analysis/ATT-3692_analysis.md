# Stage 1 Analysis: ATT-3692 - Remove dead sensor pairing code from ControlTrackingScreen, ControlTrackingViewModel, TrackingTabsScreen, and MainActivityWithNavigation

**Ticket**: [ATT-3692](https://rainerblind.atlassian.net/browse/ATT-3692)  
**Sub-task**: `TBD` (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Branch**: `feature/ATT-3692`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

In `ATT-2791` (`REQ-UI-301`), the ANT+ and Bluetooth LE sensor pairing buttons (`PairingButtons`) were deliberately removed from the Control Tracking cockpit screen (`ControlTrackingScreen`) because sensor pairing is managed exclusively in the dedicated **My Sensors** (`DevicesTabbedContainerFragment` / `PairingProtocolBottomSheet`) screen.

However, the underlying pairing state, callbacks, dialogs, navigation events, and activity helper methods were left behind as dead, unreachable code across five files:
1. `PairingButtons.kt`: Entire file (194 lines) has zero production callers.
2. `ControlTrackingScreen.kt`: Retains unused parameters (`isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection`) and an unreachable `selectingProtocol?.let { DeviceTypeSelectionDialog(...) }` block.
3. `ControlTrackingViewModel.kt`: Retains unused `ControlNavigation.ToPairing`, `_selectingProtocol` / `selectingProtocol`, `isAntProperlyInstalled()`, `isBluetoothSupported()`, `onPairingClicked()`, `onDeviceTypeSelected()`, and `onCancelDeviceTypeSelection()`.
4. `TrackingTabsScreen.kt`: Collects `selectingProtocol`, handles `is ControlNavigation.ToPairing`, and passes all 6 dead pairing parameters into `ControlTrackingScreen`.
5. `MainActivityWithNavigation.kt`: Retains `startPairing(protocol: Protocol, deviceType: DeviceType?)` which was only invoked by `ControlNavigation.ToPairing`.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Call-Site Audit Across the Codebase**:
   - `PairingButtons`: Defined in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt`. Confirmed zero callers outside its own `@Preview` functions; referenced only as a string path in `ComposeSafePainterContractTest.kt` (line 37).
   - `ControlTrackingScreen`:
     - Parameters `isAntSupported: Boolean = false` (line 65), `isBluetoothSupported: Boolean = false` (line 66), and `onPairingClicked: (Protocol) -> Unit = {}` (line 74) are never read inside `ControlTrackingScreen`'s body!
     - Parameters `selectingProtocol: Protocol? = null` (line 75), `onDeviceTypeSelected: (DeviceType) -> Unit = {}` (line 76), and `onCancelDeviceTypeSelection: () -> Unit = {}` (line 77) only feed lines 464–470 (`selectingProtocol?.let { DeviceTypeSelectionDialog(...) }`), which is permanently `null` because `onPairingClicked` is never invoked.
   - `ControlTrackingViewModel`:
     - `_selectingProtocol` / `selectingProtocol` (lines 81–82) is never mutated because `onPairingClicked` (lines 171–173) is never called.
     - Consequently, `onDeviceTypeSelected` (lines 175–181), `onCancelDeviceTypeSelection` (lines 183–185), and `ControlNavigation.ToPairing` (line 60) are 100% unreachable.
     - `isAntProperlyInstalled()` (lines 163–165) and `isBluetoothSupported()` (lines 167–169) were only passed to `ControlTrackingScreen`'s unused `isAntSupported` / `isBluetoothSupported` parameters.
   - `TrackingTabsScreen`:
     - Line 215: `val selectingProtocol by controlViewModel.selectingProtocol.collectAsState()`
     - Lines 716–721: `is ControlNavigation.ToPairing -> (context as? MainActivityWithNavigation)?.startPairing(...)`
     - Lines 755–756, 764–767: Passes `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection` to `ControlTrackingScreen`.
   - `MainActivityWithNavigation`:
     - Lines 908–921: `fun startPairing(protocol: Protocol, deviceType: DeviceType?)` has zero callers once `ControlNavigation.ToPairing` is removed.
   - **Shared Dialog Verification (`DeviceTypeSelectionDialog.kt`)**:
     - `com.atrainingtracker.banalservice.ui.devices.DeviceTypeSelectionDialog` is actively used by `DevicesTabbedContainerFragment.kt` (line 206) for pairing in the **My Sensors** screen and MUST be retained.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Delete `PairingButtons.kt` and remove its entry from `ComposeSafePainterContractTest.kt`.
  * Remove dead pairing parameters, imports, and `DeviceTypeSelectionDialog` invocation from `ControlTrackingScreen.kt` (including its `@Preview` functions).
  * Remove `ControlNavigation.ToPairing`, `_selectingProtocol` / `selectingProtocol`, `isAntProperlyInstalled()`, `isBluetoothSupported()`, `onPairingClicked()`, `onDeviceTypeSelected()`, and `onCancelDeviceTypeSelection()` from `ControlTrackingViewModel.kt`.
  * Remove `selectingProtocol` state collection, `ControlNavigation.ToPairing` branch, and dead pairing arguments from `TrackingTabsScreen.kt`.
  * Remove `startPairing(protocol: Protocol, deviceType: DeviceType?)` from `MainActivityWithNavigation.kt`.
  * Add an architectural contract unit test (`ControlTrackingDeadPairingRemovalTest`) verifying the clean API surface of `ControlTrackingViewModel`, `ControlNavigation`, `ControlTrackingScreen`, and `MainActivityWithNavigation`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying `DeviceTypeSelectionDialog.kt`, `PairingProtocolBottomSheet.kt`, or `DevicesTabbedContainerFragment.kt` (active sensor pairing flow in My Sensors).
  * Modifying `hasPairedRemoteDevices`, `remoteDevices`, or `onSearchClicked()` in `ControlTrackingViewModel` (active sensor search & status in Control Tracking).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-393`, completing the clean-up of `REQ-UI-301` (`ATT-2791`, removal of pairing buttons from `ControlTrackingScreen`). Targets `PairingButtons.kt`, `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `MainActivityWithNavigation.kt`, and `ComposeSafePainterContractTest.kt`.
* **Historical Origin & Commit Trace**: In `ATT-2791` (Sprint `2026-41.4`), `PairingButtons(...)` was removed from `ControlTrackingScreen`'s layout column to declutter the cockpit, while keeping `ResearchButton` (`Suchen`) and `RemoteDevices`. The parameter plumbing and `PairingButtons.kt` file were left behind at that time.
* **Root Reason for Existing Formulation**: `ATT-2791` focused on the visual layout transformation of the Control Tracking header (`REQ-UI-301`) without pruning the ViewModel/Activity pairing plumbing.
* **Preservation of Core Invariants**: Removing the unreachable pairing plumbing preserves 100% of active Control Tracking functionality (`hasPairedRemoteDevices` which uses `Protocol.ANT_PLUS` / `Protocol.BLUETOOTH_LE`, `remoteDevices`, `ResearchButton`, `ControlNavigation.ToEditDevice`, and `ControlNavigation.ShowRecoveryDialog`) and keeps sensor pairing in **My Sensors** (`DevicesTabbedContainerFragment`) completely intact.

---

## 5. Architectural Strategy & High-Level Solution

1. **Delete `PairingButtons.kt`** via `git rm` and remove its path entry from `ComposeSafePainterContractTest.kt`.
2. **Clean `ControlTrackingScreen.kt`**:
   - Remove unused `Protocol` and `DeviceTypeSelectionDialog` imports.
   - Remove `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, and `onCancelDeviceTypeSelection` parameters from `ControlTrackingScreen` and its 5 `@Preview` functions.
   - Remove `selectingProtocol?.let { DeviceTypeSelectionDialog(...) }`.
3. **Clean `ControlTrackingViewModel.kt`**:
   - Remove `ControlNavigation.ToPairing`.
   - Remove `_selectingProtocol` and `selectingProtocol` (and unused `MutableStateFlow` / `asStateFlow` imports).
   - Remove `isAntProperlyInstalled()`, `isBluetoothSupported()`, `onPairingClicked()`, `onDeviceTypeSelected()`, and `onCancelDeviceTypeSelection()`.
4. **Clean `TrackingTabsScreen.kt` & `MainActivityWithNavigation.kt`**:
   - Remove `selectingProtocol` collection, `is ControlNavigation.ToPairing` branch, and dead arguments in `TrackingTabsScreen.kt`.
   - Remove `startPairing(protocol: Protocol, deviceType: DeviceType?)` in `MainActivityWithNavigation.kt`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `ControlTrackingViewModel.hasPairedRemoteDevices`, `remoteDevices`, `onDeviceClicked` (`ToEditDevice`), `onSearchClicked`, and `onStartTracking` (`ShowRecoveryDialog`) remain 100% intact.
  2. `DevicesTabbedContainerFragment` and `DeviceTypeSelectionDialog` remain 100% intact for sensor pairing in **My Sensors**.
  3. `ComposeSafePainterContractTest` continues to pass for all remaining 10 active Compose files.
* **Risk Rating**: **LOW** — Pure dead-code removal verified across all call sites.
