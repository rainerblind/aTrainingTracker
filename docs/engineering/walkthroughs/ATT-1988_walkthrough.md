# Stage 5: Walkthrough & Verification - ATT-1988: [Verbesserung] [Settings/Aftermath] Remove 'Both' Option from Lap Display Mode in Expert Settings (SegmentedButton)

**Ticket**: [ATT-1988](https://rainerblind.atlassian.net/browse/ATT-1988)  
**Sub-task**: [ATT-2044](https://rainerblind.atlassian.net/browse/ATT-2044) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-229`, `REQ-UI-234`  
**Test Mapping**: `TST-UI-191`  
**Branch**: `feature/ATT-1988`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

Ticket `ATT-1988` refactored the lap display mode selector in `AdvancedTuningDialog.kt` (`WorkoutMasksAndCardsSection`). Previously, after the removal of the third option 'BOTH' in Sprint 2026-40.10, the remaining binary choice between 'Tabelle' and 'Visualizer' was presented as two adjacent `FilterChip` components. On physical device evaluation (Pixel 10), this presentation was rejected as visually unanchored and inconsistent with Material 3 design heuristics.

In accordance with `docs/design_guidelines.md` (§1.1) and `REQ-UI-234`, the selector has been replaced with a Material 3 `SingleChoiceSegmentedButtonRow` featuring two `SegmentedButton` items (`TABLE_ONLY` at index 0 and `VISUALIZER_ONLY` at index 1) with pill boundary styling via `SegmentedButtonDefaults.itemShape(index, count = 2)`.

Targeted unit and structural contract tests passed, and the complete clean-room test suite (`./gradlew testDebugUnitTest`) passed 100% cleanly in 4m 01s.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-229` | `TST-UI-191.1` | Unit Test: DataStore & Preference Mutation (`LapDisplayModeSettingsTest`) | **PASSED** | `Verified` |
| `REQ-UI-229`, `REQ-UI-234` | `TST-UI-191.2` | Structural Contract Test: SegmentedButtonRow & Zero FilterChips (`LapDisplayModeSettingsTest`, `AdvancedTuningVisualContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-229`, `REQ-UI-106` | `TST-UI-191.3` | Localization Parity Audit across all 9 Locales (`LapDisplayModeSettingsTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-191.4` | Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 4m 1s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit & Contract Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.LapDisplayModeSettingsTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningVisualContractTest" \
                            --tests "com.atrainingtracker.trainingtracker.settings.LapDisplayModePreferencesTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsDisplayModeTest"

BUILD SUCCESSFUL in 12s
32 actionable tasks: 2 executed, 30 up-to-date
```

---

## 4. Hardware / Physical Verification (Pixel 10)

* In `AdvancedTuningDialog` (expanded "Workout-Karten & Eingabemasken" accordion):
  * When `showLaps == true`, "Rundendarstellung" renders as a unified, centered Material 3 `SingleChoiceSegmentedButtonRow`.
  * Tapping 'Tabelle' selects the left pill (index 0) and updates `WorkoutCardSectionPreferences.lapDisplayMode = TABLE_ONLY`.
  * Tapping 'Visualizer' selects the right pill (index 1) and updates `WorkoutCardSectionPreferences.lapDisplayMode = VISUALIZER_ONLY`.
  * Visual anchor and tactile feedback align perfectly with card-level toggles (e.g. 5 Zonen vs. Histogramm).

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-229`) and `docs/tests.md` (`TST-UI-191`) updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket transitioned to `Final Review (Human)` and assigned to `human` for final release sign-off (Rule 1).
5. **Continuous Sprint Integration**: Branch merged via `--no-ff` into `sprint/2026-40.11` per Strategy A.
