# Stage 3: Implementation Plan - ATT-2032: Unify X-Axis Domain & Time-vs-Distance Calculation Across Telemetry Components

**Ticket**: [ATT-2032](https://rainerblind.atlassian.net/browse/ATT-2032)  
**Sub-task**: [ATT-2244](https://rainerblind.atlassian.net/browse/ATT-2244) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-UI-261` (*Canonical Horizontal Domain Evaluation & Total Span Engine (`ProfileDomainMath`)*)  
**Test Mapping**: `TST-UI-220` (*Canonical Horizontal Domain Evaluation & Total Span Engine (`ProfileDomainMath`) Verification*)  
**Branch**: `feature/ATT-2032`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

In the Aftermath inspection module (`MapDetailLayout.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`), visual telemetry charts can be rendered along either the Spatial Domain (Distance in meters) or Temporal Domain (Elapsed Time in seconds). However, evaluating whether a chart should effectively render in the Time Domain versus Distance Domain, as well as calculating the maximum horizontal domain span (`totalSpan`), has become fragmented across three distinct composables with diverging edge-case rules.

Crucially:
- `TelemetryMetricGraph.kt` checked `xAxisDomain == ProfileXAxisDomain.TIME || isTrackless` without validating whether timestamps actually exist. If an athlete selects Time domain on a GPS track recorded without timestamps (`timeSec == 0`), `TelemetryMetricGraph` remained in Time domain, resulting in collapsed zero-span rendering and invalid `0:00` ticks. In contrast, `ElevationProfile.kt` fell back to Distance domain, causing stacked charts to render on mismatched X-axes.
- In `MapDetailLayout.kt`, `totalSpan` used for the global zoom toolbar and nested scroll container did not dynamically switch between `elevationTotalSpan` and `telemetryTotalSpan` when the elevation profile was disabled, leading to viewport mismatch on trackless workouts.
- Linear $O(N)$ searches (`minByOrNull`) remained in `TelemetryMetricGraph.kt` for cursor distance resolution, rather than utilizing the optimized binary search engine (`TelemetryMetricUtils.findNearestPoint`).

This plan defines the architectural extraction of `ProfileDomainMath` as the canonical domain and span engine, and the step-by-step refactoring of all consuming composables.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-261` (*Canonical Horizontal Domain Evaluation & Total Span Engine (`ProfileDomainMath`)*)
  - AC-1: Universal consumption of `ProfileDomainMath.isEffectiveTimeDomain`.
  - AC-2: Corrupt / zero-timestamp track safety with graceful fallback to distance domain.
  - AC-3: Trackless stationary workout parity (consistently resolving to time domain).
  - AC-4: Null and empty list safety (returning false / 0.0 without exceptions).
* **Test Mapping**: `TST-UI-220` (*Canonical Horizontal Domain Evaluation & Total Span Engine (`ProfileDomainMath`) Verification*)
  - Unit tests in `ProfileDomainMathTest.kt` verifying all permutations and edge cases.
  - Integration verification across existing 209 map unit tests.
  - Localization audit and clean-room full suite execution.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All 1550 existing unit tests must continue to pass without failure.
2. **Visual Parity**: Plot area paddings (`start = 50.dp, end = 25.dp`), line strokes, markers, and axis typography remain strictly unchanged.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ProfileDomainMath.kt` (New Domain Utility)
- Package: `com.atrainingtracker.trainingtracker.ui.map`
- Encapsulates:
  - `isTracklessWorkout(points: List<PathPoint>?): Boolean`
  - `isTracklessWorkout(totalDistance: Double, totalTimeSec: Long): Boolean`
  - `isEffectiveTimeDomain(domain: ProfileXAxisDomain, points: List<PathPoint>?): Boolean`
  - `isEffectiveTimeDomain(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Boolean`
  - `calculateTotalSpan(domain: ProfileXAxisDomain, points: List<PathPoint>?): Double`
  - `calculateTotalSpan(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Double`

### Component 2: `ElevationProfile.kt` (Refactored Composable)
- Replace ad-hoc `isTimeDomain` calculation with `ProfileDomainMath.isEffectiveTimeDomain`.
- Replace ad-hoc `totalSpan` calculation with `ProfileDomainMath.calculateTotalSpan`.

### Component 3: `TelemetryMetricGraph.kt` (Refactored Composable)
- Replace duplicated `isTrackless`, `isTimeDomain`, and `totalSpan` computations with `ProfileDomainMath`.
- Replace linear `pathPoints.minByOrNull { abs(it.distance - currentDistance) }` with `TelemetryMetricUtils.findNearestPoint`.

### Component 4: `MapDetailLayout.kt` (Refactored Layout)
- Replace manual boolean logic for `isTrackless`, `activeTelemetryDomain`, `isElevationTimeDomain`, `elevationTotalSpan`, `telemetryTotalSpan`, and `totalSpan` with direct calls to `ProfileDomainMath`.
- Ensure `totalSpan` cleanly switches based on whether `showElevationProfile` is true (using `elevationTotalSpan`) or false (using `telemetryTotalSpan`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `ProfileDomainMath.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ProfileDomainMath.kt`
* **Changes**: Implement pure, allocation-free domain and span math with comprehensive docstrings referencing `REQ-UI-261`.

### Step 2: Create Comprehensive Unit Test Suite
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ProfileDomainMathTest.kt`
* **Changes**: Author unit tests verifying:
  - Trackless workouts with 0 distance and positive time return true.
  - Outdoor workouts with positive distance and positive time return false for trackless.
  - Zero-time corrupt workouts return false for trackless and fall back to distance domain.
  - Null and empty lists return false and 0.0 span.
  - Total span calculation returns correct time or distance values.
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ProfileDomainMathTest"
  ```

### Step 3: Refactor `ElevationProfile.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* **Changes**:
  - Consume `ProfileDomainMath.isEffectiveTimeDomain(xAxisDomain, totalDistance = cachedData.totalDist, totalTimeSec = cachedData.totalTimeSec)`.
  - Consume `ProfileDomainMath.calculateTotalSpan(xAxisDomain, totalDistance = cachedData.totalDist, totalTimeSec = cachedData.totalTimeSec)`.

### Step 4: Refactor `TelemetryMetricGraph.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Changes**:
  - Replace `isTrackless` with `ProfileDomainMath.isTracklessWorkout(pathPoints)`.
  - Replace `isTimeDomain` with `ProfileDomainMath.isEffectiveTimeDomain(xAxisDomain, pathPoints)`.
  - Replace `totalSpan` with `ProfileDomainMath.calculateTotalSpan(xAxisDomain, pathPoints)`.
  - Replace `minByOrNull` linear scan in cursor positioning with `TelemetryMetricUtils.findNearestPoint`.

### Step 5: Refactor `MapDetailLayout.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* **Changes**:
  - Replace manual domain derivations with:
    - `isTrackless = ProfileDomainMath.isTracklessWorkout(activeScrubPath)`
    - `isElevationTimeDomain = ProfileDomainMath.isEffectiveTimeDomain(tuningConfig.elevationXAxisDomain, activeScrubPath)`
    - `isTelemetryTimeDomain = ProfileDomainMath.isEffectiveTimeDomain(tuningConfig.telemetryXAxisDomain, activeScrubPath)`
    - `elevationTotalSpan = ProfileDomainMath.calculateTotalSpan(tuningConfig.elevationXAxisDomain, activeScrubPath)`
    - `telemetryTotalSpan = ProfileDomainMath.calculateTotalSpan(tuningConfig.telemetryXAxisDomain, activeScrubPath)`
    - `totalSpan = if (showElevationProfile) elevationTotalSpan else telemetryTotalSpan`
  - Update `scrubbingOverlay` to resolve effective badge domain via `ProfileDomainMath`.

### Step 6: Verify Map Unit Test Suite
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"
  ```
* **Goal**: Ensure 100% pass rate across all 209+ tests in `ui.map.*`.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Step-by-step unit testing during construction.
  - Stage 5 clean-room regression test suite across the entire application (`./gradlew testDebugUnitTest`).
* **Rollback Plan**:
  - All modifications are strictly isolated to `feature/ATT-2032`. If any unforeseen regression occurs, discarding `feature/ATT-2032` restores `sprint/2026-40.14` to its exact verified state without side effects.
