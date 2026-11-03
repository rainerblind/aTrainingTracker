# Stage 2: Requirement & Test Specification - ATT-5192: EditSensorFieldDialog: only show 'Relative Breite' (1x, 2x, 3x) when the row contains more than one sensor field

**Ticket**: [ATT-5192](https://rainerblind.atlassian.net/browse/ATT-5192)  
**Sub-task**: [ATT-5250](https://rainerblind.atlassian.net/browse/ATT-5250) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-437` (`REQ-UI-437.3`)  
**Test Spec ID**: `TST-UI-437` (`TST-UI-437.6`)  
**Branch**: `feature/ATT-5192`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-437.3)

### 1.1 Problem Statement & Rationale
In `EditSensorFieldDialog.kt`, the Relative Width section (`1x`, `2x`, `3x`) is rendered unconditionally. On single-field rows (`fieldsInRow.size == 1`), a field naturally spans 100% width, making relative width selection visually meaningless and confusing. Conditionally rendering this section strictly on multi-field rows (`isMultiFieldRow == true`) cleans up the dialog.

### 1.2 Functional & Architectural Requirements
1. **Cardinality Gating (`REQ-UI-437.3`)**: In `EditSensorFieldDialog.kt`, the Relative Width section (`R.string.sensor_field_relative_weight`) SHALL be rendered strictly when `uiState.isMultiFieldRow == true`. For single-field rows, the section SHALL be hidden.
2. **ViewModel State Hydration**: In `EditSensorFieldViewModel.kt`, `isMultiFieldRow` SHALL be computed during `loadInitialState()` and `setupDefaultState()` via pure helper `calculateIsMultiFieldRow(existingFieldsInTargetRow, isNewField)`.
3. **Pure Function Separation**: Cardinality logic SHALL be isolated into pure helper function `calculateIsMultiFieldRow(existingFieldsInTargetRow: Int, isNewField: Boolean): Boolean` for 100% headless JVM testing without Android mocks (Coding Standards Section 5.4).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-437.3` under parent `REQ-UI-437`. Targets `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, and `EditSensorFieldViewModelTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4861` (Sprint 2026-44.2); simplified in `ATT-5077` (Sprint 2026-45.1). Display was left unconditional across all rows.
* **Root Reason for Existing Formulation**: ATT-5077 focused on replacing the overflowing preset chips with 1x/2x/3x; single-field row hiding was deferred to user feedback.
* **Preservation of Core Invariants**: Multi-field rows retain full access to `1x`, `2x`, `3x` relative weights; `colWeight` persistence and proportional layout math in `SensorGridScreen.kt` remain 100% unaffected.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Single-Field Row)**: *Given* an athlete editing a sensor field alone in its row, *When* `EditSensorFieldDialog` is shown, *Then* the Relative Width section SHALL NOT be displayed.
* **Criterion 2 (Multi-Field Row)**: *Given* an athlete editing a sensor field sharing a row with one or more sibling fields, *When* `EditSensorFieldDialog` is shown, *Then* the Relative Width section SHALL be displayed.

---

## 2. Test Specification (TST-UI-437.6)

* **TST-UI-437.6 (Cardinality Unit Tests)**: In `EditSensorFieldViewModelTest.kt`, verify:
  1. Pure helper `calculateIsMultiFieldRow`: returns `false` for existing 1-field row, `true` for $\ge 2$ fields; for new fields, returns `false` for new row (`colNr == -1`) and `true` when inserting alongside existing field.
  2. `loadInitialState()` and `setupDefaultState()` hydrate `EditDialogUiState.isMultiFieldRow` correctly.
* **TST-UI-437 (Targeted Regression)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-437.6]` | Unit | `calculateIsMultiFieldRow` & `EditSensorFieldViewModel` | `REQ-UI-437.3` | Specified |
| `[TST-UI-437.4]` | Regression | Sensor Grid Layout Suite | `REQ-PRO-001` | Specified |
