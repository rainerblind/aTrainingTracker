# Stage 3: Implementation Plan - ATT-4460: [Verbesserung] Clean up unused smoothedBearings/calcWindowBearing dead code and sparse-vertex windowing in TurnCueDetector

**Ticket**: [ATT-4460](https://rainerblind.atlassian.net/browse/ATT-4460)  
**Sub-task**: [ATT-4473](https://rainerblind.atlassian.net/browse/ATT-4473) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-MAP-054` (`REQ-MAP-054.1`, `REQ-MAP-054.2`, `REQ-MAP-054.3`, `REQ-MAP-054.4`), refining `REQ-MAP-028`  
**Test Mapping**: `TST-MAP-057` (`TST-MAP-057.1`, `TST-MAP-057.2`, `TST-MAP-057.3`, `TST-MAP-057.4`, `TST-MAP-057.5`, `TST-MAP-057.6`)  
**Branch**: `feature/ATT-4460`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During whole-app architectural code review of [`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt) (introduced in `ATT-1450` and refined in `ATT-3494`), two critical algorithmic inefficiencies and geometric limitations were identified:

1. **Unused Smoothing Step & Dead Code Allocations**:
   - In Step 3 (line 180), `TurnCueDetector` pre-allocated `smoothedBearings = FloatArray(m - 1)`.
   - In Step 4 (lines 200–245), an expensive $O(m \cdot \text{envelope})$ loop computed a continuous distance-weighted circular vector mean (`cos`, `sin`, `atan2`) across all segments, writing into `smoothedBearings`.
   - Lines 248–264 defined local helper `fun calcWindowBearing(fromIdx: Int, toIdx: Int): Float`.
   - In Step 5 (line 268), `inBearings = FloatArray(m)` was allocated and written to on line 299.
   - None of `smoothedBearings`, `calcWindowBearing`, or `inBearings` is ever read, referenced, or returned downstream. Step 5 computes raw chord bearings directly from coordinate vertices. For long routes (5,000–10,000 points), thousands of dead trigonometric operations needlessly consume CPU cycles and battery during route import and analysis.

2. **Sparse-Vertex Window Overshoot**:
   - In Step 5, monotonic index pointers (`prevShort`, `nextShort`, `prevLong`, `nextLong`) advanced strictly in vertex index space:
     ```kotlin
     while (nextShort < m - 1 && (distances[nextShort] - dCur) < config.shortWindowMeters) {
         nextShort++
     }
     ```
   - On polylines with non-uniform or sparse trackpoint spacing (e.g. Strava/Garmin tracks with 5m corner steps transitioning to 80m–150m straightaways), the lookaround chord from vertex $i$ to `nextShort` was drawn over the entire 80m–150m segment rather than bounding to the configured 10m envelope.
   - Drawing chords over 80m–150m severely dilutes corner angles (e.g., turning a 90° turn into a 3° chord deflection), causing genuine sharp turns to be suppressed.

Under the aligned Sprint Planning 2026-43.4 agreement:
- Step 4 dead code and unreferenced array allocations (`smoothedBearings`, `calcWindowBearing`, `inBearings`) SHALL be excised from `TurnCueDetector.kt`.
- Lookaround chord endpoints for both short ($W_{\text{short}} = 10.0\text{ m}$) and long ($W_{\text{long}} = 40.0\text{ m}$) windows SHALL be bounded and continuously interpolated at exact distance envelope boundaries ($d_{\text{cur}} \pm W$) along spanning polyline segments.
- `RouteAutoDetector.kt` SHALL provide a primitive coordinate bearing overload (`fun computeBearing(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Float`) so Step 5 executes with zero heap object allocation.
- Comprehensive unit tests covering sparse vertex spacing SHALL be added to `TurnCueDetectorTest.kt` while preserving 100% green status across all 18 existing test cases (`TST-MAP-045.1` – `TST-MAP-045.18`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-054` (*TurnCueDetector Dead Code Cleanup, Unused Circular Smoothing Removal & Continuous Chord Window Boundary Interpolation Architecture*)
  * `REQ-MAP-054.1`: Excise Unused Smoothing Buffers & Dead Code Allocations (`TurnCueDetector.kt`) — remove `smoothedBearings`, Step 4 loop, `calcWindowBearing`, and `inBearings`; retain `TurnCueDetectorConfig.smoothingRadiusMeters: Double = 2.5` for binary/source compatibility.
  * `REQ-MAP-054.2`: Continuous Distance Chord Window Boundary Interpolation (`TurnCueDetector.kt`) — evaluate inbound target $D_{\text{in}} = \max(0.0, d_{\text{cur}} - W)$ and outbound target $D_{\text{out}} = \min(\text{totalDistance}, d_{\text{cur}} + W)$ along spanning segments with continuous linear coordinate interpolation.
  * `REQ-MAP-054.3`: Zero Heap Object Allocation Invariant & Primitive Coordinate Bearing Overload (`RouteAutoDetector.kt`, `TurnCueDetector.kt`) — provide primitive double bearing overload and consume it in Step 5 without allocating `LatLng` instances.
  * `REQ-MAP-054.4`: Preservation of Public API Contracts & Downstream Invariants — immutability of public detector signatures, downstream navigation engines (`TurnByTurnNavigationEngine`), UI prompts (`TurnPromptBanner`), and 100% full-suite regression pass.
* **Test Mapping**: `TST-MAP-057` (`TST-MAP-057.1` – `TST-MAP-057.6`)
  * `TST-MAP-057.1`: Sparse-Vertex 90-Degree Corner Detection Unit Test (`TurnCueDetectorTest.kt`)
  * `TST-MAP-057.2`: Sparse Inbound Segment Boundary Interpolation Unit Test (`TurnCueDetectorTest.kt`)
  * `TST-MAP-057.3`: Dead Code Elimination & Buffer Retirement Contract Test (`TurnCueDetectorTest.kt`)
  * `TST-MAP-057.4`: Zero-Allocation Primitive Coordinate Bearing Overload Unit Test (`RouteAutoDetectorTest.kt`)
  * `TST-MAP-057.5`: 5,000-Point Performance & Zero Heap Allocation Benchmark Test (`TurnCueDetectorTest.kt`)
  * `TST-MAP-057.6`: Full Existing Test Suite Regression Pass (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Regression**: All existing 18 ASPICE SWE.4 unit tests in `TurnCueDetectorTest.kt` (`TST-MAP-045.1` – `TST-MAP-045.18`) must remain 100% green.
2. **Zero Allocation**: The Step 5 internal loop must not allocate heap objects (`LatLng`, arrays, or lambdas).
3. **Public API Immutability**: Existing public method signatures of `TurnCueDetector.extractOrDetectCues`, `detectGeometricCues`, `detectGeometricCuesFromLatLng`, and `TurnCueDetectorConfig` must remain strictly unchanged.
4. **Configuration Compatibility**: `TurnCueDetectorConfig.smoothingRadiusMeters` must be preserved with default value `2.5` to maintain binary and source compatibility for external configuration callers.
5. **Pure, Stateless Execution**: Thread-safe and re-entrant processing without mutable state.
6. **Code Lock Until Gate 3 Passes (Rule 4)**: No production source code in `app/src/main/...` shall be created or edited until `check-gate` passes on this plan.
7. **Parent Human Gate Invariance (Rule 3)**: Terminal completion of parent ticket `ATT-4460` is strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

```
+--------------------------------------------------------------------------------------------------+
| com.atrainingtracker.trainingtracker.routes.RouteAutoDetector                                    |
+--------------------------------------------------------------------------------------------------+
| + computeBearing(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Float     |
| + computeBearing(from: LatLng, to: LatLng): Float  [delegates to primitive overload]             |
+--------------------------------------------------------------------------------------------------+
                                                 ^
                                                 | calls directly (zero heap allocations)
+--------------------------------------------------------------------------------------------------+
| com.atrainingtracker.trainingtracker.routes.TurnCueDetector                                      |
+--------------------------------------------------------------------------------------------------+
| [Excised]: smoothedBearings FloatArray, Step 4 smoothing loop, calcWindowBearing, inBearings     |
|                                                                                                  |
| Step 5: Continuous Chord Boundary Interpolation:                                                 |
|   Din = max(0.0, dCur - W)                                                                       |
|     * If prevIdx + 1 < i: interpolate P(Din) on segment [prevIdx, prevIdx + 1]                   |
|     * If prevIdx + 1 == i or dCur - Din <= 1e-4: collapse to rawBearings[i - 1]                  |
|   Dout = min(totalDistance, dCur + W)                                                            |
|     * If nextIdx > i + 1: interpolate P(Dout) on segment [nextIdx - 1, nextIdx]                  |
|     * If nextIdx == i + 1 or Dout - dCur <= 1e-4: collapse to rawBearings[i]                     |
|                                                                                                  |
| Public APIs & Config unchanged:                                                                  |
|   + extractOrDetectCues(route, config): List<TurnCue>                                            |
|   + detectGeometricCues(path, config): List<TurnCue>                                             |
|   + detectGeometricCuesFromLatLng(coordinates, config): List<TurnCue>                            |
|   + TurnCueDetectorConfig(..., smoothingRadiusMeters = 2.5, ...)                                 |
+--------------------------------------------------------------------------------------------------+
```

### Component 1: `RouteAutoDetector.kt` (Primitive Coordinate Bearing Overload)
- **Layer**: Routing Utilities (`com.atrainingtracker.trainingtracker.routes`)
- **Modification**:
  - Add primitive coordinate overload:
    ```kotlin
    fun computeBearing(
        lat1Deg: Double,
        lon1Deg: Double,
        lat2Deg: Double,
        lon2Deg: Double
    ): Float {
        val lat1 = Math.toRadians(lat1Deg)
        val lon1 = Math.toRadians(lon1Deg)
        val lat2 = Math.toRadians(lat2Deg)
        val lon2 = Math.toRadians(lon2Deg)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360f) % 360f
    }
    ```
  - Update `computeBearing(from: LatLng, to: LatLng): Float` to delegate:
    ```kotlin
    fun computeBearing(from: LatLng, to: LatLng): Float =
        computeBearing(from.latitude, from.longitude, to.latitude, to.longitude)
    ```

### Component 2: `TurnCueDetector.kt` (Dead Code Removal & Exact Distance Windowing)
- **Layer**: Routing Utilities (`com.atrainingtracker.trainingtracker.routes`)
- **Modifications**:
  1. Remove `val smoothedBearings = FloatArray(m - 1)` (former line 180).
  2. Remove Step 4 distance-weighted circular vector mean smoothing loop (former lines 200–245).
  3. Remove nested helper function `fun calcWindowBearing(fromIdx: Int, toIdx: Int): Float` (former lines 248–264).
  4. Remove `val inBearings = FloatArray(m)` (former line 268) and `inBearings[i] = bInShort` (former line 299).
  5. Preserve `smoothingRadiusMeters: Double = 2.5` in `TurnCueDetectorConfig`.
  6. Refactor Step 5 chord calculation with continuous distance interpolation:
     - For target distance $D$ on segment $[k, k + 1]$:
       $$t = \frac{D - \text{distances}[k]}{\text{distances}[k + 1] - \text{distances}[k]}$$
       $$\text{lat} = \text{sanitized}[k].\text{latLng}.\text{latitude} + t \cdot (\text{sanitized}[k + 1].\text{latLng}.\text{latitude} - \text{sanitized}[k].\text{latLng}.\text{latitude})$$
       $$\text{lng} = \text{sanitized}[k].\text{latLng}.\text{longitude} + t \cdot (\text{sanitized}[k + 1].\text{latLng}.\text{longitude} - \text{sanitized}[k].\text{latLng}.\text{longitude})$$
     - Evaluate inbound short chord $D_{\text{inShort}} = \max(0.0, d_{\text{cur}} - \text{config.shortWindowMeters})$:
       * If $d_{\text{cur}} \le \text{config.startDepartureWindowMeters}$ or $prevShort + 1 == i$ or $d_{\text{cur}} - D_{\text{inShort}} \le 10^{-4}$:
         $bInShort = \text{rawBearings}[\max(0, i - 1)]$.
       * Else:
         Evaluate interpolated coordinates $(lat_{\text{in}}, lon_{\text{in}})$ on segment $[prevShort, prevShort + 1]$.
         $bInShort = \text{RouteAutoDetector.computeBearing}(lat_{\text{in}}, lon_{\text{in}}, p_i.\text{latitude}, p_i.\text{longitude})$.
     - Evaluate outbound short chord $D_{\text{outShort}} = \min(\text{totalDistance}, d_{\text{cur}} + \text{config.shortWindowMeters})$:
       * If $nextShort == i + 1$ or $D_{\text{outShort}} - d_{\text{cur}} \le 10^{-4}$:
         $bOutShort = \text{rawBearings}[\min(m - 2, i)]$.
       * Else:
         Evaluate interpolated coordinates $(lat_{\text{out}}, lon_{\text{out}})$ on segment $[nextShort - 1, nextShort]$.
         $bOutShort = \text{RouteAutoDetector.computeBearing}(p_i.\text{latitude}, p_i.\text{longitude}, lat_{\text{out}}, lon_{\text{out}})$.
     - Apply identical continuous distance interpolation for long window chords ($bInLong$, $bOutLong$) using $W_{\text{long}} = \text{config.longWindowMeters}$.

### UI Consistency (Rule 23)
* **Status**: No UI changes. `TurnCueDetector` is a pure mathematical domain calculation utility operating on route trackpoints.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check & Baseline Verification (Rule 4)
* Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.
* Confirm exit code `0` before modifying any production code.
* Run baseline test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TurnCueDetectorTest"
  ```

### Step 2: Add Primitive Bearing Overload to `RouteAutoDetector.kt` & Unit Test (`REQ-MAP-054.3`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteAutoDetector.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteAutoDetectorTest.kt`
* **Changes**:
  - Implement `computeBearing(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Float`.
  - Update `computeBearing(from: LatLng, to: LatLng): Float` to delegate.
  - In `RouteAutoDetectorTest.kt`, add `testRouteAutoDetector_computeBearingPrimitiveOverload_matchesLatLng` (`TST-MAP-057.4`) asserting identical outputs within $0.001^\circ$ across cardinal and diagonal directions.
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.RouteAutoDetectorTest"
  ```

### Step 3: Remove Dead Smoothing Code and Array Allocations from `TurnCueDetector.kt` (`REQ-MAP-054.1`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt`
* **Changes**:
  - Delete `val smoothedBearings = FloatArray(m - 1)` (former line 180).
  - Delete Step 4 loop (former lines 200–245).
  - Delete `calcWindowBearing` helper (former lines 248–264).
  - Delete `val inBearings = FloatArray(m)` and assignment `inBearings[i] = bInShort` (former lines 268, 299).
  - Verify `TurnCueDetectorConfig.smoothingRadiusMeters` remains defined with default value `2.5`.

### Step 4: Implement Continuous Distance Chord Window Boundary Interpolation (`REQ-MAP-054.2`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt`
* **Changes**:
  - Implement continuous coordinate interpolation for inbound and outbound chords across short ($10\text{m}$) and long ($40\text{m}$) windows.
  - Route all chord bearing calculations through primitive `RouteAutoDetector.computeBearing(lat1, lon1, lat2, lon2)`.
  - Ensure zero heap object allocation during Step 5 loop.

### Step 5: Add Sparse-Vertex & Contract Unit Tests to `TurnCueDetectorTest.kt` (`TST-MAP-057.1`, `TST-MAP-057.2`, `TST-MAP-057.3`, `TST-MAP-057.5`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Changes**:
  - Add `testDetectGeometricCues_sparseVertexOutboundSegment_interpolatesExactBoundary` (`TST-MAP-057.1`).
  - Add `testDetectGeometricCues_sparseVertexInboundSegment_interpolatesExactBoundary` (`TST-MAP-057.2`).
  - Add `testTurnCueDetector_deadCodeAndBuffersRetirementContract` (`TST-MAP-057.3`).
  - Add `testDetectGeometricCues_performanceBenchmark5000Points` (`TST-MAP-057.5`).
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TurnCueDetectorTest"
  ```

### Step 6: Full Clean-Room Test Suite Verification (`TST-MAP-057.6`)
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Goal**: Confirm 100% pass rate across the full project test suite with zero regressions.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Phase 1: Targeted unit test execution during Stage 4 (`RouteAutoDetectorTest`, `TurnCueDetectorTest`).
  - Phase 2: Clean-room test suite execution during Stage 5 (`testDebugUnitTest`).
* **Rollback Safety**:
  - Pure stateless domain logic changes isolated on `feature/ATT-4460`.
  - No database schema migrations, preferences modifications, or background service interactions.
  - Full branch revert (`git checkout sprint/2026-43.4`) completely restores original state without side effects.
