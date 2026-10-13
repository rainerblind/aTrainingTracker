# Stage 4: Implementation Report - ATT-2873: Optimize in-ride fork route detection performance via spatial bounding-box rejection and GPS throttling

**Ticket**: [ATT-2873](https://rainerblind.atlassian.net/browse/ATT-2873)  
**Sub-task**: [ATT-2924](https://rainerblind.atlassian.net/browse/ATT-2924) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-MAP-038` (*In-Ride Fork Route Detection Performance Optimization: Spatial Bounding-Box Rejection, Sport-Type Pre-Filtering, and Location Evaluation Throttling*)  
**Test Mapping**: `TST-MAP-040` (*In-Ride Fork Route Detection Optimization Verification*)  
**Branch**: `improvement/ATT-2873`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Summary of Changes

1. **`ForkRouteMatcher.kt` (`com.atrainingtracker.trainingtracker.routes`)**:
   * Added `BOUNDING_BOX_CORRIDOR_MARGIN_METERS = 100.0`.
   * Implemented `isPointWithinBoundingBox` helper function for fast geographic containment checks.
   * Updated `findCandidateRoutes` to accept optional `activeSportType: BSportType? = null`.
   * Added Sport-Type Pre-Filtering via `RouteProximityRanker.matchesSport(route.summary.bSportType, activeSportType)`, instantly rejecting incompatible sports before geometric operations.
   * Added $O(1)$ Spatial Bounding-Box Pre-Filtering, evaluating `minLat/maxLat/minLng/maxLng` plus margin to reject geographically distant routes in $O(1)$ and bypass fine-grained polyline projection.

2. **`ForkNavigationRepository.kt` (`com.atrainingtracker.trainingtracker.routes`)**:
   * Added throttling constants `QUIESCENT_THROTTLE_INTERVAL_MS = 3000L` and `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0`.
   * Added `lastSearchTimeMs` and `lastSearchPos` internal state variables.
   * Updated `onLocationChanged` to accept `activeSportType: BSportType? = null` and deterministic `currentTimeMs: Long = System.currentTimeMillis()`.
   * Implemented quiescent tracking throttling: when `_forkDecisionState.value == null`, candidate route searches are skipped if elapsed time $< 3000\text{ms}$ and displacement $< 20\text{m}$.
   * Preserved full unthrottled 1 Hz responsiveness when a fork decision alert is active (`_forkDecisionState.value != null`), ensuring real-time countdown and immediate autonomous route snapping.

3. **`ForkRouteMatcherTest.kt` (`com.atrainingtracker.trainingtracker.routes`)**:
   * Implemented `TST-MAP-040.1`: Spatial bounding box rejection test verifying $O(1)$ rejection of distant routes (> 30 km away) and `isPointWithinBoundingBox` boundary precision.
   * Implemented `TST-MAP-040.2`: Sport-type pre-filtering test verifying that cycling sessions filter out running routes while allowing cycling and generic routes.

4. **`ForkNavigationRepositoryTest.kt` (`com.atrainingtracker.trainingtracker.routes`)**:
   * Implemented `TST-MAP-040.3`: Quiescent candidate evaluation throttling test verifying that evaluations under 3000ms and 20m displacement are skipped, but trigger when either threshold is exceeded.
   * Implemented `TST-MAP-040.4`: Active alert responsiveness test verifying unthrottled 1 Hz updates and countdown progression during active fork prompts.

---

## 2. Verification & Test Results

* Targeted Unit Tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.Fork*"
  ```
  Result: **BUILD SUCCESSFUL** (13 tests completed, 0 failures, 100% pass rate).
