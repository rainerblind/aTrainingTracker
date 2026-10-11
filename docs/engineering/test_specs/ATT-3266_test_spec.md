# Stage 2: Requirement & Test Specification - ATT-3266: Unify polyline progress tracking engines between LiveSegments and LiveClimbs

**Ticket**: [ATT-3266](https://atrainingtracker.atlassian.net/browse/ATT-3266)  
**Sub-task**: [ATT-3280](https://atrainingtracker.atlassian.net/browse/ATT-3280) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*[Epic] Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-GEO-001` (*Unified Polyline Progress Tracking Engine with Directional Virtual Gates and Orthogonal Segment Projection*)  
**Test Spec ID**: `TST-GEO-001`  
**Branch**: `feature/ATT-3266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-GEO-001)

### 1.1 Problem Statement & Rationale
`aTrainingTracker` currently maintains two separate, divergent tracking engines for guiding athletes along polylines during outdoor tracking:
1. `LiveSegmentsRepository.kt`: Starred Strava segments (`REQ-LIV-001` - `REQ-LIV-004`).
2. `LiveClimbsRepository.kt`: Route climbs and free-riding ascents (`REQ-MAP-027`, `REQ-MAP-047`).

While both manage an identical state lifecycle (`FAR_FAR_AWAY` $\rightarrow$ `APPROACHING` $\rightarrow$ `ON_TRACK` $\rightarrow$ `FINISHED`), their algorithms diverged:
- `LiveSegments` uses virtual perpendicular start/finish line gates with 2D vector cross-product sign-flip detection, but relies on raw odometer subtraction ($D_{\text{current}} - D_{\text{start}}$), leaving it vulnerable to cumulative GPS odometer drift, wander, and shortcut inaccuracies.
- `LiveClimbs` uses orthogonal coordinate projection onto polyline segments ($u \in [0, 1]$) with cross-track error detection, but relies on simple radial distance circles ($30\text{m}$ start, $35\text{m}$ summit), making it susceptible to premature false triggers on tight hairpin switchbacks or parallel trails.

This requirement extracts a unified, modular, pure-Kotlin tracking engine (`PolylineProgressTracker.kt` in `com.atrainingtracker.trainingtracker.geo`) synthesizing the best mathematical algorithms from both domains: directional virtual gates, orthogonal segment projection, 3D altitude/grade interpolation, smooth finish $\lambda$-blending, and approach heading filtering.

### 1.2 Functional & Architectural Requirements
The system SHALL extract and maintain a unified, reusable `PolylineProgressTracker` engine in `com.atrainingtracker.trainingtracker.geo` and delegate polyline tracking from both `LiveSegmentsRepository` and `LiveClimbsRepository` (ATT-3266, amending `REQ-LIV-001`, `REQ-LIV-002`, `REQ-LIV-004`, `REQ-MAP-027`, and `REQ-MAP-047`):

1. *Unified PolylineProgressTracker Engine Architecture*:
   - The system SHALL define `PolylineProgressTracker` in package `com.atrainingtracker.trainingtracker.geo` as a pure-Kotlin utility decoupled from Android framework dependencies and database persistence.
   - Core domain models SHALL include:
     - `PolylineGate`: models a directional perpendicular virtual gate with endpoints $A, B$, reference forward point $N$, reference cross-product, and configurable gate distance threshold.
     - `PolylineProjection`: models the projected point, optimal segment index, segment parameter $t \in [0, 1]$, along-track completed distance, cross-track error in meters, progress fraction $\in [0f, 1f]$, interpolated altitude, and instantaneous grade percentage.
     - `PolylineTrackingState`: enum with `FAR_FAR_AWAY`, `APPROACHING`, `ON_TRACK`, `FINISHED`.

2. *Directional Virtual Gate Crossing via 2D Vector Cross-Products*:
   - For any polyline entry or exit, `PolylineProgressTracker` SHALL construct virtual gates perpendicular to the track entry vector ($[P_0, P_{\text{sample}}]$) and exit vector ($[P_{\text{last}-\text{sample}}, P_{\text{last}}]$).
   - Gate crossing SHALL be evaluated via 2D vector cross-product:
     $$\text{cross}(A, B, C) = (B_{\text{lat}} - A_{\text{lat}})(C_{\text{lng}} - A_{\text{lng}}) - (B_{\text{lng}} - A_{\text{lng}})(C_{\text{lat}} - A_{\text{lat}})$$
   - A gate crossing event SHALL trigger if and only if:
     1. Current location is within gate distance threshold ($D \le D_{\text{gate}}$).
     2. Cross-product sign has flipped relative to the previous fix: $\text{sign}(\text{currCross}) \ne \text{sign}(\text{prevCross})$.
     3. Cross-product sign matches the forward trajectory reference: $\text{sign}(\text{currCross}) == \text{sign}(\text{refCross})$ (for start gate) or $\text{sign}(\text{currCross}) \ne \text{sign}(\text{refCross})$ (for finish gate).
   - Radial entry without cross-product sign flip (e.g. parallel pass or hairpin approach) SHALL NOT trigger gate crossing.

3. *Continuous Orthogonal Segment Projection & 3D Telemetry*:
   - For an athlete at coordinate $L$, the engine SHALL project $L$ orthogonally onto each consecutive polyline segment $[P_i, P_{i+1}]$ using scalar parameter:
     $$u = \frac{(L_{\text{lat}} - P_{i.\text{lat}})\Delta\text{lat} + (L_{\text{lng}} - P_{i.\text{lng}})\Delta\text{lng}}{\Delta\text{lat}^2 + \Delta\text{lng}^2}, \quad t = \text{coerceIn}(u, 0.0, 1.0)$$
   - The engine SHALL select the segment index $i$ and scalar $t$ that minimize geodesic cross-track distance $D_{\text{cross}}$.
   - Along-track completed distance SHALL evaluate to:
     $$D_{\text{completed}} = (P_i.\text{distance} - P_{\text{first}}.\text{distance}) + t \times (P_{i+1}.\text{distance} - P_i.\text{distance})$$
   - Altitude and grade SHALL interpolate continuously along the optimal segment:
     $$\text{alt} = P_i.\text{altitude} + t \times (P_{i+1}.\text{altitude} - P_i.\text{altitude}), \quad \text{grade} = \frac{\Delta\text{altitude}}{\Delta\text{distance}} \times 100\%$$

4. *Smooth Finish Remaining Distance Blending*:
   - When within finish proximity threshold ($D_{\text{end}} \le D_{\text{finishThreshold}}$), the engine SHALL blend along-track remaining distance with direct geodesic line distance to the finish gate using linear $\lambda$-interpolation:
     $$\lambda = \min\left(1.0, \frac{D_{\text{end}}}{D_{\text{finishThreshold}}}\right), \quad D_{\text{rem}} = (D_{\text{total}} - D_{\text{completed}}) \times \lambda + (1.0 - \lambda) \times D_{\text{end}}$$
   - This guarantees remaining distance continuously and monotonically reaches exactly $0.0\text{m}$ at the finish line without discrete sampling jumps.

5. *Approach Filtering, Heading Alignment & Off-Track Detection*:
   - Approaching state SHALL activate when athlete distance to start gate $D_{\text{start}} \le D_{\text{approach}}$ (default $250\text{m}$) AND bearing difference $|\Delta\theta| \le \theta_{\text{tol}}$ (default $45^\circ$).
   - Off-track deviation SHALL trigger when minimum cross-track error $D_{\text{cross}} > D_{\text{offTrack}}$ (default $50\text{m}$ for segments, $100\text{m}$ for climbs), resetting state to `FAR_FAR_AWAY`.

6. *Repository Delegation & Migration*:
   - `LiveSegmentsRepository.kt` SHALL delegate gate creation, gate crossing, and polyline projection to `PolylineProgressTracker`, replacing raw odometer subtraction ($D_{\text{current}} - D_{\text{start}}$) with orthogonal along-track distance.
   - `LiveClimbsRepository.kt` SHALL delegate start/summit gate detection and orthogonal projection to `PolylineProgressTracker`, replacing radial circles with directional virtual gates.

7. *Preservation of Domain Models & Invariants*:
   - Segment-specific telemetry (PR comparison, time delta, elapsed time) and Climb-specific telemetry (remaining ascent, climb category, route sequencing) MUST remain intact.
   - Multi-track candidate concurrency in `LiveSegmentsRepository` and active route vs free-riding climb detection in `LiveClimbsRepository` MUST NOT be altered.
   - 100% clean-room unit regression test suite pass rate.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-LIV-001` (*Detect segments using "Gates" and cross-products*), `REQ-LIV-002` (*Real-time progress on starred Strava segments*), `REQ-LIV-004` (*Filter segments by bearing alignment*), `REQ-MAP-027` (*ClimbPro Real-Time Live Climb Cockpit Sheet*), and `REQ-MAP-047` (*Continuous Linear Polyline Projection for Live Climbs*).
2. *Historical Origin & Commit Trace*:
   - `LiveSegmentsRepository.kt`: authored in early Strava integration milestones.
   - `LiveClimbsRepository.kt`: authored in Sprint 2026-40.14 (`ATT-1281`, commit `d5c90d81`) and refined in Sprint 2026-41.9 (`ATT-3174`, commit `070bc379`).
3. *Root Reason for Existing Formulation*: Climbs and Segments were delivered under separate feature epics. At the time of ClimbPro development, `LiveSegmentsRepository` was tied to `SegmentWithPath` and raw odometer math, causing ClimbPro to implement an independent tracking loop.
4. *Preservation of Core Invariants*: Starred segment PR pacing, ClimbPro elevation profile, rider marker progress fraction, and 100% clean-room test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Directional Gate Crossing)**:
  * *Given* an athlete approaching a virtual start or finish gate
  * *When* crossing the gate line in the correct track heading direction
  * *Then* `PolylineProgressTracker` detects the crossing via cross-product sign flip and transitions state cleanly.
  * *Given* an athlete riding on a parallel switchback near the gate
  * *When* location updates are evaluated
  * *Then* the gate is NOT triggered unless the perpendicular line is physically crossed in the designated direction.
* **Criterion 2 (Orthogonal Polyline Progress Precision)**:
  * *Given* an athlete traveling along a live segment or climb
  * *When* GPS fixes arrive (with or without lateral weaving)
  * *Then* progress is projected orthogonally onto the polyline, providing accurate along-track distance and cross-track error independent of odometer drift.
* **Criterion 3 (Smooth Finish Convergence)**:
  * *Given* an athlete approaching the finish or summit gate within $D_{\text{finishThreshold}}$
  * *When* remaining distance is computed
  * *Then* $\lambda$-blending produces a smooth, continuous decrease reaching exactly $0.0\text{m}$ at the finish line.
* **Criterion 4 (Off-Route Abort)**:
  * *Given* an athlete on a segment or climb who turns away onto another road ($D_{\text{cross}} > D_{\text{offTrack}}$)
  * *When* tracking updates
  * *Then* the tracker immediately disengages and reverts state to `FAR_FAR_AWAY`.
* **Criterion 5 (Non-Regression Parity)**:
  * *Given* the automated test suite
  * *When* `LiveClimbsRepositoryTest`, `LiveSegmentsRepositoryTest`, `PolylineProgressTrackerTest`, and full application regression tests execute
  * *Then* 100% of tests pass with 0 failures.

---

## 2. Test Specification (TST-GEO-001)

### Test Case 1: Virtual Gate Construction & Directional Crossing (`TST-GEO-001.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/geo/PolylineProgressTrackerTest.kt`
* **Actions & Assertions**:
  1. Construct start gate from initial polyline points; assert perpendicular endpoints $A$ and $B$.
  2. Simulate athlete crossing start gate in forward direction: verify `checkGateCrossing` returns `true`.
  3. Simulate athlete crossing in reverse direction: verify `checkGateCrossing` returns `false`.
  4. Simulate athlete riding parallel to gate without crossing: verify `checkGateCrossing` returns `false`.

### Test Case 2: Orthogonal Polyline Projection & 3D Telemetry (`TST-GEO-001.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/geo/PolylineProgressTrackerTest.kt`
* **Actions & Assertions**:
  1. Create a 3D polyline with known distances and altitudes.
  2. Pass a coordinate exactly at midpoint of segment 1 ($t = 0.5$): verify along-track distance equals $P_0.\text{dist} + 0.5 \times (P_1.\text{dist} - P_0.\text{dist})$, altitude equals midpoint altitude, and cross-track error is near $0.0\text{m}$.
  3. Pass coordinate displaced laterally by $25\text{m}$: verify along-track distance remains accurate and cross-track error evaluates to $\approx 25\text{m}$.

### Test Case 3: Approach Proximity, Heading Alignment & Switchback Rejection (`TST-GEO-001.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/geo/PolylineProgressTrackerTest.kt`
* **Actions & Assertions**:
  1. Verify approach activates when distance $\le 250\text{m}$ and heading difference $\le 45^\circ$.
  2. Verify approach is rejected when distance $\le 250\text{m}$ but heading difference $> 45^\circ$ (opposite direction on switchback).
  3. Verify off-track abort triggers when cross-track error $> 100\text{m}$ (climbs) or $> 50\text{m}$ (segments).

### Test Case 4: Smooth Finish Distance Convergence (`TST-GEO-001.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/geo/PolylineProgressTrackerTest.kt`
* **Actions & Assertions**:
  1. Verify blended remaining distance approaches $0.0\text{m}$ monotonically as athlete nears finish gate.
  2. Verify remaining distance equals exactly $0.0\text{m}$ when athlete crosses finish gate.

### Test Case 5: Repository Delegation & Telemetry Non-Regression (`TST-GEO-001.5`)
* **Scope**: Integration / Unit Test
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepositoryTest.kt`
* **Actions & Assertions**:
  1. Run all existing tests in `LiveClimbsRepositoryTest.kt`; assert 100% pass rate.
  2. Verify `LiveSegmentsRepositoryTest.kt` validates state transitions (`FAR_FAR_AWAY` $\rightarrow$ `APPROACHING` $\rightarrow$ `ON_SEGMENT` $\rightarrow$ `FINISHED`), remaining distance formatting, and PR comparisons.

### Test Case 6: Clean-Room Full Suite Regression (`TST-GEO-001.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full application test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement Clause | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-GEO-001.1` | Unit | `PolylineProgressTracker` | `REQ-GEO-001` (clauses 1, 2) | Specified |
| `TST-GEO-001.2` | Unit | `PolylineProgressTracker` | `REQ-GEO-001` (clauses 1, 3) | Specified |
| `TST-GEO-001.3` | Unit | `PolylineProgressTracker` | `REQ-GEO-001` (clause 5) | Specified |
| `TST-GEO-001.4` | Unit | `PolylineProgressTracker` | `REQ-GEO-001` (clause 4) | Specified |
| `TST-GEO-001.5` | Integration | `LiveSegmentsRepository`, `LiveClimbsRepository` | `REQ-GEO-001` (clauses 6, 7) | Specified |
| `TST-GEO-001.6` | Regression | Full Application Suite | `REQ-PRO-001`, `REQ-GEO-001` | Specified |
