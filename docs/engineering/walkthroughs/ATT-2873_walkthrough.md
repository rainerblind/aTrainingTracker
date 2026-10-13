# Stage 5 Walkthrough: ATT-2873 - Optimize in-ride fork route detection performance via spatial bounding-box rejection and GPS throttling

**Ticket**: [ATT-2873](https://rainerblind.atlassian.net/browse/ATT-2873)  
**Sub-task**: [ATT-2925](https://rainerblind.atlassian.net/browse/ATT-2925) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-MAP-038` (*In-Ride Fork Route Detection Performance Optimization: Spatial Bounding-Box Rejection, Sport-Type Pre-Filtering, and Location Evaluation Throttling*)  
**Test Spec ID**: `TST-MAP-040`  
**Branch**: `improvement/ATT-2873`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Outcome

In ATT-1955 (`REQ-MAP-031`), in-ride fork route detection was introduced to alert athletes at upcoming trail/road splits when riding along a common outbound corridor. An architectural efficiency audit revealed key scalability bottlenecks:
1. Every stored route in `routesRepository.allRoutes` was subjected to full polyline segment projection calculations on every fix, even when routes were tens or hundreds of kilometers away.
2. Incompatible sport routes were evaluated indiscriminately.
3. Candidate route evaluation ran unthrottled on every 1 Hz GPS update even when stationary or moving slowly without an active alert.

Ticket `ATT-2873` resolved these bottlenecks by establishing requirement `REQ-MAP-038`:
* **$O(1)$ Spatial Bounding-Box Pre-Filtering**: Evaluates route coordinate bounds (`minLat/maxLat/minLng/maxLng` + 100m corridor margin) to reject distant routes in $O(1)$, bypassing trigonometric projections.
* **Sport-Type Pre-Filtering**: Utilizes `RouteProximityRanker.matchesSport` to instantly eliminate incompatible workout disciplines (e.g., filtering out running routes during cycling).
* **Quiescent Location Evaluation Throttling**: Throttles candidate searches to a minimum interval of 3000ms or 20m displacement during quiescent tracking, conserving CPU wake-locks and battery.
* **Real-Time Divergence Alert Latency**: When a fork alert is active (`_forkDecisionState.value != null`), candidate evaluation executes at 1 Hz on every fix without throttling, ensuring real-time distance countdown and immediate autonomous route snapping.

---

## 2. Requirement & Test Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Result | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-MAP-038.1` | `TST-MAP-040.1` | Unit | `ForkRouteMatcherTest` (Bounding-box rejection in $O(1)$) | **PASSED** | `Verified` |
| `REQ-MAP-038.2` | `TST-MAP-040.2` | Unit | `ForkRouteMatcherTest` (Sport-type pre-filtering) | **PASSED** | `Verified` |
| `REQ-MAP-038.3` | `TST-MAP-040.3` | Unit | `ForkNavigationRepositoryTest` (Quiescent throttling: 3s / 20m) | **PASSED** | `Verified` |
| `REQ-MAP-038.3` | `TST-MAP-040.4` | Unit | `ForkNavigationRepositoryTest` (Active alert unthrottled 1 Hz responsiveness) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-MAP-040.5` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 3. Test Execution Results

```text
ForkRouteMatcherTest > projectOntoPolyline_calculatesAccurateDistanceAndCrossTrack PASSED
ForkRouteMatcherTest > findCandidates_sharedDepartureCorridor_returnsMatchingRoutes PASSED
ForkRouteMatcherTest > findCandidates_divergedOrReverseRoute_filtersOutRoute PASSED
ForkRouteMatcherTest > findCandidates_insufficientSharedPrefix_returnsEmpty PASSED
ForkRouteMatcherTest > findCandidates_spatialBoundingBoxRejection_discardsDistantRouteInO1 PASSED
ForkRouteMatcherTest > findCandidates_sportTypePreFiltering_filtersIncompatibleSport PASSED
ForkNavigationRepositoryTest > onLocationChanged_approachingFork_triggersAlertState PASSED
ForkNavigationRepositoryTest > onLocationChanged_pastForkOnBranchA_autoBindsRouteA PASSED
ForkNavigationRepositoryTest > onLocationChanged_deviatesFromBoth_dismissesPrompt PASSED
ForkNavigationRepositoryTest > selectRouteManually_bindsRouteImmediatelyAndClearsState PASSED
ForkNavigationRepositoryTest > dismissPrompt_clearsAlertWithoutBinding PASSED
ForkNavigationRepositoryTest > onLocationChanged_quiescentThrottling_skipsEvaluationWhenUnderTimeAndDistanceThresholds PASSED
ForkNavigationRepositoryTest > onLocationChanged_activeAlert_evaluatesEveryFixWithoutThrottling PASSED

BUILD SUCCESSFUL in 2m 33s
32 actionable tasks: 1 executed, 31 up-to-date
```

---

## 4. Modified Files

* [ForkRouteMatcher.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcher.kt): Added `BOUNDING_BOX_CORRIDOR_MARGIN_METERS = 100.0`, `isPointWithinBoundingBox` helper, and sport / bounding-box pre-filtering in `findCandidateRoutes`.
* [ForkNavigationRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepository.kt): Added `QUIESCENT_THROTTLE_INTERVAL_MS = 3000L`, `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0`, and quiescent evaluation throttling in `onLocationChanged`.
* [ForkRouteMatcherTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcherTest.kt): Added unit tests for $O(1)$ spatial bounding-box rejection and sport filtering (`TST-MAP-040.1`, `TST-MAP-040.2`).
* [ForkNavigationRepositoryTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepositoryTest.kt): Added unit tests for quiescent candidate throttling and active alert responsiveness (`TST-MAP-040.3`, `TST-MAP-040.4`).
* [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): Added `REQ-MAP-038` in status `Verified`.
* [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): Added `TST-MAP-040` in status `Verified`.
