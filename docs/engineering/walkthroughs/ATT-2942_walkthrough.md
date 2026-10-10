# Stage 5 Verification & Walkthrough: ATT-2942 - Restrict in-ride fork route candidate matching to active selected routes

**Ticket**: [ATT-2942](https://atrainingtracker.atlassian.net/browse/ATT-2942)  
**Sub-task**: [ATT-2993](https://atrainingtracker.atlassian.net/browse/ATT-2993) (`[Verification]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2942`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

Ticket `ATT-2942` restricts in-ride fork-in-the-road route candidate matching to only evaluate routes that have been explicitly selected and activated by the athlete (`route.summary.isSelected == true`), and implements an immediate $O(1)$ quiescent short-circuit when fewer than 2 active selected routes exist:
1. **Active Selected Route Candidate Filtering (`REQ-MAP-041`)**:
   - In `ForkNavigationRepository.onLocationChanged()`, candidate route evaluation now exclusively queries routes where `route.summary.isSelected == true`.
   - Historical or unselected routes sharing an outbound departure corridor are completely ignored, eliminating spurious fork decision alerts for routes the athlete never intended to ride.
2. **$O(1)$ Quiescent Short-Circuit**:
   - When fewer than 2 routes are selected in the database (0 or 1 route, the standard state for most workouts), `ForkNavigationRepository` immediately returns in $O(1)$ without evaluating candidate search throttling, spatial bounding-box checks, or polyline projections.
   - If an active fork prompt is displayed and selected routes drop below 2 (e.g. route unselected mid-ride), the alert state is immediately cleared and dismissed.
3. **Parameterized Selection Enforcement in `ForkRouteMatcher`**:
   - `ForkRouteMatcher.findCandidateRoutes()` supports `requireSelected: Boolean = false` (defaulting to `false` for raw geometrical caller compatibility), discarding unselected routes in $O(1)$ before segment projections when set to `true`.
4. **Preservation of System Invariants**:
   - Quiescent tracking throttling (`QUIESCENT_THROTTLE_INTERVAL_MS = 3000L`, `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0`), unthrottled 1 Hz active alert responsiveness (`REQ-MAP-038`), autonomous route snapping ($\ge 50\text{ m}$ past fork, cross-track $< 25\text{ m}$), and 1-tap manual route binding remain 100% operational.
   - Clean-room unit test suite achieved 100% pass rate across all 2,251 tests.

---

## 2. Changes Implemented

### 2.1 Domain & Candidate Matching
* [ForkRouteMatcher.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcher.kt):
  - Added parameter `requireSelected: Boolean = false` to `findCandidateRoutes()`.
  - Added filter check `if (requireSelected && !route.summary.isSelected) continue` before spatial bounding box and polyline projections.

### 2.2 Navigation Repository & Quiescent Short-Circuit
* [ForkNavigationRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepository.kt):
  - Filtered routes from repository: `val selectedRoutes = allRoutes.filter { it.summary.isSelected }`.
  - Added $O(1)$ short-circuit: if `selectedRoutes.size < 2`, reset `_forkDecisionState.value = null` (if active) and return immediately.
  - Forwarded `selectedRoutes` to `ForkRouteMatcher.findCandidateRoutes` with `requireSelected = true`.

### 2.3 Unit & Contract Tests
* [ForkRouteMatcherTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcherTest.kt):
  - Added `findCandidates_requireSelected_filtersOutUnselectedRoutes`: Verifies `requireSelected = true` filters out unselected routes while `requireSelected = false` preserves backward compatibility.
* [ForkNavigationRepositoryTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepositoryTest.kt):
  - Added `@After tearDown()` canceling repository scope.
  - Updated `dummySummary()` default to `isSelected = true`.
  - Added `onLocationChanged_fewerThanTwoSelectedRoutes_skipsEvaluationAndClearsAlert`: Verifies $O(1)$ quiescent short-circuit with 0 and 1 route, and immediate prompt dismissal when routes drop below 2.
  - Added `onLocationChanged_twoSelectedRoutesWithUnselectedCompetitor_onlyConsidersSelectedRoutes`: Asserts unselected routes sharing the corridor are excluded from fork decision branches.
* [RouteClimbsRepositoryTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteClimbsRepositoryTest.kt):
  - Isolated static `TrainingApplication.getStravaAccessToken()` mocking to eliminate cross-test suite flakiness.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit Tests
* `ForkRouteMatcherTest`: **100% PASS** (all tests passing including `TST-MAP-043`).
* `ForkNavigationRepositoryTest`: **100% PASS** (all tests passing including `TST-MAP-043`).

### 3.2 Full Regression Suite
* Executed `./gradlew testDebugUnitTest` across the entire application codebase:
  - **Total Tests Completed**: 2,251 tests.
  - **Failures**: 0.
  - **Pass Rate**: 100% PASS RATE.
  - **Execution Time**: 2m 36s.

---

## 4. Requirement & Test Specification Traceability

| Requirement | Test Specification | Target File | Status |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-041` | `TST-MAP-043` | `ForkNavigationRepository.kt`, `ForkRouteMatcher.kt` | **Verified** |

---

## 5. Invariants Maintained

* **Zero Spurious Alerts**: Fork decision cards only prompt choices between routes explicitly selected by the athlete.
* **CPU & Battery Optimization**: 0 or 1 route workouts consume $O(1)$ processing for fork navigation checks.
* **Autonomous Snapping Preserved**: Branch traversal ($\ge 50\text{ m}$) automatically locks route navigation to `activeNavigatedRouteId`.
* **Zero Regressions**: 100% full-suite unit test pass rate.
