# Stage 3: Implementation Plan - ATT-1741: [Aftermath/Splits] Revert Lap & Interval Split Chart from Aftermath screens

**Ticket**: [ATT-1741](https://atrainingtracker.atlassian.net/browse/ATT-1741)  
**Sub-task**: [ATT-1757](https://atrainingtracker.atlassian.net/browse/ATT-1757) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-UI-204` (*Aftermath: Compact Lap & Interval Split Chart Revert & Deferral Architecture*)  
**Test Mapping**: `TST-UI-158` (*Aftermath Lap & Interval Split Chart Revert Verification*)  
**Branch**: `feature/ATT-1741`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

Following Sprint Review 2026-40.5 feedback on ATT-1392, the human user determined that the compact lap & interval split chart in its current form was unsatisfactory ("looks really bad") and mandated reverting the visual cards from the Aftermath inspection and workout laps screens (`ATT-1741`), while splitting future redesign into `ATT-1742` in the backlog for pair refinement.

This plan details the surgical removal of `LapSplitChartCard` and `LapSplitChart` from UI screens, cleaning up state and map highlighting wiring in `TrackOnMapScreen.kt` and `WorkoutLaps.kt`, while preserving domain models and calculation logic.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-204` (*Aftermath: Compact Lap & Interval Split Chart Revert & Deferral Architecture*)
* **Test Mapping**: `TST-UI-158` (*Aftermath Lap & Interval Split Chart Revert Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Lap viewing, editing, deletion in `WorkoutLaps.kt`, and map navigation in `TrackOnMapScreen.kt` continue to function without error.
2. **Domain Logic Preservation**: Domain models (`LapSplitModels.kt`), calculations (`LapSplitCalculator.kt`), and segment slicing (`LapSegmentUtils.kt`) remain in the codebase so future redesign (`ATT-1742`) can build upon them without re-engineering.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TrackOnMapScreen.kt` (UI Clean-Up)
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`
* **Changes**:
  - Remove imports `LapSplitCalculator` and `LapSplitChartCard`.
  - Remove `selectedLapNr` state and `splitChartData` memoized calculation.
  - Remove lap segment distance calculation and slicing (`startDistM`, `endDistM`, `lapSegment`).
  - In `mapContent`, simplify to `markers(markers)`.
  - In `analyticsContent`, remove the `LapSplitChartCard` composable call.

### Component 2: `WorkoutLaps.kt` (UI Clean-Up)
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt`
* **Changes**:
  - Remove imports `LapSplitCalculator` and `LapSplitChart`.
  - Remove `splitChartData` memoized calculation.
  - Remove `if (splitChartData != null) { LapSplitChart(...) }` from the top of the lap list.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Clean Up `TrackOnMapScreen.kt`
* Remove unused imports.
* Remove `selectedLapNr`, `splitChartData`, `lapSegment` and associated calculations.
* Clean up `mapContent` and remove `LapSplitChartCard` from `analyticsContent`.

### Step 2: Clean Up `WorkoutLaps.kt`
* Remove unused imports.
* Remove `splitChartData` and `LapSplitChart` rendering block.

### Step 3: Run Targeted Unit Tests
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.splits.*"
  ```
* Verify `LapSplitCalculatorTest` and localization tests still compile and pass.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests compile and pass, followed by clean-room full suite regression in Stage 5.
* **Rollback Plan**: All changes isolated on `feature/ATT-1741`.
