# Stage 1 Analysis: ATT-4460 - [Verbesserung] Clean up unused smoothedBearings/calcWindowBearing dead code and sparse-vertex windowing in TurnCueDetector

**Ticket**: [ATT-4460](https://rainerblind.atlassian.net/browse/ATT-4460)  
**Sub-task**: [ATT-4471](https://rainerblind.atlassian.net/browse/ATT-4471) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Branch**: `feature/ATT-4460`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During code review of `ATT-3494` ([`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt)), two critical algorithmic inefficiencies and geometric limitations were identified:

1. **Unused Smoothing Step & Dead Code Allocation**:
   - In Step 3 (line 180), the detector allocates `val smoothedBearings = FloatArray(m - 1)`.
   - In Step 4 (lines 200–245), an expensive $O(m \cdot \text{envelope})$ loop computes a distance-weighted circular vector mean (`cos`, `sin`, `atan2`) across all route segments, writing the resulting angles into `smoothedBearings`.
   - Lines 248–264 define a local helper function `fun calcWindowBearing(fromIdx: Int, toIdx: Int): Float`.
   - In Step 5 (line 268), `val inBearings = FloatArray(m)` is allocated, and line 299 sets `inBearings[i] = bInShort`.
   - **Finding**: *None* of `smoothedBearings`, `calcWindowBearing`, or `inBearings` is ever read, referenced, or returned anywhere in Step 5 or Step 6. Instead, Step 5 computes raw chord bearings directly from coordinate vertices via `RouteAutoDetector.computeBearing(...)`. For long routes (e.g. 5,000–10,000 points), this dead computation needlessly consumes substantial CPU cycles (thousands of trigonometric calls) and allocates unneeded heap buffers.

2. **Sparse-Vertex Window Overshoot**:
   - Step 5 (lines 278–294) advances monotonic vertex index pointers (`prevShort`, `nextShort`, `prevLong`, `nextLong`) along the cumulative distance array:
     ```kotlin
     while (nextShort < m - 1 && (distances[nextShort] - dCur) < config.shortWindowMeters) {
         nextShort++
     }
     ```
   - If a polyline contains non-uniform or sparse vertex spacing—such as a short 5m corner segment immediately followed by a long 80m–150m straight or curving segment—the `while` loop steps `nextShort` to the distant vertex (e.g. at $d_{\text{cur}} + 100\text{ m}$) because the intermediate vertex at $d_{\text{cur}} + 5\text{ m}$ was less than `shortWindowMeters` (10m).
   - The outbound chord is then computed directly from vertex $i$ to vertex `nextShort` (at $d_{\text{cur}} + 100\text{ m}$), inflating the effective lookaround window by up to $10\times$.
   - **Geometric Failure Mode**: Drawing a chord over 100 meters dilutes and averages out local corner angles. For example, if a route turns 90° East at $d_{\text{cur}} + 5\text{ m}$ and then heads North for 95m, the 100m chord has $\Delta x = 5\text{ m}$ and $\Delta y = 95\text{ m}$, yielding a chord bearing of $\sim 3^\circ$. The deflection is calculated as $3^\circ \ll 25^\circ$, completely masking a genuine 90° turn and suppressing necessary turn guidance prompts.

Athletes navigating routes with sparse GPX trackpoints or mixed road/trail segment lengths need turn detection that strictly bounds lookaround chord windows to the configured distance envelope, eliminating dead CPU loops while accurately resolving sharp turns regardless of vertex density.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Dead Smoothing Loop & Array Allocations
A line-by-line inspection of [`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt) reveals why the dead code exists:
- In Sprint 2026-42.3 (`ATT-3494`), a circular vector mean smoothing step was prototyped in lines 200–245 to filter out polyline bearing noise before calculating deflection.
- However, during implementation, the developer found that direct multi-scale chords ($W_{\text{short}} = 10\text{ m}$, $W_{\text{long}} = 40\text{ m}$) paired with perpendicular displacement jitter filtering (lines 370–387) provided superior apex localization and noise rejection without smearing sharp intersection angles.
- Step 5 was implemented using raw chord bearings:
  ```kotlin
  val bInShort = if (prevShort >= i || isDepartureZone) rawBearings[maxOf(0, i - 1)] 
                 else RouteAutoDetector.computeBearing(sanitized[prevShort].latLng, sanitized[i].latLng)
  val bOutShort = if (nextShort <= i) rawBearings[minOf(m - 2, i)] 
                  else RouteAutoDetector.computeBearing(sanitized[i].latLng, sanitized[nextShort].latLng)
  ```
- The developer forgot to delete Step 4 (`smoothedBearings`, `calcWindowBearing`) and the unused buffer `inBearings`, leaving ~70 lines of dead computation executing on every route analysis.

### 2.2 Discrete Pointer Overshoot on Sparse Polylines
In Step 5, the lookaround window is intended to evaluate directional heading changes over a spatial neighborhood of $\pm W$ around vertex $i$ ($W_{\text{short}} = 10.0\text{ m}$, $W_{\text{long}} = 40.0\text{ m}$).

The existing pointer advance logic:
```kotlin
// Advance short window pointers
while (prevShort < i - 1 && (dCur - distances[prevShort + 1]) >= config.shortWindowMeters) {
    prevShort++
}
if (nextShort < i) nextShort = i
while (nextShort < m - 1 && (distances[nextShort] - dCur) < config.shortWindowMeters) {
    nextShort++
}
```
operates strictly in index space rather than continuous distance space:
1. **Inbound Chord Under-Windowing / Over-Windowing**:
   - `distances[prevShort + 1]` is tested against `shortWindowMeters`. When the loop terminates, `prevShort` stops at the last index where `distances[prevShort + 1] > dCur - shortWindowMeters`.
   - Therefore, `distances[prevShort]` lies *before* or at the window boundary $d_{\text{cur}} - W$.
   - If the segment `[prevShort, prevShort + 1]` is 80m long, `sanitized[prevShort]` is located 80m upstream rather than 10m upstream.
2. **Outbound Chord Window Overshoot**:
   - `nextShort` increments while `distances[nextShort] - dCur < shortWindowMeters`.
   - As soon as a vertex satisfies `distances[nextShort] - dCur >= shortWindowMeters`, the loop stops.
   - If that vertex is at $d_{\text{cur}} + 120\text{ m}$, `nextShort` lands on that vertex.
   - The chord `RouteAutoDetector.computeBearing(sanitized[i].latLng, sanitized[nextShort].latLng)` is computed across the entire 120m span instead of the configured 10m envelope.

Because GPX routes downloaded from Strava, Komoot, or Garmin often have sparse vertex spacing on straight or gently curved roads (points spaced 50m–200m apart) and dense points only at corners (5m spacing), the discrete vertex chords produce massive geometric distortion at transitions between dense and sparse segments.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Dead Code Cleanup**: Remove `smoothedBearings` array allocation, the Step 4 circular smoothing loop (lines 200–245), the nested `calcWindowBearing` function (lines 248–264), and the `inBearings` array allocation/write (lines 268, 299) from `TurnCueDetector.kt`.
  * **Exact Window Boundary Interpolation**: Bound and interpolate lookaround chord endpoints at exact spatial positions along the polyline segments spanning the window boundaries:
    - Inbound: $D_{\text{in}} = \max(0.0, d_{\text{cur}} - W)$ on segment `[prevIdx, prevIdx + 1]`.
    - Outbound: $D_{\text{out}} = \min(\text{totalDistance}, d_{\text{cur}} + W)$ on segment `[nextIdx - 1, nextIdx]`.
    - Apply consistently to both short window ($W_{\text{short}} = 10.0\text{ m}$) and long window ($W_{\text{long}} = 40.0\text{ m}$).
  * **Zero-Allocation Primitive Bearing Extraction**: Support primitive double coordinate calculation in bearing calculations to preserve the $O(N)$ zero-allocation runtime invariant.
  * **Sparse-Vertex Regression Unit Tests**: Add test cases in `TurnCueDetectorTest.kt` verifying turn cue detection accuracy on non-uniform and sparse polylines (e.g. 5m corner segments adjacent to 80m–150m straightaways).
  * **100% Green Existing Test Suite**: Verify that all existing unit tests in `TurnCueDetectorTest` (TST-MAP-045.1 to TST-MAP-045.18) and the full test suite pass without regression.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT alter public method signatures of `TurnCueDetector.extractOrDetectCues`, `detectGeometricCues`, or `detectGeometricCuesFromLatLng`.
  * Do NOT delete `smoothingRadiusMeters` from `TurnCueDetectorConfig` (preserve binary and source compatibility for external config instances).
  * Do NOT modify downstream navigation prompt handling (`TurnByTurnNavigationEngine.kt`, `TurnPromptBanner.kt`, `NavigationAudioAlertManager.kt`).
  * Do NOT change turn classification angular thresholds or `TurnDirection` enum mappings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-028` (Section 1: *Turn Cue Source & Geometric Synthesis*), targeting [`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt).
* **Historical Origin & Commit Trace**:
  - `ATT-1450`: Sprint 2026-40.14, Commit `54c5c2d3` / `26886443` (Initial turn-by-turn guidance and geometric detection).
  - `ATT-3494`: Sprint 2026-42.3, Commit `7aabbd0c` / `f5d30167` (Multi-scale sliding windows, apex localization, and circular smoothing specification).
* **Root Reason for Existing Formulation**:
  - `REQ-MAP-028` originally described *"pre-smoothing polyline bearings using a continuous distance-weighted spatial circular vector mean over a 5-meter envelope"*.
  - During `ATT-3494` implementation, multi-scale chord evaluation with perpendicular displacement jitter filtering was found to be significantly cleaner and more reliable for apex detection. The smoothing code was left in place but disconnected.
  - The discrete index pointer loop (`prevShort`, `nextShort`) was written assuming relatively uniform point spacing (~5m steps). On sparse or variable-pitch GPX tracks, the lack of boundary interpolation caused large chord overshoots.
* **Preservation of Core Invariants**:
  - Existing 18 ASPICE SWE.4 test cases (`TST-MAP-045.1` to `TST-MAP-045.18`) test uniform 5m grids, chicanes, 5,000-point performance benchmarks, start/finish boundaries, and jitter filtering. All MUST continue passing 100%.
  - Pure, stateless, thread-safe, and re-entrant execution invariant is preserved.
  - Zero-heap allocation during geometric processing invariant is maintained.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Removal of Dead Code in Step 4
In [`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt):
1. Remove `val smoothedBearings = FloatArray(m - 1)` (line 180).
2. Remove lines 200–245 (the distance-weighted spatial circular vector mean loop).
3. Remove lines 248–264 (`fun calcWindowBearing(fromIdx: Int, toIdx: Int): Float`).
4. Remove `val inBearings = FloatArray(m)` (line 268) and `inBearings[i] = bInShort` (line 299).

### 5.2 Continuous Distance Chord Boundary Interpolation
To evaluate chords at exact window boundaries without index overshoot:

1. **Segment Index Invariants**:
   - For any target distance $D$ along the route ($0.0 \le D \le \text{totalDistance}$):
     If $D$ falls on segment $[k, k+1]$ with $\text{distances}[k] \le D \le \text{distances}[k+1]$, the position along the segment is given by:
     $$t = \frac{D - \text{distances}[k]}{\text{distances}[k+1] - \text{distances}[k]}$$
     $$\text{lat} = \text{lat}_k + t \cdot (\text{lat}_{k+1} - \text{lat}_k)$$
     $$\text{lng} = \text{lng}_k + t \cdot (\text{lng}_{k+1} - \text{lng}_k)$$
2. **Inbound Chord Endpoint ($D_{\text{in}} = \max(0.0, d_{\text{cur}} - W)$)**:
   - When the existing monotonic loop advances `prevIdx`:
     ```kotlin
     while (prevIdx < i - 1 && (dCur - distances[prevIdx + 1]) >= windowMeters) {
         prevIdx++
     }
     ```
   - It is guaranteed that $\text{distances}[prevIdx] \le D_{\text{in}} \le \text{distances}[prevIdx + 1]$.
   - If `dCur - D_in <= 1e-4`, the chord collapses to the local segment bearing `rawBearings[maxOf(0, i - 1)]`.
   - If $prevIdx + 1 == i$, $D_{\text{in}}$ lies on segment $[i-1, i]$, so the bearing from $P(D_{\text{in}})$ to vertex $i$ is identically `rawBearings[i - 1]`.
   - If $prevIdx + 1 < i$, the interpolated coordinate $P(D_{\text{in}})$ on segment $[prevIdx, prevIdx + 1]$ is used as the chord start point.
3. **Outbound Chord Endpoint ($D_{\text{out}} = \min(\text{totalDistance}, d_{\text{cur}} + W)$)**:
   - When the monotonic loop advances `nextIdx`:
     ```kotlin
     if (nextIdx < i) nextIdx = i
     while (nextIdx < m - 1 && (distances[nextIdx] - dCur) < windowMeters) {
         nextIdx++
     }
     ```
   - It is guaranteed that $\text{distances}[nextIdx - 1] \le D_{\text{out}} \le \text{distances}[nextIdx]$.
   - If `D_out - dCur <= 1e-4`, the chord collapses to `rawBearings[minOf(m - 2, i)]`.
   - If $nextIdx == i + 1$, $D_{\text{out}}$ lies on segment $[i, i+1]$, so the bearing from vertex $i$ to $P(D_{\text{out}})$ is identically `rawBearings[i]`.
   - If $nextIdx > i + 1$, the interpolated coordinate $P(D_{\text{out}})$ on segment $[nextIdx - 1, nextIdx]$ is used as the chord end point.

### 5.3 Zero-Allocation Primitive Overload in `RouteAutoDetector`
To ensure zero object allocation during Step 5 loop iterations:
- Add a primitive coordinate overload:
  ```kotlin
  fun computeBearing(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Float
  ```
- Make `computeBearing(from: LatLng, to: LatLng)` delegate to the primitive overload.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Regression**: All existing 18 unit tests in `TurnCueDetectorTest.kt` must remain 100% green.
  2. **Zero Allocation**: Step 5 internal loop must not allocate heap objects (`LatLng`, arrays, or lambdas).
  3. **Strict Monotonicity & Thread Safety**: Stateless, pure function execution preserved.
  4. **Human Decision Gate**: Parent ticket progression to `Erledigt` is strictly reserved for the human user.

* **Risk Rating**: **LOW**
  - **Justification**: The change is a purely mathematical refinement within an isolated algorithmic utility ([`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt)) with comprehensive offline unit test coverage. There are no database migrations, background services, or UI state interactions affected.
