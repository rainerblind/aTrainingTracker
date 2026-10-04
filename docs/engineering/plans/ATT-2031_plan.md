# Stage 3: Implementation Plan - ATT-2031: Centralize Zone Threshold Resolution (DRY) and Optimize Scrubbing Point Search to O(log N)

**Ticket**: [ATT-2031](https://rainerblind.atlassian.net/browse/ATT-2031)  
**Sub-task**: [ATT-2239](https://rainerblind.atlassian.net/browse/ATT-2239) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-UI-260` (*Centralized Zone Threshold Resolution & O(log N) Binary Search Scrubbing Point Lookup*)  
**Test Mapping**: `TST-UI-219` (*Centralized Zone Threshold Resolution & O(log N) Scrubbing Point Search Verification*)  
**Branch**: `feature/ATT-2031`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

In post-workout visual analytics (`MapDetailLayout.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`), athletes inspect heart rate, cycling power, and speed/pace metrics along an interactive scrubbing timeline.

Two architectural deficiencies and performance bottlenecks degrade code maintainability and interaction fluidity:
1. **Duplicate Zone Threshold Resolution (DRY Violation)**:
   A ~15-line boilerplate block querying `SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1..4)` is copy-pasted across three files (`ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt`). In `MapDetailLayout.kt`, threshold queries were also executed un-memoized during scrubbing header text updates.
2. **Linear Search During Interactive Scrubbing ($O(N)$ vs $O(\log N)$)**:
   In `MapDetailLayout.kt` (lines 176–184, 394–400, 441–447) and in `TelemetryMetricGraph.kt` (lines 921–925 inside the `Canvas` `drawWithContent` render pass), finding the active trackpoint executes `pathPoints.minByOrNull { abs(...) }`.
   On workouts with $> 2,000$ points, this performs an $O(N)$ linear scan on the UI thread for every touch move event and during Canvas drawing frames, resulting in UI micro-stutters and frame drops during scrub gesture dragging.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-260` (*Centralized Zone Threshold Resolution & O(log N) Scrubbing Point Search*)
* **Test Mapping**: `TST-UI-219`
  * `TST-UI-219.1`: `TelemetryZoneMath.loadHeartRateThresholds` correctly queries `HR_BIKE` for `BSportType.BIKE` and `HR_RUN` for others.
  * `TST-UI-219.2`: `TelemetryZoneMath.loadHeartRateThresholds` returns `null` when any zone threshold $\le 0$.
  * `TST-UI-219.3`: `TelemetryZoneMath.loadPowerThresholds` queries `PWR_BIKE` and returns `PowerZoneThresholds` (or `null` if invalid).
  * `TST-UI-219.4`: `TelemetryMetricUtils.findNearestPoint` distance domain binary search achieves 100% numerical parity with `minByOrNull`.
  * `TST-UI-219.5`: `TelemetryMetricUtils.findNearestPoint` time domain binary search achieves 100% numerical parity with `minByOrNull`.
  * `TST-UI-219.6`: `TelemetryMetricUtils.findNearestPoint` edge cases (empty list, single item, boundary targets, out-of-range targets).
  * `TST-UI-219.7`: Full clean-room test suite regression execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly (1530+ tests).
2. **Visual Parity**: Visual styling of telemetry graphs, curve drawing, zone coloring, and scrubber pins MUST NOT change.
3. **Exact Mathematical Parity**: The $O(\log N)$ binary search must yield the exact same nearest `PathPoint` as `minByOrNull` across all possible target distances and timestamps, including tie-breaking behavior.
4. **Data Model Immutability**: `PathPoint`, `TrackPoint`, `HeartRateZoneThresholds`, and `PowerZoneThresholds` data classes remain unchanged.
5. **Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TelemetryZoneMath.kt` (Domain Math Layer)
* Add standardized factory methods:
  ```kotlin
  fun loadHeartRateThresholds(context: Context, bSportType: BSportType): HeartRateZoneThresholds?
  fun loadPowerThresholds(context: Context): PowerZoneThresholds?
  ```
* Encapsulate safe retrieval via `SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1..4)`.
* Enforce strict ascending positive threshold validation (`z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3`).

### Component 2: `TelemetryMetricUtils.kt` (Utility Layer)
* Extract `TelemetryMetricUtils` into its own dedicated file `TelemetryMetricUtils.kt` under package `com.atrainingtracker.trainingtracker.ui.map`.
* Add optimized, allocation-free $O(\log N)$ binary search helper:
  ```kotlin
  fun findNearestPoint(
      points: List<PathPoint>,
      targetValue: Double,
      isTimeDomain: Boolean = false
  ): PathPoint?
  ```
* Implement monotonic binary search using `binarySearch` with $O(1)$ memory allocation and tie-breaking matching `minByOrNull`.

### Component 3: Call-Site Consolidation & Memoization (UI Layer)
* **`ElevationProfile.kt`**: Replace duplicate 25-line inline zone query in `ScrubbingTelemetryBadge` with `TelemetryZoneMath.loadHeartRateThresholds` and `loadPowerThresholds`.
* **`TelemetryMetricGraph.kt`**:
  * Replace duplicate 25-line inline zone query with `TelemetryZoneMath` helpers.
  * In `Canvas` scrubbing marker rendering (lines 921–925), replace `pathPoints.minByOrNull` with `TelemetryMetricUtils.findNearestPoint`.
* **`MapDetailLayout.kt`**:
  * Replace duplicate 25-line inline zone query with `TelemetryZoneMath` helpers.
  * In scrubbing point resolution (lines 176–184, 394–400, 441–447), replace `path.minByOrNull` with `TelemetryMetricUtils.findNearestPoint`.
  * Pass memoized `hrThresholds` and `powerThresholds` directly to child composables.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Factory Methods to `TelemetryZoneMath.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMath.kt`
* Implement `loadHeartRateThresholds` and `loadPowerThresholds`.

### Step 2: Extract & Extend `TelemetryMetricUtils.kt`
* Targets:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtils.kt` (Create dedicated file)
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt` (Remove duplicate declaration of object)
* Add `findNearestPoint(points: List<PathPoint>, targetValue: Double, isTimeDomain: Boolean = false): PathPoint?`.

### Step 3: Author Unit Tests for Zone Loading & Binary Search
* Targets:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMathTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtilsTest.kt`
* Implement `TST-UI-219.1` through `TST-UI-219.6`.

### Step 4: Refactor Call Sites in `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt`
* Targets:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Wire new `TelemetryZoneMath` factory methods and `TelemetryMetricUtils.findNearestPoint`.

### Step 5: Execute Targeted Tests
* Run:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
