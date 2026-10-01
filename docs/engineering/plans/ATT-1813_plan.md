# Stage 3: Implementation Plan - ATT-1813: Reorder telemetry graphs: place Speed/Pace above Heart Rate graph

**Ticket**: [ATT-1813](https://rainerblind.atlassian.net/browse/ATT-1813)  
**Sub-task**: [ATT-1851](https://rainerblind.atlassian.net/browse/ATT-1851) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-214` (*Aftermath: Telemetry Metric Graph Ordering (Speed/Pace Preceding Heart Rate) for Clustered Cardiovascular Exertion Flow*)  
**Test Mapping**: `TST-UI-168` (*Aftermath Telemetry Metric Graph Ordering & Visual Flow Verification*)  
**Branch**: `feature/ATT-1813`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In the Aftermath workout inspection layout (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) and inside detailed workout summary journal cards (`WorkoutSummary.kt`), continuous telemetry graphs are currently rendered in the order:
1. Heart Rate
2. Speed / Pace
3. Power

Directly below the continuous telemetry charts section are the zone distribution cards (Heart Rate 5-zone distribution, followed by Power 5-zone distribution). In typical running, walking, and cycling activities without a power meter, having Speed/Pace situated between the Heart Rate graph and the Heart Rate Zone Distribution card creates a fragmented visual flow. Reordering the graphs so that Speed/Pace precedes Heart Rate ensures that the continuous Heart Rate curve is positioned directly adjacent to the 5-Zone Distribution card, producing a cohesive, uninterrupted cardiovascular exertion visual layout.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-214` (*Aftermath: Telemetry Metric Graph Ordering (Speed/Pace Preceding Heart Rate) for Clustered Cardiovascular Exertion Flow*)
* **Test Mapping**: `TST-UI-168` (*Aftermath Telemetry Metric Graph Ordering & Visual Flow Verification*)
  * `[TST-UI-168.1]`: `MapDetailLayoutTest.testMapDetailLayout_telemetryGraphOrdering_speedPrecedesHeartRate`
  * `[TST-UI-168.2]`: `TelemetryMetricGraphTest.testWorkoutSummary_telemetryGraphOrdering_speedPrecedesHeartRate`
  * `[TST-UI-168.3]`: `TelemetryMetricLocalizationTest`
  * `[TST-UI-168.4]`: `./gradlew testDebugUnitTest`

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All existing graph behaviors (touch scrubbing, dynamic scaling, zero-padding omission when data is absent) remain completely unchanged.
2. **Synchronized Multi-Chart Scrubbing**: Shared `selectedDistance` parameter remains synchronously forwarded to all graphs and the map cursor.
3. **Sport Type Unit Adaptation**: Running sports (`bSportType == BSportType.RUN`) continue selecting Pace (`min/km`), while other sports select Speed (`km/h`).
4. **Mandatory Programmatic Pre-Check Before Stage 4 Code Modifications**:
   - `python3 tools/jira_util.py check-gate ATT-1851` must exit code 0 (`GATE_PASSED`) before any modification to `app/src/...`.
5. **Human Gate Invariance**: Terminal transition of parent ticket `ATT-1813` is strictly `Final Review (Human)` assigned to the user.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
- In Section 3 under `if (showZoomControls)`:
  - Relocate the Speed / Pace graph composable block (including heading and `TelemetryMetricGraph`) so that it appears directly before the Heart Rate graph composable block.
  - Preserve the 8.dp spacer between graphs.
  - Resulting order:
    1. Speed / Pace Graph (`if (TelemetryMetricUtils.hasSpeedData(path))`)
    2. Heart Rate Graph (`if (TelemetryMetricUtils.hasHeartRateData(path))`)
    3. Power Graph (`if (TelemetryMetricUtils.hasPowerData(path))`)

### Component 2: `WorkoutSummary.kt` (`com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist`)
- Under `if (preferences.showTelemetryCharts && telemetryPoints.isNotEmpty())`:
  - Relocate the Speed / Pace graph composable block (including divider, heading, and `TelemetryMetricGraph`) so that it appears directly before the Heart Rate graph composable block.
  - Resulting order:
    1. Speed / Pace Graph (`if (TelemetryMetricUtils.hasSpeedData(telemetryPoints))`)
    2. Heart Rate Graph (`if (TelemetryMetricUtils.hasHeartRateData(telemetryPoints))`)
    3. Power Graph (`if (TelemetryMetricUtils.hasPowerData(telemetryPoints))`)

### Component 3: Unit & Structural Contract Tests
- `MapDetailLayoutTest.kt`: Add test verifying Speed/Pace graph block precedes Heart Rate graph block in `MapDetailLayout.kt`.
- `TelemetryMetricGraphTest.kt`: Add test verifying Speed/Pace graph block precedes Heart Rate graph block in `WorkoutSummary.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Command: `python3 tools/jira_util.py check-gate ATT-1851`
* Enforce Gate 3 sign-off before modifying production source files.

### Step 2: Reorder Graphs in `MapDetailLayout.kt`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Cut lines 292–312 (Speed / Pace Graph block) and paste above line 271 (Heart Rate Graph block).

### Step 3: Reorder Graphs in `WorkoutSummary.kt`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* Cut lines 301–323 (Speed / Pace Graph block) and paste above line 279 (Heart Rate Graph block).

### Step 4: Add Structural Contract Tests in `MapDetailLayoutTest.kt` & `TelemetryMetricGraphTest.kt`
* Target Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* Add assertions verifying that the Speed/Pace graph occurrence index precedes the Heart Rate graph occurrence index in both files.

### Step 5: Execute Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted UI and map layout unit tests pass.
  2. Full suite `./gradlew testDebugUnitTest` passes cleanly with zero regressions.
  3. Author Stage 5 walkthrough deliverable (`docs/engineering/walkthroughs/ATT-1813_walkthrough.md`).
* **Rollback**:
  - `feature/ATT-1813` is an isolated branch off `sprint/2026-40.7`. Any failure can be discarded cleanly via `git checkout sprint/2026-40.7 && git branch -D feature/ATT-1813`.
