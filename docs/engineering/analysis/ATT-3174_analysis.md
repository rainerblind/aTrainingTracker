# Stage 1: Problem Domain & Root Cause Analysis - ATT-3174

## 1. Executive Summary & Problem Domain
* **Issue**: [ATT-3174](https://atrainingtracker.atlassian.net/browse/ATT-3174): `[Verbesserung] Linearly interpolate sparse path points on Climb elevation profile and live progress metrics`
* **Parent Epic**: [ATT-2464](https://atrainingtracker.atlassian.net/browse/ATT-2464): *ClimbPro Live Climb Engine*
* **Target Requirement**: Amend `REQ-MAP-027` (`REQ-MAP-039`)
* **Target Test Spec**: `TST-MAP-039`
* **Sprint**: `2026-41.9`

### Problem Description
During testing of the Live Climb cockpit bottom sheet (`LiveClimbSheet.kt`), when moving along routes with sparse trackpoints (50m–200m vertex spacing on straight roads or imported GPX tracks):
1. The rider indicator pin on the elevation profile canvas remains completely frozen at a fixed X coordinate and then abruptly leaps forward to the next discrete vertex.
2. In-ride telemetry metrics (remaining distance, remaining elevation gain, current gradient, and completed distance) remain static over long stretches, suddenly jumping in coarse discrete increments.
3. On tracks with vertices $> 100\text{ m}$ apart, an athlete riding precisely on the road between two vertices could approach or exceed the 100m off-route threshold (`CLIMB_OFF_ROUTE_THRESHOLD_METERS`) because distance was measured to the nearest discrete vertex rather than the polyline segment.

---

## 2. Forensic Investigation & Technical Root Cause

### 2.1 Code Touchpoint in `LiveClimbsRepository.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt` (lines 220–256):
```kotlin
// Find closest point along climb path
var closestIdx = 0
var minPointDist = Double.MAX_VALUE
for (i in path.indices) {
    Location.distanceBetween(location.latitude, location.longitude, path[i].latLng.latitude, path[i].latLng.longitude, results)
    val d = results[0].toDouble()
    if (d < minPointDist) {
        minPointDist = d
        closestIdx = i
    }
}

// Off-climb check
if (minPointDist > CLIMB_OFF_ROUTE_THRESHOLD_METERS) {
    return null
}

val completedDist = path[closestIdx].distance - path.first().distance
val remainingDist = (climb.distanceMeters - completedDist).coerceAtLeast(0.0)
val currentAlt = path[closestIdx].altitude
val summitAlt = path.last().altitude
val remainingGain = (summitAlt - currentAlt).coerceAtLeast(0.0)
val progressFraction = (completedDist / climb.distanceMeters).coerceIn(0.0, 1.0).toFloat()
```

### 2.2 Root Cause Analysis
1. **Discrete Vertex Snapping**: Progress is calculated solely against a single vertex `path[closestIdx]`. Between adjacent vertices $P_i$ and $P_{i+1}$, `completedDist`, `remainingDist`, `currentAlt`, `remainingGain`, and `progressFraction` remain identical for all GPS fixes until the athlete crosses the halfway Voronoi boundary between the vertices.
2. **False Off-Route Vulnerability**: `minPointDist` measures the distance from the athlete to the nearest discrete vertex, rather than the perpendicular cross-track distance to the road segment. If adjacent vertices are 180m apart, an athlete riding in the center of the road is 90m from both vertices, leaving a dangerous 10m margin before falsely triggering a 100m off-route climb cancellation.
3. **Continuous Polyline Projection Solution**:
   - In accordance with `docs/design_guidelines.md` Section 2.4 (*Continuous Linear Projection for Sparse Geometry Progress Tracking*), the GPS position must be orthogonally projected onto the nearest line segment $[P_i, P_{i+1}]$.
   - Fractional scalar $t \in [0.0, 1.0]$ along the segment determines continuous interpolated distance and altitude:
     $$\text{completedDist} = (P_i.\text{dist} - P_0.\text{dist}) + t \times (P_{i+1}.\text{dist} - P_i.\text{dist})$$
     $$\text{currentAlt} = P_i.\text{alt} + t \times (P_{i+1}.\text{alt} - P_i.\text{alt})$$
     $$\text{progressFraction} = \left(\frac{\text{completedDist}}{\text{climb.distanceMeters}}\right).coerceIn(0.0, 1.0)$$

---

## 3. Chesterton's Fence & Requirement Archaeology

1. **Original Requirement ID & Target**:
   - `REQ-MAP-027` (*ClimbPro Real-Time Live Climb Cockpit Sheet with Dynamically Colored Gradient Profile & Elevation Pacing*, Sprint 2026-40.14, ATT-1281).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.14 (`ATT-1281`). The initial engine implementation adopted discrete vertex lookup for simplicity and minimum CPU overhead.
   - Refined in Sprint 2026-41.7 (`ATT-2947`, `REQ-UI-328`) and Sprint 2026-41.8 (`ATT-3103`, `REQ-UI-341`), focusing on UI layout, typography, and `@Preview` tooling.
3. **Reason for Refinement (Chesterton's Fence Forensics)**:
   - Field testing on high-resolution displays (Google Pixel 10) demonstrated that discrete vertex jumping breaks the premium user experience and gives the impression of GPS lag or frozen UI.
   - Design Guidelines §2.4 was authored specifically to codify the continuous linear projection invariant across all geometric path progress trackers.
4. **Preservation of Core Invariants**:
   - Summit gate threshold (`CLIMB_SUMMIT_GATE_THRESHOLD_METERS = 35.0m`) and start gate threshold (`CLIMB_START_GATE_THRESHOLD_METERS = 30.0m`).
   - Bearing alignment filtering during approach (`CLIMB_APPROACH_THRESHOLD_METERS = 250.0m`).
   - Clean off-route dismissal when cross-track distance exceeds 100m.

---

## 4. Scope Bounding & Invariants (Rule 26 / ATT-1250)

* **Bounded Scope**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt` (lines 218–258).
  - `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`.
  - `docs/requirements.md` and `docs/tests.md`.
* **Zero Cross-Cutting Modifications**:
  - `Climb.kt` data structures remain unchanged.
  - `LiveClimbSheet.kt` rendering consumes the smoothly updated fields without requiring layout refactoring.
  - Zero modifications to database schemas, foreign keys, or Android manifests.

---

## 5. ASPICE Traceability Matrix & Impact Analysis

| Component | Target File | Impact |
| :--- | :--- | :--- |
| **Climb Tracking Engine** | `LiveClimbsRepository.kt` | Implement orthogonal projection onto segments $[P_i, P_{i+1}]$ with linear interpolation of distance, altitude, progress fraction, and segment gradient. |
| **Unit Verification** | `LiveClimbsRepositoryTest.kt` | Add test cases verifying continuous interpolation on sparse paths (e.g. at 25%, 50%, 75% between 500m vertices) and accurate cross-track off-route evaluation. |
| **Living Documentation** | `docs/requirements.md` | Add `REQ-MAP-039` (amending `REQ-MAP-027`). |
| **Test Documentation** | `docs/tests.md` | Add `TST-MAP-039`. |
| **Design Guidelines** | `docs/design_guidelines.md` | Align with Section 2.4. |
