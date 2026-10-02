# Stage 1 Analysis: ATT-1988 - [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings

**Ticket**: [ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)  
**Sub-task**: [ATT-1995](https://rainerblind.atlassian.net/browse/ATT-1995) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Branch**: `feature/ATT-1988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

During on-device physical testing on Pixel 10 hardware of `ATT-1958` (where `LapDisplayMode` persistence and the `VISUALIZER_ONLY` default were verified), user confirmed that settings persistence works cleanly, but observed:
> *"This works now. However while testing, I observed that we don't need the 'both' option."*

Currently, `LapDisplayMode` in `AdvancedTuningDialog.kt` offers three FilterChips:
1. `Table` (`TABLE_ONLY`)
2. `Visualizer` (`VISUALIZER_ONLY`)
3. `Both` (`BOTH`)

The `Both` mode renders both the graphical `LapSplitVisualizer` and the classic numeric table stacked together. In practice, this creates unnecessary visual clutter, redundant representations of the exact same lap data, and cognitive load in Expert Settings. Athletes want a clear binary choice: either the high-aesthetic graphical split visualizer or the classic numeric table, not both simultaneously.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Domain Model (`LapDisplayMode.kt`)**:
   `LapDisplayMode` contains 3 enum constants: `TABLE_ONLY`, `VISUALIZER_ONLY`, and `BOTH`.
2. **Settings UI (`AdvancedTuningDialog.kt`)**:
   Under `WorkoutMasksAndCardsSection`, lines 770–792 render three FilterChips in a Row with equal weight `1f`. On narrow screens or long German translations ("Visualizer", "Tabelle", "Beide"), three chips constrain horizontal breathing room. Reducing this to two chips (`Tabelle` and `Visualizer`) creates a clean 50/50 balance.
3. **DataStore Deserialization (`MyPreferenceManager.kt`)**:
   Line 114 deserializes `preferences[WORKOUT_CARD_LAP_DISPLAY_MODE]` via `LapDisplayMode.valueOf(rawMode)`. If a user currently has `"BOTH"` persisted, deserialization must gracefully map this legacy string to `LapDisplayMode.VISUALIZER_ONLY` to avoid `IllegalArgumentException` or unexpected behavior.
4. **Rendering (`WorkoutLaps.kt`)**:
   Default argument in `WorkoutLaps` is still `LapDisplayMode.BOTH`. `shouldShowVisualizer` and `shouldShowTable` both check `mode == LapDisplayMode.BOTH`. With `BOTH` removed, `WorkoutLaps` defaults to `LapDisplayMode.VISUALIZER_ONLY`, and the rendering logic cleanly alternates between the visualizer and the table.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Streamline `LapDisplayMode.kt` to a binary choice: `TABLE_ONLY` and `VISUALIZER_ONLY`.
  * Ensure defensive deserialization in `MyPreferenceManager.kt` seamlessly maps any legacy persisted `"BOTH"` values to `VISUALIZER_ONLY`.
  * Update `AdvancedTuningDialog.kt` to present only two FilterChips (`Table` and `Visualizer`), removing the `Both` chip.
  * Update `WorkoutLaps.kt` default parameter to `VISUALIZER_ONLY` and streamline conditional checks.
  * Update unit test suites (`LapDisplayModePreferencesTest`, `WorkoutLapsDisplayModeTest`, `LapDisplayModeSettingsTest`) to validate binary choice and legacy migration.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to `LapSplitVisualizer.kt` bar layout, animation, or rendering logic.
  * No changes to `LapTableHeader` or `LapRow` table styling.
  * No database migrations (lap display mode is stored exclusively in DataStore preferences).
  * No modification of `LapEditBottomSheet` or interactive lap editing flows.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-229` (*Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.8 (`ATT-1870`, Commit `7f747b02`) and refined in Sprint 2026-40.9 (`ATT-1958`, Commit `2730177f`).
* **Root Reason for Existing Formulation**: When the `LapSplitVisualizer` was originally introduced in ATT-1870, `BOTH` was provided as a transitional fallback option to allow athletes to see both representations simultaneously without losing the familiar numeric table. However, physical device testing in Sprint 2026-40.9 confirmed that stacking both views creates unwanted visual clutter, and athletes strictly prefer either the modern visualizer or the classic table.
* **Preservation of Core Invariants**:
  - The default mode remains `VISUALIZER_ONLY`.
  - Full backward compatibility for existing installations is guaranteed via defensive mapping of legacy `"BOTH"` to `VISUALIZER_ONLY`.
  - Interactive lap editing via `LapEditBottomSheet` remains 100% functional across both modes.
  - 9-language localization parity is strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **`LapDisplayMode.kt`**:
   Remove `BOTH` constant from enum:
   ```kotlin
   enum class LapDisplayMode {
       TABLE_ONLY,
       VISUALIZER_ONLY
   }
   ```
2. **`MyPreferenceManager.kt`**:
   Safely handle legacy `"BOTH"` in DataStore deserialization:
   ```kotlin
   lapDisplayMode = try {
       val rawMode = preferences[WORKOUT_CARD_LAP_DISPLAY_MODE]
       if (rawMode != null && rawMode != "BOTH") {
           LapDisplayMode.valueOf(rawMode)
       } else {
           LapDisplayMode.VISUALIZER_ONLY
       }
   } catch (e: Exception) {
       LapDisplayMode.VISUALIZER_ONLY
   }
   ```
3. **`AdvancedTuningDialog.kt`**:
   Remove the `FilterChip` for `BOTH`, leaving only `Table` and `Visualizer`:
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
4. **`WorkoutLaps.kt`**:
   - Default parameter: `lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`.
   - `shouldShowVisualizer(mode: LapDisplayMode) = mode == LapDisplayMode.VISUALIZER_ONLY`.
   - `shouldShowTable(mode: LapDisplayMode) = mode == LapDisplayMode.TABLE_ONLY`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit test suites.
  2. DataStore schema forward/backward compatibility preserved via defensive deserialization.
  3. Parent ticket Human Decision Gate remains strictly enforced (`Final Review (Human)`).
* **Risk Rating**: **LOW**  
  The change simplifies state from 3 options to 2, eliminates visual clutter, contains defensive fallback for legacy data, and touches zero database schemas.
