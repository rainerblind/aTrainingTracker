# Stage 2: Requirement & Test Specification - ATT-8859: WheelPositionSelectionDialog Front/Rear Only

**Ticket**: [ATT-8859](https://rainerblind.atlassian.net/browse/ATT-8859)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8859`  
**Requirement Mapping**: `REQ-SEN-032` (refining `REQ-SEN-029.3` & `REQ-SEN-031.2`)  
**Test Spec ID**: `TST-SEN-032`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19  

---

## 1. Requirement Specification (`REQ-SEN-032`)

### 1.1 Problem Statement & Rationale
PO review follow-up from `ATT-8719`: `WheelPositionSelectionDialog.kt` (`L83-87`) currently displays `Unspecified` (`WheelPosition.UNSPECIFIED`) alongside `Front` and `Rear`. Removing `Unspecified` from the interactive selection dialog ensures clear Front/Rear mounting intent while preserving `WheelPosition.UNSPECIFIED` in the enum, `Equipment.db` v4 fallback, and `EditDeviceDialog` legacy summary display.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit (Compose UI & Contract)
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`

### 1.3 Functional & Architectural Requirements
1. `WheelPositionSelectionDialog` **SHALL** render strictly two options: `WheelPosition.FRONT` (`wheel_position_front`) and `WheelPosition.REAR` (`wheel_position_rear`), and **MUST NOT** render `WheelPosition.UNSPECIFIED` (`wheel_position_unspecified`).
2. When `currentPosition == WheelPosition.UNSPECIFIED` (legacy link), neither `Front` nor `Rear` radio **SHALL** be selected; `Unlink` (`isLinked == true`), `Cancel`, conflict hints, and selecting `Front`/`Rear` **SHALL** work cleanly.
3. `WheelPosition.UNSPECIFIED` enum/DB fallback and 9-locale `R.string.equipment_wheel_position_unspecified` (actively rendered by `EditDeviceDialog.kt`) **MUST** remain intact; `@Preview`s in `WheelPositionSelectionDialog.kt` **MUST** be `private fun` (`REQ-ARC-003.5`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Front/Rear Only Options)**: *Given* `WheelPositionSelectionDialog`, *When* rendered, *Then* `wheel_position_front` & `wheel_position_rear` render, `wheel_position_unspecified` does not exist, and tapping `Front`/`Rear` invokes `onSelectPosition(FRONT/REAR)`.
* **Criterion 2 (Legacy `UNSPECIFIED` & Invariants)**: *Given* `currentPosition = WheelPosition.UNSPECIFIED` (`isLinked = true`), *When* opened, *Then* neither radio is selected, `Unlink` & `Cancel` work, selecting `Front`/`Rear` upgrades cleanly, and `EditDeviceEquipmentWheelPositionTest` legacy display + `WheelPosition.values().size == 3` pass.

### 1.5 System Invariants
1. Zero DB schema, enum, or string-resource changes (`Localization Parity: N/A - no UI strings`).
2. Zero `src/main/` source-text reads in new tests (`REQ-PRO-049.1`); Light/Dark PNG captured & inspected (`Rule 8.5`).

---

## 2. Test Specification (`TST-SEN-032`)

### Test Case 1: Behavioral Compose UI & Legacy State Verification (`TST-SEN-032.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelPositionSelectionDialogUiTest.kt` (new, `createComposeRule()`, `0` `src/main` reads)
* **Action**: Render `WheelPositionSelectionDialog` across default, `UNSPECIFIED`, `isLinked = true/false`, and `conflictHints` states; assert `wheel_position_front` & `wheel_position_rear` exist, `wheel_position_unspecified` does not exist, tapping selects `FRONT`/`REAR`, `UNSPECIFIED` leaves both unselected while `Unlink`/`Cancel` function, and capture Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8859_wheel_position_dialog.png` (`Rule 8.5`).

### Test Case 2: Regression, Private `@Preview` & Governance Verification (`TST-SEN-032.2`)
* **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "*WheelPositionSelectionDialogUiTest" --tests "*WheelPositionDialogsTest" --tests "*EditDeviceEquipmentWheelPositionTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"`
* **Expected Result**: `100%` pass (`0` failures, `0` skipped); `Localization Parity: N/A - no UI strings`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Suite Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-032.1` | Compose UI & Rule 8.5 | `WheelPositionSelectionDialogUiTest` | `REQ-SEN-032.1`, `.2` | Specified |
| `TST-SEN-032.2` | Legacy & Contract | `WheelPositionDialogsTest`, `EditDeviceEquipmentWheelPositionTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest` | `REQ-SEN-032.3`, `REQ-ARC-003.5`, `REQ-PRO-133` | Specified |
