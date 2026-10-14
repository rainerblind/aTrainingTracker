# Stage 3: Implementation Plan - ATT-3494: Improve turn cue detection algorithm and curvature classification along route polylines

**Ticket**: [ATT-3494](https://rainerblind.atlassian.net/browse/ATT-3494)  
**Sub-task**: [ATT-3499](https://rainerblind.atlassian.net/browse/ATT-3499) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts*, Clause 1)  
**Test Mapping**: `TST-MAP-045` (`TST-MAP-045.1` – `TST-MAP-045.9`)  
**Branch**: `feature/ATT-3494`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

In `TurnCueDetector.kt`, the existing polyline turn generation heuristic evaluates heading changes over a single, static 15-meter window (`WINDOW_LOOKAROUND_METERS = 15.0m`). This simple chord heuristic exhibits three major flaws:
1. **Sweeping Turn Blind Spots**: Gradual turns and roundabouts distributed over 30m–80m produce small localized point-to-point deltas ($< 30^\circ$) at each 15m step, causing real intersections and direction changes to be missed entirely.
2. **Spurious Cues on Curvy Roads**: Micro-jitter from GPS fixes and natural road curvature trigger false *"Halb rechts"* (`SLIGHT_RIGHT`) or *"Halb links"* (`SLIGHT_LEFT`) prompts on winding paths.
3. **Suboptimal Apex Anchoring**: In extended curves, cues are pinned to the first threshold-crossing vertex rather than the true geometric apex (point of maximum curvature rate $\max |\frac{d\theta}{ds}|$), distorting countdown distances and audio chime timing.

This implementation plan defines the exact mathematical construction and architectural refactoring of `TurnCueDetector.kt` to deliver robust, multi-scale, noise-filtered turn cue detection with linear $O(N)$ execution and zero heap object churn during hot-loop processing.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-028` (Clause 1: *Turn Cue Source & Geometric Synthesis*)
  - Clause 1.1: Non-destructive distance-weighted spatial heading smoothing over $\pm 2.5\text{m}$ ($5\text{m}$ envelope) without RDP coordinate decimation, with continuous spatial transition and degenerate chord fallback ($S_{\text{total}} \le 10^{-4}\text{m}$).
  - Clause 1.2: Multi-scale forward sliding windows ($W_{\text{short}} = 12\text{--}15\text{m}$, $W_{\text{long}} = 35\text{--}50\text{m}$) in single-pass linear $O(N)$ sweep with zero per-point heap object allocations in internal processing loops.
  - Clause 1.3: Deterministic apex anchoring at $\max |\frac{d\theta}{ds}|$, $O(1)$ tie-breaking formula $i_{\text{apex}} = i_{\text{start}} + \lfloor (K - 1) / 2 \rfloor$ for constant-curvature arcs, 30m deduplication separation, and $5^\circ$ hysteresis buffer.
  - Clause 1.4: Curvature classification thresholds (Slight: $25^\circ \le |\Delta \theta| < 60^\circ$, Regular: $60^\circ \le |\Delta \theta| < 120^\circ$, Sharp: $120^\circ \le |\Delta \theta| < 155^\circ$, U-Turn: $|\Delta \theta| \ge 155^\circ$), with single consolidated `U_TURN` cue emission.
  - Clause 1.5: Short route guard ($< 20\text{m}$ or $< 3$ points returns `emptyList()`), 10m start/end boundary clamping, and $< 100\text{ms}$ execution for 5,000 trackpoints.
* **Test Mapping**: `TST-MAP-045`
  - `[TST-MAP-045.1]`: Sharp 90° intersection turn detected at apex within $\pm 5\text{m}$.
  - `[TST-MAP-045.2]`: Sweeping 90° curve over 45m detected via long window.
  - `[TST-MAP-045.3]`: Winding road gentle bends ($< 25^\circ$) suppressed.
  - `[TST-MAP-045.4]`: Constant-curvature arc midpoint apex tie-breaking.
  - `[TST-MAP-045.5]`: 180° hairpin switchback consolidated into single `U_TURN` cue.
  - `[TST-MAP-045.6]`: High-frequency GPS jitter ($\pm 3\text{m}$ alternating) filtered with 0 false cues.
  - `[TST-MAP-045.7]`: Short route ($< 20\text{m}$) safely returns empty list without exception.
  - `[TST-MAP-045.8]`: 5,000-point synthetic route completes in $< 100\text{ms}$.
  - `[TST-MAP-045.9]`: Chicane / rapid opposite-direction turns at exactly 15m separation preserves both cues.
  - `[TST-MAP-045.10]`: Boundary condition testing at exactly 20.0m route length.
  - `[TST-MAP-045.11]`: Straight route with continuous $\pm 2\text{m}$ lateral GPS jitter produces 0 false cues.
  - `[TST-MAP-045.12]`: Polyline with zero-length duplicate segments ($\Delta s < 10^{-4}\text{m}$) sanitizes cleanly and detects turns accurately.
  - `[TST-MAP-045.13]`: Threshold oscillation ($24.9^\circ \dots 25.1^\circ$) suppresses stuttering/flickering duplicate cues.
  - `[TST-MAP-045.14]`: High-frequency lateral GPS jitter test ($\pm 2\text{m}$ perpendicular noise on straight path) emits 0 cues.
  - `[TST-MAP-045.15]`: Closed loop route (start and end within 10m) suppresses phantom cues at start/finish line.
  - `[TST-MAP-045.16]`: Clean-room full test suite regression pass (`./gradlew testDebugUnitTest`).
  - `[TST-MAP-045.17]`: Immediate start-of-route turn (turn at 2m departure clamped to 0.0m immediate prompt).
  - `[TST-MAP-045.18]`: Threshold oscillation hysteresis test ($24.8^\circ \to 25.2^\circ \to 24.9^\circ \to 25.1^\circ$ suppressed by $5^\circ$ hysteresis buffer).

---

## 3. System Invariants & Preserved Behavior

1. **Pre-Embedded Turn Waypoint Priority**: If a route contains explicit waypoints with category `WaypointCategory.TURN_CUE`, `extractOrDetectCues` MUST return those waypoints verbatim without invoking geometric synthesis.
2. **DTO & Monotonicity Invariant**: Output `List<TurnCue>` MUST remain strictly sorted by ascending `distanceFromStart` ($d_0 < d_1 < \dots < d_k$).
3. **Downstream Safety & Timing Preservation**: Cue generation executes statically upon route activation and anchors cues to immutable polyline distance offsets. Velocity-aware prompt timing is managed dynamically by `TurnByTurnNavigationEngine` (`APPROACHING` at 150m, `TURN_NOW` at 25m). Static apex placement does NOT alter these downstream safety thresholds.
4. **Zero RDP Coordinate Decimation**: RDP decimation is 100% eliminated and prohibited. All computations execute directly on the original raw polyline coordinates, guaranteeing zero coordinate shift or spatial apex offset.
5. **Internal Zero Heap Allocation Invariant & Bounded Footprint**: Instantiating returned `List<TurnCue>` DTO elements is the only permitted heap allocation. All intermediate buffers (`smoothedBearings: FloatArray`, `distances: DoubleArray`, `segmentLengths: DoubleArray`, `rawBearings: FloatArray`) are sized once to `path.size` ($\approx 120\text{ KB}$ heap footprint for 5,000 points, zero stack overflow risk). Hot-loop iterations navigate strictly via primitive `Int` index loops (`while` or `for (i in 0 until size)`), completely avoiding Kotlin `Iterator`, `forEach`, `map`, `filter`, `Pair`, or lambda closures.
6. **Stateless Purity & Concurrency**: `TurnCueDetector` operates as a 100% pure, stateless Kotlin `object`. It contains zero class-level state or background caches. All accumulators (`val cues = ArrayList<TurnCue>()`) and working arrays are allocated locally on the method call stack/heap frame, guaranteeing complete re-entrancy and thread safety without side effects.

---

## 4. UI Consistency (Rule 23)

* `TurnCueDetector` is a pure mathematical calculation engine residing in the domain layer (`com.atrainingtracker.trainingtracker.routes`). It does not define, render, or alter any Jetpack Compose UI composables directly.
* However, it preserves complete contract compatibility with `TurnDirection` enum entries (`LEFT`, `RIGHT`, `SLIGHT_LEFT`, `SLIGHT_RIGHT`, `SHARP_LEFT`, `SHARP_RIGHT`, `STRAIGHT`, `U_TURN`), ensuring all icons and display titles rendered in `TurnPromptBanner.kt` conform to `REQ-UI-324` and `REQ-UI-360`.

---

## 5. Architectural Decomposition (SWE.2)

### Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt`

#### 5.1 Public Interface Contract & Method Signatures
```kotlin
object TurnCueDetector {
    /**
     * Extracts pre-embedded waypoints with category WaypointCategory.TURN_CUE,
     * or synthesizes geometric turn cues from polyline coordinates if none exist.
     */
    fun extractOrDetectCues(
        route: RouteWithPath,
        config: TurnCueDetectorConfig = TurnCueDetectorConfig()
    ): List<TurnCue>

    /**
     * Synthesizes turn cues from an ordered list of [PathPoint] trackpoints.
     * Pure, stateless, thread-safe, and re-entrant.
     */
    fun detectGeometricCues(
        path: List<PathPoint>,
        config: TurnCueDetectorConfig = TurnCueDetectorConfig()
    ): List<TurnCue>

    /**
     * Synthesizes turn cues directly from raw [LatLng] geographic coordinates.
     * Pure, stateless, thread-safe, and re-entrant.
     */
    fun detectGeometricCuesFromLatLng(
        coordinates: List<LatLng>,
        config: TurnCueDetectorConfig = TurnCueDetectorConfig()
    ): List<TurnCue>
}
```

#### 5.2 Formalized Configuration (`TurnCueDetectorConfig`) & Geodesic Projection
* **Formalized Configuration Object**: All kinematic, windowing, and angular thresholds are consolidated into `TurnCueDetectorConfig`, decoupling algorithm logic from magic numbers and facilitating empirical parameter tuning:
```kotlin
data class TurnCueDetectorConfig(
    val shortWindowMeters: Double = 14.0,
    val longWindowMeters: Double = 40.0,
    val smoothingRadiusMeters: Double = 2.5,
    val minSeparationMeters: Double = 30.0,
    val minChicaneSeparationMeters: Double = 15.0,
    val startDepartureWindowMeters: Double = 10.0,
    val finishWindowMeters: Double = 10.0,
    val minRouteLengthMeters: Double = 20.0,
    val minPointsCount: Int = 3,
    val thresholdSlightDeg: Float = 25.0f,
    val thresholdRegularDeg: Float = 60.0f,
    val thresholdSharpDeg: Float = 120.0f,
    val thresholdUTurnDeg: Float = 155.0f,
    val hysteresisBufferDeg: Float = 5.0f,
    val apexMinSpatialShiftMeters: Double = 1.0,
    val spikeMaxLegMeters: Double = 5.0,
    val spikeDeflectionThresholdDeg: Float = 165.0f
)
```
* **Geodesic Projection & Bearing Drift Analysis**:
  - **Geodesic Distance**: Spherical Haversine geodesic distance with mean Earth radius $R = 6,371,000.0\text{m}$, exactly matching `RouteAutoDetector.computeDistanceMeters` and Android's `Location.distanceBetween`.
  - **Geodesic Forward Azimuth**: Great-circle initial forward bearing formula ($\theta = \text{atan2}(\sin \Delta \lambda \cos \phi_2, \cos \phi_1 \sin \phi_2 - \sin \phi_1 \cos \phi_2 \cos \Delta \lambda)$), matching `RouteAutoDetector.computeBearing`.
  - **Zero Bearing Drift Proof**: For any sub-segment $\Delta s \le 50.0\text{m}$, the great-circle convergence $/ \text{drift}$ between initial forward azimuth and final azimuth is bounded by:
    $$\Delta \theta_{\text{drift}} \le \frac{\Delta s}{R} \sin(\phi) \le \frac{50\text{m}}{6.371 \times 10^6\text{m}} \approx 7.8 \times 10^{-6}\text{ rad} \approx 0.00045^\circ$$
    This is five orders of magnitude below the $25.0^\circ$ turn threshold and well within single-precision floating point epsilon, proving that initial forward azimuth introduces zero drift across window evaluation chords.

#### 5.3 Thread-Safety, Concurrency & Memory Footprint
* `TurnCueDetector` is a stateless Kotlin `object`. It contains zero mutable class-level properties or shared state.
* The output accumulator `val cues = ArrayList<TurnCue>()` is allocated as a local variable inside the call frame of `detectGeometricCues`. It is populated during the single forward sweep and returned as an immutable `List<TurnCue>` (`cues.toList()`).
* All flat primitive arrays (`distances`, `segmentLengths`, `rawBearings`, `smoothedBearings`) are allocated locally within the stack frame of `detectGeometricCues` on the JVM heap. For a 5,000-point route, total memory is $(2 \times 8 + 2 \times 4) \times 5000 \approx 120\text{ KB}$, with only object references on the JVM execution stack. This eliminates stack overflow risks and makes multi-threaded invocations completely re-entrant without synchronization locks.

#### 5.4 Pre-Filtering Coordinate Sanitization & Multipath Outlier Rejection
* Trackpoints in real GPS polylines occasionally include duplicate consecutive coordinates ($\Delta s < 10^{-4}\text{m}$), micro-dips from device pauses, or multipath rebound spikes.
* **Pre-Filtering Sanitization Pass**:
  1. **Duplicate Elimination**: Consecutive points with geodesic distance $\Delta s < 10^{-4}\text{m}$ are explicitly skipped from active calculation indices. Every recorded segment in the working arrays satisfies $\Delta s_k \ge 10^{-4}\text{m}$.
  2. **Single-Pass Multipath Rebound Spike Rejection**: If point $p_i$ exhibits a sudden acute reversal ($|\Delta \theta| > 165^\circ$) with leg lengths $< 5.0\text{m}$ ($\Delta s_{i-1, i} < 5\text{m}$ and $\Delta s_{i, i+1} < 5\text{m}$) where the subsequent segment immediately returns within $15^\circ$ of the incoming corridor heading, $p_i$ is flagged as a multipath GPS spike and skipped. The filter executes in a single linear forward scan without recursive index backtracking, avoiding infinite loops.
* **Sub-Threshold Route Handling**: If a route polyline is composed entirely of stationary/duplicate points such that the sanitized point count $m < 3$ or total distance $< 20.0\text{m}$ (e.g. 50 points at the exact same coordinate), `detectGeometricCues` safely returns `emptyList()` immediately without throwing an exception.
* This guarantees:
  1. No zero-length segments enter the spatial smoothing window, preventing weighted mean skew or division-by-zero.
  2. Bearings are strictly calculated between geographically distinct coordinates, eliminating `atan2(0,0)`, `NaN`, or `Infinity`.

#### 5.5 Lateral GPS Jitter & Distance-Weighted Spatial Heading Smoothing
* Lateral GPS jitter produces high-frequency zig-zag oscillations perpendicular to the path direction.
* To completely eliminate phantom curvature spikes from lateral jitter:
  * Segment bearings $\theta_i$ are smoothed using a distance-weighted spatial circular vector mean over a 5m envelope ($[s_i - 2.5\text{m}, s_i + 2.5\text{m}]$):
    $$w_k = \Delta s_k, \quad \bar{x} = \sum w_k \cos(\text{radians}(\theta_k)), \quad \bar{y} = \sum w_k \sin(\text{radians}(\theta_k))$$
  * Because lateral noise alternates symmetrically around the forward trajectory over 2–5m intervals, the orthogonal components cancel out in the vector sum $(\bar{x}, \bar{y})$, attenuating high-frequency heading spikes well below $10^\circ \ll 25^\circ$.
  * If $S_{\text{total}} = \sum w_k \le 10^{-4}\text{m}$, $\text{smoothedBearings}[i] = \text{rawBearings}[i]$.
  * Else, $\text{smoothedBearings}[i] = \text{toDegrees}(\text{atan2}(\bar{y}, \bar{x}))$.

#### 5.6 Multi-Scale Sliding Windows & Start/Finish Boundary Clamping
* **Proof of Linear $O(N)$ Monotonicity**: Window pointers $j_{\text{short}}$ and $j_{\text{long}}$ only advance forward ($j \leftarrow j + 1$) as vertex $i$ increments. They never backtrack. Across the entire traversal of $N$ points, $j_{\text{short}}$ and $j_{\text{long}}$ each advance at most $N$ times. The amortized complexity per vertex is $O(1)$, guaranteeing strictly linear $O(N)$ execution time ($< 20\text{ms}$ for 5,000 points).
* **Start-of-Route Departure Window Clamping ($[0, 10\text{m}]$) & Engine Compatibility**:
  - If a legitimate turn occurs within the departure window ($d_{\text{apex}} \le 10.0\text{m}$, e.g. at 2m or 5m pulling out of an intersection or driveway), its cue distance is pinned to **$0.0\text{m}$** (`distanceFromStart = 0.0m`, coordinate = `path.first().latLng`).
  - **Engine Compatibility**: In `TurnByTurnNavigationEngine`, at navigation start ($d = 0.0\text{m}$), `distToCue` initializes to `0.0m`, immediately triggering `isApproaching = true` and `isTurnNow = true`. `remainingCues` retains cues where `distanceFromStart >= (currentDist - 20.0)`, so the prompt is actively maintained from 0m to 20m progress, after which it smoothly advances to the next cue. Zero race conditions or premature cutoffs occur.
* **Finish Boundary Window**: At the route finish ($d > \text{totalDistance} - 10.0\text{m}$), cues are suppressed to conclude guidance cleanly at the destination pin.
* **Closed Loop Handling**: For circular or closed routes where the start and finish lines converge within $10\text{m}$ ($\text{distance}(p_0, p_{m-1}) < 10\text{m}$), the $10\text{m}$ finish boundary clamp prevents duplicate or phantom cues directly on the finish line.
* **Deterministic Window Priority (Short Window Precedence)**:
  * Short window: forward index $j_{\text{short}}$ such that $d_{j_{\text{short}}} - d_i \approx W_{\text{short}}$ (12–15m).
  * Long window: forward index $j_{\text{long}}$ such that $d_{j_{\text{long}}} - d_i \approx W_{\text{long}}$ (35–50m).
  * State Machine Evaluation at vertex $i$:
    1. If $|\Delta \theta_{\text{short}}| \ge 25^\circ$: Trigger candidate turn deflection $\Delta \theta = \Delta \theta_{\text{short}}$ (**Short Window Precedence**). Localized intersection turns take absolute priority over sweeping curve window evaluation, preventing cue shadowing and anchoring the apex at the physical street corner.
    2. Else if $|\Delta \theta_{\text{long}}| \ge 50^\circ$: Trigger candidate turn deflection $\Delta \theta = \Delta \theta_{\text{long}}$ (Sweeping Curve).
    3. Else: No turn detected at vertex $i$ (straight road alignment).

#### 5.7 Junction-Aware Conflict Resolution & Monotonic Non-Downgrading Refinement
* **Apex Selection Priority & Midpoint Tie-Breaking**:
  - The apex of a turn maneuver is defined as the vertex with the **maximum local curvature rate** $\max |\Delta \theta_k|$.
  - For continuous constant-curvature arcs spanning vertex index range $[i_{\text{start}}, i_{\text{end}}]$ with $K = i_{\text{end}} - i_{\text{start}} + 1$ candidate peak vertices sharing the maximum curvature rate, the chosen apex index is deterministically:
    $$i_{\text{apex}} = i_{\text{start}} + \lfloor (K - 1) / 2 \rfloor$$
    (centering the cue on the physical curve and maintaining $\pm 5\text{m}$ accuracy).
* **Chicane Separation Threshold Justification ($15.0\text{m}$)**:
  - Grounded in vehicle/cyclist kinematics: negotiating an S-curve at minimum turning radius $R \approx 5\text{--}7\text{m}$ consumes $\approx 5\text{m}$ of arc per turn, plus a $3\text{--}5\text{m}$ straight inflection transition $\implies 13\text{--}15\text{m}$ minimum spatial separation between inflection apexes. Reversals occurring closer than $15\text{m}$ represent high-frequency slalom noise or road surface swerving rather than navigable street junctions.
* **Apex Stability & Monotonic Non-Downgrade Invariant**:
  - Apex refinement is strictly additive/upward-adjusting for severity and **cannot downgrade** an existing turn classification.
  - In same-direction refinement within 30m, an update occurs only if:
    1. Curvature severity increases by at least the hysteresis buffer: $|\Delta \theta_{\text{cand}}| \ge |\Delta \theta_{\text{last}}| + 5.0^\circ$.
    2. Apex spatial shift is significant: $|d_{\text{cand}} - d_{\text{last}}| \ge 1.0\text{m}$. Spatial shifts $< 1.0\text{m}$ are suppressed to prevent UI micro-flickering while preserving the monotonic severity upgrade.
* **Refinement Algorithm Pseudo-Code & Linear $O(N)$ Proof**:
```kotlin
val cues = ArrayList<TurnCue>() // Local variable on call frame
// Immediate turns <= 10m departure window are pinned to 0.0m:
val candidateDist = if (distances[iApex] <= config.startDepartureWindowMeters) 0.0 else distances[iApex]
val candidateLatLng = if (distances[iApex] <= config.startDepartureWindowMeters) path.first().latLng else path[iApex].latLng

if (cues.isNotEmpty()) {
    val lastCue = cues[cues.size - 1]
    val distDiff = candidateDist - lastCue.distanceFromStart
    if (distDiff < config.minSeparationMeters) {
        val isOppositeDirection = (candidateDelta * lastDelta < 0.0f)
        if (isOppositeDirection && distDiff >= config.minChicaneSeparationMeters) {
            // Valid Chicane: Preserve both distinct cues
            cues.add(candidateCue)
            lastDelta = candidateDelta
        } else if (!isOppositeDirection && abs(candidateDelta) >= abs(lastDelta) + config.hysteresisBufferDeg) {
            // Same-Direction Apex Refinement: O(1) in-place replacement (Strictly Non-Downgrading)
            // Spatial hysteresis: Only shift apex position if displacement >= 1.0m to prevent UI jitter
            val targetDist = if (abs(candidateDist - lastCue.distanceFromStart) >= config.apexMinSpatialShiftMeters) candidateDist else lastCue.distanceFromStart
            val targetLatLng = if (abs(candidateDist - lastCue.distanceFromStart) >= config.apexMinSpatialShiftMeters) candidateLatLng else lastCue.latLng
            cues[cues.size - 1] = candidateCue.copy(distanceFromStart = targetDist, latLng = targetLatLng)
            lastDelta = candidateDelta
        }
        // Otherwise, drop candidate (Drop Strategy - prevents downgrade and duplication)
        continue
    }
}
cues.add(candidateCue)
lastDelta = candidateDelta
```
* **Proof**: Evaluating `distDiff < 30m` accesses strictly `cues[cues.size - 1]` in $O(1)$ time. There is no inner loop, no backtracking, and no re-evaluation of prior cues. The complexity per vertex is strictly $O(1)$, guaranteeing that the entire traversal executes in $O(N)$ time.

---

## 6. Atomic Implementation Steps

| Step | Action & Target File | Description | Verification Command |
| :--- | :--- | :--- | :--- |
| **Step 1** | Edit `TurnCueDetector.kt` | Define `TurnCueDetectorConfig`, pre-allocate primitive arrays (`distances`, `segmentLengths`, `rawBearings`, `smoothedBearings`), implement single-pass pre-filtering coordinate sanitization skipping $\Delta s < 10^{-4}\text{m}$ and rejecting multipath acute rebound spikes, and add short route guard ($< 20\text{m}$ / $< 3$ points returns `emptyList()`). | `./gradlew testDebugUnitTest --tests "*TurnCueDetectorTest*"` |
| **Step 2** | Edit `TurnCueDetector.kt` | Implement distance-weighted spatial circular vector mean on sanitized raw coordinates using spherical Haversine and forward azimuth geodesics (zero RDP decimation) to attenuate lateral GPS jitter. | `./gradlew testDebugUnitTest --tests "*TurnCueDetectorTest*"` |
| **Step 3** | Edit `TurnCueDetector.kt` | Implement multi-scale forward sliding windows ($W_{\text{short}}$, $W_{\text{long}}$) with deterministic short-window precedence state machine and monotonic pointers ($O(N)$). | `./gradlew testDebugUnitTest --tests "*TurnCueDetectorTest*"` |
| **Step 4** | Edit `TurnCueDetector.kt` | Implement start-of-route departure clamping to 0.0m for immediate departure turns ($d \le 10\text{m}$), finish boundary clamping, junction-aware conflict resolution (chicane preservation at $\ge 15\text{m}$, strictly non-downgrading same-curve $5^\circ$ apex refinement with $1\text{m}$ spatial hysteresis), and apex curvature peak selection with midpoint tie-breaking. | `./gradlew testDebugUnitTest --tests "*TurnCueDetectorTest*"` |
| **Step 5** | Edit `TurnCueDetector.kt` | Implement curvature classification thresholds and U-turn consolidation ($|\Delta \theta| \ge 155^\circ$). | `./gradlew testDebugUnitTest --tests "*TurnCueDetectorTest*"` |
| **Step 6** | Edit `TurnCueDetectorTest.kt` | Add unit tests covering `TST-MAP-045.1` to `TST-MAP-045.18` (sharp corner, sweeping curve, winding suppression, midpoint arc, hairpin U-turn, GPS jitter, short route, 5000-point benchmark, chicane preservation at 15m, 20m boundary conditions, $\pm 2\text{m}$ lateral jitter, zero-length duplicate segments, threshold oscillation jitter suppression, high-frequency lateral noise zero cues, closed loop start/finish boundary protection, immediate departure turn at 2m clamped to 0.0m, threshold oscillation hysteresis). | `./gradlew testDebugUnitTest --tests "*TurnCueDetectorTest*"` |
| **Step 7** | Full Test Suite | Run complete unit test suite across all modules to verify clean-room regression zero-failure guarantee (`TST-MAP-045.16`). | `./gradlew testDebugUnitTest` |

---

## 7. Gate 3 Pre-Check & Verification Protocol

* Before modifying production code in Stage 4, verify Gate 3 status via:
  `python3 tools/jira_util.py check-gate ATT-3499`
* Stage 4 implementation SHALL NOT commence until Gate 3 returns exit code 0.
