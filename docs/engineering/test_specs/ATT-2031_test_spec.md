# Stage 2: Requirement & Test Specification - ATT-2031: Centralize Zone Threshold Resolution (DRY) and Optimize Scrubbing Point Search to O(log N)

**Ticket**: [ATT-2031](https://rainerblind.atlassian.net/browse/ATT-2031)  
**Sub-task**: [ATT-2238](https://rainerblind.atlassian.net/browse/ATT-2238) (`[Test-Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-UI-260` (*Centralized Zone Threshold Resolution & O(log N) Binary Search Scrubbing Point Lookup*)  
**Test Spec ID**: `TST-UI-219`  
**Branch**: `feature/ATT-2031`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-260)

### 1.1 Problem Statement & Rationale
During post-workout telemetry analysis in Aftermath (`MapDetailLayout.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`), athletes inspect heart rate, cycling power, and speed/pace metrics along an interactive scrubbing timeline.

Two architectural deficiencies and performance bottlenecks currently degrade code maintainability and touch interaction fluidity:
1. **Duplicate Zone Threshold Resolution (DRY Violation)**:
   A ~15-line boilerplate query retrieving zone thresholds from `SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1..4)` is copy-pasted across three components:
   - `ElevationProfile.kt` (lines 785–815)
   - `TelemetryMetricGraph.kt` (lines 280–305)
   - `MapDetailLayout.kt` (lines 145–175)
   Any future change to zone fallback handling or data store keys requires synchronized edits across multiple files.
2. **Linear Search During Interactive Scrubbing ($O(N)$ vs $O(\log N)$)**:
   In `MapDetailLayout.kt` (lines 394–400, 441–447) and in `TelemetryMetricGraph.kt` (lines 921–925), finding the active trackpoint closest to `selectedDistance` executes `pathPoints.minByOrNull { abs(...) }`.
   On long workout recordings with $> 2,000$ points, this performs an $O(N)$ linear scan on the UI thread for every touch move event and during Canvas drawing frames, resulting in UI micro-stutters and frame drops during scrub gesture dragging.

### 1.2 Functional & Architectural Requirements
1. **Centralized Zone Threshold Resolution (`TelemetryZoneMath.kt`)**:
   - `TelemetryZoneMath` SHALL serve as the single source of truth for loading athlete training zone thresholds from user preferences via standard factory functions:
     ```kotlin
     fun loadHeartRateThresholds(context: Context, bSportType: BSportType): HeartRateZoneThresholds?
     fun loadPowerThresholds(context: Context): PowerZoneThresholds?
     ```
   - The methods SHALL encapsulate `SettingsDataStoreJavaHelper.getZoneMax` queries (inspecting zones 1..4), mapping `bSportType == BSportType.BIKE` to `HR_BIKE` vs `HR_RUN`, and returning `null` if any zone threshold $\le 0$.
2. **DRY Call-Site Consolidation & Memoization**:
   - Duplicated 15-line boilerplate zone queries across `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` SHALL be eliminated and replaced with unified calls to `TelemetryZoneMath`.
   - In `MapDetailLayout.kt`, zone thresholds SHALL be resolved and memoized at the top level via `remember(bSportType, context)` and `remember(context)`, passing resolved threshold models directly into dependent child composables (`ElevationProfile` and `TelemetryMetricGraph`).
3. **Optimized $O(\log N)$ Scrubbing Point Search (`TelemetryMetricUtils.kt`)**:
   - Point selection along the workout scrubbing timeline SHALL utilize an allocation-free binary search algorithm:
     ```kotlin
     fun findNearestPoint(points: List<PathPoint>, targetValue: Double, isTimeDomain: Boolean = false): PathPoint?
     ```
   - Exploiting the monotonic ordering of recorded `PathPoint` lists (sorted by `distance` or `timeSec`), the search SHALL identify the closest trackpoint in $O(\log N)$ comparisons with exact numerical parity to linear nearest-neighbor resolution, avoiding allocation of temporary closures or full list traversals on the UI thread during drag gestures.
4. **Scrubbing Point Search Replacement**:
   - Ad-hoc linear scans `pathPoints.minByOrNull { abs(it.distance - selectedDistance) }` and `it.timeSec` in `MapDetailLayout.kt` and `TelemetryMetricGraph.kt` SHALL be replaced with `TelemetryMetricUtils.findNearestPoint`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Valid HR and Power Zone Threshold Resolution)**:
  * *Given* an athlete inspecting a workout in `MapDetailLayout`, `ElevationProfile`, or `TelemetryMetricGraph`,
  * *When* HR or Power zone thresholds are configured in settings,
  * *Then* `TelemetryZoneMath.loadHeartRateThresholds` and `loadPowerThresholds` SHALL load the exact thresholds corresponding to the workout's `BSportType` with 100% numerical parity.
* **AC-2 (Invalid or Unset Thresholds Handling)**:
  * *Given* unconfigured or invalid ($\le 0$) zone settings,
  * *Then* the threshold factory functions SHALL return `null` safely without runtime exceptions or partial zone initialization.
* **AC-3 (Scrubbing Point Lookup Exact Numerical Parity in $O(\log N)$)**:
  * *Given* a recorded `PathPoint` track containing $N \ge 1$ points monotonically sorted by distance and time,
  * *When* scrubbing interactively to any distance or timestamp (including exact match, intermediate positions, boundary values, or out-of-range targets),
  * *Then* `TelemetryMetricUtils.findNearestPoint` SHALL return the identical nearest `PathPoint` in $O(\log N)$ comparisons that `minByOrNull` would have selected.
* **AC-4 (Empty and Boundary PathPoint Collections)**:
  * *Given* an empty `PathPoint` list,
  * *When* `findNearestPoint` is invoked,
  * *Then* it SHALL return `null` safely.
* **AC-5 (Time Domain Lookup Parity)**:
  * *Given* a trackless workout scrubbing timeline operating on seconds (`isTimeDomain = true`),
  * *When* `findNearestPoint` is invoked with a target time,
  * *Then* it SHALL return the nearest `PathPoint` matching `timeSec` with exact parity to linear search.

### 1.4 System Invariants
1. Visual styling of telemetry graphs, curve drawing, zone coloring, and scrubber pins MUST NOT change.
2. `PathPoint` and `TrackPoint` data structures MUST NOT be altered.
3. Clean-room unit test suite pass rate (1530+ tests) MUST NOT regress.
4. Human Decision Gate on parent tickets remains inviolable.

---

## 2. Test Specification (TST-UI-219)

### Test Case 1: `testLoadHeartRateThresholds_withBikeAndRun_queriesAccurateZoneTypes` (`TST-UI-219.1`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMathTest.kt`
* **Preconditions**: Mock `SettingsDataStoreJavaHelper.getZoneMax` returning valid thresholds (e.g. 130, 150, 165, 180).
* **Action**:
  1. Call `TelemetryZoneMath.loadHeartRateThresholds(context, BSportType.BIKE)`.
  2. Call `TelemetryZoneMath.loadHeartRateThresholds(context, BSportType.RUN)`.
* **Expected Result**:
  - `BSportType.BIKE` queries `SettingsDataStore.ZoneType.HR_BIKE`.
  - `BSportType.RUN` queries `SettingsDataStore.ZoneType.HR_RUN`.
  - Returned `HeartRateZoneThresholds` contains matching z1, z2, z3, z4 values.

### Test Case 2: `testLoadHeartRateThresholds_whenInvalidOrZero_returnsNull` (`TST-UI-219.2`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMathTest.kt`
* **Preconditions**: Mock `SettingsDataStoreJavaHelper.getZoneMax` returning 0 for any zone.
* **Action**: Call `TelemetryZoneMath.loadHeartRateThresholds(context, BSportType.RUN)`.
* **Expected Result**: Returns `null` without throwing exceptions.

### Test Case 3: `testLoadPowerThresholds_queriesPwrBikeAndValidates` (`TST-UI-219.3`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMathTest.kt`
* **Preconditions**: Mock `SettingsDataStoreJavaHelper.getZoneMax` with valid power values (e.g. 150, 200, 250, 300) vs 0.
* **Action**:
  1. Call `TelemetryZoneMath.loadPowerThresholds(context)` with valid mock.
  2. Call with invalid mock.
* **Expected Result**:
  1. Returns `PowerZoneThresholds(150, 200, 250, 300)`.
  2. Returns `null`.

### Test Case 4: `testFindNearestPoint_distanceDomain_matchesMinByOrNullParity` (`TST-UI-219.4`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtilsTest.kt`
* **Preconditions**: Monotonically sorted list of 1,000 synthetic `PathPoint` objects with realistic distance increments (0m to 25,000m).
* **Action**: Probe 500 arbitrary distance values (exact matches, mid-points, fractional values). Compare `TelemetryMetricUtils.findNearestPoint(points, target, isTimeDomain = false)` with `points.minByOrNull { abs(it.distance - target) }`.
* **Expected Result**: 100% identical point returned for every probe value.

### Test Case 5: `testFindNearestPoint_timeDomain_matchesMinByOrNullParity` (`TST-UI-219.5`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtilsTest.kt`
* **Preconditions**: Monotonically sorted list of `PathPoint` objects with elapsed time (0s to 3,600s).
* **Action**: Probe 200 time values comparing `findNearestPoint(points, target, isTimeDomain = true)` with `points.minByOrNull { abs(it.timeSec - target) }`.
* **Expected Result**: 100% identical point returned for every probe value.

### Test Case 6: `testFindNearestPoint_edgeCases_emptySingleAndBoundary` (`TST-UI-219.6`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtilsTest.kt`
* **Action & Expected Results**:
  - Empty list: returns `null`.
  - Single element: returns that element for any target.
  - Target < first point: returns first point.
  - Target > last point: returns last point.
  - Exact match on boundary: returns matching boundary point.

### Test Case 7: Clean-Room Full Suite Regression Execution (`TST-UI-219.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all unit tests in the repository.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-219.1` | Unit | `TelemetryZoneMath.loadHeartRateThresholds` | `REQ-UI-260` (1.2.1, AC-1) | Specified |
| `TST-UI-219.2` | Unit | `TelemetryZoneMath.loadHeartRateThresholds` | `REQ-UI-260` (1.2.1, AC-2) | Specified |
| `TST-UI-219.3` | Unit | `TelemetryZoneMath.loadPowerThresholds` | `REQ-UI-260` (1.2.1, AC-1, AC-2) | Specified |
| `TST-UI-219.4` | Unit | `TelemetryMetricUtils.findNearestPoint` (Distance) | `REQ-UI-260` (1.2.3, AC-3) | Specified |
| `TST-UI-219.5` | Unit | `TelemetryMetricUtils.findNearestPoint` (Time) | `REQ-UI-260` (1.2.3, AC-5) | Specified |
| `TST-UI-219.6` | Unit | `TelemetryMetricUtils.findNearestPoint` (Edge cases) | `REQ-UI-260` (1.2.3, AC-4) | Specified |
| `TST-UI-219.7` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
