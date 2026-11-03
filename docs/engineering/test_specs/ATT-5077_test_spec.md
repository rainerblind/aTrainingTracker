# Stage 2: Requirement & Test Specification - ATT-5077: Remove overflowing row-split presets, keep Relative Breite

**Ticket**: [ATT-5077](https://rainerblind.atlassian.net/browse/ATT-5077)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-437` (*User-Configurable Horizontal Width Ratios & Split Presets in Cockpit Sensor Grid*)  
**Test Spec ID**: `TST-UI-437`  
**Branch**: `feature/ATT-5077`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-UI-437)

### 1.1 Problem Statement & Rationale
In `EditSensorFieldDialog.kt`, 5 *Zeilenaufteilung* chips in a non-wrapping `Row` overflow dialog viewports on mobile devices. Having both row-split presets and individual relative width (1x, 2x, 3x) selectors is redundant. Removing row-split presets and retaining only *Relative Breite* simplifies UI and prevents layout clipping while supporting all ratio combinations.

### 1.2 Functional & Architectural Requirements
1. `EditSensorFieldDialog.kt` SHALL remove the `uiState.isTwoFieldRow` preset chips section and retain the `1x`, `2x`, `3x` relative width selector (`REQ-UI-437.3`).
2. `EditSensorFieldViewModel.kt` SHALL remove `isTwoFieldRow`, `siblingFieldId`, `siblingColWeight`, `isLeftField`, `onRowSplitSelected()`, and sibling persistence from `saveChanges()`.
3. Unused string `sensor_field_row_split_presets` SHALL be removed across all 9 locales (`REQ-UI-437.4`).

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Modifies `REQ-UI-437.3` and `REQ-UI-437.4` under `REQ-UI-437`, targeting `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, and 9 `strings.xml` files.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-44.2 via commit `244300a2` (`ATT-4861`).
* `Root Reason for Existing Formulation`: Row-split presets were added as 1-tap shortcuts for 2-field rows, but 5 chips overflowed phone viewports and caused dual-control redundancy.
* `Preservation of Core Invariants`: Proportional `Modifier.weight(effectiveWeight)` rendering and `colWeight` database persistence remain fully preserved; all width ratios remain configurable via 1x/2x/3x.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Relative Width Only)**:
  * *Given* `EditSensorFieldDialog` is opened for any field,
  * *When* the user views width settings,
  * *Then* only *Relative Breite* (1x, 2x, 3x) is displayed without overflowing row-split preset chips.
* **Criterion 2 (ColWeight State & Persistence)**:
  * *Given* an athlete selects `2x` or `3x` in `EditSensorFieldDialog`,
  * *When* changes are saved,
  * *Then* `colWeight` (`2.0f` or `3.0f`) is persisted to `TrackingViews.db` and rendered proportionally.

---

## 2. Test Specification (TST-UI-437)

### Test Case 1: `testColWeight_individualWeightSelection_updatesStateAndPersists` (`TST-UI-437.3`)
* **Scope**: Unit Test (`EditSensorFieldViewModelTest.kt`)
* **Preconditions**: Initial `colWeight = 1.0f`.
* **Action**: Invoke `onColWeightChanged(2.0f)`, `onColWeightChanged(3.0f)`, verify clamp on `0f`, call `saveChanges()`.
* **Expected Result**: State updates to `2.0f`/`3.0f` (clamped to `1.0f` on zero/negative), `trackingViewsRepo.updateSensorFieldConfig` called with updated `colWeight`, zero sibling update calls.

### Test Case 2: 9-Language Localization Audit (`TST-UI-437.5`)
* **Scope**: Unit Test (`TranslationParityTest.kt`)
* **Goal**: Verify removal of `sensor_field_row_split_presets` and parity of `sensor_field_relative_weight` across all 9 locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.

### Test Case 3: Tier 1 Targeted Test Suite
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Goal**: 100% pass on modified ViewModel tests, theme audit, and translation parity.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-437.3` | Unit | `EditSensorFieldViewModel.onColWeightChanged` | `REQ-UI-437.3` | Specified |
| `TST-UI-437.5` | Localization | `TranslationParityTest` | `REQ-UI-437.4` | Specified |
| Tier 1 Suite | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
