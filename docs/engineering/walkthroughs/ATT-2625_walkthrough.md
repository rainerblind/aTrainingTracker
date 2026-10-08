# Walkthrough - ATT-2625: Remove Alle button from device type selection dialog in sensor pairing flow

**Ticket**: [ATT-2625](https://atrainingtracker.atlassian.net/browse/ATT-2625)
**Sub-task**: ATT-2689 (`[Test]`)
**Date**: 2026-10-08
**Author**: Antigravity (AI Assistant)
**Reviewer**: Agent 2 (Auditor) / Rainer Blind (Human User)
**Target Branch**: `feature/ATT-2625`

---

## 1. Problem Domain & Objective
During sensor pairing setup via the Floating Action Button (FAB) in the Sensors view (`DevicesTabbedScreen`) and the Cockpit Sensor Action (`ControlTrackingScreen`), selecting a wireless protocol (ANT+ or Bluetooth LE) presents a bottom sheet dialog (`DeviceTypeSelectionDialog`) listing available device profiles (Heart Rate, Power, Speed/Cadence, etc.).
Previously, this dialog rendered an "Alle" ("All") button at the bottom of the list. In a pairing context, scans must be targeted to a specific sensor profile and protocol; an "All" button caused ambiguous behavior or confusing UX.

**Objective**: Omit the "Alle" button from `DeviceTypeSelectionDialog` during sensor pairing workflows while keeping hardware device profile selection completely intact.

---

## 2. Changes Implemented

### 2.1 UI Component Parameterization
- **File**: [DeviceTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt)
  - Added parameter `showAllOption: Boolean = false` to `DeviceTypeSelectionDialog`.
  - Conditioned the `actions` slot of `AppModalBottomSheet`:
    ```kotlin
    actions = if (showAllOption) {
        {
            TextButton(
                onClick = { onSelected(DeviceType.ALL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.devices_all))
            }
        }
    } else null
    ```
  - Call sites (`DevicesTabbedScreen.kt` and `ControlTrackingScreen.kt`) now omit the "Alle" button without requiring call-site boilerplate modifications.

### 2.2 Unit & Contract Tests
- **File**: [DeviceTypeSelectionDialogTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialogTest.kt)
  - `testSourceFileExists`: Validates presence of the composable source file.
  - `testDeviceTypeSelectionDialog_hasShowAllOptionDefaultParameter`: Verifies default `showAllOption: Boolean = false`.
  - `testDeviceTypeSelectionDialog_conditionallyRendersActionsSlot`: Verifies actions slot branching and null fallback.
  - `testDeviceTypeResolution_antPlusRemoteTypesAreNonEmpty`: Verifies ANT+ remote hardware profiles (HRM, Power, etc.) without `DeviceType.ALL`.
  - `testDeviceTypeResolution_bleRemoteTypesAreNonEmpty`: Verifies BLE remote hardware profiles without `DeviceType.ALL`.

---

## 3. Verification & Test Results

### 3.1 Targeted Test Execution
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.DeviceTypeSelectionDialogTest"
```
**Result**: BUILD SUCCESSFUL (100% pass rate).

### 3.2 Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
**Result**: BUILD SUCCESSFUL across all test modules (9m 2s execution, 0 test failures, 0 regressions).

---

## 4. Living Documentation Updates
- Updated [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): `REQ-UI-290` status advanced to `Verified`.
- Updated [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): `TST-UI-250` status advanced to `Verified`.
