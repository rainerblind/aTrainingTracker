# Stage 3: Implementation Plan - ATT-2873: Optimize in-ride fork route detection performance via spatial bounding-box rejection and GPS throttling

**Ticket**: [ATT-2873](https://rainerblind.atlassian.net/browse/ATT-2873)  
**Sub-task**: [ATT-2923](https://rainerblind.atlassian.net/browse/ATT-2923) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-MAP-038` (*In-Ride Fork Route Detection Performance Optimization: Spatial Bounding-Box Rejection, Sport-Type Pre-Filtering, and Location Evaluation Throttling*)  
**Test Mapping**: `TST-MAP-040` (*In-Ride Fork Route Detection Optimization Verification*)  
**Branch**: `improvement/ATT-2873`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

In ATT-1955 (`REQ-MAP-031`), in-ride fork route detection was introduced to alert athletes at upcoming trail/road splits when riding along a common outbound corridor.
While computational overhead is negligible for small route libraries (< 10 routes), an architectural efficiency audit revealed key scalability bottlenecks in `ForkNavigationRepository.kt` and `ForkRouteMatcher.kt`:
1. **Absence of Spatial Bounding-Box Rejection**: In `ForkRouteMatcher.findCandidateRoutes()`, the engine executes `projectOntoPolyline()` across all coordinate segments for *every* stored route in `routesRepository.allRoutes`. For an athlete with 100+ routes in their library (spanning 500k+ polyline points), this results in thousands of trigonometric and great-circle calculations against distant, geographically irrelevant routes (e.g., routes 500 km away).
2. **Unthrottled 1 Hz Execution**: `ForkNavigationRepository.onLocationChanged()` triggers full candidate evaluation on every 1 Hz GPS fix even during quiescent tracking when moving slowly or stationary.
3. **Absence of Sport-Type Pre-Filtering**: Routes intended for different sports (e.g., Trail Run vs Road Bike) are evaluated indiscriminately.

To resolve these bottlenecks while preserving real-time fork alert responsiveness, we introduce $O(1)$ spatial bounding-box rejection, sport-type pre-filtering, and quiescent evaluation throttling.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-038`
  * `REQ-MAP-038.1`: Spatial Bounding-Box Pre-Filtering ($O(1)$ Spatial Rejection with margin = 100m).
  * `REQ-MAP-038.2`: Sport-Type Pre-Filtering via `RouteProximityRanker.matchesSport`.
  * `REQ-MAP-038.3`: Location Evaluation Throttling during quiescent tracking (interval >= 3000ms or distance >= 20m), while maintaining unthrottled 1 Hz evaluation when a fork decision alert is active.
  * `REQ-MAP-038.4`: Invariant preservation (accuracy, auto-binding, zero false negatives).
* **Test Mapping**: `TST-MAP-040`
  * `TST-MAP-040.1`: Unit test verifying that routes with bounding boxes $> 100\text{m}$ from the athlete are rejected in $O(1)$ without polyline projection.
  * `TST-MAP-040.2`: Unit test verifying that candidate route matching pre-filters out incompatible sport types (e.g. running routes when cycling).
  * `TST-MAP-040.3`: Unit test verifying that quiescent candidate evaluations are skipped if time elapsed $< 3000\text{ms}$ and displacement $< 20\text{m}$.
  * `TST-MAP-040.4`: Unit test verifying unthrottled 1 Hz evaluation when a fork decision alert is active.
  * `TST-MAP-040.5`: Clean-room full regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero False Negative Invariant**: The bounding box margin ($\ge 100\text{m}$) strictly envelopes the corridor tolerance (`MAX_CORRIDOR_TOLERANCE_METERS` = 50.0m). Any route that could possibly match the corridor tolerance is guaranteed to pass the bounding box check.
2. **Real-Time Divergence Alert Latency**: When an alert is active (`_forkDecisionState.value != null`), candidate evaluation executes at 1 Hz on every GPS update to ensure continuous distance countdown and prompt auto-binding.
3. **Corridor Tolerance & Shared Prefix Rules**: `MAX_CORRIDOR_TOLERANCE_METERS` (50.0m) and `MIN_SHARED_PREFIX_METERS` (300.0m) remain unchanged.
4. **Autonomous Binding & Auto-Dismissal**: Auto-binding ($D_{\text{past}} \ge 50\text{m}$) and deviation dismissal remain 100% intact.
5. **Human Gate Governance**: Sub-task `ATT-2923` transitions directly to `Erledigt` upon Gate 3 pass; parent `ATT-2873` transitions only to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ForkRouteMatcher.kt` (`com.atrainingtracker.trainingtracker.routes`)
1. Add constant:
   ```kotlin
   const val BOUNDING_BOX_CORRIDOR_MARGIN_METERS = 100.0
   ```
2. Add helper function `isPointWithinBoundingBox`:
   ```kotlin
   fun isPointWithinBoundingBox(
       point: LatLng,
       minLat: Double,
       maxLat: Double,
       minLng: Double,
       maxLng: Double,
       marginMeters: Double = BOUNDING_BOX_CORRIDOR_MARGIN_METERS
   ): Boolean {
       val deltaLat = marginMeters / 111_000.0
       val cosLat = cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01)
       val deltaLng = marginMeters / (111_000.0 * cosLat)

       return point.latitude >= (minLat - deltaLat) &&
              point.latitude <= (maxLat + deltaLat) &&
              point.longitude >= (minLng - deltaLng) &&
              point.longitude <= (maxLng + deltaLng)
   }
   ```
3. Update `findCandidateRoutes` signature and implementation:
   ```kotlin
   fun findCandidateRoutes(
       allRoutes: List<RouteWithPath>,
       currentPos: LatLng,
       recentHistory: List<LatLng>? = null,
       activeSportType: BSportType? = null
   ): List<RouteWithPath> {
       val candidates = mutableListOf<RouteWithPath>()
       val deltaLat = BOUNDING_BOX_CORRIDOR_MARGIN_METERS / 111_000.0
       val cosLat = cos(Math.toRadians(currentPos.latitude)).coerceAtLeast(0.01)
       val deltaLng = BOUNDING_BOX_CORRIDOR_MARGIN_METERS / (111_000.0 * cosLat)

       for (route in allRoutes) {
           if (route.path.size < 2) continue

           // 1. Sport-Type Pre-Filtering (REQ-MAP-038 clause 2)
           if (activeSportType != null && !RouteProximityRanker.matchesSport(route.summary.bSportType, activeSportType)) {
               continue
           }

           // 2. Spatial Bounding-Box Pre-Filtering (REQ-MAP-038 clause 1)
           val minLat = route.summary.minLat ?: route.path.minOf { it.latLng.latitude }
           val maxLat = route.summary.maxLat ?: route.path.maxOf { it.latLng.latitude }
           val minLng = route.summary.minLng ?: route.path.minOf { it.latLng.longitude }
           val maxLng = route.summary.maxLng ?: route.path.maxOf { it.latLng.longitude }

           if (currentPos.latitude < minLat - deltaLat ||
               currentPos.latitude > maxLat + deltaLat ||
               currentPos.longitude < minLng - deltaLng ||
               currentPos.longitude > maxLng + deltaLng
           ) {
               continue
           }

           // 3. Fine-grained projection onto polyline
           val proj = projectOntoPolyline(currentPos, route.path)
           ...
   ```

### Component 2: `ForkNavigationRepository.kt` (`com.atrainingtracker.trainingtracker.routes`)
1. Add throttling constants:
   ```kotlin
   const val QUIESCENT_THROTTLE_INTERVAL_MS = 3000L
   const val QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0
   ```
2. Track state for throttling:
   ```kotlin
   private var lastSearchTimeMs: Long = 0L
   private var lastSearchPos: LatLng? = null
   ```
3. Update `onLocationChanged`:
   ```kotlin
   fun onLocationChanged(
       currentPos: LatLng,
       recentHistory: List<LatLng>? = null,
       activeSportType: BSportType? = null,
       currentTimeMs: Long = System.currentTimeMillis()
   ) {
       if (routesRepository.activeNavigatedRouteId.value != null) {
           if (_forkDecisionState.value != null) {
               _forkDecisionState.value = null
           }
           return
       }

       if (isDismissed) {
           return
       }

       val activeState = _forkDecisionState.value
       val isAlertActive = activeState != null

       if (!isAlertActive && lastSearchPos != null) {
           val elapsedMs = currentTimeMs - lastSearchTimeMs
           val distanceMoved = GeoUtils.haversineDistanceMeters(
               lastSearchPos!!.latitude, lastSearchPos!!.longitude,
               currentPos.latitude, currentPos.longitude
           )
           if (elapsedMs < QUIESCENT_THROTTLE_INTERVAL_MS && distanceMoved < QUIESCENT_THROTTLE_DISTANCE_METERS) {
               return
           }
       }

       lastSearchTimeMs = currentTimeMs
       lastSearchPos = currentPos

       val allRoutes = routesRepository.allRoutes.value
       val candidates = ForkRouteMatcher.findCandidateRoutes(allRoutes, currentPos, recentHistory, activeSportType)
       ...
   ```

### UI Consistency (Rule 23)
* Non-UI algorithmic performance ticket; no user interface modifications.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate 3 Audit Sign-Off
* Ensure subtask `ATT-2923` is audited and approved (`Erledigt`).
* Run mandatory check: `python3 tools/jira_util.py check-gate ATT-2923`.

### Step 2: Create Stage 4 Subtask & Transition to In Bearbeitung
* Create `[Implementation]` sub-task under `ATT-2873`.
* Transition subtask to `In Bearbeitung`.

### Step 3: Implement `ForkRouteMatcher.kt`
* Add bounding-box pre-filtering and `activeSportType` parameter to `findCandidateRoutes`.
* Add `isPointWithinBoundingBox` helper function.

### Step 4: Implement `ForkNavigationRepository.kt`
* Add `QUIESCENT_THROTTLE_INTERVAL_MS` (3000L) and `QUIESCENT_THROTTLE_DISTANCE_METERS` (20.0).
* Implement quiescent throttling with deterministic `currentTimeMs` parameter in `onLocationChanged`.

### Step 5: Expand Unit Tests in `ForkRouteMatcherTest.kt`
* Implement `TST-MAP-040.1`: Bounding box rejection test with distant routes.
* Implement `TST-MAP-040.2`: Sport type pre-filtering test with matching and conflicting sports.

### Step 6: Expand Unit Tests in `ForkNavigationRepositoryTest.kt`
* Implement `TST-MAP-040.3`: Quiescent throttling test (skipping candidate evaluation within 3s and 20m).
* Implement `TST-MAP-040.4`: Active alert test (evaluating every fix when alert is active).

### Step 7: Run Targeted Unit Tests
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.Fork*"
  ```

### Step 8: Document Construction Deliverable & Pass Gate 4
* Author `docs/engineering/implementation/ATT-2873_implementation.md`.
* Transition Stage 4 subtask to `In Überprüfung` and run audit (`python3 tools/review_agent.py audit <KEY>`).

---

## 6. Clean-Room Regression Verification (Stage 5)
* Execute full clean-room suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Update `REQ-MAP-038` and `TST-MAP-040` to `Verified` in `docs/requirements.md` and `docs/tests.md`.
* Author walkthrough: `docs/engineering/walkthroughs/ATT-2873_walkthrough.md`.
* Commit changes on `improvement/ATT-2873`, merge into `sprint/2026-41.5` (`--no-ff`), and delete branch.
* Advance parent ticket `ATT-2873` to `Final Review (Human)`.
