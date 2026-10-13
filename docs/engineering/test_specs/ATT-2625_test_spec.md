# Stage 2: Requirement & Test Specification - ATT-2625: Remove Alle button from device type selection dialog in sensor pairing flow

**Ticket**: [ATT-2625](https://atrainingtracker.atlassian.net/browse/ATT-2625)  
**Sub-task**: [ATT-2686](https://atrainingtracker.atlassian.net/browse/ATT-2686) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.3`  
**Branch**: `feature/ATT-2625`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-UI-290`)

### REQ-UI-290: Sensor Pairing Device Type Selection: Omission of 'Alle' Action and Targeted Hardware Profile Discovery

The system SHALL omit the "Alle" ("All") button from the device type selection bottom sheet (`DeviceTypeSelectionDialog`) during sensor pairing flows, enforcing targeted wireless discovery for specific hardware sensor profiles (ATT-2625):

1. **Exclusion of 'Alle' Button from Pairing Flows (`DeviceTypeSelectionDialog.kt`)**:
   - `DeviceTypeSelectionDialog` SHALL expose a parameter `showAllOption: Boolean = false`.
   - When `showAllOption == false` (the default for all pairing flows), `AppModalBottomSheet`'s `actions` slot SHALL be `null`, completely omitting the bottom action bar and divider.
   - When `showAllOption == false`, `DeviceType.ALL` SHALL NOT be presented to the user.

2. **Presentation of Specific Hardware Profiles**:
   - The dialog SHALL exclusively present the supported physical hardware device profiles for the selected protocol (`DeviceType.getRemoteDeviceTypes(protocol)`), such as Heart Rate, Power Meter, Speed/Cadence, etc.
   - Each item SHALL display its official icon (`getIconId(type, protocol)`) and localized display name (`UIHelper.getNameId(type)`).

3. **Unbroken Pairing Initiation & Interaction**:
   - Tapping a specific device type item SHALL invoke `onSelected(type)` with the concrete `DeviceType`.
   - Tapping outside, swiping down, or tapping the top-right close icon SHALL invoke `onDismiss()`.

4. **Integration Sites**:
   - In `DevicesTabbedScreen.kt` (Sensors view pairing FAB flow), `DeviceTypeSelectionDialog` SHALL invoke without `showAllOption` (defaulting to `false`).
   - In `ControlTrackingScreen.kt` (Control screen pairing button flow), `DeviceTypeSelectionDialog` SHALL invoke without `showAllOption` (defaulting to `false`).

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Refines `REQ-UI-042` (*Sensor Selection Flow and Navigation Overlay*) and `REQ-UI-278` (*Sensors Screen Pairing FAB & Protocol Selection*).
2. **Historical Origin & Commit Trace**:
   - Commit `d4b2e864b7` (SCRUM-37): `DeviceTypeSelectionDialog` originally included `confirmButton = { TextButton(onClick = { onSelected(DeviceType.ALL) }) }` as an unfiltered view reset.
   - Commit `43b35eb6` (ATT-2189): Added pairing FAB in `DevicesTabbedScreen.kt` which reused `DeviceTypeSelectionDialog`.
3. **Root Reason for Existing Formulation**: The dialog carried over the legacy filter action bar from the pre-sprint sensors screen. In a pairing initiation flow, "Alle" is semantically invalid because discovery scans must target a specific hardware profile.
4. **Preservation of Core Invariants**:
   - Profile discovery, icon rendering, close dismiss, and full unit test pass rate remain strictly preserved.

---

### Acceptance Criteria (Given-When-Then)

* **Scenario 1: Opening Pairing Dialog from Sensors FAB**
  - **Given** an athlete in "Meine Sensoren" (`DevicesTabbedScreen`),
  - **When** tapping the '+' pairing FAB and selecting Bluetooth LE or ANT+,
  - **Then** `DeviceTypeSelectionDialog` SHALL open displaying only specific sensor types (Heart Rate, Power, etc.).
  - **Then** no "Alle" button and no bottom action bar divider SHALL be displayed.

* **Scenario 2: Opening Pairing Dialog from Tracking Screen**
  - **Given** an athlete on `ControlTrackingScreen`,
  - **When** tapping the ANT+ or Bluetooth pairing button,
  - **Then** `DeviceTypeSelectionDialog` SHALL open displaying only specific sensor types.
  - **Then** no "Alle" button SHALL be displayed.

* **Scenario 3: Selecting a Sensor Profile**
  - **Given** `DeviceTypeSelectionDialog` is open,
  - **When** the athlete selects "Heart Rate",
  - **Then** `onSelected(DeviceType.HEART_RATE)` SHALL be dispatched and discovery scan initiated for that profile.

---

## 2. Test Specification (`TST-UI-250`)

### TST-UI-250: Sensor Pairing Device Type Selection: Omission of 'Alle' Action Verification

1. **Unit & Contract Tests for `DeviceTypeSelectionDialog` (`DeviceTypeSelectionDialogTest.kt`)**:
   - Verify default `showAllOption == false`.
   - Verify that when `showAllOption == false`, no `actions` composable content or "Alle" button is provided to `AppModalBottomSheet`.
   - Verify that `DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)` and `DeviceType.getRemoteDeviceTypes(Protocol.BLUETOOTH_LE)` do NOT contain `DeviceType.ALL`.
   - Verify `DeviceTypeSelectionDialog` composable signature and public modifier in `ModalBottomSheetDialogsIntegrityTest.kt`.

2. **Call Site Integration Audits**:
   - Verify `DevicesTabbedScreen.kt` invokes `DeviceTypeSelectionDialog` without enabling `showAllOption`.
   - Verify `ControlTrackingScreen.kt` invokes `DeviceTypeSelectionDialog` without enabling `showAllOption`.

3. **Clean-Room Regression Suite Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Verification Criteria |
| :--- | :--- | :--- | :--- |
| `REQ-UI-290.1` (Omission of 'Alle' button) | `TST-UI-250.1` | `DeviceTypeSelectionDialogTest.kt` | Default `showAllOption == false`, `actions == null`. |
| `REQ-UI-290.2` (Specific Hardware Profiles) | `TST-UI-250.1` | `DeviceTypeSelectionDialogTest.kt` | Lists contain only valid remote device types, excluding `DeviceType.ALL`. |
| `REQ-UI-290.3` (Unbroken Selection & Dismiss) | `TST-UI-250.1` | `DeviceTypeSelectionDialogTest.kt` | `onSelected(type)` called with concrete `DeviceType`. |
| `REQ-UI-290.4` (Integration Sites) | `TST-UI-250.2` | `DevicesTabbedScreen.kt`, `ControlTrackingScreen.kt` | Both call sites use default `showAllOption = false`. |
| `REQ-PRO-001` (Zero Regressions) | `TST-UI-250.3` | `./gradlew testDebugUnitTest` | Full suite executes with 100% pass rate. |
