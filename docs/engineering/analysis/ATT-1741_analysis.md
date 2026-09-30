# Stage 1 Analysis: ATT-1741 - [Aftermath/Splits] Revert Lap & Interval Split Chart from Aftermath screens

**Ticket**: [ATT-1741](https://atrainingtracker.atlassian.net/browse/ATT-1741)  
**Sub-task**: [ATT-1755](https://atrainingtracker.atlassian.net/browse/ATT-1755) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1741`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During the sprint review of Sprint `2026-40.5` for ticket [ATT-1392](https://atrainingtracker.atlassian.net/browse/ATT-1392), the user inspected the newly introduced compact lap & interval split chart on-device and evaluated:
> *"I saw already saw this. It looks really bad. Thus, please create one ticket to revert this and one ticket to make this much more better."*

As a result:
1. Backlog ticket `ATT-1742` (*High-Aesthetic Redesign of Lap & Interval Split Visualizer*) was created for future pair refinement outside the sprint.
2. Ticket `ATT-1741` was created within sprint `2026-40.6` to promptly remove the unsatisfactory visual chart elements from Aftermath screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) and [WorkoutLaps.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt)), restoring clean layout aesthetics and removing dead wiring.

---

## 2. Root Cause Analysis & Architectural Investigation

### 2.1 Current Points of Coupling
1. **[TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt)**:
   - Computes `splitChartData` via `LapSplitCalculator.calculateSplitData(...)`.
   - Manages state `selectedLapNr` (`var selectedLapNr by rememberSaveable { mutableStateOf<Long?>(null) }`).
   - Slices track segments (`LapSegmentUtils.calculateLapDistanceRange`, `LapSegmentUtils.sliceLapSegment`).
   - Renders start/stop markers and highlighted polyline (`lapHighlight(lapSegment)`) on the map.
   - Renders `LapSplitChartCard` inside the default `analyticsContent` column below the zone cards.
2. **[WorkoutLaps.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt)**:
   - Computes `splitChartData` via `LapSplitCalculator.calculateSplitData(...)`.
   - Renders `LapSplitChart` above the lap table rows if `splitChartData != null`.
3. **Underlying Domain & Calculator Package**:
   - `LapSplitCalculator.kt`, `LapSplitModels.kt`, and `LapSegmentUtils.kt` in package `com.atrainingtracker.trainingtracker.ui.aftermath.splits` can remain preserved as dormant calculation utilities for when the redesign (`ATT-1742`) is implemented, or the obsolete visual composables (`LapSplitChart.kt`, `LapSplitChartCard.kt`) can be deprecated/removed. Keeping the calculation and model classes prevents breaking existing unit tests in `LapSplitCalculatorTest.kt`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remove `LapSplitChartCard` invocation from `TrackOnMapScreen.kt`.
  * Clean up `selectedLapNr`, `splitChartData`, and lap highlight rendering wiring from `TrackOnMapScreen.kt`.
  * Remove `LapSplitChart` invocation from `WorkoutLaps.kt`.
  * Restore `TrackOnMapScreen` and `WorkoutLaps` to their clean, uncluttered visual layout.
  * Update living documentation (`REQ-UI-204`, `TST-UI-158`) with Chesterton's Fence archaeology explaining the user-directed revert.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Designing a replacement split chart (this is explicitly deferred to `ATT-1742` in the backlog for pair refinement).
  * Modifying existing lap table editing, lap addition, or lap deletion logic in `WorkoutLaps.kt`.
  * Modifying zone distribution cards or elevation profile in `TrackOnMapScreen.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - `REQ-UI-204` (*Aftermath: Compact Lap & Interval Split Chart Architecture*).
* **Historical Origin & Commit Trace**:
  - Ticket `ATT-1392`, Sprint `2026-40.5`.
* **Root Reason for Existing Formulation**:
  - `REQ-UI-204` introduced the visual `LapSplitChart` and `LapSplitChartCard` to provide graphical interval comparisons.
* **Preservation of Core Invariants**:
  - During sprint review `2026-40.5`, the human user evaluated the visual presentation as unsatisfactory ("looks really bad") and mandated reverting the visual cards immediately (`ATT-1741`) while splitting the redesign into a separate backlog ticket (`ATT-1742`).
  - Underlying lap data in SQLite, lap tables in `WorkoutLaps.kt`, and map detail layouts remain 100% stable and unregressed.

---

## 5. Architectural Strategy & High-Level Solution

1. **Clean `TrackOnMapScreen.kt`**:
   - Remove unused imports `LapSplitCalculator` and `LapSplitChartCard`.
   - Remove `splitChartData`, `selectedLapNr`, `startDistM`, `endDistM`, and `lapSegment`.
   - In `mapContent`, simplify to standard markers (`markers(markers)`).
   - In `analyticsContent`, remove `LapSplitChartCard` rendering block.
2. **Clean `WorkoutLaps.kt`**:
   - Remove `LapSplitCalculator` and `LapSplitChart` imports.
   - Remove `splitChartData` calculation and `LapSplitChart` composable rendering.
3. **Traceability**:
   - Update `REQ-UI-204` in `docs/requirements.md` and `TST-UI-158` in `docs/tests.md` with Chesterton's Fence archaeology.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in workout lap list inspection, editing, or deletion.
  2. Map rendering, track polyline, and marker rendering continue to function without errors.
  3. Single-thread SQLite confinement and parent human gate governance preserved.
* **Risk Rating**: **LOW**
  - Justification: Clean removal of UI components and local state without database schema or threading changes.
