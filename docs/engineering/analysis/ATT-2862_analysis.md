# Stage 1 Analysis: ATT-2862 - Investigate missed segment detection for Schönaich Welle 1 on Route Nur ein Test

**Ticket**: [ATT-2862](https://rainerblind.atlassian.net/browse/ATT-2862)  
**Sub-task**: [ATT-2900](https://rainerblind.atlassian.net/browse/ATT-2900) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Branch**: `improvement/ATT-2862`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.4 on-device review of `ATT-2585` (Show Saved Routes Containing Segment in Segment Details), physical verification on a Google Pixel 10 revealed that the starred segment **"Schönaich Welle 1 --> ganz"** was not detected as belonging to or traversed by the route **"Nur ein Test"** in `RouteSegmentMatcher`.

### Expected Behavior
When an athlete inspects a route ("Nur ein Test") that physically traverses an active starred segment ("Schönaich Welle 1 --> ganz"), or conversely inspects the segment details to view containing routes, `RouteSegmentMatcher.matchSegments` and `findRoutesContainingSegment` should reliably identify the spatial overlap and register the segment/route association.

### Current Behavior
The segment is silently omitted from the route's segments breakdown, and the route is omitted from the segment's containing routes section. The matching engine evaluates the pair and rejects the match without error or diagnostic warning.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of [RouteSegmentMatcher.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcher.kt), existing test suites, and geodesic path geometry identified four compounding architectural flaws in the matching algorithm:

### 1. Discrete Vertex Comparison vs. Polyline Segment Orthogonal Projection (Point-to-Vertex Fallacy)
In `RouteSegmentMatcher.kt`:
```kotlin
for (i in routePath.indices) {
    val routePt = routePath[i]
    val dStart = GeoUtils.haversineDistanceMeters(segStart.latLng.latitude, segStart.latLng.longitude, routePt.latLng.latitude, routePt.latLng.longitude)
    if (dStart < bestStartDist) { bestStartDist = dStart; bestStartIndex = i }
    val dEnd = GeoUtils.haversineDistanceMeters(segEnd.latLng.latitude, segEnd.latLng.longitude, routePt.latLng.latitude, routePt.latLng.longitude)
    if (dEnd < bestEndDist) { bestEndDist = dEnd; bestEndIndex = i }
}
if (bestStartDist > CORRIDOR_TOLERANCE_METERS || bestEndDist > CORRIDOR_TOLERANCE_METERS) {
    continue
}
```
* **Failure Mechanism**: `RouteSegmentMatcher` computes geodesic distance from the segment terminals (`segStart`, `segEnd`) strictly to the **discrete vertices** (`routePt`) of the route, rather than calculating the orthogonal cross-track distance to the route's polyline segments (`routePath[i] -> routePath[i+1]`).
* In real-world GPS activities or planned GPX tracks (from Komoot, Strava, or GPS recordings), route vertices are typically spaced 30 m to 80 m apart along straight sections or gentle curves.
* If a segment starts or terminates midway between two route vertices (e.g. 30 m from vertex $i$ and 30 m from vertex $i+1$), both vertex distances exceed `CORRIDOR_TOLERANCE_METERS` (25.0 m). The candidate is immediately rejected, despite lying *directly* on the polyline corridor ($d_{\text{cross-track}} \approx 0\text{ m}$).
* By contrast, [ForkRouteMatcher.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcher.kt) and [WaypointDistanceCalculator.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/WaypointDistanceCalculator.kt) correctly calculate point-to-segment orthogonal projection.

### 2. Independent Global Minimum Index Selection on Loops and Out-and-Back Routes
* In `RouteSegmentMatcher.kt`, `bestStartIndex` and `bestEndIndex` are determined by global minimization across the entire route independently.
* If a route is an out-and-back route or circuit (such as "Nur ein Test" where the rider traversed out and returned on the same road):
  - On the outbound leg, the route passes `segStart` (e.g. at index 15, $d = 8\text{ m}$) and `segEnd` (at index 35, $d = 9\text{ m}$).
  - On the return leg (in reverse), the route passes near `segEnd` (e.g. at index 120, $d = 4\text{ m}$) and near `segStart` (at index 140, $d = 3\text{ m}$).
  - Global minimization selects `bestStartIndex = 140` and `bestEndIndex = 120`.
  - Condition `if (bestEndIndex <= bestStartIndex) continue` triggers, and the segment is rejected as an apparent reverse traversal, completely missing the valid forward traversal between indices 15 and 35.

### 3. Instantaneous 2-Point Bearing Alignment Sensitivity ($\Delta \theta \le 45^\circ$)
* In `RouteSegmentMatcher.kt`:
```kotlin
val segStartBearing = GeoUtils.calculateInitialBearing(segStart.latLng.latitude, segStart.latLng.longitude, candidatePath[1].latLng.latitude, candidatePath[1].latLng.longitude)
val routeStartBearing = GeoUtils.calculateInitialBearing(routePath[bestStartIndex].latLng.latitude, routePath[bestStartIndex].latLng.longitude, routePath[routeNextIndex].latLng.latitude, routePath[routeNextIndex].latLng.longitude)
```
* Calculating heading using only the first two raw points of the segment and two arbitrary discrete route vertices is highly susceptible to GPS noise (±5–10 m jitter on 10 m vectors generates up to 50° angular distortion) and local bends/intersections.
* Aligning against the bearing of the projected polyline segment onto which the anchor falls (or a lookahead window) eliminates this instability while preserving directional rejection of genuine reverse traversals ($> 90^\circ$).

### 4. Real-World Corridor Proximity & GPS Accuracy in Undulating Terrain
* In undulating, tree-lined topography such as Schönaich ("Schönaich Welle 1"), differential GPS drift between different recording hardware (e.g. dedicated bike computer vs. smartphone) and road/bike path offsets often reaches 20–30 m.
* A strict 25.0 m vertex-only threshold without projection creates extreme brittleness. Enhancing the corridor tolerance to 35.0 m when combined with orthogonal polyline projection and forward bearing verification provides robust detection without false positives.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Upgrade `RouteSegmentMatcher` to utilize orthogonal polyline segment projection for segment start, end, and midpoint sampling.
  2. Maintain accurate `distanceAlongRouteMeters` and `crossTrackDistanceMeters` based on polyline projection rather than discrete vertex distance.
  3. Support localized traversal intervals to correctly match forward segments on out-and-back and loop routes.
  4. Tune `CORRIDOR_TOLERANCE_METERS` to 35.0 m and midpoint tolerance to 60.0 m for reliable outdoor GPS matching.
  5. Expand `RouteSegmentMatcherTest.kt` with tests for:
     - Segments starting/ending between sparse route vertices (point-to-vertex failure case).
     - Segments on out-and-back / loop routes.
     - Real-world tolerance verification.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not modify UI components or bottom sheets (`RouteDetailSheet.kt`, `SegmentDetailSheet.kt`, `RouteOnMapScreen.kt`).
  * Do not alter live GPS recording or workout trackpoint storage.
  * Do not modify database schemas or migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-302` (*Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy*) and `REQ-UI-305` (*Inverted Route-Segment Spatial Association, Segment-Containing Routes Breakdown & Bidirectional Navigation*).
* **Historical Origin & Commit Trace**: Tickets `ATT-2583` (Sprint 2026-41.3) and `ATT-2585` (Sprint 2026-41.4).
* **Root Reason for Existing Formulation**: In ATT-2583, `RouteSegmentMatcher` was implemented as a simple first iteration using a discrete vertex loop. The synthetic unit test suite placed route vertices and segment points at identical coordinates ($d = 0.0\text{ m}$), masking the discrete vertex discretization gap.
* **Preservation of Core Invariants**:
  - Conflicting sport types remain rejected.
  - Genuine reverse traversals ($\Delta \theta > 90^\circ$, negative dot product) remain rejected.
  - Perpendicular crossings and diverging finishes/midpoints remain rejected.
  - All existing unit tests in `RouteSegmentMatcherTest.kt` must continue to pass.

---

## 5. Architectural Strategy & High-Level Solution

### Component: `RouteSegmentMatcher.kt`
1. **Polyline Projection Helper (`projectPointOntoPath`)**:
   Implement geodesic orthogonal projection of a coordinate onto the polyline line segments `(p[i], p[i+1])`:
   - Computes local equirectangular / spherical projection $t \in [0.0, 1.0]$.
   - Calculates geodesic cross-track distance to the projected coordinate.
   - Calculates exact cumulative distance along the route: $D = D_i + t \cdot (D_{i+1} - D_i)$.
2. **Interval Matching for Forward Progression**:
   - For a candidate segment, find all valid forward intervals $(proj_{start}, proj_{end})$ where:
     - $proj_{start}.crossTrack \le \text{CORRIDOR\_TOLERANCE\_METERS}$ (35.0 m).
     - $proj_{end}.crossTrack \le \text{CORRIDOR\_TOLERANCE\_METERS}$ (35.0 m).
     - $proj_{end}.distanceAlongRoute > proj_{start}.distanceAlongRoute$.
     - Span distance $|(D_{end} - D_{start}) - \text{intrinsicLength}| \le \max(100.0, \text{intrinsicLength} \times 0.25)$.
   - Select the optimal matching interval with minimal cross-track deviation.
3. **Directional Bearing Alignment**:
   - Compute route bearing along the projected line segment `(p[startSegmentIndex], p[startSegmentIndex+1])`.
   - Compare with segment heading: $\Delta \theta \le \text{MAX\_BEARING\_DELTA\_DEGREES}$ (45.0°).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regressions in `RouteSegmentMatcherTest.kt` and `SegmentRoutesSectionContractTest.kt`.
  2. Forward matching, reverse rejection, and sport filtering preserved.
  3. Headless pure computation runs synchronously or on `Dispatchers.Default` without Android UI framework dependencies.
  4. Parent ticket Human Decision Gate remains strictly intact (`Final Review (Human)`).
* **Risk Rating**: **LOW**. The changes are isolated to mathematical spatial geometry calculations in `RouteSegmentMatcher.kt` and have dedicated comprehensive unit tests.
