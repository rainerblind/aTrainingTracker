# Stage 1 Analysis: ATT-8859 - WheelPositionSelectionDialog Front/Rear Only

**Ticket**: [ATT-8859](https://rainerblind.atlassian.net/browse/ATT-8859)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8859`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19  

---

## 1. Problem Statement & Motivation

PO Review follow-up from `ATT-8719`: `WheelPositionSelectionDialog.kt` (`L83-87`) currently displays three radio options (`Front Wheel`, `Rear Wheel`, `Unspecified`). Explicitly offering `Unspecified` encourages ambiguous wheel sensor links that prevent dual-wheel iTPMS (`ATT-7753`) and Front/Rear odometry differentiation (`ATT-7752`). Only **Front Wheel** (`WheelPosition.FRONT`) and **Rear Wheel** (`WheelPosition.REAR`) should be selectable in the dialog.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification** (live `sprint/2026-11-19_02_Spät` @ `a93f3230`):
  * **Dialog (`WheelPositionSelectionDialog.kt`, `159` LoC)**: `L83-87` defines `options = listOf(FRONT, REAR, UNSPECIFIED)` with `testTag`s `wheel_position_front`, `wheel_position_rear`, and `wheel_position_unspecified`. `isSelected = isLinked && currentPosition == pos` (`L90`). Removing `UNSPECIFIED` from `options` leaves `isSelected == false` for both remaining radios when `currentPosition == WheelPosition.UNSPECIFIED`, allowing the user to pick `Front`/`Rear`, tap `Unlink` (`isLinked == true`), or `Cancel` cleanly.
  * **Callers (2 files, `0` code edits needed)**:
    1. `EditDeviceDialog.kt` (`1,843 <= 2,004` LoC; `L412-434`): passes `currentPosition = draftPositions[bike] ?: WheelPosition.UNSPECIFIED`, `conflictHints = data.wheelPositionConflicts[bike].orEmpty()`. Summary/sheet rows (`L275-315`, `L461-495`) continue rendering `R.string.equipment_wheel_position_unspecified` for legacy DB links.
    2. `EquipmentSensorMatrixScreen.kt` (`716` LoC; `L113-129`, `L631-647`): passes `currentPosition = target.equipment.linkedDeviceWheelPositions[target.sensor.id] ?: WheelPosition.UNSPECIFIED`.
  * **Legacy `UNSPECIFIED` & 9-Locale Strings**: `WheelPosition.UNSPECIFIED` MUST remain in `WheelPosition` enum, `EquipmentDbHelper` (schema v4 `DEFAULT 'UNSPECIFIED'`), `RawDeviceDataProvider`, `DeviceDataRepository`, `EditDeviceViewModel`, and `strings_equipment.xml` (`R.string.equipment_wheel_position_unspecified` is still actively used by `EditDeviceDialog.kt:288,313` and `EditDeviceEquipmentWheelPositionTest.kt:202`). Zero string deletions -> 9-locale parity untouched.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remove `WheelPosition.UNSPECIFIED` option from `WheelPositionSelectionDialog.kt` (`L86`) and update its KDoc (`L47`) + add `private fun` Light/Dark `@Preview`s (`REQ-ARC-003.5`).
  * Add `createComposeRule()` behavioral test + Light/Dark PNG capture (`Rule 8.5`, `REQ-PRO-132`).
* **Out-of-Scope Non-Goals**:
  * Removing `WheelPosition.UNSPECIFIED` from enum/DB/repositories, deleting `equipment_wheel_position_unspecified` from `strings_equipment.xml`, or changing `EditDeviceDialog.kt` / `EquipmentSensorMatrixScreen.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-SEN-029` (`REQ-SEN-029.3`, `ATT-7752`, `4edcda86`) and `REQ-SEN-031` (`REQ-SEN-031.2`, `ATT-8719`, `b08c779e`), refined by net-new `REQ-SEN-032` / `TST-SEN-032`.
* **Historical Origin & Commit Trace**: `4edcda86` introduced `WheelPositionSelectionDialog.kt` with 3 options (`FRONT`, `REAR`, `UNSPECIFIED`); `b08c779e` reused it in `EditDeviceDialog.kt`.
* **Root Reason for Existing Formulation**: `UNSPECIFIED` was exposed as a UI option to mirror the 3-state DB enum (`EquipmentDbHelper` v4 migration default).
* **Preservation of Core Invariants**: Enum/DB/repository fallback `WheelPosition.UNSPECIFIED` and legacy display labels remain untouched; only the interactive dialog options narrow to `FRONT` / `REAR`.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-SEN-032` (refining `REQ-SEN-029.3` & `REQ-SEN-031.2` dialog option text). `SYS.2`/`SYS.3` impact: **None** (no hardware/BLE/ANT+/API changes).
* **Affected Architecture Elements**: `WheelPositionSelectionDialog.kt` (`159` LoC); `EditDeviceDialog.kt` (`1,843 <= 2,004` LoC) & `EquipmentSensorMatrixScreen.kt` (`716` LoC) callers unaffected.
* **Verification Impact & Test Surface**: `TST-SEN-032` (new Robolectric `createComposeRule()` UI test + Light/Dark PNG `ATT-8859_wheel_position_dialog.png`), `WheelPositionDialogsTest`, `EditDeviceEquipmentWheelPositionTest`, `DeadCodeEliminationContractTest`.
* **Risk Assessment & Invariants**: **LOW Risk** (`1 SP`); zero DB schema, enum, or string-resource regressions.

---

## 6. Architectural Strategy & High-Level Solution

Remove `Triple(WheelPosition.UNSPECIFIED, ...)` from `options` in `WheelPositionSelectionDialog.kt`, update KDoc, add `private fun` Light/Dark `@Preview` composables, and verify via Robolectric Compose UI test + Light/Dark PNG screenshot (`Rule 8.5`).

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Legacy DB rows and `WheelPosition.fromString()` fallback to `UNSPECIFIED` remain 100% compatible.
  2. `R.string.equipment_wheel_position_unspecified` retained across all 9 locales for `EditDeviceDialog` legacy display.
  3. Any `@Preview` in `WheelPositionSelectionDialog.kt` MUST be `private fun` (`REQ-ARC-003.5`).
  4. Parent ticket Human Decision Gate remains strictly enforced (`Final Review (Human)`).
* **Risk Rating**: **LOW** — Isolated 2-option radio refinement in a single 159-LoC Compose dialog.
