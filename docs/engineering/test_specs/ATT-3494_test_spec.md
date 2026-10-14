# Stage 2: Requirement & Test Specification - ATT-3494: Improve turn cue detection algorithm and curvature classification along route polylines

**Ticket**: [ATT-3494](https://rainerblind.atlassian.net/browse/ATT-3494)  
**Sub-task**: [ATT-3498](https://rainerblind.atlassian.net/browse/ATT-3498) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts*)  
**Test Spec ID**: `TST-MAP-045`  
**Branch**: `feature/ATT-3494`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13 (Revised per Gate 2 Auditor Scrutiny v3)  

---

## 1. Requirement Specification (Amending REQ-MAP-028 Clause 1)

### 1.1 Problem Statement & Rationale
During route navigation testing, the legacy single-chord geometric turn detection heuristic in `TurnCueDetector.kt` evaluates heading changes over a static 15-meter window (`WINDOW_LOOKAROUND_METERS = 15.0m`). This induces three primary failure modes:
1. **Sweeping Turn Blind Spots**: Gradual $90^\circ$ or $180^\circ$ direction changes distributed across 30m–80m exhibit low point-to-point chord deltas ($< 30^\circ$) at each 15m step, causing real intersection maneuvers to be missed entirely.
2. **Spurious Cues on Curvy Paths**: Unfiltered GPS micro-jitter or sharp single-vertex kinks on winding roads trigger false *"Halb rechts"* (`SLIGHT_RIGHT`) or *"Halb links"* (`SLIGHT_LEFT`) prompts when the athlete should simply follow the main path.
3. **Suboptimal Apex Anchoring**: In extended curves, cues are pinned to the first threshold-crossing vertex rather than the true geometric apex (point of maximum curvature rate $\max |\frac{d\theta}{ds}|$), distorting countdown distances and audio chime timing.

Athletes require an intelligent multi-scale detection algorithm that captures sharp intersection corners and sweeping curves alike, smooths heading sequences over distance without mutating coordinates, suppresses natural road curvature, anchors cues at the true apex, and classifies turn severity deterministically.

---

### 1.2 Functional & Architectural Requirements

1. **Non-Destructive Distance-Weighted Spatial Heading Smoothing**:
   * The detector SHALL NOT decimate or alter polyline coordinates via coordinate decimation algorithms (RDP decimation is strictly prohibited).
   * Successive polyline segment bearings $\theta_i$ SHALL be pre-smoothed using a **distance-weighted spatial moving average** over a $\pm 2.5\text{m}$ distance envelope ($5\text{m}$ total span along cumulative polyline distance $s$). To guarantee true sample-rate and point-density independence across variable GPS frequencies, each segment $k$ within the envelope is weighted by its physical segment length $\Delta s_k$:
     $(\bar{x}, \bar{y}) = (\sum_{k} \Delta s_k \cos \theta_k, \sum_{k} \Delta s_k \sin \theta_k)$, and $\bar{\theta} = \text{atan2}(\bar{y}, \bar{x})$.
   * **Continuous Spatial Transition & Sparse Fallback**: The distance-weighted circular vector mean is mathematically continuous across all segment densities. When polyline vertex spacing is coarse ($\Delta s_k \ge 5\text{m}$), the window contains a single segment of weight $\Delta s_k$, whose weighted mean $(\Delta s_k \cos \theta_k, \Delta s_k \sin \theta_k)$ naturally evaluates to $\text{atan2}(\sin \theta_k, \cos \theta_k) \equiv \theta_k$ (the raw chord bearing) with zero transition discontinuity. The only fallback occurs when total window length $S_{\text{total}} = \sum \Delta s_k \le 10^{-4}\text{m}$ (degenerate coincident trackpoints), where the filter adopts the forward chord bearing to prevent $\text{atan2}(0,0)$ or division-by-zero.
   * **Sharp Corner Preservation Justification**: The $\pm 2.5\text{m}$ smoothing envelope acts strictly as a low-pass filter on micro-jitter ($\pm 1\text{--}3\text{m}$). At a sharp $90^\circ$ street corner, physical turning radius spans $\ge 5\text{m}$. The $5\text{m}$ smoothing preserves the macro $90^\circ$ deflection across the $12\text{--}15\text{m}$ evaluation window ($W_{\text{short}}$) without smoothing out the corner, anchoring the apex within $\pm 2.5\text{m}$ of the physical intersection.

2. **Multi-Scale Sliding Window Evaluation & Linear $O(N)$ Throughput**:
   * Curvature deflection SHALL be evaluated using two forward-advancing sliding window iterators:
     * **Short Window** ($W_{\text{short}} = 12\text{--}15\text{m}$): Captures sharp, localized intersection turns ($|\Delta \theta| \ge 25^\circ$).
     * **Long Window** ($W_{\text{long}} = 35\text{--}50\text{m}$): Captures sweeping curves and roundabouts where point-to-point deltas are gradual ($< 25^\circ$) but cumulative heading deflection satisfies $\sum \Delta \theta \ge 50^\circ$.
   * Sliding window pointers SHALL advance monotonically forward without nested scans, guaranteeing single-pass $O(N)$ execution time.
   * **Zero Heap Allocation Invariant (Internal Processing)**: The zero-heap-allocation constraint applies strictly to internal processing buffers and the detection loop; instantiating the returned `List<TurnCue>` DTO elements is the only permitted heap allocation. All intermediate buffers (`smoothedBearings: FloatArray`, `distances: DoubleArray`) SHALL be pre-allocated once at the start of `detectGeometricCues` sized to `path.size`. Hot-loop iterations SHALL navigate the polyline strictly using primitive `Int` index loops (`while (i < size)` or `for (i in 0 until size)`), avoiding Kotlin `Iterator`, `forEach`, `map`, `filter`, lambda captures, or boxed `Float`/`Double` allocations.

3. **Deterministic Apex Anchoring, Tie-Breaking & 5° Hysteresis**:
   * For any detected turn maneuver, the turn cue coordinate (`latLng`, `distanceFromStart`) SHALL anchor at the vertex with the maximum local curvature rate $\max |\frac{d\theta}{ds}|$.
   * **Deterministic Apex Tie-Breaking**: For constant-curvature arcs spanning vertex index range $[i_{\text{start}}, i_{\text{end}}]$ with $K = i_{\text{end}} - i_{\text{start}} + 1$ candidate peak vertices sharing the maximum curvature rate within the deduplication window, apex selection SHALL deterministically pick index $i_{\text{apex}} = i_{\text{start}} + \lfloor (K - 1) / 2 \rfloor$ (integer division selecting the lower median vertex for even vertex counts, e.g. for $K=10$ spanning $0..9 \to \text{index } 4$, for $K=11$ spanning $0..10 \to \text{index } 5$). This computation is strictly $O(1)$ arithmetic evaluated during the single forward pass with zero secondary scans.
   * Cues SHALL maintain a minimum separation distance of $30\text{m}$ (`MIN_SEPARATION_METERS = 30.0`).
   * **5° Apex Hysteresis Buffer**: When evaluating a subsequent vertex within the $30\text{m}$ deduplication window, the candidate vertex SHALL ONLY replace an already-recorded cue's apex position if its local curvature deflection strictly exceeds the recorded peak by at least $5^\circ$ ($|\Delta \theta_{\text{candidate}}| \ge |\Delta \theta_{\text{recorded}}| + 5.0^\circ$). This prevents position fluttering caused by micro-fluctuations along uniform curves.

4. **Curvature Classification & Noise Suppression**:
   * Road curvature below $25^\circ$ ($|\Delta \theta| < 25^\circ$) SHALL be suppressed as straight road alignment (zero spurious cues).
   * Valid turns SHALL be classified based on net heading deflection:
     * **Slight Turn**: $25^\circ \le |\Delta \theta| < 60^\circ$ (`SLIGHT_RIGHT` / `SLIGHT_LEFT`).
     * **Regular Turn**: $60^\circ \le |\Delta \theta| < 120^\circ$ (`RIGHT` / `LEFT`).
     * **Sharp Turn**: $120^\circ \le |\Delta \theta| < 155^\circ$ (`SHARP_RIGHT` / `SHARP_LEFT`).
     * **U-Turn / Hairpin**: $|\Delta \theta| \ge 155^\circ$ (`U_TURN`). Hairpin switchbacks and $180^\circ$ U-turns SHALL emit exactly one consolidated `U_TURN` cue, suppressing redundant secondary cues.

5. **Boundary Protection & Short Route Guard**:
   * The detector SHALL clamp boundaries so that zero turn cues are generated within the first $10\text{m}$ from route start or within the final $10\text{m}$ of route end.
   * **Short Route Guard**: If the total polyline length is $< 20\text{m}$ or `path.size < 3`, `detectGeometricCues` SHALL immediately return `emptyList()` ($O(1)$ short-circuit guard), safely preventing boundary inversion or out-of-bounds indexing.
   * For polylines containing over 5,000 track points, execution SHALL complete in $< 100\text{ms}$ on standard mobile hardware.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-028` (Section 1: *Turn Cue Source & Geometric Synthesis*), targeting `TurnCueDetector.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-1450`, sprint `2026-40.14`, commit `54c5c2d3` (target release `V4.9.39`, Epic `ATT-66` / `ATT-2564`).
* **Root Reason for Existing Formulation**: The initial implementation used a single fixed 15-meter window chord heuristic ($|\Delta \theta| \ge 30^\circ$) to provide a minimal viable turn generator for city street corners. However, fixed 15m chords are blind to sweeping 40–80m curves, trigger spurious prompts on winding roads, and misanchor cues at the first threshold vertex rather than the true geometric apex.
* **Preservation of Core Invariants**:
  * Pre-embedded course points (`WaypointCategory.TURN_CUE`) continue to take absolute precedence over geometric polyline detection.
  * The `TurnCue` DTO schema and strictly monotonic `distanceFromStart` ordering are preserved 100%.
  * **Caller Navigation Safety & Timing Preservation**: Because cue generation executes statically upon route activation and anchors cues to immutable polyline distance offsets, refining apex positioning does NOT alter the runtime distance thresholds (150m for `APPROACHING`, 25m for `TURN_NOW`) or the state transition timing in `TurnByTurnNavigationEngine`, strictly preserving caller safety contracts.
  * Downstream navigation lifecycle in `TurnByTurnNavigationEngine` (`APPROACHING`, `TURN_NOW`, `PASSED`) remains unchanged.
  * Offline calculation sovereignty and full-suite test pass rate are maintained.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Sharp 90-Degree Intersection Turn**:
  * *Given* a route polyline with an abrupt 90-degree corner at an intersection,
  * *When* evaluated by `TurnCueDetector.detectGeometricCues`,
  * *Then* exactly 1 turn cue of type `RIGHT` or `LEFT` SHALL be detected and anchored within 5 meters of the corner apex.

* **Criterion 2: Sweeping 90-Degree Curve Detection**:
  * *Given* a gradual 90-degree turn distributed across 45 meters with point-to-point chords $< 20^\circ$,
  * *When* evaluated by `TurnCueDetector.detectGeometricCues`,
  * *Then* the multi-scale detector SHALL identify the turn via the long window ($W_{\text{long}}$) and emit a single `RIGHT` or `LEFT` cue at the apex.

* **Criterion 3: Winding Road Curvature Noise Suppression**:
  * *Given* a winding road path with continuous gentle bends ($< 25^\circ$),
  * *When* evaluated,
  * *Then* zero false turn cues SHALL be emitted.

* **Criterion 4: Circular Arc Midpoint Anchoring**:
  * *Given* a uniform circular arc with constant curvature,
  * *When* evaluated,
  * *Then* apex tie-breaking SHALL deterministically anchor the cue at the median midpoint vertex of the curve.

* **Criterion 5: Hairpin U-Turn Consolidation**:
  * *Given* a 180-degree mountain switchback / hairpin turn ($|\Delta \theta| \ge 155^\circ$),
  * *When* evaluated,
  * *Then* exactly one `TurnDirection.U_TURN` cue SHALL be emitted, suppressing secondary cues.

* **Criterion 6: High-Frequency GPS Jitter Smoothing & Sparse Fallback**:
  * *Given* a straight road segment contaminated with synthetic $\pm 3\text{m}$ high-frequency GPS zig-zag noise,
  * *When* evaluated,
  * *Then* the distance-weighted spatial heading moving average SHALL filter out the jitter with zero spurious turn cues. When points are spaced $> 5\text{m}$, the filter safely evaluates raw chords without division-by-zero.

* **Criterion 7: Short Route Clamping Safety**:
  * *Given* a short route polyline of length $< 20\text{m}$ or fewer than 3 trackpoints,
  * *When* evaluated,
  * *Then* `TurnCueDetector.detectGeometricCues` SHALL immediately return an empty list without throwing index or range exceptions.

* **Criterion 8: Execution Performance on Large Polylines**:
  * *Given* a complex route polyline with 5,000 path points,
  * *When* calculating turn cues,
  * *Then* execution SHALL complete in $< 100\text{ms}$ with zero heap allocation churn.

---

### 1.5 System Invariants

1. **Pre-Embedded Turn Waypoint Sovereignty**:
   * If a route contains explicit waypoints with `category == WaypointCategory.TURN_CUE`, `extractOrDetectCues` MUST return those waypoints verbatim without running geometric synthesis.
2. **DTO Compatibility & Monotonic Distance**:
   * Output `List<TurnCue>` MUST remain strictly sorted by ascending `distanceFromStart` ($d_0 < d_1 < \dots < d_k$).
3. **Downstream Engine Immutability & Timing Invariant**:
   * `TurnByTurnNavigationEngine`, `TurnPromptBanner`, and `NavigationAudioAlertManager` contracts MUST NOT be altered. Cue countdown timing in `TurnByTurnNavigationEngine` remains mathematically anchored to route distance without jitter.
4. **Offline Purity**:
   * `TurnCueDetector` MUST operate 100% in-memory with zero disk, network, or external dependencies.

---

## 2. Test Specification (TST-MAP-045)

### Test Case 1: `testDetectGeometricCues_sharp90DegreeIntersection_detectsSingleCueAtApex` (`TST-MAP-045.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Polyline starting North for 100m, turning sharply East for 100m.
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path)`.
* **Expected Result**: Exactly 1 cue returned with `direction == TurnDirection.RIGHT` and `distanceFromStart == 100.0m` ($\pm 5\text{m}$).

### Test Case 2: `testDetectGeometricCues_sweepingCurve_detectsSingleTurnCue` (`TST-MAP-045.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Polyline entering a sweeping 90° curve over 45m with 8 intermediate points (chord angles $\approx 11^\circ$).
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path)`.
* **Expected Result**: Exactly 1 cue returned with `direction == TurnDirection.RIGHT` anchored at the curve apex.

### Test Case 3: `testDetectGeometricCues_windingRoadGentleBends_suppressesNoise` (`TST-MAP-045.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Polyline with S-curves having localized angles of $15^\circ - 20^\circ$.
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path)`.
* **Expected Result**: Empty list of cues (`cues.isEmpty() == true`).

### Test Case 4: `testDetectGeometricCues_constantCurvatureArc_anchorsAtMidpoint` (`TST-MAP-045.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Circular arc with 11 equidistant points of identical curvature rate.
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path)`.
* **Expected Result**: Cue is placed deterministically at index 5 (the median midpoint vertex).

### Test Case 5: `testDetectGeometricCues_hairpinUTurn_detectsSingleUTurnCue` (`TST-MAP-045.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Polyline heading North, executing a $180^\circ$ hairpin reversal over 20m, then heading South.
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path)`.
* **Expected Result**: Exactly 1 cue of type `TurnDirection.U_TURN`, zero duplicate cues.

### Test Case 6: `testDetectGeometricCues_noisyZigZagPolyline_spatialSmoothingFiltersJitter` (`TST-MAP-045.6`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Straight path sampled at high spatial frequency ($\Delta s = 2\text{m}$, representing 1–2 Hz GPS sampling at typical running/cycling speeds) with synthetic alternating lateral displacement of $\pm 3\text{m}$ ($+3\text{m}, -3\text{m}, +3\text{m}, \dots$) at each vertex.
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path)`.
* **Expected Result**: 0 cues detected (heading jitter completely suppressed by 5m spatial distance-weighted vector smoothing).

### Test Case 7: `testDetectGeometricCues_shortRouteUnder20m_returnsEmptyListSafely` (`TST-MAP-045.7`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Short polyline with total distance 15m (below 20m threshold).
* **Action**: Invoke `TurnCueDetector.detectGeometricCues(path)`.
* **Expected Result**: Empty list returned safely without IndexOutOfBoundsException or range violations.

### Test Case 8: `testDetectGeometricCues_5000Points_completesUnder100ms` (`TST-MAP-045.8`)
* **Scope**: Unit Test / Benchmark
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Synthetic 5,000-point route polyline with mixed straights, curves, and turns.
* **Action**: Measure execution duration of `TurnCueDetector.detectGeometricCues(path)`.
* **Expected Result**: Duration $< 100\text{ms}$ with zero heap allocation churn.

### Test Case 9: Clean-Room Full-Suite Regression (`TST-MAP-045.9`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Method / Artifact | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-045.1]` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-028` (Clause 1) | Specified |
| `[TST-MAP-045.2]` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-028` (Clause 1) | Specified |
| `[TST-MAP-045.3]` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-028` (Clause 1) | Specified |
| `[TST-MAP-045.4]` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-028` (Clause 1) | Specified |
| `[TST-MAP-045.5]` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-028` (Clause 1) | Specified |
| `[TST-MAP-045.6]` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-028` (Clause 1) | Specified |
| `[TST-MAP-045.7]` | Unit | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-028` (Clause 1) | Specified |
| `[TST-MAP-045.8]` | Benchmark | `TurnCueDetector.detectGeometricCues` | `REQ-MAP-028` (Clause 1) | Specified |
| `[TST-MAP-045.9]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
