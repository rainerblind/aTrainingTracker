# Stage 3: Implementation Plan - ATT-2033: Modularize AdvancedTuningDialog into Focused Category Composables

**Ticket**: [ATT-2033](https://rainerblind.atlassian.net/browse/ATT-2033)  
**Sub-task**: [ATT-2250](https://rainerblind.atlassian.net/browse/ATT-2250) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog (`AdvancedTuningDialog`)*)  
**Test Mapping**: `TST-UI-221` (*Modular Component Architecture for Advanced Tuning Dialog Verification*)  
**Branch**: `feature/ATT-2033`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

`AdvancedTuningDialog.kt` in `com.atrainingtracker.trainingtracker.ui.settings.tuning` has accumulated 1,205 lines across multiple agile sprints. It currently serves as a monolith containing:
1. Dialog lifecycle, modal container layout, scroll containers, DataStore reactive state flows, and batch reset/save interactions.
2. Five expansive category section composables matching the canonical `TuningSection` enum:
   - `CockpitTypographySection`: Font family dropdown (360dp constraint), font weight picker, tabular numerals.
   - `AmoledBatterySaverSection`: Display timeout slider, dimming factor slider, dynamic battery saver toggles.
   - `SensorsGpsFilterSection`: GPS accuracy filter slider, slope calculation speed threshold, elevation window.
   - `AftermathAnalysisSection`: Independent X-axis domain selectors for elevation vs. telemetry, pace ceiling slider (15 discrete steps).
   - `WorkoutMasksAndCardsSection` & `WorkoutAftermathMatrixSection`: 8 card visibility toggles, section reordering list controls, and single-choice segmented button row for lap display mode.
3. Reusable form controls (`TuningSliderItem`, `TuningToggleItem`).

This monolith violates the Single Responsibility Principle, degrades developer agility, inflates review overhead, and risks recomposition cascades across unrelated setting categories.

This implementation plan details the decomposition of `AdvancedTuningDialog.kt` into dedicated, cohesive category composables and form controls, ensuring every file in the package is strictly $< 400$ lines (AC-1) while guaranteeing 100% behavioral (AC-2) and test parity (AC-3).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog (`AdvancedTuningDialog`)*)
  - **AC-1 (File Size & Modularity Enforcement)**: All `.kt` files in `com.atrainingtracker.trainingtracker.ui.settings.tuning` (including sub-packages) have strictly $< 400$ lines.
  - **AC-2 (Behavioral & Functional Parity)**: All sliders, toggles, chips, segmented buttons, and accordion containers behave and render identically.
  - **AC-3 (Test Suite Parity)**: All visual and behavioral contract tests pass cleanly.
  - **AC-4 (Zero Preference Schema Regression)**: No DataStore preference keys, types, or default values are modified.
* **Test Mapping**: `TST-UI-221` (*Modular Component Architecture for Advanced Tuning Dialog Verification*)
  - `TST-UI-221.1`: Automated structural test `AdvancedTuningModularityTest.kt` verifying file presence and line count $< 400$.
  - `TST-UI-221.2`: Visual and behavioral contract test suite execution.
  - `TST-UI-221.3`: 9-language localization audit via `TranslationParityTest`.
  - `TST-UI-221.4`: Clean-room full test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Strict File Size Limit**: Every source file in `com.atrainingtracker.trainingtracker.ui.settings.tuning` must not exceed 400 lines of code.
2. **Recomposition Isolation**: Extracted composables must be stateless, receiving immutable state arguments and callback lambdas (`(Float) -> Unit`, `(Boolean) -> Unit`, etc.), preventing cross-category recomposition cascades.
3. **Zero Layout Regressions**: UI spacing, color tokens, icons, and 360dp dropdown constraints must remain identical.
4. **Subtask Self-Sufficiency**: Subtasks transition autonomously to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Human Gate Governance**: Parent ticket `ATT-2033` must NOT be moved to `Erledigt` autonomously; it must transition to `Final Review (Human)` assigned to `human`.

---

## 4. Proposed Architectural Structure

```text
com.atrainingtracker.trainingtracker.ui.settings.tuning/
├── AdvancedTuningDialog.kt                 (~320 lines - Dialog scaffolding, DataStore flows, reset/save)
├── AdvancedTuningAccordion.kt              (285 lines - Accordion container, card, enum, headers)
├── AdvancedTuningDialogFragment.kt         (50 lines - DialogFragment wrapper)
├── TuningPaceCeilingFormatter.kt           (49 lines - Formatter utility)
├── TuningFormControls.kt                   (~80 lines - Reusable TuningSliderItem, TuningToggleItem)
└── categories/
    ├── CockpitTypographySection.kt        (~185 lines - Typography dropdown, weight, tabular nums)
    ├── AmoledBatterySaverSection.kt        (~105 lines - Display timeout, dimming, toggles)
    ├── SensorsGpsFilterSection.kt          (~50 lines - GPS accuracy filter, slope, elevation)
    ├── AftermathAnalysisSection.kt         (~130 lines - X-axis chips, pace ceiling slider)
    └── WorkoutMasksAndCardsSection.kt      (~270 lines - Card toggles, section reordering, lap mode)
```

### Component Details:
1. **`TuningFormControls.kt`**:
   - `TuningSliderItem`: Stateless slider with title, current formatted value, range, steps, and value change callback.
   - `TuningToggleItem`: Stateless switch row with title, description, checked state, and toggle callback.
2. **`categories/CockpitTypographySection.kt`**:
   - Encapsulates font family `ExposedDropdownMenuBox` with the mandatory `heightIn(max = 360.dp)` constraint.
   - Encapsulates numeric font weight slider and tabular numerals toggle.
3. **`categories/AmoledBatterySaverSection.kt`**:
   - Encapsulates display timeout slider, dimming factor slider, and dynamic battery saver toggles.
4. **`categories/SensorsGpsFilterSection.kt`**:
   - Encapsulates GPS accuracy filter slider, slope calculation speed threshold slider, and elevation smoothing window.
5. **`categories/AftermathAnalysisSection.kt`**:
   - Encapsulates independent elevation and telemetry X-axis domain chips (`Distance` vs `Time`).
   - Encapsulates minimum pace ceiling slider with 15 discrete steps (2.0–6.0 min/km) and `TuningPaceCeilingFormatter`.
6. **`categories/WorkoutMasksAndCardsSection.kt`**:
   - Encapsulates 8 workout card toggles with non-null preference gating.
   - Encapsulates interactive section reordering controls with up/down arrows and boundary checks.
   - Encapsulates `WorkoutAftermathMatrixSection` and single-choice segmented button row for lap display mode.
7. **`AdvancedTuningDialog.kt`**:
   - Retains modal dialog container, state hoisting, observation of DataStore flows, and host accordion cards delegating to the category section composables.
8. **Test File Lookups**:
   - Update contract test helper methods (`findSourceFile` / candidate lists) in `CockpitFontExpansionTest.kt`, `AdvancedTuningVisualContractTest.kt`, `AdvancedTuningPaceCeilingContractTest.kt`, `LapDisplayModeSettingsTest.kt`, `AdvancedTuningAftermathContractTest.kt`, and `WorkoutSectionReorderContractTest.kt` so that candidate search paths include the new `categories/` directory and category files.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `TuningFormControls.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`
* **Contents**: Extract `TuningSliderItem` and `TuningToggleItem` with their parameter contracts and Compose imports.

### Step 2: Create Category Section Composables
* **Directory**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/`
* **Files**:
  1. `CockpitTypographySection.kt`: Extract `CockpitTypographySection`, ensuring `heightIn(max = 360.dp)` is preserved.
  2. `AmoledBatterySaverSection.kt`: Extract `AmoledBatterySaverSection`.
  3. `SensorsGpsFilterSection.kt`: Extract `SensorsGpsFilterSection`.
  4. `AftermathAnalysisSection.kt`: Extract `AftermathAnalysisSection`.
  5. `WorkoutMasksAndCardsSection.kt`: Extract `WorkoutMasksAndCardsSection` and `WorkoutAftermathMatrixSection`.

### Step 3: Streamline `AdvancedTuningDialog.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Contents**:
  - Import category composables and form controls.
  - Wire each `TuningAccordionSection` card directly to its extracted category composable.
  - Verify total file line count is strictly $< 400$ lines.

### Step 4: Author `AdvancedTuningModularityTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt`
* **Verifications**:
  - Existence of all 7 `.kt` files in `ui.settings.tuning` (root and `categories/`).
  - Read lines of every `.kt` file and assert `lineCount < 400`.

### Step 5: Adapt Existing Contract Test Candidate Search Paths
* **Target Files**:
  - `CockpitFontExpansionTest.kt`
  - `AdvancedTuningVisualContractTest.kt`
  - `AdvancedTuningPaceCeilingContractTest.kt`
  - `LapDisplayModeSettingsTest.kt`
  - `AdvancedTuningAftermathContractTest.kt`
  - `WorkoutSectionReorderContractTest.kt`
* **Changes**:
  - Update file resolvers to search the relevant category file or the entire tuning directory, ensuring tests find their target AST / code snippets seamlessly.

### Step 6: Targeted Test Verification
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.typography.*"
  ```
* **Expected Result**: 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Modularity test confirms file structure and `< 400` line threshold.
  - All visual contract tests pass.
  - Full clean-room test suite passes (`./gradlew testDebugUnitTest`).
* **Rollback Plan**:
  - All modifications remain on `feature/ATT-2033`. In case of unexpected issues, checkout `sprint/2026-40.14` cleanly without side effects.
