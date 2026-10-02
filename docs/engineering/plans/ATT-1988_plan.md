# Stage 3: Implementation Plan - ATT-1988: [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings

**Ticket**: [ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)  
**Sub-task**: [ATT-1997](https://rainerblind.atlassian.net/browse/ATT-1997) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Requirement Mapping**: `REQ-UI-229`  
**Test Mapping**: `TST-UI-191`  
**Branch**: `feature/ATT-1988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

During on-device testing on Pixel 10 hardware, user observed that having a third 'Both' option in `LapDisplayMode` (which stacks both the `LapSplitVisualizer` and the classic numeric table) is redundant and adds unnecessary visual clutter to Expert Settings. Athletes want a clear binary choice: either the modern visualizer or the classic table.

Forensic analysis confirmed:
- `LapDisplayMode` enum declares 3 values: `TABLE_ONLY`, `VISUALIZER_ONLY`, and `BOTH`.
- `AdvancedTuningDialog.kt` renders 3 FilterChips in a row, constraining horizontal space on compact screens.
- `MyPreferenceManager.kt` needs defensive handling to map legacy persisted `"BOTH"` values to `LapDisplayMode.VISUALIZER_ONLY`.
- `WorkoutLaps.kt` currently defaults to `LapDisplayMode.BOTH` and renders both views when `mode == LapDisplayMode.BOTH`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-229` (*Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings*)
  - Eliminate `BOTH` option from `LapDisplayMode`.
  - Streamline `AdvancedTuningDialog` to a 2-chip selector (`Table` and `Visualizer`).
  - Defensively migrate legacy persisted `"BOTH"` DataStore entries to `VISUALIZER_ONLY`.
  - Update `WorkoutLaps` default parameter and rendering checks to binary choice.
* **Test Mapping**: `TST-UI-191` (*Lap Display Mode Binary Choice (Table vs. Visualizer) & Legacy Migration Verification*)
  - Unit tests in `LapDisplayModePreferencesTest.kt`, `WorkoutLapsDisplayModeTest.kt`, and `LapDisplayModeSettingsTest.kt`.
  - Clean-room test suite regression.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Defensive DataStore Migration**: Legacy stored string `"BOTH"` seamlessly resolves to `VISUALIZER_ONLY` with zero crashes or migration errors.
3. **Interactive Lap Editing Invariant**: Tapping a split in `LapSplitVisualizer` or a row in `LapRow` opens `LapEditBottomSheet` across both active display modes.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.
6. **Programmatic Pre-Check Gate**: Gate 3 verification via `tools/jira_util.py check-gate ATT-1997` must pass before any production code edits.

---

## 4. Proposed Architectural Changes

### Component 1: `LapDisplayMode.kt`
* **Changes**: Remove `BOTH` enum constant:
  ```kotlin
  enum class LapDisplayMode {
      TABLE_ONLY,
      VISUALIZER_ONLY
  }
  ```

### Component 2: `MyPreferenceManager.kt`
* **Changes**: In `workoutCardPreferencesFlow`, map legacy `"BOTH"` to `VISUALIZER_ONLY`:
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

### Component 3: `AdvancedTuningDialog.kt`
* **Changes**: Remove the third FilterChip for `BOTH`. Display two 50/50 weighted FilterChips (`TABLE_ONLY` and `VISUALIZER_ONLY`):
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

### Component 4: `WorkoutLaps.kt`
* **Changes**:
  - Update default parameter: `lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`.
  - Update `WorkoutLapsHelper`:
    ```kotlin
    fun shouldShowVisualizer(mode: LapDisplayMode): Boolean =
        mode == LapDisplayMode.VISUALIZER_ONLY

    fun shouldShowTable(mode: LapDisplayMode): Boolean =
        mode == LapDisplayMode.TABLE_ONLY
    ```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 status of `ATT-1997` via `python3 tools/jira_util.py check-gate ATT-1997`.

### Step 2: Update Domain Model & DataStore Persistence
* Modify `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapDisplayMode.kt`.
* Modify `app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt`.

### Step 3: Streamline Settings Dialog UI & WorkoutLaps
* Modify `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`.
* Modify `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt`.

### Step 4: Update Unit Tests
* Update `app/src/test/java/com/atrainingtracker/trainingtracker/settings/LapDisplayModePreferencesTest.kt`.
* Update `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLapsDisplayModeTest.kt`.
* Update `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/LapDisplayModeSettingsTest.kt`.

### Step 5: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.LapDisplayModePreferencesTest" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsDisplayModeTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.LapDisplayModeSettingsTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests during construction, followed by clean-room suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-1988` allows full revert via `git reset --hard origin/sprint/2026-40.10` without affecting the integration branch.
