# Stage 1 Analysis: ATT-1988 - [Verbesserung] [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings (SegmentedButton)

**Ticket**: [ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)  
**Sub-task**: [ATT-2039](https://rainerblind.atlassian.net/browse/ATT-2039) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Branch**: `feature/ATT-1988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

During Sprint 2026-40.10 review, technical functionality of `LapDisplayMode` was verified (the legacy stacked option `BOTH` was eliminated from domain logic and defaults to `VISUALIZER_ONLY`). However, the presentation in `AdvancedTuningDialog.kt` (`WorkoutMasksAndCardsSection`) used two adjacent `FilterChip` components (`Tabelle` and `Visualizer`).

On physical device review (Pixel 10), human testing concluded that using two `FilterChip` items for a binary mutually exclusive display mode selection is an anti-pattern:
1. `FilterChip` visually implies multi-select or filter tags that can be toggled on/off independently.
2. Two adjacent unanchored chips lack structural cohesion and tactile affordance.
3. Per `docs/design_guidelines.md` (§1.1) and `REQ-UI-234`, any binary mutually exclusive mode selection across the application MUST utilize Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton` (identical to the pattern established in `HeartRateZoneDistributionCard` / `PowerZoneDistributionCard` for 5 Zonen vs. Histogramm, and in `DisplaySettingsDialog`).

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Current Composable Implementation**:
   In `AdvancedTuningDialog.kt` (lines 833–850):
   ```kotlin
   Row(
       modifier = Modifier.fillMaxWidth(),
       horizontalArrangement = Arrangement.spacedBy(8.dp)
   ) {
       FilterChip(
           selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.TABLE_ONLY,
           onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.TABLE_ONLY)) },
           label = { Text(stringResource(R.string.settings_lap_display_mode_table)) },
           modifier = Modifier.weight(1f)
       )
       FilterChip(
           selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.VISUALIZER_ONLY,
           onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)) },
           label = { Text(stringResource(R.string.settings_lap_display_mode_visualizer)) },
           modifier = Modifier.weight(1f)
       )
   }
   ```
   This implementation bypassed `SingleChoiceSegmentedButtonRow`, introducing visual inconsistency with modern Material 3 guidelines.

2. **Domain Model & Persistence State**:
   - `LapDisplayMode` enum already contains exactly two values: `TABLE_ONLY` and `VISUALIZER_ONLY`.
   - `MyPreferenceManager` defaults to `VISUALIZER_ONLY` and defensively deserializes legacy `"BOTH"` or unknown values to `VISUALIZER_ONLY`.
   - The domain and persistence layers are fully established and need zero mutations.

3. **Design Guidelines & Requirement Alignment**:
   - `docs/design_guidelines.md` §1.1 explicitly dictates:
     *"Standard Control: Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton`."*
     *"When to Use: Choosing between exactly two mutually exclusive display, calculation, or visualization modes (e.g. Tabelle vs. Visualizer)."*
     *"Anti-Pattern: Do NOT use `FilterChip` for binary exclusive choices."*
   - `REQ-UI-234` mandates `SingleChoiceSegmentedButtonRow` for binary exclusive choices.
   - `REQ-UI-229` section 3 still mentions `FilterChip` in its textual description, which must be updated to `SingleChoiceSegmentedButtonRow` with `SegmentedButton`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Replace the two `FilterChip` components under "Rundendarstellung" (`R.string.tuning_lap_display_mode_title`) in `WorkoutMasksAndCardsSection` of `AdvancedTuningDialog.kt` with a Material 3 `SingleChoiceSegmentedButtonRow` containing two `SegmentedButton` components:
     - Index 0: `TABLE_ONLY` (`settings_lap_display_mode_table`).
     - Index 1: `VISUALIZER_ONLY` (`settings_lap_display_mode_visualizer`).
  2. Use standard `SegmentedButtonDefaults.itemShape(index = index, count = 2)`.
  3. Update `REQ-UI-229` in `docs/requirements.md` and `TST-UI-191` in `docs/tests.md` to reflect `SingleChoiceSegmentedButtonRow` / `SegmentedButton`.
  4. Update unit test `LapDisplayModeSettingsTest.kt` and contract test `AdvancedTuningVisualContractTest.kt` to assert presence of `SingleChoiceSegmentedButtonRow` and zero `FilterChip` elements for lap display mode.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to `LapDisplayMode.kt` domain enum or `WorkoutLaps.kt` rendering logic.
  2. No changes to DataStore keys or `MyPreferenceManager.kt` serialization/deserialization.
  3. No changes to other sections in `AdvancedTuningDialog.kt` (e.g. X-Axis Domain in AftermathAnalysisSection, typography, battery saver).
  4. No database schema changes or SQLite migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

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
  - Replacing the two `FilterChip` elements with `SingleChoiceSegmentedButtonRow` strictly preserves the underlying model: `TABLE_ONLY` and `VISUALIZER_ONLY` toggle the exact same `WorkoutCardSectionPreferences.copy(lapDisplayMode = ...)`, maintain 9-language localization parity, and cause zero functional disruption to workout card rendering or lap editing bottom sheet.

---

## 5. Architectural Strategy & High-Level Solution

1. **`AdvancedTuningDialog.kt` Refactoring**:
   In `WorkoutMasksAndCardsSection`:
   ```kotlin
   SingleChoiceSegmentedButtonRow(
       modifier = Modifier.fillMaxWidth()
   ) {
       SegmentedButton(
           selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.TABLE_ONLY,
           onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.TABLE_ONLY)) },
           shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
       ) {
           Text(stringResource(R.string.settings_lap_display_mode_table))
       }
       SegmentedButton(
           selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.VISUALIZER_ONLY,
           onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)) },
           shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
       ) {
           Text(stringResource(R.string.settings_lap_display_mode_visualizer))
       }
   }
   ```
2. **Testing Strategy**:
   - `LapDisplayModeSettingsTest.kt`: Add structural contract test checking `AdvancedTuningDialog.kt` contains `SingleChoiceSegmentedButtonRow` for lap display mode and zero `FilterChip` items for `lapDisplayMode`.
   - `AdvancedTuningVisualContractTest.kt`: Add assertion ensuring `SingleChoiceSegmentedButtonRow` is utilized in `WorkoutMasksAndCardsSection`.
   - Run unit test suite: `./gradlew testDebugUnitTest`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. DataStore persistence and defensive legacy fallback remain intact.
  3. Parent ticket Human Decision Gate remains strictly enforced.
  4. 9-language localization parity preserved.
* **Risk Rating**: **LOW**
  - Self-contained UI component replacement within `WorkoutMasksAndCardsSection`.
  - Zero database schema or domain enum alterations.
