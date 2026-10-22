# Stage 2 Requirement & Test Specification: ATT-3692 - Remove dead sensor pairing code from ControlTrackingScreen, ControlTrackingViewModel, TrackingTabsScreen, and MainActivityWithNavigation

**Ticket**: [ATT-3692](https://rainerblind.atlassian.net/browse/ATT-3692)  
**Sub-task**: `TBD` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Branch**: `feature/ATT-3692`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Formal Requirement Specification (`REQ-UI-393`)

### `REQ-UI-393`: Removal of Dead Sensor Pairing Code from Control Tracking Cockpit & Activity Navigation

* **Statement**: The system SHALL remove all dead, unreachable ANT+ and Bluetooth LE sensor pairing UI components, parameters, ViewModel state/callbacks, and activity navigation helpers from the Control Tracking cockpit flow (`PairingButtons.kt`, `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, and `MainActivityWithNavigation.kt`), while preserving active sensor discovery (`ResearchButton` / `onSearchClicked`), connected device management (`RemoteDevices` / `ToEditDevice`), and sensor pairing in **My Sensors** (`DevicesTabbedContainerFragment` / `DeviceTypeSelectionDialog`) (`ATT-3692`, completing `REQ-UI-301`):
  1. **Deletion of Dead `PairingButtons.kt` (`REQ-UI-393.1`)**:
     - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt` SHALL be deleted from the repository.
     - `ComposeSafePainterContractTest.kt` SHALL remove `"com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt"` from its `targetFiles` list while continuing to enforce `safePainterResource` across all remaining active Compose components.
  2. **Clean `ControlTrackingScreen` Signature & Body (`REQ-UI-393.2`)**:
     - `ControlTrackingScreen` in `ControlTrackingScreen.kt` (and all of its `@Preview` composables) SHALL NOT declare or pass `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, or `onCancelDeviceTypeSelection`.
     - `ControlTrackingScreen.kt` SHALL NOT import or invoke `DeviceTypeSelectionDialog`.
  3. **Clean `ControlTrackingViewModel` & `ControlNavigation` (`REQ-UI-393.3`)**:
     - `ControlNavigation` in `ControlTrackingViewModel.kt` SHALL contain strictly `ToEditDevice` and `ShowRecoveryDialog` (omitting `ToPairing`).
     - `ControlTrackingViewModel` SHALL NOT declare `_selectingProtocol`, `selectingProtocol`, `isAntProperlyInstalled()`, `isBluetoothSupported()`, `onPairingClicked()`, `onDeviceTypeSelected()`, or `onCancelDeviceTypeSelection()`.
  4. **Clean `TrackingTabsScreen` & `MainActivityWithNavigation` (`REQ-UI-393.4`)**:
     - `TrackingTabsScreen.kt` SHALL NOT collect `selectingProtocol`, handle `ControlNavigation.ToPairing`, or pass pairing parameters to `ControlTrackingScreen`.
     - `MainActivityWithNavigation.kt` SHALL NOT declare `fun startPairing(protocol: Protocol, deviceType: DeviceType?)`.

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-UI-393`, completing the clean-up of `REQ-UI-301` (`ATT-2791`). Targets `PairingButtons.kt`, `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `MainActivityWithNavigation.kt`, and `ComposeSafePainterContractTest.kt`.
2. **Historical Origin & Commit Trace**: In `ATT-2791` (Sprint `2026-41.4`), `PairingButtons(...)` was removed from `ControlTrackingScreen`'s layout column to declutter the cockpit, while keeping `ResearchButton` (`Suchen`) and `RemoteDevices`. The parameter plumbing and `PairingButtons.kt` file were left behind at that time.
3. **Root Reason for Existing Formulation**: `ATT-2791` focused on the visual layout transformation of the Control Tracking header (`REQ-UI-301`) without pruning the ViewModel/Activity pairing plumbing.
4. **Preservation of Core Invariants**: Removing the unreachable pairing plumbing preserves 100% of active Control Tracking functionality (`hasPairedRemoteDevices`, `remoteDevices`, `ResearchButton`, `ControlNavigation.ToEditDevice`, and `ControlNavigation.ShowRecoveryDialog`) and keeps sensor pairing in **My Sensors** (`DevicesTabbedContainerFragment`) completely intact.

### Acceptance Criteria (Given-When-Then)
* **Scenario 1: Dead `PairingButtons.kt` Removed**
  * *Given* the project source tree under `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/`,
  * *When* inspected by `ControlTrackingDeadPairingRemovalTest` and `ComposeSafePainterContractTest`,
  * *Then* `PairingButtons.kt` does not exist and all remaining target files in `ComposeSafePainterContractTest` pass.
* **Scenario 2: Clean Control Tracking API Surface**
  * *Given* `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, and `MainActivityWithNavigation.kt`,
  * *When* audited by `ControlTrackingDeadPairingRemovalTest`,
  * *Then* none of those files contain `ToPairing`, `selectingProtocol`, `onPairingClicked`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection`, `isAntSupported`, `isBluetoothSupported`, `isAntProperlyInstalled`, or `startPairing(`.
* **Scenario 3: Active Sensor Discovery & My Sensors Pairing Preserved**
  * *Given* `ControlTrackingViewModel.kt` and `DevicesTabbedContainerFragment.kt`,
  * *When* audited by `ControlTrackingDeadPairingRemovalTest`,
  * *Then* `ControlTrackingViewModel` retains `hasPairedRemoteDevices`, `remoteDevices`, `onSearchClicked`, `ToEditDevice`, and `ShowRecoveryDialog`, and `DevicesTabbedContainerFragment.kt` retains `DeviceTypeSelectionDialog`.

---

## 2. Formal Test Specification (`TST-UI-356`)

### `TST-UI-356`: Control Tracking Dead Sensor Pairing Removal & Active Sensor Flow Preservation Verification Suite

1. **Architectural Contract Tests (`ControlTrackingDeadPairingRemovalTest.kt`)**:
   - Assert `PairingButtons.kt` does not exist on disk.
   - Assert `ControlTrackingScreen.kt` omits `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection`, and `DeviceTypeSelectionDialog`.
   - Assert `ControlTrackingViewModel.kt` omits `ToPairing`, `selectingProtocol`, `_selectingProtocol`, `isAntProperlyInstalled`, `isBluetoothSupported`, `onPairingClicked`, `onDeviceTypeSelected`, and `onCancelDeviceTypeSelection`, while retaining `hasPairedRemoteDevices`, `remoteDevices`, `onSearchClicked`, `ToEditDevice`, and `ShowRecoveryDialog`.
   - Assert `TrackingTabsScreen.kt` omits `selectingProtocol`, `ToPairing`, `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `onDeviceTypeSelected`, and `onCancelDeviceTypeSelection`.
   - Assert `MainActivityWithNavigation.kt` omits `fun startPairing(`.
   - Assert `DeviceTypeSelectionDialog.kt` still exists and is referenced by `DevicesTabbedContainerFragment.kt`.
2. **Safe Painter Contract Verification (`ComposeSafePainterContractTest.kt`)**:
   - Execute `ComposeSafePainterContractTest` verifying all 10 active Compose files exist and use `safePainterResource` with a semantic `fallback`.
3. **9-Language Localization Audit**:
   - Verify that `R.string.protocol_ant_plus` and `R.string.protocol_bluetooth` (used by `PairingProtocolBottomSheet.kt` in My Sensors) remain intact and non-empty across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
4. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` in Stage 5, asserting 100% pass rate across all unit tests.

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Requirement Summary | Test Case ID | Verification Artifact / Test Class | Status |
| :--- | :--- | :--- | :--- | :---: |
| `REQ-UI-393.1` | Delete `PairingButtons.kt` & update `ComposeSafePainterContractTest.kt` | `TST-UI-356.1` | `ControlTrackingDeadPairingRemovalTest.kt`, `ComposeSafePainterContractTest.kt` | `In Progress` |
| `REQ-UI-393.2` | Remove dead pairing parameters & dialog from `ControlTrackingScreen.kt` | `TST-UI-356.2` | `ControlTrackingDeadPairingRemovalTest.kt` | `In Progress` |
| `REQ-UI-393.3` | Remove dead pairing state & events from `ControlTrackingViewModel.kt` | `TST-UI-356.3` | `ControlTrackingDeadPairingRemovalTest.kt` | `In Progress` |
| `REQ-UI-393.4` | Remove dead pairing wiring from `TrackingTabsScreen.kt` & `MainActivityWithNavigation.kt` | `TST-UI-356.4` | `ControlTrackingDeadPairingRemovalTest.kt` | `In Progress` |
