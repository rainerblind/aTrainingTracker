# Stage 1 Analysis: ATT-5077 - EditSensorFieldDialog: remove overflowing 'Zeilenaufteilung' row-split presets and keep only 'Relative Breite' (1x, 2x, 3x)

**Ticket**: [ATT-5077](https://rainerblind.atlassian.net/browse/ATT-5077)  
**Sub-task**: [[ATT-5144]](https://rainerblind.atlassian.net/browse/ATT-5144) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5077`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
In `EditSensorFieldDialog.kt` (introduced in ATT-4861), editing a sensor field that shares a row with another field (`uiState.isTwoFieldRow`) renders 5 *Zeilenaufteilung* (`sensor_field_row_split_presets`) chips (`3/4 : 1/4`, `2/3 : 1/3`, `1/2 : 1/2`, `1/3 : 2/3`, `1/4 : 3/4`) in a non-wrapping `Row`. On standard phone viewports, these 5 chips overflow horizontally, cutting off the `1/4 : 3/4` option. Furthermore, providing both *Zeilenaufteilung* presets and individual *Relative Breite* (1x, 2x, 3x) selectors is redundant and confusing. Removing the preset chips and keeping only *Relative Breite* simplifies the UI, eliminates horizontal clipping, and supports all ratio combinations across 1-field, 2-field, and 3-field rows.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Root Cause**:
  - `EditSensorFieldDialog.kt:214-226` placed 5 fixed-width `FilterChip` items inside an unconstrained horizontal `Row`, violating design guidelines for $> 3\text{--}4$ chips (`docs/design_guidelines.md` §1.2 & §1.4).
  - Redundant dual-configuration model: `EditSensorFieldViewModel.kt` computed `isTwoFieldRow` via an extra DB query (`getSensorFieldConfigsForView`), managed sibling state (`siblingFieldId`, `siblingColWeight`, `isLeftField`), and updated sibling row weights in `saveChanges()`.
- **Independent Peer Review & Verification**:
  - Audited `EditSensorFieldDialog.kt:197-228`: confirmed non-wrapping `Row` with 5 chips overflows dialog width on standard portrait screens.
  - Audited `EditSensorFieldViewModel.kt:296-301, 323-326, 335-344, 686-688`: confirmed sibling resolution, `isTwoFieldRow` state, and sibling DB update logic.
  - Audited string resources: confirmed `sensor_field_row_split_presets` is exclusively used by the row-split section and is defined in all 9 locale `strings.xml` files.
  - Audited database & repository: `TrackingViewsDatabaseManager.java` and `TrackingViewsRepository.kt` maintain `updateColWeight(sensorFieldId, colWeight)`. The `colWeight` column in SQLite `LayoutRowsTable` remains intact and is saved per field via `updateSensorFieldConfig()`.
  - Audited test suite: `EditSensorFieldViewModelTest.kt:621-687` (`testRowSplit_twoFieldRow_presetsUpdateComplementaryWeights`) tests `isTwoFieldRow` and `onRowSplitSelected`.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Remove *Zeilenaufteilung* row-split presets section (`isTwoFieldRow`, 5 chips) from `EditSensorFieldDialog.kt`.
  2. Retain *Relative Breite* (1x, 2x, 3x) selector in `EditSensorFieldDialog.kt` using `sensor_field_relative_weight` as title.
  3. Clean up `EditSensorFieldViewModel.kt`: remove `isTwoFieldRow`, `siblingFieldId`, `siblingColWeight`, `isLeftField` from `EditDialogUiState`, remove sibling query from `init`, remove `onRowSplitSelected()`, and remove sibling update from `saveChanges()`.
  4. Remove unused `sensor_field_row_split_presets` string resource from all 9 `strings.xml` files.
  5. Update `EditSensorFieldViewModelTest.kt` to remove obsolete row-split preset tests while verifying `colWeight` 1x, 2x, 3x behavior.
  6. Verify 9-language parity via `TranslationParityTest` and requirement governance via `verify_requirement_governance.py`.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying SQLite schema v17 or `SensorGridScreen.kt` proportional weighting logic (`Modifier.weight(effectiveWeight)`).
  - Removing `colWeight` persistence or `TrackingViewsRepository.updateColWeight()`.
  - Introducing continuous sliders or freeform decimal weight inputs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Modifies `REQ-UI-437.3` (*User Configuration UI in Edit Dialog*) and `TST-UI-437.3`, under parent requirement `REQ-UI-437` (*User-Configurable Horizontal Width Ratios & Split Presets in Cockpit Sensor Grid*), Parent Epic `ATT-355`. Targets `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, and all 9 `strings.xml` files.
- **Historical Origin & Commit Trace**: Introduced in Sprint 2026-44.2 via commit `244300a2` (`ATT-4861`).
- **Root Reason for Existing Formulation**: ATT-4861 introduced row split presets as 1-tap shortcuts for two-field rows, but 5 chips exceeded viewport widths on mobile devices, and having two separate weight controls caused UI clutter and redundancy.
- **Preservation of Core Invariants**:
  - `colWeight` database persistence and Compose `Modifier.weight(effectiveWeight)` rendering remain 100% intact.
  - All desired width ratios (`1:1`, `2:1`, `3:1`, `1:2`, `1:3`) remain fully configurable via individual field relative weights (1x, 2x, 3x).
  - Completely resolves horizontal dialog overflow while maintaining 100% 9-language translation parity.

---

## 5. Architectural Strategy & High-Level Solution
- **UI (`EditSensorFieldDialog.kt`)**: Remove `uiState.isTwoFieldRow` block; use `sensor_field_relative_weight` header above `1x`, `2x`, `3x` `FilterChip` row.
- **ViewModel (`EditSensorFieldViewModel.kt`)**: Remove `isTwoFieldRow`, `siblingFieldId`, `siblingColWeight`, `isLeftField` from `EditDialogUiState`; drop `getSensorFieldConfigsForView` query in `init`; remove `onRowSplitSelected()`; remove sibling update in `saveChanges()`.
- **Resources (`strings.xml` x 9)**: Delete `sensor_field_row_split_presets` in `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
- **Testing (`EditSensorFieldViewModelTest.kt`)**: Replace obsolete row-split tests with explicit assertions verifying individual `colWeight` state changes and database save.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. `SensorGridScreen.kt` proportional rendering with `Modifier.weight(effectiveWeight)` and non-zero clamping ($> 0f$) remains unchanged.
  2. Database `ColWeight` persistence and default `1.0f` backward compatibility remain unaffected.
  3. 9-language localization parity maintained (`TranslationParityTest`).
- **Risk Rating**: **LOW**. Pure UI/ViewModel simplification; removes dead/overflowing chips, eliminates redundant DB queries, zero impact on background sensor tracking.
