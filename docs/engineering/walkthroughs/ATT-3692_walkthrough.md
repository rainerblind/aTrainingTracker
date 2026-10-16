# Stage 5 Walkthrough & Verification Report: ATT-3692 - Remove dead sensor pairing code from ControlTrackingScreen, ControlTrackingViewModel, TrackingTabsScreen, and MainActivityWithNavigation

**Ticket**: [ATT-3692](https://rainerblind.atlassian.net/browse/ATT-3692)  
**Sub-task**: `TBD` (`[Test]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Branch**: `feature/ATT-3692`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary

Completed the removal of dead, unreachable ANT+ and Bluetooth LE sensor pairing code from the Control Tracking cockpit flow (`PairingButtons.kt`, `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, and `MainActivityWithNavigation.kt`) under `REQ-UI-393` / `TST-UI-356`, completing the architectural clean-up after `ATT-2791` (`REQ-UI-301`) moved sensor pairing exclusively to **My Sensors** (`DevicesTabbedContainerFragment` / `PairingProtocolBottomSheet` / `DeviceTypeSelectionDialog`).

---

## 2. Summary of Changes

1. **Deleted `PairingButtons.kt` (`REQ-UI-393.1`)**:
   - Deleted [`PairingButtons.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PairingButtons.kt) (194 lines, zero production callers).
   - Updated [`ComposeSafePainterContractTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/ComposeSafePainterContractTest.kt) to remove `PairingButtons.kt` from `targetFiles`.
2. **Cleaned `ControlTrackingScreen.kt` (`REQ-UI-393.2`)**:
   - Removed unused imports `Protocol` and `DeviceTypeSelectionDialog`, removed the 6 unused pairing parameters (`isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection`) from [`ControlTrackingScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt#L58-L75) and all 5 `@Preview` functions, and removed the unreachable `selectingProtocol?.let { DeviceTypeSelectionDialog(...) }` block.
3. **Cleaned `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt` & `MainActivityWithNavigation.kt` (`REQ-UI-393.3`, `REQ-UI-393.4`)**:
   - In [`ControlTrackingViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt): removed `ControlNavigation.ToPairing`, `_selectingProtocol` / `selectingProtocol`, `isAntProperlyInstalled()`, `isBluetoothSupported()`, `onPairingClicked()`, `onDeviceTypeSelected()`, and `onCancelDeviceTypeSelection()`.
   - In [`TrackingTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt): removed `selectingProtocol` collection, `is ControlNavigation.ToPairing` handling, and dead pairing arguments.
   - In [`MainActivityWithNavigation.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt): removed `fun startPairing(protocol: Protocol, deviceType: DeviceType?)`.
4. **Added Architectural Contract Test (`TST-UI-356`)**:
   - Created [`ControlTrackingDeadPairingRemovalTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingDeadPairingRemovalTest.kt) verifying the deletion of `PairingButtons.kt`, removal of all dead pairing symbols across `ControlTrackingScreen`, `ControlTrackingViewModel`, `TrackingTabsScreen`, and `MainActivityWithNavigation`, and preservation of active sensor discovery and My Sensors pairing across all 9 locales.

---

## 3. Clean-Room Verification Results (`TST-UI-356`)

* **Targeted Unit & Contract Tests**:
  - `ControlTrackingDeadPairingRemovalTest`: **5/5 PASSED**
  - `ComposeSafePainterContractTest`: **2/2 PASSED**
* **Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)**:
  - **100% PASS RATE (0 failures, 0 errors)** across the entire unit test suite.

---

## 4. Bidirectional Traceability Status

| Requirement ID | Test Case ID | Verification Artifact | Status |
| :--- | :--- | :--- | :---: |
| `REQ-UI-393` | `TST-UI-356` | `ControlTrackingDeadPairingRemovalTest.kt`, `ComposeSafePainterContractTest.kt` | **Verified** |
