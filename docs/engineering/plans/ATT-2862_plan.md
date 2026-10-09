# Stage 3: Implementation Plan - ATT-2862: Investigate missed segment detection for Schönaich Welle 1 on Route Nur ein Test

**Ticket**: [ATT-2862](https://rainerblind.atlassian.net/browse/ATT-2862)  
**Sub-task**: [ATT-2902](https://rainerblind.atlassian.net/browse/ATT-2902) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-317` (*Geodesic Polyline Orthogonal Projection & Interval Matching for Route-Segment Correlation*)  
**Test Mapping**: `TST-UI-277` (*Geodesic Polyline Orthogonal Projection & Interval Matching Verification*)  
**Branch**: `improvement/ATT-2862`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

In Sprint 2026-41.4 on-device testing of `ATT-2585`, the user observed that the starred segment "Schönaich Welle 1 --> ganz" was not recognized on the route "Nur ein Test". Forensic investigation in Stage 1 revealed that `RouteSegmentMatcher.kt` used naive discrete vertex comparison against route coordinates rather than orthogonal projection onto route polyline segments. Because real-world route points are spaced 30–80 m apart, segment coordinates lying between vertices exceeded the 25 m vertex threshold. Additionally, global independent min/max index selection inverted indices on out-and-back and loop routes.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-317` (*Geodesic Polyline Orthogonal Projection & Interval Matching for Route-Segment Correlation*)
* **Test Mapping**: `TST-UI-277` (*Geodesic Polyline Orthogonal Projection & Interval Matching Verification*)
  * `TST-UI-277.1`: Unit test verifying segment midpoint matching between sparse route vertices.
  * `TST-UI-277.2`: Unit test verifying forward traversal matching on out-and-back route corridors.
  * `TST-UI-277.3`: Unit test asserting rejection invariants (reverse direction, perpendicular crossing, sport mismatch, diverging finish/midpoint).
  * `TST-UI-277.4`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route matching unit tests in `RouteSegmentMatcherTest.kt` and contract tests in `SegmentRoutesSectionContractTest.kt` continue to pass cleanly.
2. **Rejection Invariants Preserved**:
   - Conflicting sport types remain rejected.
   - Reverse traversals ($\Delta \theta > 90^\circ$) remain rejected.
   - Perpendicular crossings and diverging finishes/midpoints remain rejected.
3. **Headless Execution & Thread Safety**: All geodesic computations remain pure Kotlin functions without Android framework or UI dependencies, executable on background dispatchers (`Dispatchers.Default`).
4. **Subtask Direct Completion**: Sub-task transitions directly to `Erledigt` via transition `freigabe` upon Gate 3 audit pass.
5. **Parent Human Decision Gate**: Parent ticket ATT-2862 terminal transition is strictly `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `RouteSegmentMatcher.kt` (`com.atrainingtracker.trainingtracker.routes`)
* **Constants**:
  * Update `CORRIDOR_TOLERANCE_METERS` from 25.0 to 35.0 to absorb outdoor GPS jitter and cross-device drift on undulating terrain.
  * Update `MIDPOINT_TOLERANCE_METERS` from 50.0 to 60.0.
* **Internal Data Structure**:
  ```kotlin
  private data class PathProjection(
      val distanceAlongRouteMeters: Double,
      val crossTrackDistanceMeters: Double,
      val segmentIndex: Int,
      val projectedPoint: LatLng,
      val segmentBearing: Double
  )
  ```
* **Projection Algorithm (`projectPointOntoPath`)**:
  Projects any `LatLng` coordinate onto the polyline line segments `(path[i], path[i+1])`, evaluating the projection parameter $t \in [0.0, 1.0]$, geodesic cross-track distance, exact cumulative distance along the route, and segment travel bearing.
* **Interval Search & Matching Logic**:
  * Finds candidate projection points for segment start and segment end where cross-track distance $\le 35.0\text{ m}$.
  * Evaluates candidate intervals $(p_{start}, p_{end})$ where $p_{end}.distanceAlongRoute > p_{start}.distanceAlongRoute$.
  * Verifies directional bearing alignment at start and end segments ($\le 45.0^\circ$).
  * Verifies route span consistency: $|(D_{end} - D_{start}) - \text{intrinsicLength}| \le \max(100.0, \text{intrinsicLength} \times 0.25)$.
  * For multi-point segments ($\ge 3$ points), verifies midpoint projection distance onto route sub-path $\le 60.0\text{ m}$.
  * Selects the best candidate interval minimizing total cross-track distance.

### UI Consistency (Rule 23)
* **No UI changes**. This is a pure mathematical and algorithmic enhancement within `RouteSegmentMatcher.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate 3 Audit Sign-Off
* Ensure Gate 3 passes and ATT-2902 is `Erledigt`.
* Run mandatory check: `python3 tools/jira_util.py check-gate ATT-2902`.

### Step 2: Implement Polyline Orthogonal Projection & Interval Matching in `RouteSegmentMatcher.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcher.kt`
* Implement `PathProjection`, `projectPointOntoPath`, interval matching, and tolerance tuning.

### Step 3: Expand Unit Tests in `RouteSegmentMatcherTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcherTest.kt`
* Add test cases:
  * `testMatchSegments_sparseRouteVertices_matchesSegmentBetweenVertices`: Route vertices at 60 m spacing; segment terminals located midway between vertices (vertex distance > 25 m, cross-track < 5 m).
  * `testMatchSegments_outAndBackRoute_matchesForwardOutboundLeg`: Out-and-back route (East then West); verifies forward outbound traversal is matched without interference from return leg.
* Validate existing tests: All 10 existing tests must continue to pass without regression.

### Step 4: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.RouteSegmentMatcherTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4, followed by full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: All changes are committed to the feature branch `improvement/ATT-2862`. In case of unexpected issues, `git checkout sprint/2026-41.5` completely isolates the sprint integration baseline.
