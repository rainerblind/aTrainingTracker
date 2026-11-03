# Stage 3: Implementation Plan - ATT-5077: Remove overflowing row-split presets, keep Relative Breite

**Ticket**: [ATT-5077](https://rainerblind.atlassian.net/browse/ATT-5077)  
**Sub-task**: [[ATT-5146]](https://rainerblind.atlassian.net/browse/ATT-5146) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-437` (`REQ-UI-437.3`, `REQ-UI-437.4`)  
**Test Mapping**: `TST-UI-437` (`TST-UI-437.3`, `TST-UI-437.5`)  
**Branch**: `feature/ATT-5077`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
In `EditSensorFieldDialog.kt`, editing a sensor field sharing a row with another field renders 5 *Zeilenaufteilung* chips (`3/4 : 1/4`, `2/3 : 1/3`, `1/2 : 1/2`, `1/3 : 2/3`, `1/4 : 3/4`) in a non-wrapping `Row`. This causes horizontal overflow and clipping on standard phone viewports. Maintaining both row-split presets and individual *Relative Breite* (1x, 2x, 3x) selectors is redundant. Removing row-split presets and keeping only *Relative Breite* eliminates overflow and provides full ratio flexibility across all row configurations.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-437.3` (Relative Width UI), `REQ-UI-437.4` (9-Language Parity)
* **Test Mapping**: `TST-UI-437.3` (Relative Width Unit Tests), `TST-UI-437.5` (`TranslationParityTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Compose Layout Stability**: `SensorGridScreen.kt` proportional weighting via `Modifier.weight(effectiveWeight)` and non-zero clamping ($> 0f$) remains untouched.
2. **Database Persistence Integrity**: `ColWeight real DEFAULT 1.0` in `LayoutRowsTable` and `TrackingViewsRepository.updateSensorFieldConfig` remain unchanged.
3. **100% Translation Parity**: Active string keys maintain 100% parity across all 9 locales.
4. **Code Lock Until Gate 3**: No production code changes occur before Gate 3 passes audit.

---

## 4. Proposed Architectural Changes

### Component 1: `EditSensorFieldDialog.kt` (UI Layer)
* Remove the `uiState.isTwoFieldRow` block (5 row split chips and `sensor_field_row_split_presets` title).
* Display `sensor_field_relative_weight` as the `labelLarge` section header directly above the 3 `FilterChip` items (`1x`, `2x`, `3x`).
### Component 2: `EditSensorFieldViewModel.kt` (ViewModel Layer)
* In `EditDialogUiState`: remove `isTwoFieldRow`, `siblingFieldId`, `siblingColWeight`, `isLeftField`.
* In `init`: remove unnecessary `getSensorFieldConfigsForView` query used to resolve siblings.
* Delete `onRowSplitSelected(leftWeight: Float, rightWeight: Float)`.
* In `saveChanges()`: remove sibling `updateColWeight` invocation.
### Component 3: 9 Locale Resource Files (`strings.xml`)
* Delete `sensor_field_row_split_presets` across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
### UI Consistency (Rule 23)
* **Reference component**: `EditSensorFieldDialog.kt` Display Elements selector (`FilterChip` row).
* **Reused components**: Material 3 `FilterChip`, `Text`, `Row`.
* **Theme tokens**: `MaterialTheme.typography.labelLarge`, `MaterialTheme.colorScheme.onSurfaceVariant`, `8.dp` chip spacing.
* **New one-off styles**: None. Conforms to `docs/design_guidelines.md` §1.2.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Resource Cleanup (`strings.xml` x 9)
* Remove `<string name="sensor_field_row_split_presets">...</string>` from all 9 locale files.

### Step 2: ViewModel Refactoring (`EditSensorFieldViewModel.kt`)
* Remove `isTwoFieldRow`, `siblingFieldId`, `siblingColWeight`, `isLeftField` from `EditDialogUiState`.
* Remove sibling lookup from `init`, delete `onRowSplitSelected()`, remove sibling update in `saveChanges()`.

### Step 3: UI Simplification (`EditSensorFieldDialog.kt`)
* Remove `uiState.isTwoFieldRow` chip row; use `sensor_field_relative_weight` as the header for `1x`, `2x`, `3x`.

### Step 4: Unit Test Updates (`EditSensorFieldViewModelTest.kt`)
* Remove `testRowSplit_twoFieldRow_presetsUpdateComplementaryWeights()`.
* Verify `onColWeightChanged` updates `colWeight` and persists to repository with zero sibling calls.

### Step 5: Targeted Unit Test Suite & Governance Verification
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and `TranslationParityTest` in Stage 4; clean-room regression in Stage 5.
* **Rollback**: Branch `feature/ATT-5077` can be cleanly reverted via `git restore`.
