# Stage 3: Implementation Plan - ATT-5192: Conditionally Display Relative Width in EditSensorFieldDialog

**Ticket**: [ATT-5192](https://rainerblind.atlassian.net/browse/ATT-5192)  
**Sub-task**: [ATT-5251](https://rainerblind.atlassian.net/browse/ATT-5251) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-437` (`REQ-UI-437.3`)  
**Test Mapping**: `TST-UI-437` (`TST-UI-437.6`)  
**Branch**: `feature/ATT-5192`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Description & Background
In `EditSensorFieldDialog.kt`, the *Relative Breite* (1x, 2x, 3x) `FilterChip` section (`R.string.sensor_field_relative_weight`) is rendered unconditionally. On single-field rows (`fieldsInRow.size == 1`), the tile spans 100% width, making relative weight selection visually meaningless and confusing. This plan determines row field cardinality in `EditSensorFieldViewModel` and conditionally renders the section strictly when `uiState.isMultiFieldRow == true`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-437.3` (*User Configuration UI in Edit Dialog*)
* **Test Mapping**: `TST-UI-437.6` in `EditSensorFieldViewModelTest.kt` (pure functional testing per Section 5.4).
* **Chesterton's Fence Audit**: Amends `REQ-UI-437.3`. Preserves multi-column relative weight selection (`1x`, `2x`, `3x`), default weight (`1.0f`), SQLite schema v17 persistence, and `Modifier.weight` rendering in `SensorGridScreen.kt`.

---

## 3. System Invariants & Preserved Behavior
1. **Section 1.10 Compliance**: No Compose snapshot state writes during composition. `isMultiFieldRow` is hydrated reactively in ViewModel.
2. **Section 5.3 Caller Audit**: Audited `TrackingTabGridContent.kt:177, 185`. No caller passes a wrong or conflicting default parameter.
3. **Section 5.4 Compliance**: Pure helper `calculateIsMultiFieldRow` tested without UI text grepping.
4. **Rule 4 Code Lock**: Zero production code edits before Gate 3 passes `check-gate`.
5. **Persistence Invariant**: `colWeight` defaults to `1.0f` and persists identically; only dialog visibility is gated.

---

## 4. Proposed Architectural Changes

### Component 1: Pure Cardinality Helper (`EditSensorFieldViewModel.kt`)
* Extract pure function:
  ```kotlin
  fun calculateIsMultiFieldRow(existingFieldsInTargetRow: Int, isNewField: Boolean): Boolean {
      return if (isNewField) existingFieldsInTargetRow >= 1 else existingFieldsInTargetRow > 1
  }
  ```

### Component 2: ViewModel State Hydration (`EditSensorFieldViewModel.kt`)
* Add `val isMultiFieldRow: Boolean = false` to `EditDialogUiState`.
* In `loadInitialState()`: Fetch `trackingViewsRepository.getSensorFieldConfigsForView(tabViewId)`, filter matching `rowNr`, and set `isMultiFieldRow = calculateIsMultiFieldRow(fieldsInRow.size, isNewField = false)`.
* In `setupDefaultState()`: Launch a coroutine to check target row configs; if `colNr == -1`, set `false`, else evaluate via `calculateIsMultiFieldRow(fieldsInRow.size, isNewField = true)`.

### Component 3: Conditional UI Gating (`EditSensorFieldDialog.kt`)
* Wrap lines 190–210 (Relative Width header, `Spacer`, and `FilterChip` `Row`) inside `if (uiState.isMultiFieldRow) { ... }`.

### UI Consistency (Rule 23)
* **Reference**: `EditSensorFieldDialog.kt`.
* **Tokens**: `R.string.sensor_field_relative_weight`, `MaterialTheme.typography.labelLarge`, `MaterialTheme.colorScheme.onSurfaceVariant`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update EditSensorFieldViewModel.kt
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt`
* Changes: Add `calculateIsMultiFieldRow`, add `isMultiFieldRow` to `EditDialogUiState`, hydrate in `loadInitialState()` and `setupDefaultState()`.

### Step 2: Gate Relative Width in EditSensorFieldDialog.kt
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`
* Changes: Wrap Relative Width section in `if (uiState.isMultiFieldRow)`.

### Step 3: Implement Unit Tests in EditSensorFieldViewModelTest.kt
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModelTest.kt`
* Changes: Add `testCalculateIsMultiFieldRow` and `testIsMultiFieldRow_hydratedFromRepository`.

### Step 4: Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*" --max-workers=2`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted `EditSensorFieldViewModelTest` and `verify_requirement_governance.py`.
* **Rollback**: Clean git branch revert via `git restore app/ docs/`.
