# Stage 2: Requirement & Test Specification - ATT-2862: Investigate missed segment detection for Schönaich Welle 1 on Route Nur ein Test

**Ticket**: [ATT-2862](https://rainerblind.atlassian.net/browse/ATT-2862)  
**Sub-task**: [ATT-2901](https://rainerblind.atlassian.net/browse/ATT-2901) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-317` (*Geodesic Polyline Orthogonal Projection & Interval Matching for Route-Segment Correlation*)  
**Test Spec ID**: `TST-UI-277`  
**Branch**: `improvement/ATT-2862`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-317)

### 1.1 Problem Statement & Rationale
In real-world cycling activities and route planning, GPS tracks and GPX route files have discrete vertices that are spaced 30 m to 80 m apart along roads. Strava segments and recorded routes rarely have coincident vertex coordinates. The initial implementation of [RouteSegmentMatcher.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcher.kt) compared segment start, end, and midpoint coordinates strictly against discrete route vertices, causing segments whose terminal coordinates fall midway between vertices (such as "Schönaich Welle 1 --> ganz" on "Nur ein Test") to be silently rejected because vertex distances exceeded the 25 m threshold. Furthermore, independent global minimum search across the entire route inverted start/end indices on out-and-back and loop routes.

### 1.2 Functional & Architectural Requirements
The system SHALL evaluate candidate route-segment spatial associations in `RouteSegmentMatcher.kt` using geodesic orthogonal projection onto polyline line segments and interval-based forward traversal tracking:
1. **Orthogonal Polyline Segment Projection**:
   * Calculate orthogonal projection of segment start, end, and sampled midpoint coordinates onto each line segment $(P_i, P_{i+1})$ of the candidate route polyline.
   * Determine the projected coordinate $Q \in (P_i, P_{i+1})$, orthogonal cross-track distance $d_{\text{cross-track}}$, and exact interpolated cumulative route distance $D_{\text{along}} = D_i + t \cdot (D_{i+1} - D_i)$ where $t \in [0.0, 1.0]$.
2. **Corridor Distance Tolerance & Real-World GPS Adaptability**:
   * Segment terminal point (start/end) corridor proximity threshold: $d_{\text{cross-track}} \le 35.0\text{ m}$ (`CORRIDOR_TOLERANCE_METERS = 35.0`).
   * Multi-point segment ($\ge 3$ points) midpoint corridor threshold: $d_{\text{mid\_cross-track}} \le 60.0\text{ m}$ (`MIDPOINT_TOLERANCE_METERS = 60.0`).
3. **Interval-Based Forward Traversal on Circuit, Loop & Out-and-Back Routes**:
   * Evaluate candidate forward traversal intervals $(proj_{\text{start}}, proj_{\text{end}})$ satisfying:
     a. $proj_{\text{end}}.distanceAlongRoute > proj_{\text{start}}.distanceAlongRoute$.
     b. Both start and end cross-track distances $\le 35.0\text{ m}$.
     c. Span distance consistency: $|(D_{\text{end}} - D_{\text{start}}) - \text{intrinsicDistance}| \le \max(100.0, \text{intrinsicDistance} \times 0.25)$.
4. **Directional Bearing Alignment on Projected Line Segments**:
   * Forward bearing alignment calculated using travel bearing along the route's nearest polyline segment $(P_i, P_{i+1})$ containing the projection point, compared to initial segment traversal bearing.
   * Angular difference: $\Delta \theta \le 45.0^\circ$ (`MAX_BEARING_DELTA_DEGREES = 45.0`) with positive vector dot product ($\cos(\Delta \theta) > 0$).
5. **Preservation of System Invariants & Zero Regression**:
   * Reverse traversals ($\Delta \theta > 90^\circ$), perpendicular crossing corridors, conflicting sport types, diverging finishes, and horseshoe midpoints remain strictly rejected.
   * Zero Android UI dependencies in `RouteSegmentMatcher.kt` (pure functional evaluation).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Sparse Route Point Resolution)**:
  * *Given* a route with vertices spaced 50 m apart and a candidate segment whose start and end coordinates fall midway between vertices (vertex distance > 25 m, cross-track distance < 10 m),
  * *When* evaluated via `RouteSegmentMatcher.matchSegments` or `findRoutesContainingSegment`,
  * *Then* the segment SHALL be successfully matched with accurate start and end route distances.
* **Criterion 2 (Out-and-Back Route Support)**:
  * *Given* an out-and-back route that traverses a segment in forward direction on the outbound leg and returns in reverse direction on the return leg,
  * *When* matching segments,
  * *Then* the forward traversal interval SHALL be matched with $D_{\text{end}} > D_{\text{start}}$ and valid span distance.
* **Criterion 3 (Rejection Invariants)**:
  * *Given* candidate segments with opposing sport types, reverse traversal, perpendicular crossing, diverging finish, or horseshoe midpoint,
  * *When* matching segments,
  * *Then* each invalid candidate SHALL be strictly rejected.

### 1.4 System Invariants
1. Pure mathematical calculation with zero UI framework dependencies.
2. Inverted route matching (`findRoutesContainingSegment`) reuses the exact same matching engine.
3. 100% clean-room test pass rate across all unit and contract tests.

---

## 2. Test Specification (TST-UI-277)

### Test Case 1: Sparse Route Point Midpoint Matching (`TST-UI-277.1`)
* **Scope**: Unit Test (`RouteSegmentMatcherTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcherTest.kt`
* **Preconditions**: A route with 60 m vertex spacing. A candidate segment starting at 230 m (midway between 180 m and 240 m vertices) and ending at 510 m (midway between 480 m and 540 m vertices).
* **Action**: Execute `RouteSegmentMatcher.matchSegments(route, listOf(segment), BSportType.BIKE)`.
* **Expected Result**: 1 match returned; `startDistanceMeters` $\approx 230.0\text{ m}$, `endDistanceMeters` $\approx 510.0\text{ m}$.

### Test Case 2: Out-and-Back Route Forward Traversal Matching (`TST-UI-277.2`)
* **Scope**: Unit Test (`RouteSegmentMatcherTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcherTest.kt`
* **Preconditions**: A route that goes East from 0 to 5000 m, then turns around and goes West from 5000 m back to 0 m (total 10000 m). A candidate segment that goes East from 1000 m to 2500 m.
* **Action**: Execute `RouteSegmentMatcher.matchSegments(outAndBackRoute, listOf(eastSegment), BSportType.BIKE)`.
* **Expected Result**: 1 match returned corresponding to the outbound leg; `startDistanceMeters` $\approx 1000.0\text{ m}$, `endDistanceMeters` $\approx 2500.0\text{ m}$.

### Test Case 3: Rejection Invariants Validation (`TST-UI-277.3`)
* **Scope**: Unit Test (`RouteSegmentMatcherTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcherTest.kt`
* **Preconditions**: Existing and extended suite of negative cases: reverse traversal, perpendicular crossing, conflicting sport type, diverging finish, horseshoe midpoint.
* **Action**: Execute `RouteSegmentMatcher.matchSegments` on all negative candidates.
* **Expected Result**: All negative candidates return empty match lists.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-277.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across the complete unit test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-277.1` | Unit | `RouteSegmentMatcher.matchSegmentsPure` (sparse route) | `REQ-UI-317` | Specified |
| `TST-UI-277.2` | Unit | `RouteSegmentMatcher.matchSegmentsPure` (out-and-back) | `REQ-UI-317` | Specified |
| `TST-UI-277.3` | Unit | `RouteSegmentMatcher.matchSegmentsPure` (rejection invariants) | `REQ-UI-317` | Specified |
| `TST-UI-277.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
