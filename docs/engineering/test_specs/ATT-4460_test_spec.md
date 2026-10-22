# Stage 2: Requirement & Test Specification - ATT-4460: [Verbesserung] Clean up unused smoothedBearings/calcWindowBearing dead code and sparse-vertex windowing in TurnCueDetector

**Ticket**: [ATT-4460](https://rainerblind.atlassian.net/browse/ATT-4460)  
**Sub-task**: `[Req & Test Spec]` (To be created via `submit-stage`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-MAP-054`, `REQ-MAP-028`  
**Test Spec ID**: `TST-MAP-057` (`TST-MAP-057.1` – `TST-MAP-057.6`)  
**Branch**: `feature/ATT-4460`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-MAP-054, REQ-MAP-028)

### 1.1 Problem Statement & Rationale
During whole-app architectural code review of `TurnCueDetector.kt` (introduced in `ATT-1450` and refined in `ATT-3494`), two algorithmic inefficiencies and geometric limitations were identified:

1. **Unused Smoothing Step & Dead Code Allocation**:
   - `TurnCueDetector` pre-allocated `smoothedBearings = FloatArray(m - 1)` (former line 180).
   - In Step 4 (former lines 200–245), an expensive $O(m \cdot \text{envelope})$ loop calculated a continuous distance-weighted circular vector mean (`cos`, `sin`, `atan2`) across all route segments.
   - Former lines 248–264 defined helper `fun calcWindowBearing(fromIdx: Int, toIdx: Int): Float`.
   - In Step 5 (former line 268), `inBearings = FloatArray(m)` was allocated and written to on line 299.
   - None of `smoothedBearings`, `calcWindowBearing`, or `inBearings` is ever read, referenced, or returned downstream. Chords are computed directly from coordinate vertices. For long routes (5,000–10,000 points), thousands of dead trigonometric operations needlessly consume CPU cycles and battery during route import and processing.

2. **Sparse-Vertex Window Overshoot**:
   - In Step 5, monotonic index pointers (`prevShort`, `nextShort`, `prevLong`, `nextLong`) advanced in vertex index space:
     ```kotlin
     while (nextShort < m - 1 && (distances[nextShort] - dCur) < config.shortWindowMeters) {
         nextShort++
     }
     ```
   - On polylines with non-uniform or sparse trackpoint spacing (e.g. Strava/Garmin tracks with 5m corner steps transitioning to 80m–150m straightaways), the lookaround chord from vertex $i$ to `nextShort` was drawn over the entire 80m–150m segment rather than bounding to the configured 10m envelope.
   - Drawing chords over 80m–150m severely dilutes corner angles (e.g., turning a 90° turn into a 3° chord deflection), causing genuine sharp turns to be suppressed.

Athletes navigating routes with variable vertex density require exact distance envelope windowing with zero dead code CPU overhead.

### 1.2 Functional & Architectural Requirements

#### 1.2.1 Dead Code & Unused Smoothing Removal (`TurnCueDetector.kt`, `REQ-MAP-054.1`)
1. Remove `val smoothedBearings = FloatArray(m - 1)` array allocation.
2. Remove Step 4 distance-weighted circular vector mean smoothing loop.
3. Remove nested helper function `fun calcWindowBearing(fromIdx: Int, toIdx: Int): Float`.
4. Remove `val inBearings = FloatArray(m)` array allocation and assignment `inBearings[i] = bInShort`.
5. Retain `smoothingRadiusMeters: Double = 2.5` in `TurnCueDetectorConfig` with its default value to preserve binary and source compatibility for external callers.

#### 1.2.2 Continuous Distance Chord Window Boundary Interpolation (`TurnCueDetector.kt`, `REQ-MAP-054.2`)
1. Lookaround chords for both short window ($W_{\text{short}} = 10.0\text{ m}$) and long window ($W_{\text{long}} = 40.0\text{ m}$) SHALL be evaluated at exact distance boundaries ($d_{\text{cur}} \pm W$):
   - **Inbound boundary target** $D_{\text{in}} = \max(0.0, d_{\text{cur}} - W)$ on segment $[prevIdx, prevIdx + 1]$:
     * When $prevIdx + 1 < i$, start point coordinate $P(D_{\text{in}})$ is interpolated linearly along segment $[prevIdx, prevIdx + 1]$ at fraction $t = (D_{\text{in}} - \text{distances}[prevIdx]) / (\text{distances}[prevIdx + 1] - \text{distances}[prevIdx])$:
       $$\text{lat} = \text{lat}_{prevIdx} + t \cdot (\text{lat}_{prevIdx + 1} - \text{lat}_{prevIdx})$$
       $$\text{lng} = \text{lng}_{prevIdx} + t \cdot (\text{lng}_{prevIdx + 1} - \text{lng}_{prevIdx})$$
       and the chord bearing is evaluated from $P(D_{\text{in}})$ to vertex $i$.
     * When $prevIdx + 1 == i$ or $d_{\text{cur}} - D_{\text{in}} \le 10^{-4}\text{ m}$, the chord collapses to segment bearing `rawBearings[i - 1]`.
   - **Outbound boundary target** $D_{\text{out}} = \min(\text{totalDistance}, d_{\text{cur}} + W)$ on segment $[nextIdx - 1, nextIdx]$:
     * When $nextIdx > i + 1$, end point coordinate $P(D_{\text{out}})$ is interpolated linearly along segment $[nextIdx - 1, nextIdx]$ at fraction $t = (D_{\text{out}} - \text{distances}[nextIdx - 1]) / (\text{distances}[nextIdx] - \text{distances}[nextIdx - 1])$:
       $$\text{lat} = \text{lat}_{nextIdx - 1} + t \cdot (\text{lat}_{nextIdx} - \text{lat}_{nextIdx - 1})$$
       $$\text{lng} = \text{lng}_{nextIdx - 1} + t \cdot (\text{lng}_{nextIdx} - \text{lng}_{nextIdx - 1})$$
       and the chord bearing is evaluated from vertex $i$ to $P(D_{\text{out}})$.
     * When $nextIdx == i + 1$ or $D_{\text{out}} - d_{\text{cur}} \le 10^{-4}\text{ m}$, the chord collapses to segment bearing `rawBearings[i]`.

#### 1.2.3 Zero-Allocation Primitive Overload in `RouteAutoDetector` (`REQ-MAP-054.3`)
1. In `RouteAutoDetector.kt`, add primitive coordinate overload:
   ```kotlin
   fun computeBearing(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Float
   ```
2. Existing overload `computeBearing(from: LatLng, to: LatLng): Float` SHALL delegate to the primitive overload without changing behavior.
3. The Step 5 loop in `TurnCueDetector` SHALL invoke the primitive overload directly with primitive double coordinates, ensuring zero heap allocations (`LatLng` instances) per loop iteration.

#### 1.2.4 Preservation of Public API Contracts & Downstream Invariants (`REQ-MAP-054.4`)
1. Public method signatures of `TurnCueDetector.extractOrDetectCues`, `detectGeometricCues`, and `detectGeometricCuesFromLatLng` remain strictly unchanged.
2. Downstream navigation engine (`TurnByTurnNavigationEngine.kt`), UI prompt banners (`TurnPromptBanner.kt`), audio alerts (`NavigationAudioAlertManager.kt`), and turn angle classification thresholds (`classifyTurn`) remain strictly unchanged.
3. All 18 existing ASPICE SWE.4 unit tests in `TurnCueDetectorTest.kt` (`TST-MAP-045.1` – `TST-MAP-045.18`) MUST continue passing 100%.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - Refines `REQ-MAP-028` (Section 1: *Turn Cue Source & Geometric Synthesis*).
  - Targets `TurnCueDetector.kt`, `RouteAutoDetector.kt`, and `TurnCueDetectorTest.kt`.
* **Historical Origin & Commit Trace**:
  - Ticket `ATT-1450` (Sprint `2026-40.14`, commit `54c5c2d3`: initial geometric detection and turn synthesis).
  - Ticket `ATT-3494` (Sprint `2026-42.3`, commit `7aabbd0c`: multi-scale sliding windows, apex localization, and circular vector smoothing specification).
* **Root Reason for Existing Formulation**:
  - In `ATT-3494`, a distance-weighted spatial circular vector mean smoothing step was prototyped to filter polyline bearing jitter before computing deflection angles. During implementation, multi-scale chords ($W_{\text{short}} = 10\text{ m}$, $W_{\text{long}} = 40\text{ m}$) coupled with perpendicular displacement jitter filtering provided superior apex localization and noise rejection. Step 4 smoothing was disconnected from Step 5 chords but left in the source code as dead execution.
  - Furthermore, the monotonic index loop was implemented assuming relatively dense, uniform trackpoints (~5m steps). When traversing sparse polylines with 80m–150m straightaways adjacent to tight corners, the discrete vertex loop stepped to distant points, inflating lookaround chords by up to $10\times$ and washing out genuine corner deflections.
* **Preservation of Core Invariants**:
  - Public API compatibility: Method signatures and return types are completely unaltered.
  - Configuration compatibility: `TurnCueDetectorConfig.smoothingRadiusMeters` is preserved.
  - Pure execution invariant: Stateless, re-entrant, thread-safe function execution is preserved.
  - Zero heap allocation invariant: Internal primitive buffers and primitive coordinate bearing calculations guarantee zero garbage collection pressure during route analysis.
  - Test suite stability: All 18 existing test cases in `TurnCueDetectorTest` pass with 100% fidelity.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Sparse-Vertex 90-Degree Turn Detection)**:
  * *Given* a polyline with a sharp 90-degree corner having a short 5m approach and an 80m–150m long straightaway,
  * *When* `TurnCueDetector.detectGeometricCues` is evaluated,
  * *Then* the outbound chord endpoint is interpolated at exact boundary $d_{\text{cur}} + 10.0\text{ m}$ along the long segment, detecting the corner accurately as `TurnDirection.RIGHT` / `LEFT` at the true vertex apex without angle dilution.

* **Criterion 2 (Sparse-Vertex Inbound Segment Interpolation)**:
  * *Given* a polyline with an 80m approach straightaway followed by a sharp 90-degree corner,
  * *When* `TurnCueDetector.detectGeometricCues` is evaluated,
  * *Then* the inbound chord endpoint is interpolated at exact boundary $d_{\text{cur}} - 10.0\text{ m}$ along the approach segment, preventing over-windowing.

* **Criterion 3 (Dead Code Elimination & Buffer Retirement)**:
  * *Given* route processing in `TurnCueDetector`,
  * *When* inspected,
  * *Then* zero CPU cycles are spent computing unused circular vector mean smoothed bearings, and neither `smoothedBearings` nor `inBearings` arrays are allocated.

* **Criterion 4 (Zero-Allocation Primitive Coordinate Bearing Overload)**:
  * *Given* `RouteAutoDetector.computeBearing(lat1, lon1, lat2, lon2)`,
  * *When* evaluated across cardinal and diagonal directions,
  * *Then* it returns identical angular bearings to `computeBearing(LatLng, LatLng)` within $0.001^\circ$ precision with zero heap object allocations.

* **Criterion 5 (5,000-Point Performance Benchmark)**:
  * *Given* a 5,000-point synthetic route,
  * *When* processed by `detectGeometricCues`,
  * *Then* execution completes in $< 100\text{ ms}$ with zero allocation churn during internal window iterations.

* **Criterion 6 (Full Existing Test Suite Regression Pass)**:
  * *Given* the 18 existing test cases in `TurnCueDetectorTest.kt` (`TST-MAP-045.1` to `TST-MAP-045.18`),
  * *When* executed via `./gradlew testDebugUnitTest`,
  * *Then* 100% of tests pass without regression.

---

### 1.5 System Invariants

1. **Zero Regression**: All existing 18 test cases in `TurnCueDetectorTest.kt` must remain 100% green.
2. **Zero Allocation**: Step 5 internal loop must not allocate heap objects (`LatLng`, arrays, or lambdas).
3. **Public API Immutability**: Existing public method signatures of `TurnCueDetector` and `TurnCueDetectorConfig` must not change.
4. **Pure, Stateless Execution**: Thread-safe and re-entrant processing without mutable state.
5. **Human Decision Gate**: Progression to `Erledigt` is strictly reserved for the human user.

---

## 2. Test Specification (TST-MAP-057)

### Test Case 1: `testDetectGeometricCues_sparseVertexOutboundSegment_interpolatesExactBoundary` (`TST-MAP-057.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**:
  - Polyline: 30m North, 5m East (sharp 90° right turn), followed by 100m East (sparse straightaway).
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path, TurnCueDetectorConfig())`.
* **Expected Result**:
  - Exactly 1 turn cue detected at the 90° corner apex.
  - `cue.direction == TurnDirection.RIGHT`.
  - Cue position anchored at the corner apex ($D \approx 30.0\text{ m}$).

### Test Case 2: `testDetectGeometricCues_sparseVertexInboundSegment_interpolatesExactBoundary` (`TST-MAP-057.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**:
  - Polyline: 100m North (sparse approach straightaway), 5m East (sharp 90° right turn), followed by 30m East.
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path, TurnCueDetectorConfig())`.
* **Expected Result**:
  - Exactly 1 turn cue detected at the corner apex.
  - `cue.direction == TurnDirection.RIGHT`.
  - Inbound chord start point interpolated at $d_{\text{cur}} - 10\text{ m}$ along the approach segment.

### Test Case 3: `testTurnCueDetector_deadCodeAndBuffersRetirementContract` (`TST-MAP-057.3`)
* **Scope**: Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: `TurnCueDetector.kt` source on disk.
* **Action**: Inspect source content and `TurnCueDetectorConfig` reflection.
* **Expected Result**:
  - Zero occurrences of `smoothedBearings`, `calcWindowBearing`, or `inBearings`.
  - `TurnCueDetectorConfig().smoothingRadiusMeters` exists and equals `2.5`.

### Test Case 4: `testRouteAutoDetector_computeBearingPrimitiveOverload_matchesLatLng` (`TST-MAP-057.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteAutoDetectorTest.kt`
* **Preconditions**: Coordinates representing North, East, South, West, and diagonal bearings.
* **Action**: Compare `computeBearing(lat1, lon1, lat2, lon2)` with `computeBearing(LatLng(lat1, lon1), LatLng(lat2, lon2))`.
* **Expected Result**: Output bearings match within `0.001f` across all quadrants.

### Test Case 5: `testDetectGeometricCues_performanceBenchmark5000Points` (`TST-MAP-057.5`)
* **Scope**: Benchmark Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Route with 5,000 synthetic trackpoints and multiple turns.
* **Action**: Measure execution time of `detectGeometricCues`.
* **Expected Result**: Completes in $< 100\text{ ms}$ with zero memory churn.

### Test Case 6: Clean-Room Regression Suite (`TST-MAP-057.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-057.1` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-054.2` | Specified |
| `TST-MAP-057.2` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-054.2` | Specified |
| `TST-MAP-057.3` | Contract | `TurnCueDetector.kt` | `REQ-MAP-054.1` | Specified |
| `TST-MAP-057.4` | Unit | `RouteAutoDetector.computeBearing` | `REQ-MAP-054.3` | Specified |
| `TST-MAP-057.5` | Benchmark | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-054.3` | Specified |
| `TST-MAP-057.6` | Regression | Full Gradle test suite | `REQ-MAP-054.4`, `REQ-PRO-001` | Specified |
