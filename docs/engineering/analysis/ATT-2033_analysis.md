# Stage 1 Analysis: ATT-2033 - Modularize AdvancedTuningDialog into Focused Category Composables

**Ticket**: [ATT-2033](https://rainerblind.atlassian.net/browse/ATT-2033)  
**Sub-task**: [ATT-2248](https://rainerblind.atlassian.net/browse/ATT-2248) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Branch**: `feature/ATT-2033`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

`AdvancedTuningDialog.kt` has evolved into a monolithic source file of 1,205 lines of code. It currently co-locates:
1. Dialog lifecycle, modal container, tab/accordion coordination, DataStore state collection, and batch reset/save logic.
2. Five expansive category section composables matching the canonical `TuningSection` enum:
   - `CockpitTypographySection` (Font family selection, numeric font weight, tabular numerals, 360dp menu constraints).
   - `AmoledBatterySaverSection` (Display timeout, dimming factor, dynamic battery saver toggles).
   - `SensorsGpsFilterSection` (GPS filter accuracy, slope calculations, elevation window).
   - `AftermathAnalysisSection` (Independent X-axis domain selectors for elevation vs. telemetry, pace ceiling slider).
   - `WorkoutMasksAndCardsSection` & `WorkoutAftermathMatrixSection` (8 card visibility toggles, section reordering list controls, lap display mode segmented button).
3. Generic form controls (`TuningSliderItem`, `TuningToggleItem`).

This monolithic structure violates separation of concerns, degrades IDE performance, complicates code reviews, and risks unnecessary recomposition cascades across unrelated setting categories when local state changes.

---

## 2. Root Cause Analysis & Taxonomy Reconciliation

### Evolutionary Root Cause:
The monolithic state is the result of rapid agile accretion across sprints 2026-40.8 through 2026-40.14:
- `ATT-1957` (`REQ-UI-222`): Replaced flat scrolling list with 5-section accordion, placing all section implementations directly inside `AdvancedTuningDialog.kt`.
- `ATT-1958` (`REQ-UI-229`): Added Workout Mask and Card section toggles.
- `ATT-1986` (`REQ-UI-233`): Added independent elevation and telemetry X-axis domain chips.
- `ATT-1988` (`REQ-UI-234`): Added single-choice segmented buttons for lap display mode.
- `ATT-2014` (`REQ-UI-243`): Added minimum pace ceiling slider with step formatting.
- `ATT-2176` (`REQ-UI-255`): Added interactive section reordering controls with up/down arrows.

### Taxonomy Reconciliation Addendum:
The initial epic backlog description for ATT-2033 utilized informal placeholders (`LiveTuningSection`, `MapTuningSection`, `RouteClusteringSection`, `IndependentXAxisSection`). In production, Sprint 2026-40.8 formalized the canonical 5-section architecture in `TuningSection`:
1. `COCKPIT_TYPOGRAPHY` -> `CockpitTypographySection`
2. `BATTERY_SAVER` -> `AmoledBatterySaverSection`
3. `SENSORS_GPS` -> `SensorsGpsFilterSection`
4. `AFTERMATH_ANALYSIS` -> `AftermathAnalysisSection`
5. `WORKOUT_MASKS_CARDS` -> `WorkoutMasksAndCardsSection` (including `WorkoutAftermathMatrixSection`)

The parent ticket `ATT-2033` description has been formally updated and harmonized with this canonical 5-section taxonomy.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Decompose `AdvancedTuningDialog.kt` into dedicated, modular component files under `com.atrainingtracker.trainingtracker.ui.settings.tuning.categories`:
    - `CockpitTypographySection.kt`
    - `AmoledBatterySaverSection.kt`
    - `SensorsGpsFilterSection.kt`
    - `AftermathAnalysisSection.kt`
    - `WorkoutMasksAndCardsSection.kt` (including `WorkoutAftermathMatrixSection`)
  * Extract shared generic form controls into `TuningFormControls.kt` (`TuningSliderItem`, `TuningToggleItem`) in `com.atrainingtracker.trainingtracker.ui.settings.tuning`.
  * Streamline `AdvancedTuningDialog.kt` to contain strictly dialog scaffolding, accordion coordinator, lifecycle DataStore observation, and reset actions (< 400 lines).
  * Ensure no single file in `ui.settings.tuning` exceeds 400 lines (AC-1).
  * Guarantee 100% behavioral parity (AC-2) and test parity (AC-3).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to preference keys or `TuningConfig` schema.
  * No visual redesign of sliders, chips, segmented buttons, or accordion containers.
  * No alteration of DataStore persistence mechanics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Archaeology of Preserved Requirements:
1. **REQ-UI-222** (`ATT-1957`): 5-section accordion structure with `emptySet()` initial expansion.
2. **REQ-UI-229** (`ATT-1958`): Non-null preference collection gating for workout cards.
3. **REQ-UI-233** (`ATT-1986`): Independent elevation (Distance) and telemetry (Time) domain chips.
4. **REQ-UI-234** (`ATT-1988`): SingleChoiceSegmentedButtonRow for lap display mode.
5. **REQ-UI-243** (`ATT-2014`): Pace ceiling slider with 15 steps (2.0–6.0 min/km).
6. **REQ-UI-255** (`ATT-2176`): Section reordering controls with boundary safety.

### Net-New Requirement Formulation:
* **Requirement**: `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog (`AdvancedTuningDialog`)*).
* **Rationale**: Decouples monolithic UI into single-responsibility category composables. Preserves 100% of all UI contracts and test assertions while eliminating maintainability bottlenecks.

---

## 5. Architectural Strategy & High-Level Solution

### Package Structure:
```text
com.atrainingtracker.trainingtracker.ui.settings.tuning/
├── AdvancedTuningDialog.kt          (~350 lines - Dialog scaffold & state coordination)
├── AdvancedTuningAccordion.kt       (285 lines - Accordion card, section enum, subtitles)
├── AdvancedTuningDialogFragment.kt  (50 lines - Fragment wrapper)
├── TuningPaceCeilingFormatter.kt    (49 lines - Formatter utility)
├── TuningFormControls.kt            (~80 lines - TuningSliderItem, TuningToggleItem)
└── categories/
    ├── CockpitTypographySection.kt (~190 lines - Typography controls)
    ├── AmoledBatterySaverSection.kt (~110 lines - Battery saver options)
    ├── SensorsGpsFilterSection.kt   (~55 lines - Sensor & GPS filter parameters)
    ├── AftermathAnalysisSection.kt  (~130 lines - X-axis domains & pace ceiling)
    └── WorkoutMasksAndCardsSection.kt (~270 lines - Section reordering & card toggles)
```

### State Hoisting & Recomposition Boundary Invariants:
1. **Stateless Extracted Composables**: Each category section composable accepts strictly immutable state values and targeted callback lambdas (e.g. `(Float) -> Unit`, `(Boolean) -> Unit`, `(ProfileXAxisDomain) -> Unit`).
2. **Recomposition Containment**: When an athlete drags a slider in `AftermathAnalysisSection`, Compose recomposition is localized to that specific section and control, without re-evaluating layout or state trees in `CockpitTypographySection` or `WorkoutMasksAndCardsSection`.
3. **Public API Invariance**: Public entry points (`AdvancedTuningDialog` composable, `AdvancedTuningDialogFragment`) retain identical caller signatures.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Behavioral Invariance**: All sliders, toggles, chips, and segmented buttons continue to update and persist to DataStore identically.
  2. **File Size Invariance**: No file in the tuning package exceeds 400 lines of code.
  3. **Test Invariance**: All visual contract and unit tests pass cleanly.
  4. **Human Decision Gate**: Parent ticket `ATT-2033` must NOT be moved to `Erledigt` autonomously.
* **Risk Rating**: **LOW**
  - Pure structural refactoring without business logic, database, or network changes.
  - Comprehensive contract test coverage ensures regression detection.
