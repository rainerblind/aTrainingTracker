# Stage 1 Analysis: ATT-5192 - EditSensorFieldDialog: only show 'Relative Breite' (1x, 2x, 3x) when the row contains more than one sensor field

**Ticket**: [ATT-5192](https://rainerblind.atlassian.net/browse/ATT-5192)  
**Sub-task**: [ATT-5249](https://rainerblind.atlassian.net/browse/ATT-5249) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5192`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Statement & Motivation

In `EditSensorFieldDialog.kt`, the *Relative Breite* (1x, 2x, 3x) `FilterChip` section (`R.string.sensor_field_relative_weight`) is rendered unconditionally for all sensor fields.
When a cockpit row contains only a single sensor field (`fieldsInRow.size == 1`), the tile spans the full row width regardless of its relative weight. Presenting relative width options (`1x`, `2x`, `3x`) on single-field rows is misleading and adds visual clutter because weight ratios only take effect when two or more sibling tiles share a row.
The goal of ATT-5192 is to conditionally render the *Relative Breite* section only when the target row contains multiple sensor fields (`uiState.isMultiFieldRow == true`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause 1 (Unconditional UI Section)**: In `EditSensorFieldDialog.kt:190-210`, the *Relative Width* title and `FilterChip` row are drawn directly in the content `Column` without checking row field cardinality.
* **Root Cause 2 (Missing Cardinality State in ViewModel)**: `EditDialogUiState` stores `colWeight: Float` but lacks an `isMultiFieldRow: Boolean` property. `EditSensorFieldViewModel` does not query sibling fields in the target row during `loadInitialState()` or `setupDefaultState()`.
* **Hypothesis Verification**:
  * For existing fields (`sensorFieldId != -1L`): querying `trackingViewsRepository.getSensorFieldConfigsForView(tabViewId)` allows counting fields where `it.rowNr == initialConfig.rowNr`. If `fieldsInRow.size > 1`, `isMultiFieldRow = true`.
  * For newly added fields (`sensorFieldId == -1L`): if `colNr == -1` (new row), the field will be alone (`isMultiFieldRow = false`). If inserting alongside an existing field (`colNr >= 0`), the row will contain `fieldsInRow.size + 1 > 1` tiles (`isMultiFieldRow = true`).
  * Guarding the dialog section with `if (uiState.isMultiFieldRow)` eliminates the misleading control while preserving full functionality for multi-field rows.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `isMultiFieldRow: Boolean = false` to `EditDialogUiState`.
  2. Implement pure helper `calculateIsMultiFieldRow(existingFieldsInTargetRow: Int, isNewField: Boolean): Boolean`.
  3. Hydrate `isMultiFieldRow` in `EditSensorFieldViewModel` during `loadInitialState()` and `setupDefaultState()` via `trackingViewsRepository.getSensorFieldConfigsForView(tabViewId)`.
  4. Wrap the *Relative Breite* section in `EditSensorFieldDialog.kt` with `if (uiState.isMultiFieldRow)`.
  5. Add unit tests in `EditSensorFieldViewModelTest.kt` covering single-field, multi-field, new row, and existing row insertion scenarios.
  6. Synchronize living documentation (`REQ-UI-437.3` / `TST-UI-437`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter SQLite schema v17 (`TrackingViewsDatabaseManager.java`) or `ColWeight` persistence.
  * Do not change proportional layout math in `SensorGridScreen.kt` (`Modifier.weight(effectiveWeight)`).
  * Do not reintroduce removed row-split preset chips (ATT-5077).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-UI-437.3` (*User Configuration UI in Edit Dialog*) in `docs/requirements/req_ui_design_system.md`. Targets `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, and `EditSensorFieldViewModelTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4861` (Sprint 2026-44.2); cleaned up in `ATT-5077` (Sprint 2026-45.1) to use simple `1x`, `2x`, `3x` chips. Visibility was left unconditional.
* **Root Reason for Existing Formulation**: In ATT-5077, focus was on removing the overflowing row-split preset chips and simplifying to 1x/2x/3x. Cardinality filtering was deferred to feedback.
* **Preservation of Core Invariants**: Multi-field rows retain full access to `1x`, `2x`, `3x` relative weights; `colWeight` persistence is completely unaffected.

---

## 5. Architectural Strategy & High-Level Solution

1. **Pure Cardinality Engine (SWE.2 / Rule 36)**:
   * `fun calculateIsMultiFieldRow(existingFieldsInTargetRow: Int, isNewField: Boolean): Boolean = if (isNewField) existingFieldsInTargetRow >= 1 else existingFieldsInTargetRow > 1`
2. **ViewModel State Hydration (`EditSensorFieldViewModel.kt`)**:
   * In `loadInitialState()`: count matching row configs from `trackingViewsRepository.getSensorFieldConfigsForView(tabViewId)` and compute `isMultiFieldRow`.
   * In `setupDefaultState()`: if `colNr == -1`, `isMultiFieldRow = false`; else count matching row configs and evaluate via helper.
3. **Conditional UI (`EditSensorFieldDialog.kt`)**:
   * Wrap *Relative Breite* `Text`, `Spacer`, and `FilterChip` `Row` inside `if (uiState.isMultiFieldRow)`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Default `colWeight = 1.0f` remains intact for all fields regardless of dialog visibility.
  2. Zero Compose recomposition regressions or state writes during composition (Section 1.10).
  3. 100% headless JVM testability with zero Android mocking (Section 5.4).
* **Side-Effect Audit (Rule 68)**:
  * Only affects `EditSensorFieldDialog.kt` and `EditSensorFieldViewModel.kt`. Cockpit rendering and database persistence remain unchanged.
* **Risk Rating**: **LOW** — Localized UI visibility condition and state hydration; zero schema or runtime stability risk.
