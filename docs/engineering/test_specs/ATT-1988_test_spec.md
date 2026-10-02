# Stage 2: Requirement & Test Specification - ATT-1988: [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings

**Ticket**: [ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)  
**Sub-task**: [ATT-1996](https://rainerblind.atlassian.net/browse/ATT-1996) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Requirement Mapping**: `REQ-UI-229` (*Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings*)  
**Test Spec ID**: `TST-UI-191`  
**Branch**: `feature/ATT-1988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-229)

### 1.1 Problem Statement & Rationale
During Sprint 2026-40.9 physical testing on Pixel 10 hardware, user confirmed that persistence and `VISUALIZER_ONLY` default work properly, but observed:
> *"This works now. However while testing, I observed that we don't need the 'both' option."*

Stacking both the `LapSplitVisualizer` and the classic numeric table within the same workout card creates redundant information density and unnecessary visual clutter. In Advanced Settings (`AdvancedTuningDialog.kt`), displaying three FilterChips in a row cramps horizontal layout on smaller displays. Athletes strictly prefer a binary choice: either the high-aesthetic graphical split visualizer or the classic numeric table.

### 1.2 Functional & Architectural Requirements

1. *Domain Model Simplification (`LapDisplayMode.kt`)*:
   - `LapDisplayMode` enum SHALL contain exactly two options: `TABLE_ONLY` and `VISUALIZER_ONLY`.
   - The redundant constant `BOTH` SHALL be eliminated.
2. *DataStore Deserialization & Legacy Migration (`MyPreferenceManager.kt`)*:
   - Deserialization of `WORKOUT_CARD_LAP_DISPLAY_MODE` SHALL safely parse `rawMode`. If `rawMode == "BOTH"`, it SHALL map defensively to `LapDisplayMode.VISUALIZER_ONLY`.
   - Any corrupt or invalid stored strings SHALL fall back to `LapDisplayMode.VISUALIZER_ONLY`.
3. *Settings Dialog UI Streamlining (`AdvancedTuningDialog.kt`)*:
   - In `WorkoutMasksAndCardsSection`, the Rundendarstellung selector row SHALL display exactly two equal-width FilterChips:
     - `Table` (`LapDisplayMode.TABLE_ONLY` / `R.string.settings_lap_display_mode_table`)
     - `Visualizer` (`LapDisplayMode.VISUALIZER_ONLY` / `R.string.settings_lap_display_mode_visualizer`)
   - The 'Both' FilterChip SHALL be removed.
4. *WorkoutLaps Rendering Cleanup (`WorkoutLaps.kt`)*:
   - Default parameter SHALL be `lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`.
   - `shouldShowVisualizer(mode)` SHALL return `true` IF AND ONLY IF `mode == LapDisplayMode.VISUALIZER_ONLY`.
   - `shouldShowTable(mode)` SHALL return `true` IF AND ONLY IF `mode == LapDisplayMode.TABLE_ONLY`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-229` (*Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.8 (`ATT-1870`, Commit `7f747b02`), refined in Sprint 2026-40.9 (`ATT-1958`, Commit `2730177f`), and refined in Sprint 2026-40.10 (`ATT-1988`).
* **Root Reason for Existing Formulation**: `BOTH` was introduced in ATT-1870 as a transitional fallback so users would not lose tabular rows when visualizer was introduced. Testing on physical hardware in Sprint 2026-40.9 confirmed that athletes strictly prefer either the modern visualizer or the classic table, and having a third stacked option creates redundant visual clutter and cramped settings chips.
* **Preservation of Core Invariants**:
  - Binary choice (`VISUALIZER_ONLY` default vs. `TABLE_ONLY`).
  - Defensive mapping of legacy `"BOTH"` to `VISUALIZER_ONLY`.
  - Interactive lap editing via `LapEditBottomSheet` across both modes.
  - Zero database schema mutations.
  - 9-language localization parity preserved for all active tokens.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Default Value & Reset)**:
  * *Given* a fresh installation or factory reset in Advanced Settings,
  * *When* inspecting `lapDisplayMode`,
  * *Then* the preference SHALL default to `LapDisplayMode.VISUALIZER_ONLY`.
* **Criterion 2 (Settings Two-Chip Presentation)**:
  * *Given* an athlete opening Advanced Settings with `showLaps == true`,
  * *When* viewing the Rundendarstellung section,
  * *Then* exactly 2 FilterChips (`Table` and `Visualizer`) SHALL be displayed with equal width, and zero 'Both' chip SHALL be visible.
* **Criterion 3 (Visualizer Only Rendering)**:
  * *Given* an athlete selecting `Visualizer`,
  * *When* inspecting a workout card in `WorkoutSummary` / `WorkoutLaps`,
  * *Then* only `LapSplitVisualizer` SHALL be rendered, and classic table components (`LapTableHeader` and rows) SHALL NOT be rendered.
* **Criterion 4 (Table Only Rendering)**:
  * *Given* an athlete selecting `Table`,
  * *When* inspecting a workout card in `WorkoutSummary` / `WorkoutLaps`,
  * *Then* only classic table components SHALL be rendered, and `LapSplitVisualizer` SHALL NOT be rendered.
* **Criterion 5 (Legacy DataStore Migration)**:
  * *Given* an athlete device with legacy `"BOTH"` stored in DataStore,
  * *When* preferences are deserialized in `MyPreferenceManager.kt`,
  * *Then* the preference SHALL seamlessly evaluate to `LapDisplayMode.VISUALIZER_ONLY` without errors or exceptions.
* **Criterion 6 (Interactive Lap Editing)**:
  * *Given* either active mode (`Table` or `Visualizer`),
  * *When* tapping a lap split item or table row,
  * *Then* `LapEditBottomSheet` SHALL open for that lap.

---

## 2. Test Specification (TST-UI-191)

### Test Case 1: `testLapDisplayModeEnumCoverage_binaryChoice` (`TST-UI-191.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/LapDisplayModePreferencesTest.kt`
* **Preconditions**: `LapDisplayMode` enum loaded.
* **Action**: Inspect `LapDisplayMode.values()`.
* **Expected Result**: Exactly 2 enum constants: `TABLE_ONLY` and `VISUALIZER_ONLY`. `BOTH` is absent.

### Test Case 2: `testLegacyBothDeserialization_mapsToVisualizerOnly` (`TST-UI-191.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/LapDisplayModePreferencesTest.kt`
* **Preconditions**: DataStore emits raw string `"BOTH"`.
* **Action**: Run deserialization logic with `"BOTH"`.
* **Expected Result**: Deserializes cleanly to `LapDisplayMode.VISUALIZER_ONLY`.

### Test Case 3: `testAdvancedTuningDialog_displaysOnlyTwoFilterChips` (`TST-UI-191.3`)
* **Scope**: Settings UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/LapDisplayModeSettingsTest.kt`
* **Preconditions**: `AdvancedTuningDialog.kt` source loaded.
* **Action**: Verify `FilterChip` declarations for `lapDisplayMode`.
* **Expected Result**: Exactly 2 FilterChips present (`TABLE_ONLY` and `VISUALIZER_ONLY`). Zero references to `LapDisplayMode.BOTH` or `settings_lap_display_mode_both`.

### Test Case 4: `testWorkoutLaps_conditionalDisplayLogic_binary` (`TST-UI-191.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLapsDisplayModeTest.kt`
* **Preconditions**: `WorkoutLapsHelper` loaded.
* **Action**: Evaluate `shouldShowVisualizer` and `shouldShowTable` for `TABLE_ONLY` and `VISUALIZER_ONLY`.
* **Expected Result**:
  - `VISUALIZER_ONLY`: `shouldShowVisualizer == true`, `shouldShowTable == false`.
  - `TABLE_ONLY`: `shouldShowVisualizer == false`, `shouldShowTable == true`.

### Test Case 5: 9-Language Localization Audit (`TST-UI-191.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/LapDisplayModeSettingsTest.kt`
* **Goal**: Verify string presence and non-empty values for `settings_lap_display_mode_title`, `settings_lap_display_mode_table`, and `settings_lap_display_mode_visualizer` across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity across all 9 languages.

### Test Case 6: Clean-Room Full Suite Regression Execution (`TST-UI-191.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-191.1` | Unit | `LapDisplayMode.values()` | `REQ-UI-229` (Clause 1) | Specified |
| `TST-UI-191.2` | Unit | `MyPreferenceManager.workoutCardPreferencesFlow` | `REQ-UI-229` (Clause 2) | Specified |
| `TST-UI-191.3` | Contract | `AdvancedTuningDialog` | `REQ-UI-229` (Clause 3) | Specified |
| `TST-UI-191.4` | Unit | `WorkoutLapsHelper.shouldShowVisualizer/Table` | `REQ-UI-229` (Clause 4) | Specified |
| `TST-UI-191.5` | Localization | `LapDisplayModeSettingsTest.testLapDisplayModeLocalizationParity` | `REQ-UI-229` (Clause 5) | Specified |
| `TST-UI-191.6` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
