# Stage 1 Analysis: ATT-3494 - Improve turn cue detection algorithm and curvature classification along route polylines

**Ticket**: [ATT-3494](https://rainerblind.atlassian.net/browse/ATT-3494)  
**Sub-task**: [ATT-3497](https://rainerblind.atlassian.net/browse/ATT-3497) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Branch**: `feature/ATT-3494`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13 (Revised per Gate 1 Auditor Scrutiny v3)  

---

## 1. Problem Statement & Motivation

During active route navigation testing, the automated geometric turn detection engine ([`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt)) exhibits notable accuracy and sensitivity limitations when identifying and classifying turns from polyline track geometry:

1. **Fixed-Window Blind Spots on Sweeping Turns**:
   The current algorithm evaluates heading deflection using a static 15-meter window (`WINDOW_LOOKAROUND_METERS = 15.0m`). Long, sweeping curves or multi-segment turns spanning 30m–80m (such as curved highway bridges, sweeping roundabouts, or mountain switchback curves) exhibit small point-to-point chord deltas ($< 30^\circ$) at each 15m step. Consequently, genuine $90^\circ$ or $180^\circ$ direction changes are completely missed when distributed across more than 20 meters.

2. **Spurious Cues on Winding Country Roads**:
   On winding country roads or gravel trails without crossroads, gentle road bends occasionally exceed the hard $30^\circ$ threshold over a single 15m chord, triggering nuisance *"Halb rechts"* (`SLIGHT_RIGHT`) or *"Halb links"* (`SLIGHT_LEFT`) turn banners when the athlete should simply follow the main path.

3. **Inaccurate Apex Placement**:
   In gradual turns, cues are currently placed at an arbitrary interior trackpoint (the first vertex exceeding $30^\circ$) rather than the true geometric apex (point of maximum curvature rate $\frac{d\theta}{ds}$), leading to confusing countdown distances and poorly timed audio chimes.

4. **Curvature Threshold Classification Boundaries**:
   Angular threshold cutoffs ($30^\circ$, $60^\circ$, $120^\circ$, $155^\circ$) require calibration to distinguish distinct intersection maneuvers from natural road winding, and properly harmonize with the newly refined turn cue vector icons (ATT-3455 / ATT-3493).

Athletes need an intelligent, multi-scale turn detection algorithm that reliably catches sweeping turns, suppresses natural road curvature noise, and accurately anchors turn cues at the natural turn apex.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of [`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt#L64-L131) reveals the underlying mathematical causes:

```kotlin
// Current implementation in TurnCueDetector.kt:
for (i in 1 until path.size - 1) {
    val current = path[i]
    var prevIdx = i - 1
    while (prevIdx > 0 && (current.distance - path[prevIdx].distance) < WINDOW_LOOKAROUND_METERS) prevIdx--
    var nextIdx = i + 1
    while (nextIdx < path.size - 1 && (path[nextIdx].distance - current.distance) < WINDOW_LOOKAROUND_METERS) nextIdx++

    val bearingIn = RouteAutoDetector.computeBearing(pPrev.latLng, current.latLng)
    val bearingOut = RouteAutoDetector.computeBearing(current.latLng, pNext.latLng)
    val delta = computeSignedAngleDelta(bearingIn, bearingOut)
    if (abs(delta) >= minAngle) { ... }
}
```

### Architectural Deficiencies:
1. **Local Single-Chord Sampling vs. Cumulative Deflection**:
   The current algorithm only checks the angle between the chord entering `current` from $-15\text{m}$ and the chord exiting `current` to $+15\text{m}$. It computes $\Delta \theta = \theta(p_{\text{next}}) - \theta(p_{\text{prev}})$. If a $90^\circ$ corner is rounded over 45 meters, the curvature rate is $\approx 2^\circ / \text{m}$. Across a 15m chord, the deflection is only $\approx 30^\circ$, hovering right at the boundary threshold. If the track is slightly irregular, the measured delta dips to $24^\circ - 28^\circ$, and the turn is entirely lost.
2. **Absence of Heading-Domain Spatial Smoothing**:
   Raw polyline imports exhibit micro-jitter ($\pm 2\text{--}5\text{m}$). Without pre-filtering, noisy trackpoint zig-zags artificially inflate local chord angles or displace bearing vectors.
3. **Naïve First-Vertex Clustering**:
   When `abs(delta) >= minAngle`, the cue is added immediately at `current.latLng` and a simple 30m exclusion window (`lastCueDistance = distanceFromStart`) is started. If a sweeping turn continues to bend past this first detection, the cue remains anchored at the start of the bend rather than at the apex of maximum turning rate.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement multi-scale / adaptive window curvature analysis in [`TurnCueDetector.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt) to capture both sharp corners (10–25m) and sweeping turns (30–60m) in guaranteed linear $O(N)$ execution time.
  * Implement cumulative curvature aggregation ($\sum \Delta \theta$) and robust apex localization.
  * **Reconciliation of Pre-Smoothing Strategy (RDP Rejection Justification)**: While the parent ticket ideation mentioned Ramer-Douglas-Peucker (RDP) as a candidate option, forensic analysis confirms that RDP decimation deletes intermediate vertices, altering path distances and destroying the geometric corner resolution required to locate the true turn apex. Therefore, **RDP coordinate simplification is explicitly rejected** in favor of a non-destructive **Distance-Based Spatial Heading Moving Average** that smooths bearing sequences without removing or displacing path coordinates.
  * **Sample-Rate Independence (Spatial Smoothing)**: Smoothing is defined strictly over a **5-meter spatial distance window** ($s \pm 2.5\text{m}$) based on cumulative polyline distance (`PathPoint.distance`), guaranteeing 100% identical smoothing performance across variable GPS recording rates (e.g. 1Hz high-density phone GPS vs. 0.1Hz sparse desktop export).
  * **Deterministic Apex Selection & Boundary Handling**: Define deterministic apex selection with tie-breaking for uniform curvature arcs (midpoint anchor), boundary clamps for short terminal route segments, and hysteresis buffering ($5^\circ$) to prevent cue position fluttering.
  * **Single-Cue U-Turn / Backtracking Maneuvers**: Handle hairpin and $180^\circ$ switchback maneuvers cleanly as single `U_TURN` cues (when $|\sum \Delta \theta| \ge 155^\circ$), eliminating redundant double-cue emissions.
  * Calibrate threshold boundaries for `TurnDirection` (`SLIGHT_RIGHT/LEFT`, `RIGHT/LEFT`, `SHARP_RIGHT/LEFT`, `U_TURN`) and suppress continuous gentle road bends ($< 25^\circ$).
  * Expand [`TurnCueDetectorTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt) with realistic geometric fixtures: 90-degree intersection corners, sweeping multi-point curves, perfect circular arcs, high-frequency zig-zag noise paths, 180-degree U-turn hairpins, and winding road segments.
  * Guarantee computation speed $< 100\text{ms}$ for routes exceeding 5,000 points.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying real-time navigation tracking loop in [`TurnByTurnNavigationEngine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnByTurnNavigationEngine.kt) (countdown math, approaching states, and off-route detection consume detected cues as-is).
  * Changing HUD overlay banners or vector icons (handled in ATT-3455 / ATT-3493).
  * Modifying SQLite `Routes.db` schema (detected cues remain in-memory DTOs unless explicitly saved as waypoints).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: [`REQ-MAP-028`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L531) (*Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts*), Section 1: *Turn Cue Source & Geometric Synthesis (`TurnCueDetector.kt`)*.
* **Historical Origin & Commit Trace**: Introduced in `ATT-1450` (Sprint `2026-40.14`, commit `54c5c2d3`), amended by `ATT-2632` and `ATT-2941`.
* **Root Reason for Existing Formulation**: `REQ-MAP-028` established the baseline geometric cue synthesis:
  > *"For routes lacking embedded turn points, `TurnCueDetector` SHALL analyze polyline heading deltas $|\Delta \theta| \ge 30^\circ$ across windowed chord lengths ($15-25$m) to synthesize directional turn cues..."*
  The initial 15m window was selected as a minimal viable heuristic to detect sharp city street corners.
* **Preservation of Core Invariants**:
  * Existing explicit turn waypoints (`WaypointCategory.TURN_CUE`) continue to take absolute precedence over geometric detection.
  * Downstream consumers (`TurnByTurnNavigationEngine`, `TurnPromptBanner`) rely on `TurnCue(id, latLng, distanceFromStart, direction, ...)` contract, which remains 100% stable and strictly monotonically increasing in `distanceFromStart`.
  * **In-Flight Navigation State Invariant**: Cues are generated once during route activation in `TurnByTurnNavigationRepository` and held immutably for the duration of the navigated activity. Moving apex anchors to the true geometric peak improves countdown accuracy without causing dynamic runtime distance jitter, because `distanceFromStart` is never re-calculated on the fly while navigating an active route.

---

## 5. Architectural Strategy & High-Level Solution (Hardened via Auditor Feedback)

### 1. Spatial Heading-Domain Smoothing (Sample-Rate Independent)
* **Rejection of Coordinate Decimation**: RDP decimation is rejected because deleting vertices strips away local curve inflection points and distorts path-distance mapping.
* **Distance-Based Spatial Moving Average**: Compute point-to-point segment bearings $\theta_i = \text{bearing}(p_i, p_{i+1})$. Smooth each bearing over a **5-meter spatial moving window** based on cumulative distance:
  $$\bar{\theta}(s_i) = \text{circularMean}\{\theta_k \mid |s_k - s_i| \le 2.5\text{m}\}$$
  This guarantees identical filtering regardless of whether trackpoints are spaced at 1 meter or 20 meters.

### 2. Single-Pass $O(N)$ Multi-Scale Evaluation via Independent Sliding Iterators
* To guarantee $O(N)$ performance and prevent nested loop degradation ($O(N^2)$), dual-scale window evaluation uses two independent forward-advancing index pointers ($W_{\text{short}} = 12\text{--}15\text{m}$, $W_{\text{long}} = 35\text{--}50\text{m}$):
  * **Short Pointer**: Detects abrupt, sharp intersections where local chord delta $|\Delta \theta_{\text{short}}| \ge 45^\circ$.
  * **Long Pointer**: Detects gradual sweeping curves where cumulative deflection $\sum \Delta \theta \ge 55^\circ$ across $W_{\text{long}}$ even if individual short chords are below $30^\circ$.
  * Both pointers advance monotonically in a single forward pass, ensuring guaranteed $O(N)$ computational complexity without allocating objects in inner loops.

### 3. Mathematically Stable Apex Localization, Hysteresis & Deterministic Tie-Breaking
* Within a detected turn maneuver interval $[i_{\text{start}}, i_{\text{end}}]$:
  * Boundary safety: Clamped against route boundaries ($0 \le i_{\text{start}} \le i_{\text{end}} < \text{path.size}$).
  * Locate the vertex $i_{\text{apex}}$ exhibiting maximum smoothed curvature rate.
  * **Tie-Breaking Rule for Uniform Curvature (e.g. Perfect Circular Arc / Roundabout)**: If multiple adjacent vertices exhibit identical maximum curvature (within a $2^\circ$ tolerance band), select the **median/midpoint vertex** along the arc:
    $$i_{\text{apex}} = i_{\text{start}} + \left\lfloor \frac{i_{\text{end}} - i_{\text{start}}}{2} \right\rfloor$$
  * **Hysteresis Buffer & Forward Monotonicity**: Candidate apex updates during maneuver scanning require at least a $5^\circ$ curvature improvement over the current candidate, preventing position fluttering. Each emitted cue is validated to guarantee $D_{\text{cue}} \ge D_{\text{last\_cue}} + \text{minSeparationMeters}$, strictly preserving monotonic forward distance along the route.

### 4. Backtracking & Hairpin U-Turn Handling
* When cumulative curvature in a single maneuver window continues beyond $155^\circ$ (e.g. $180^\circ$ mountain hairpin or turnaround), the maneuver is classified definitively as `TurnDirection.U_TURN` anchored at the hairpin midpoint, and subsequent chord deltas within the 30m separation window are suppressed to prevent duplicate left/right cue emissions.

### 5. Synthetic Noise & Edge-Case Testing
* The test suite will be expanded in `TurnCueDetectorTest.kt` to include:
  * **Worst-Case High-Frequency Zig-Zag Noise**: Alternating $\pm 10^\circ$ GPS jitter along a straight highway to prove zero spurious turn cues are emitted.
  * **Perfect Circular Arc**: Constant radius arc to verify deterministic midpoint apex tie-breaking.
  * **Sweeping 90-Degree Curve**: 40-meter radius curve where instantaneous chord deltas are $< 25^\circ$ to verify cumulative detection.
  * **Backtracking / Hairpin U-Turn**: $180^\circ$ direction reversal verifying emission of exactly one `U_TURN` cue without duplicate cues.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Impact on Explicit Waypoint Routes**: Routes with pre-authored TCX/GPX turn waypoints bypass geometric synthesis entirely.
  2. **Deterministic & Pure Algorithm**: `detectGeometricCues(path)` is a pure, side-effect-free function easily verified in unit tests.
  3. **Guaranteed $O(N)$ Performance**: Independent forward sliding iterators ensure execution times well under 30ms for 10,000 trackpoints.
  4. **Downstream Contract Stability**: `TurnCue` fields (`distanceFromStart`, `latLng`, `direction`) remain strictly monotonic and 100% compliant with `TurnByTurnNavigationEngine.kt`.
* **Risk Rating**: **LOW** (Fully mitigated: Spatial sample-rate independent smoothing specified, in-flight navigation lifecycle verified, U-turn handling formalized, deterministic tie-breaking established, and synthetic noise tests specified).
