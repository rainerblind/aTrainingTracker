# Stage 2: Requirement & Test Specification - ATT-2033: Modularize AdvancedTuningDialog into Focused Category Composables

**Ticket**: [ATT-2033](https://rainerblind.atlassian.net/browse/ATT-2033)  
**Sub-task**: [ATT-2249](https://rainerblind.atlassian.net/browse/ATT-2249) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog (`AdvancedTuningDialog`)*)  
**Test Spec ID**: `TST-UI-221`  
**Branch**: `feature/ATT-2033`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-262)

### 1.1 Problem Statement & Rationale
`AdvancedTuningDialog.kt` in package `com.atrainingtracker.trainingtracker.ui.settings.tuning` has expanded into a 1,205-line monolithic file. It co-locates:
1. Modal container lifecycle, top-level layout scaffolding, accordion coordination, DataStore state collection flows, and batch reset/save interactions.
2. Five distinct, full-featured category composables matching the canonical `TuningSection` enum:
   - `CockpitTypographySection` (Font family selection, numeric font weight, tabular numerals, 360dp menu constraints).
   - `AmoledBatterySaverSection` (Display timeout, dimming factor, dynamic battery saver toggles).
   - `SensorsGpsFilterSection` (GPS filter accuracy, slope calculations, elevation window).
   - `AftermathAnalysisSection` (Independent X-axis domain selectors for elevation vs. telemetry, pace ceiling slider).
   - `WorkoutMasksAndCardsSection` & `WorkoutAftermathMatrixSection` (8 card visibility toggles, section reordering list controls, lap display mode segmented button).
3. Generic form controls (`TuningSliderItem`, `TuningToggleItem`).

This monolith violates the Single Responsibility Principle, degrades developer agility and IDE indexing performance, complicates peer code reviews, and risks unnecessary recomposition cascades across unrelated setting categories when local state changes.

`REQ-UI-262` establishes a modular component architecture decomposing `AdvancedTuningDialog.kt` into dedicated, cohesive category composables and reusable form controls, while strictly maintaining 100% behavioral parity and test coverage.

### 1.2 Functional & Architectural Requirements
The system SHALL decompose the monolithic `AdvancedTuningDialog.kt` into focused, single-responsibility composables under package `com.atrainingtracker.trainingtracker.ui.settings.tuning` (ATT-2033):
1. **Target Architecture & File Granularity**:
   - `AdvancedTuningDialog.kt`: SHALL retain strictly dialog scaffolding, accordion card hosting, DataStore state observation/collection, and batch reset/save actions (< 400 lines).
   - `TuningFormControls.kt`: SHALL contain reusable form controls (`TuningSliderItem`, `TuningToggleItem`).
   - `categories/CockpitTypographySection.kt`: SHALL encapsulate cockpit font family dropdown (max height 360dp constraint preserved), font weight slider/picker, and tabular numerals toggle.
   - `categories/AmoledBatterySaverSection.kt`: SHALL encapsulate display timeout slider, dimming factor slider, and dynamic battery saver toggles.
   - `categories/SensorsGpsFilterSection.kt`: SHALL encapsulate GPS accuracy filter slider, slope calculation speed threshold, and elevation window.
   - `categories/AftermathAnalysisSection.kt`: SHALL encapsulate independent X-axis domain chips (Elevation Distance/Time vs Telemetry Distance/Time) and minimum pace ceiling slider (15 discrete steps, 2.0–6.0 min/km).
   - `categories/WorkoutMasksAndCardsSection.kt`: SHALL encapsulate 8 workout card toggles, section reordering list with bounds checking and up/down arrows, `WorkoutAftermathMatrixSection`, and single-choice segmented button row for lap display mode.
2. **File Size Limit (AC-1)**:
   - Every file within `com.atrainingtracker.trainingtracker.ui.settings.tuning` (including sub-packages) SHALL NOT exceed 400 lines of code.
3. **Stateless Extracted Composables**:
   - Each category section composable SHALL be stateless, accepting strictly immutable configuration values and explicit callback lambdas (`(Float) -> Unit`, `(Boolean) -> Unit`, etc.), ensuring clean recomposition containment.
4. **Zero Behavioral & Preference Regression (AC-2 & AC-4)**:
   - DataStore preference keys, persistence logic, UI strings, visual layout spacing, and default values SHALL remain 100% identical.
5. **Contract Test Parity (AC-3)**:
   - Visual and behavioral contract tests targeting the tuning screens SHALL be preserved and updated to resolve composables from their new modular locations without regression.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (File Size & Modularity Enforcement)**:
  * *Given* the `com.atrainingtracker.trainingtracker.ui.settings.tuning` source tree,
  * *When* line counts of all `.kt` files are evaluated,
  * *Then* every file SHALL have strictly fewer than 400 lines of code.
* **AC-2 (Behavioral & Functional Parity)**:
  * *Given* the advanced tuning dialog displayed on device or in tests,
  * *When* navigating accordion sections and toggling or sliding any setting (typography, battery saver, GPS filter, X-axis domain, pace ceiling, card visibility, section ordering, lap mode),
  * *Then* the behavior, visual styling, and DataStore updates SHALL be completely indistinguishable from the pre-refactoring implementation.
* **AC-3 (Test Suite Parity)**:
  * *Given* the existing suite of contract tests (`AdvancedTuningVisualContractTest`, `AdvancedTuningAccordionTest`, `AdvancedTuningAftermathContractTest`, `AdvancedTuningPaceCeilingContractTest`, `WorkoutSectionReorderContractTest`, `CockpitFontExpansionTest`, `LapDisplayModeSettingsTest`),
  * *When* executed against the modularized codebase,
  * *Then* 100% of test assertions SHALL pass without failure.
* **AC-4 (Zero Preference Schema Regression)**:
  * *Given* the persistent DataStore schema for tuning preferences,
  * *When* inspecting `TuningConfig`, keys, or defaults,
  * *Then* no preference keys, types, or default values SHALL be altered.

### 1.4 System Invariants
1. **Zero Visual Layout Shift**: Visual layout margins, paddings, color tokens, and elevation values remain strictly identical.
2. **Recomposition Containment**: Sub-section adjustments do not trigger recomposition across unrelated category composables.
3. **100% Pass Rate**: Full unit test suite (1550+ tests) must pass with zero failures.
4. **Human Decision Gate**: Parent ticket `ATT-2033` must NOT be moved to `Erledigt` autonomously.

---

## 2. Test Specification (TST-UI-221)

### Test Case 1: Structural Modularity & File Size Audit (`TST-UI-221.1`)
* **Scope**: Automated Structural Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt`
* **Preconditions**: Project source tree available at runtime.
* **Action**:
  - Scan directory `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/`.
  - Verify existence of all modularized files:
    - `AdvancedTuningDialog.kt`
    - `TuningFormControls.kt`
    - `categories/CockpitTypographySection.kt`
    - `categories/AmoledBatterySaverSection.kt`
    - `categories/SensorsGpsFilterSection.kt`
    - `categories/AftermathAnalysisSection.kt`
    - `categories/WorkoutMasksAndCardsSection.kt`
  - Read line count of each `.kt` file in `ui.settings.tuning` and its subdirectories.
  - Assert `lineCount < 400` for every file.
* **Expected Result**:
  - All target files exist in their specified locations.
  - All files strictly satisfy the `< 400` lines constraint.

### Test Case 2: Visual & Behavioral Contract Parity Suite (`TST-UI-221.2`)
* **Scope**: UI & Architecture Contract Tests
* **Target Files**:
  - `AdvancedTuningVisualContractTest.kt`
  - `AdvancedTuningAccordionTest.kt`
  - `AdvancedTuningAftermathContractTest.kt`
  - `AdvancedTuningPaceCeilingContractTest.kt`
  - `WorkoutSectionReorderContractTest.kt`
  - `CockpitFontExpansionTest.kt`
  - `LapDisplayModeSettingsTest.kt`
* **Action**:
  - Execute contract tests validating:
    - Typography dropdown constraint (360dp max height).
    - Battery saver dimming factor and timeout sliders.
    - GPS accuracy filter controls.
    - Aftermath independent X-axis chips and pace ceiling slider (15 discrete steps).
    - Workout mask and card visibility toggles, section reordering logic, and lap display mode segmented button.
    - Accordion card state transitions and expansion behavior.
* **Expected Result**:
  - 100% pass rate across all contract test classes.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-221.3`)
* **Scope**: Localization Parity Test
* **Target Test**: `TranslationParityTest.kt`
* **Goal**:
  - Verify that all strings referenced by the modularized category composables exist and match across all 9 supported locales:
    - English (EN)
    - German (DE)
    - Spanish (ES)
    - French (FR)
    - Italian (IT)
    - Japanese (JA)
    - Dutch (NL)
    - Polish (PL)
    - Portuguese (PT)
* **Expected Result**:
  - 100% parity across all 9 localized `strings.xml` files with zero missing keys or mismatched specifiers.

### Test Case 4: Clean-Room Full Suite Regression Execution (`TST-UI-221.4`)
* **Scope**: Full Clean-Room Regression Test
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**:
  - Execute full unit test suite (1550+ tests) in clean-room environment.
* **Expected Result**:
  - Zero test failures, zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-221.1]` | Structural | `AdvancedTuningModularityTest` | `REQ-UI-262` (AC-1, Section 1, 2) | Specified |
| `[TST-UI-221.2]` | Contract | Visual Contract Test Suite (`AdvancedTuning*`, `WorkoutSectionReorder*`) | `REQ-UI-262` (AC-2, AC-3) | Specified |
| `[TST-UI-221.3]` | Localization | `TranslationParityTest` | `REQ-UI-262`, `REQ-UI-106` | Specified |
| `[TST-UI-221.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
