# Stage 1 Analysis: ATT-3266 - Unify polyline progress tracking engines between LiveSegments and LiveClimbs

**Ticket**: [ATT-3266](https://atrainingtracker.atlassian.net/browse/ATT-3266)  
**Sub-task**: [ATT-3279](https://atrainingtracker.atlassian.net/browse/ATT-3279) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*[Epic] Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Branch**: `feature/ATT-3266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

`aTrainingTracker` currently maintains two independent tracking engines for guiding athletes along polylines during outdoor tracking:
1. `LiveSegmentsRepository.kt`: Tracks starred Strava segments during active rides and runs (`REQ-LIV-001` - `REQ-LIV-004`).
2. `LiveClimbsRepository.kt`: Tracks route climbs and free-riding ascents for ClimbPro (`REQ-MAP-027`, `REQ-MAP-047`).

While both repositories manage an identical operational lifecycle (`FAR_FAR_AWAY` $\rightarrow$ `APPROACHING` $\rightarrow$ `ON_TRACK` $\rightarrow$ `FINISHED`), their tracking mathematics, gate detection, and progress algorithms have significantly diverged:

| Dimension | `LiveSegmentsRepository` | `LiveClimbsRepository` | Divergence Impact |
| :--- | :--- | :--- | :--- |
| **Start & Finish Gating** | 2D Perpendicular Virtual Gates with 2D vector cross-product sign-flip detection (`startCross.sign != lastCross.sign`). | Radial distance threshold circle ($30\text{m}$ start, $35\text{m}$ summit). | Climbs can false-trigger prematurely on hairpin switchbacks or parallel trails. Segments enforce strict directional entry. |
| **Progress Tracking** | Raw odometer subtraction ($D_{\text{current}} - D_{\text{start}}$) from `BANALServiceRepository.currentDistance`. | Orthogonal coordinate projection onto 3D polyline segments ($u \in [0, 1]$). | Segments are vulnerable to cumulative GPS odometer drift, wander, and shortcut inaccuracies. Climbs compute exact along-track distance. |
| **Off-Track Detection** | Stepwise index traversal (`indexOfDistance`) to find nearest line segment. | Minimum cross-track orthogonal distance across all segments ($> 100\text{m}$ abort). | Segments can get stuck at discrete index points if the athlete skips a section; climbs evaluate global minimum cross-track error. |
| **Finish Convergence** | Smooth $\lambda$-blending between remaining polyline distance and direct line distance to finish gate. | Discrete summit distance without blending. | Climbs can display small non-zero remaining distance jumps at the summit; segments smoothly converge to $0\text{m}$. |
| **Geometry Math** | Private `crossProduct(a, b, c)` and `getBearingDifference(b1, b2)`. | Private `getBearingDifference(b1, b2)` and manual projection math. | Redundant code duplicated across modules, increasing maintenance overhead. |

By extracting a canonical, reusable `PolylineProgressTracker` engine in `com.atrainingtracker.trainingtracker.geo`, we synthesize the best mathematical techniques from both domains into a single unified tracking engine, eliminating duplication and ensuring consistent, robust tracking behavior across segments and climbs.

---

## 2. Root Cause Analysis & Architectural Forensic Investigation

### 2.1. Odometer Subtraction Vulnerability in `LiveSegmentsRepository`
In `LiveSegmentsRepository.kt` lines 308–309:
```kotlin
val distanceOnSegment = currentDistance - liveSegment.math.startDistance
```
`currentDistance` is sourced directly from `banalRepository.currentDistance.value`. If an athlete swerves around obstacles, weaves uphill, or if GPS jitter accumulates during the effort, the odometer advances faster than the physical segment polyline. Consequently, `distanceOnSegment` over-estimates progress and causes the athlete's virtual avatar or PR ghost comparison to misalign.

Furthermore, line 320 iterates an index pointer:
```kotlin
while (liveSegment.math.indexOfDistance < liveSegment.staticData.path.size - 1
    && liveSegment.staticData.path[liveSegment.math.indexOfDistance].distance <= distanceOnSegment
) {
    liveSegment.math.indexOfDistance++
}
```
If an athlete cuts a corner or the odometer drifts, `indexOfDistance` can desynchronize from the athlete's true physical position along the polyline.

### 2.2. Radial Proximity Gating Limitations in `LiveClimbsRepository`
In `LiveClimbsRepository.kt` lines 141 and 194:
```kotlin
if (distToStart <= CLIMB_START_GATE_THRESHOLD_METERS) { // 30m
    status = LiveClimbStatus.ON_CLIMB
}
```
And line 177:
```kotlin
if (distToSummit <= CLIMB_SUMMIT_GATE_THRESHOLD_METERS) { // 35m
    status = LiveClimbStatus.FINISHED
}
```
Radial distance circles do not evaluate trajectory direction. On alpine climbs with tight switchbacks near the base or summit, an athlete riding on a lower hairpin tier or approaching the summit from a perpendicular ridge can trigger `ON_CLIMB` or `FINISHED` prematurely. 

In contrast, `LiveSegmentsRepository` constructs virtual perpendicular start/finish line gates `[start_a, start_b]` and `[end_a, end_b]` perpendicular to the entry/exit track vectors:
```kotlin
val startA = LatLng(start.latitude + (start.longitude - next.longitude), start.longitude - (start.latitude - next.latitude))
val startB = LatLng(start.latitude - (start.longitude - next.longitude), start.longitude + (start.latitude - next.latitude))
```
And verifies line crossing via cross-product sign flip:
```kotlin
liveSegment.math.start_cross_loc.sign != start_cross_loc.sign && start_cross_loc.sign == liveSegment.math.start_cross_n.sign
```
This guarantees the athlete crosses the gate in the correct forward direction.

### 2.3. Orthogonal Projection & Finish Blending Synthesis
`LiveClimbsRepository` implements mathematically sound orthogonal segment projection (`REQ-MAP-047`):
$$u = \frac{(\text{lat} - P_{1.\text{lat}})\Delta\text{lat} + (\text{lng} - P_{1.\text{lng}})\Delta\text{lng}}{\Delta\text{lat}^2 + \Delta\text{lng}^2}, \quad t = \text{clamp}(u, 0.0, 1.0)$$
It derives cross-track error $D_{\text{cross}}$ and completed along-track distance:
$$\text{completedDist} = (P_{1.\text{dist}} - P_{\text{first}.\text{dist}}) + t \times (P_{2.\text{dist}} - P_{1.\text{dist}})$$
And smooth finish $\lambda$-blending from `LiveSegmentsRepository`:
$$\lambda = \min\left(1.0, \frac{D_{\text{end}}}{\text{THRESHOLD}}\right), \quad D_{\text{rem}} = (D_{\text{total}} - D_{\text{completed}}) \times \lambda + (1.0 - \lambda) \times D_{\text{end}}$$
Combining orthogonal projection with finish $\lambda$-blending produces an optimal tracking engine that is accurate throughout the entire track trajectory and reaches exactly $0.0\text{m}$ at the finish line without jumps.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

### Field 1: Original Requirement ID & Target
* Amends `REQ-LIV-001` (*Detect segments using "Gates" and cross-products*), `REQ-LIV-002` (*Real-time progress on starred Strava segments*), and `REQ-LIV-004` (*Filter segments by bearing alignment*).
* Amends `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*) and `REQ-MAP-047` (*Continuous Linear Polyline Projection & Progress Metric Interpolation for Live Climbs*).
* Targets `LiveSegmentsRepository.kt`, `LiveClimbsRepository.kt`, and the new component `PolylineProgressTracker.kt` (`com.atrainingtracker.trainingtracker.geo`).

### Field 2: Historical Origin & Commit Trace
* `LiveSegmentsRepository.kt` was authored in the early architecture of `aTrainingTracker` establishing the 2D cross-product gate mathematics.
* `LiveClimbsRepository.kt` was introduced under Sprint 2026-40.14 (`ATT-1281`, commit `d5c90d81`) utilizing radial proximity gating.
* `ATT-3174` (Sprint 2026-41.9, commit `070bc379`) upgraded `LiveClimbsRepository` to orthogonal polyline projection (`REQ-MAP-047`) to prevent UI stutter and false off-route cancellations on sparse tracks.

### Field 3: Root Reason for Existing Formulation
* Climbs and Segments were originally developed under separate milestones by different requirements (`ATT-1281` vs early Strava integration).
* At the time of ClimbPro implementation, `LiveSegmentsRepository` was considered Strava-specific and tightly coupled to `SegmentWithPath`, `SegmentsRepository`, and odometer subtraction.
* Consequently, `LiveClimbsRepository` was implemented with isolated tracking code. With both features fully stabilized, unifying them into a shared mathematical engine eliminates tech debt.

### Field 4: Preservation of Core Invariants
* All segment-specific pacing (PR delta, time on segment, segment offset) and climb-specific pacing (remaining ascent, current grade %, route index) MUST remain 100% mathematically and functionally identical.
* Multi-track candidate monitoring in `LiveSegmentsRepository` (monitoring all starred segments) and active route vs free-riding climb detection in `LiveClimbsRepository` MUST be strictly preserved.
* 100% full clean-room unit regression test suite pass rate.

---

## 4. User Scope Grounding (ATT-1250)

### 4.1 In-Scope Objectives
1. **Engine Extraction (`com.atrainingtracker.trainingtracker.geo.PolylineProgressTracker.kt`)**:
   - Reusable domain models: `PolylineGate`, `PolylineProjection`, `PolylineTrackingState`, `PolylineTrack`.
   - Directional perpendicular virtual gate creation and crossing detection via 2D vector cross-products.
   - Continuous orthogonal polyline segment projection ($u \in [0, 1]$), along-track distance calculation, cross-track error measurement, and 3D altitude/grade interpolation.
   - Linear $\lambda$-blending for remaining distance convergence near finish.
   - Approach proximity and heading alignment filtering ($\le 45^\circ$).
   - Consolidated geometry helpers (`getBearingDifference`, `crossProduct`, `distanceToLine`).
2. **`LiveSegmentsRepository.kt` Refactoring**:
   - Replace odometer subtraction with orthogonal polyline along-track distance from `PolylineProgressTracker`.
   - Delegate gate detection and crossing to `PolylineProgressTracker`.
   - Maintain all external StateFlow contracts (`liveSegments`) and Strava PR comparison logic.
3. **`LiveClimbsRepository.kt` Refactoring**:
   - Replace radial distance start/summit circles with directional virtual gates from `PolylineProgressTracker`.
   - Delegate polyline projection, off-route evaluation, and grade calculation to `PolylineProgressTracker`.
   - Maintain all external StateFlow contracts (`activeLiveClimb`, `allClimbs`).
4. **Comprehensive Test Suite**:
   - Create `PolylineProgressTrackerTest.kt` verifying virtual gate crossings, heading filtering, orthogonal projections, switchback rejection, and finish $\lambda$-blending.
   - Verify `LiveClimbsRepositoryTest.kt` passes with 0 regressions.
   - Create `LiveSegmentsRepositoryTest.kt` verifying live segment tracking lifecycle.

### 4.2 Out-of-Scope (Boundaries)
1. Modifying UI BottomSheets (`LiveClimbSheet.kt`, `LiveSegmentSheet.kt`).
2. Modifying SQLite database schemas (`Climbs.db`, `Segments.db`).
3. Modifying background sensor service (`BANALService.java`) or location provider interfaces.

---

## 5. Architectural Design of `PolylineProgressTracker`

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PolylineProgressTracker                         │
│                  (com.atrainingtracker.trainingtracker.geo)            │
├────────────────────────────────────────────────────────────────────────┤
│ • createGate(pointA, pointB, widthMeters): PolylineGate               │
│ • isApproaching(location, bearing, startGate, threshold, headingTol)   │
│ • checkGateCrossing(prevLoc, currLoc, gate): GateCrossingResult       │
│ • projectOrthogonal(location, pathPoints): PolylineProjection          │
│ • blendRemainingDistance(totalDist, alongDist, distToFinish, thresh)   │
│ • getBearingDifference(b1, b2): Double                                 │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │
                 ┌────────────────┴────────────────┐
                 ▼                                 ▼
   ┌───────────────────────────┐     ┌───────────────────────────┐
   │  LiveSegmentsRepository   │     │   LiveClimbsRepository    │
   ├───────────────────────────┤     ├───────────────────────────┤
   │ • Starred segments pool   │     │ • Route & Free-ride climbs│
   │ • PR delta comparison     │     │ • Remaining elevation gain│
   │ • Elapsed time pacing     │     │ • Instantaneous gradient  │
   │ • LiveSegment UI model    │     │ • LiveClimbData UI model  │
   └───────────────────────────┘     └───────────────────────────┘
```

---

## 6. Review Inspection Protocol (Mandatory User Commitment)

> [!IMPORTANT]
> In accordance with the explicit user screening commitment, all code changes and unified tracking diffs will be presented in detail during the Review phase of `ATT-3266`.
