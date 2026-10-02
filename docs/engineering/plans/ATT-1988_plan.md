# Stage 3: Implementation Plan - ATT-1988: [Verbesserung] [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings (SegmentedButton)

**Ticket**: [ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)  
**Sub-task**: [ATT-2041](https://rainerblind.atlassian.net/browse/ATT-2041) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-229` (*Configurable Lap Section Display Mode in Advanced Settings*), `REQ-UI-234` (*UI & Interaction Design System: Mutually Exclusive Binary Mode Selection*)  
**Test Mapping**: `TST-UI-191` (*Lap Display Mode Binary Choice & Legacy Migration Verification*)  
**Branch**: `feature/ATT-1988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In Sprint 2026-40.10, the third option 'BOTH' was eliminated from `LapDisplayMode` and the default was set to `VISUALIZER_ONLY`. However, in `AdvancedTuningDialog.kt` (`WorkoutMasksAndCardsSection`), the selector under 'Rundendarstellung' was rendered as two adjacent `FilterChip` components.

During physical hardware review (Pixel 10), human testing concluded that using two `FilterChip` items for a binary mutually exclusive display mode selection is an anti-pattern. Per `docs/design_guidelines.md` (§1.1) and `REQ-UI-234`, binary mutually exclusive display modes across the application MUST utilize Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton` (matching the 5 Zonen vs. Histogramm pattern in zone distribution cards).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-229` (*Configurable Lap Section Display Mode in Advanced Settings*), `REQ-UI-234` (*UI & Interaction Design System: Mutually Exclusive Binary Mode Selection*)
* **Test Mapping**: `TST-UI-191` (*Lap Display Mode Binary Choice & Legacy Migration Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites, unit tests, and layout structures continue to pass cleanly.
2. **Persistence Integrity**: DataStore preference key `workout_card_lap_display_mode` and defensive fallback to `VISUALIZER_ONLY` are strictly preserved without mutations.
3. **Interactive Lap Editing Parity**: `LapEditBottomSheet` continues to open identically across both `TABLE_ONLY` and `VISUALIZER_ONLY` modes.
4. **9-Language Localization Parity**: Existing localized string resources (`settings_lap_display_mode_title`, `settings_lap_display_mode_table`, `settings_lap_display_mode_visualizer`) are preserved across all 9 supported locales.
5. **Subtask Self-Sufficiency**: Subtask transitions directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-1988` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AdvancedTuningDialog.kt` (`WorkoutMasksAndCardsSection`)
* Replace the `Row` hosting two `FilterChip` items (lines 833–850) with:
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
* Ensures full Material 3 styling compliance, rounded pill boundaries, and unified mode switcher aesthetics.

### Component 2: Test Suite Verification (`LapDisplayModeSettingsTest.kt` & `AdvancedTuningVisualContractTest.kt`)
* In `LapDisplayModeSettingsTest.kt`:
  - Add structural AST / source contract test asserting `AdvancedTuningDialog.kt` contains `SingleChoiceSegmentedButtonRow` for lap display mode and zero `FilterChip` instances for `lapDisplayMode`.
* In `AdvancedTuningVisualContractTest.kt`:
  - Add contract assertion asserting `SingleChoiceSegmentedButtonRow` presence in `WorkoutMasksAndCardsSection`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate Verification
* Programmatic verification per ASPICE Governance Rule 3:
  `python3 tools/jira_util.py check-gate ATT-2041`

### Step 2: Refactor `AdvancedTuningDialog.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* Replace `FilterChip` row with `SingleChoiceSegmentedButtonRow` and `SegmentedButton` items.

### Step 3: Update Test Suite & Structural Assertions
* Target 1: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/LapDisplayModeSettingsTest.kt`
* Target 2: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt`
* Add assertions verifying `SingleChoiceSegmentedButtonRow` and `SegmentedButton` usage and zero `FilterChip` for lap display mode.

### Step 4: Targeted Test Suite Execution
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.LapDisplayModeSettingsTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningVisualContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.settings.LapDisplayModePreferencesTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsDisplayModeTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit and contract tests during construction, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Work is isolated on `feature/ATT-1988`. In case of unexpected issues, `git reset --hard HEAD~1` or branch deletion provides safe rollback without affecting `sprint/2026-40.11` or `develop`.
