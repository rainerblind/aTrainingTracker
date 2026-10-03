# Stage 2: Requirement & Test Specification - ATT-1988: [Verbesserung] [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings (SegmentedButton)

**Ticket**: [ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)  
**Sub-task**: [ATT-2040](https://rainerblind.atlassian.net/browse/ATT-2040) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-229` (*Configurable Lap Section Display Mode in Advanced Settings*), `REQ-UI-234` (*UI & Interaction Design System: Mutually Exclusive Binary Mode Selection*)  
**Test Spec ID**: `TST-UI-191`  
**Branch**: `feature/ATT-1988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-229 / REQ-UI-234)

### 1.1 Problem Statement & Rationale
During Sprint 2026-40.10 review, technical functionality was verified (the legacy stacked option `BOTH` was eliminated from `LapDisplayMode` domain logic and defaults to `VISUALIZER_ONLY`). However, using two `FilterChip` buttons for the binary choice between 'Tabelle' and 'Visualizer' in `AdvancedTuningDialog.kt` was rejected during physical hardware review (Pixel 10) as visually unanchored and inconsistent with modern Material 3 conventions.

Per `docs/design_guidelines.md` (§1.1) and `REQ-UI-234`, binary mutually exclusive display modes MUST utilize `SingleChoiceSegmentedButtonRow` with `SegmentedButton` (matching the 5 Zonen vs. Histogramm toggle pattern in zone distribution cards).

### 1.2 Functional & Architectural Requirements
1. **Material 3 Segmented Control in Settings (`AdvancedTuningDialog.kt`)**:
   - In `WorkoutMasksAndCardsSection` (`TuningSection.WORKOUT_MASKS_CARDS`), when `workoutCardPrefs.showLaps` is enabled (`true`), the dialog SHALL render a selector row with title `R.string.settings_lap_display_mode_title`.
   - The selector SHALL render as a Material 3 `SingleChoiceSegmentedButtonRow` with `modifier = Modifier.fillMaxWidth()`.
   - It SHALL host exactly two `SegmentedButton` items:
     - Index 0: `TABLE_ONLY` with label `stringResource(R.string.settings_lap_display_mode_table)`, selected when `workoutCardPrefs.lapDisplayMode == LapDisplayMode.TABLE_ONLY`.
     - Index 1: `VISUALIZER_ONLY` with label `stringResource(R.string.settings_lap_display_mode_visualizer)`, selected when `workoutCardPrefs.lapDisplayMode == LapDisplayMode.VISUALIZER_ONLY`.
   - Each button SHALL use `shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)`.
   - The usage of `FilterChip` for lap display mode selection is strictly prohibited.
2. **Domain Model & Persistence Invariants (`LapDisplayMode.kt` & `MyPreferenceManager.kt`)**:
   - `LapDisplayMode` enum has exactly two values: `TABLE_ONLY` and `VISUALIZER_ONLY`.
   - Default value remains `LapDisplayMode.VISUALIZER_ONLY`.
   - Defensive deserialization of legacy `"BOTH"` or unrecognized strings to `VISUALIZER_ONLY` remains intact.
3. **Acceptance Criteria (Given-When-Then)**:
   - *Given* an athlete in Advanced Settings (Workout-Karten & Eingabemasken),
   - *When* inspecting 'Rundendarstellung' with `showLaps == true`,
   - *Then* the mode selection SHALL render as a Material 3 `SingleChoiceSegmentedButtonRow` containing exactly two segmented buttons ('Tabelle' and 'Visualizer'), with zero `FilterChip` components.
   - *Given* an athlete tapping 'Tabelle' on the segmented button row,
   - *Then* `onWorkoutCardPrefsChange` SHALL be invoked with `lapDisplayMode = LapDisplayMode.TABLE_ONLY`.
   - *Given* an athlete tapping 'Visualizer' on the segmented button row,
   - *Then* `onWorkoutCardPrefsChange` SHALL be invoked with `lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-229`: *Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings.*
  - Refined by `REQ-UI-234`: *UI & Interaction Design System: Mutually Exclusive Binary Mode Selection & Component Heuristics.*
* **Historical Origin & Commit Trace**:
  - `7f747b02` (Sprint 2026-40.8, `ATT-1870`): Initial introduction of `LapDisplayMode` with 3 options (`TABLE_ONLY`, `VISUALIZER_ONLY`, `BOTH`).
  - Sprint 2026-40.9 / 40.10 (`ATT-1958`): Removal of `BOTH` option and defaulting to `VISUALIZER_ONLY`.
  - Sprint 2026-40.10 Review (`ATT-1988` / `ATT-1989`): Identification of `FilterChip` UI defect on physical hardware, mandating migration to `SingleChoiceSegmentedButtonRow`.
* **Root Reason for Existing Formulation**:
  - In `ATT-1870`, 3 chips were used (`Table`, `Visualizer`, `Both`). When `Both` was removed in `ATT-1958`, the remaining two options were kept inside the existing `FilterChip` layout as a simple deletion without redesigning the container into a segmented button row.
* **Preservation of Core Invariants**:
  - Binary choice (`VISUALIZER_ONLY` default vs. `TABLE_ONLY`), defensive mapping of legacy `"BOTH"` to `VISUALIZER_ONLY`, interactive lap editing via `LapEditBottomSheet` in both modes, zero database schema mutations, and 9-language localization parity are 100% strictly preserved.

---

## 2. Test Specification (TST-UI-191)

### Test Case 1: `testWorkoutCardSectionPreferences_lapDisplayModeDefaultAndMutation` (`TST-UI-191.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/LapDisplayModeSettingsTest.kt`
* **Preconditions**: Fresh instantiations of `WorkoutCardSectionPreferences`.
* **Action**: Verify default value, copy mutations with `TABLE_ONLY` and `VISUALIZER_ONLY`.
* **Expected Result**: Default is `VISUALIZER_ONLY`; mutations hold exact enum values.

### Test Case 2: `testAdvancedTuningDialog_lapDisplayModeUsesSegmentedButton` (`TST-UI-191.2`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/LapDisplayModeSettingsTest.kt` & `AdvancedTuningVisualContractTest.kt`
* **Preconditions**: Source file `AdvancedTuningDialog.kt` exists.
* **Action**: Inspect `WorkoutMasksAndCardsSection` AST / text for lap display mode:
  1. Assert `SingleChoiceSegmentedButtonRow` is used for lap display mode selection.
  2. Assert `SegmentedButton` is invoked with `SegmentedButtonDefaults.itemShape(index = ..., count = 2)`.
  3. Assert zero `FilterChip` instances exist for `lapDisplayMode` in `WorkoutMasksAndCardsSection`.
* **Expected Result**: SegmentedButton row pattern is verified and FilterChips are absent for lap display mode.

### Test Case 3: `testLapDisplayModeLocalizationParityAcrossAll9Locales` (`TST-UI-191.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/LapDisplayModeSettingsTest.kt`
* **Goal**: Verify string presence and matching tokens across all 9 locales:
  - EN, DE, ES, FR, IT, JA, NL, PL, PT
  - String keys: `settings_lap_display_mode_title`, `settings_lap_display_mode_table`, `settings_lap_display_mode_visualizer`.
* **Expected Result**: 100% parity across all 9 resource directories.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-191.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-191.1` | Unit | `WorkoutCardSectionPreferences` | `REQ-UI-229` | Specified |
| `TST-UI-191.2` | Structural Contract | `AdvancedTuningDialog.kt` | `REQ-UI-229`, `REQ-UI-234` | Specified |
| `TST-UI-191.3` | Localization | `LapDisplayModeSettingsTest` | `REQ-UI-229`, `REQ-UI-106` | Specified |
| `TST-UI-191.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
