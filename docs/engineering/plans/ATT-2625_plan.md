# Implementation Plan - ATT-2625: Remove Alle button from device type selection dialog in sensor pairing flow

**Ticket**: [ATT-2625](https://atrainingtracker.atlassian.net/browse/ATT-2625)
**Sub-task**: ATT-2687 (`[Impl-Plan]`)
**Date**: 2026-10-08
**Author**: Antigravity (AI Assistant)
**Reviewer**: Agent 2 (Auditor) / Rainer Blind (Human User)
**Target Branch**: `feature/ATT-2625`

---

## 1. Executive Summary & Architectural Design (SWE.2)

### 1.1 Goal
During sensor pairing setup in the Sensors screen (`DevicesTabbedScreen`) and Workout Cockpit (`ControlTrackingScreen`), selecting a wireless protocol (ANT+ or Bluetooth LE) presents a `DeviceTypeSelectionDialog` listing available sensor profiles (Heart Rate, Power, Speed/Cadence, etc.). Historically, this dialog displayed an "Alle" ("All") button at the bottom (`actions` slot of `ModalBottomSheetDialog`), inherited from general sensor filter dialogs. In sensor pairing, scanning must be targeted to a specific sensor profile and protocol.

This plan details the modification of `DeviceTypeSelectionDialog` to omit the "Alle" button by default (`showAllOption: Boolean = false`), while preserving backward compatibility and zero regressions across callers.

### 1.2 Architectural Changes
1. **[DeviceTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/devices/DeviceTypeSelectionDialog.kt)**:
   - Introduce parameter `showAllOption: Boolean = false` to `DeviceTypeSelectionDialog`.
   - Update `ModalBottomSheetDialog` `actions` parameter:
     - When `showAllOption == true`, render `TextButton(onClick = { onSelected(null) }) { Text(stringResource(R.string.device_type_all)) }`.
     - When `showAllOption == false`, pass `actions = null` (omitting the action slot completely).
2. **Call Sites**:
   - `DevicesTabbedScreen.kt` (line 173): uses default `showAllOption = false`.
   - `ControlTrackingScreen.kt` (line 197): uses default `showAllOption = false`.
3. **Unit Tests**:
   - Create/expand [DeviceTypeSelectionDialogTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/devices/DeviceTypeSelectionDialogTest.kt) verifying parameter behavior, action slot omission, and selection callbacks.

---

## 2. Invariants & Guardrails
1. **Targeted Sensor Profiles**: All hardware profiles returned by `DeviceType.getRemoteDeviceTypes(protocol)` (HR, BIKE_POWER, SPEED_CADENCE, etc.) must remain discoverable with their respective icons and localized names.
2. **Non-Breaking Callers**: Existing callers omitting `showAllOption` automatically receive `showAllOption = false`, cleanly omitting the "Alle" button in pairing flows without call-site churn.
3. **ModalBottomSheetDialog Integrity**: When `actions = null`, `ModalBottomSheetDialog` renders the item list without bottom button padding or empty layout space.
4. **Localization Parity**: `R.string.device_type_all` remains in `strings.xml` for any future or existing legacy filtering contexts (no string deletion required).

---

## 3. Implementation Steps (Atomic Work Breakdown)

### Step 1: Composable Signature & Actions Slot Refactoring
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/devices/DeviceTypeSelectionDialog.kt`
- Add `showAllOption: Boolean = false` to `DeviceTypeSelectionDialog` signature.
- Wrap `actions` slot in `if (showAllOption) { ... } else null`.

### Step 2: Unit & Contract Tests
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/devices/DeviceTypeSelectionDialogTest.kt`
- Author unit/contract tests asserting:
  - Default `showAllOption == false`.
  - Omission of `R.string.device_type_all` when `showAllOption == false`.
  - Inclusion of `R.string.device_type_all` when `showAllOption == true`.
  - Correct device type items and selection callbacks.

### Step 3: Verification & Clean-Room Regression
- Run targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.devices.DeviceTypeSelectionDialogTest"`.
- Run full unit test suite `./gradlew testDebugUnitTest`.

---

## 4. Verification & Gate Governance
- Review against Gate 3 criteria:
  - SWE.2 architectural alignment.
  - Traceability to `REQ-UI-290` and `TST-UI-250`.
  - Invariants preserved and risk assessed as LOW.
