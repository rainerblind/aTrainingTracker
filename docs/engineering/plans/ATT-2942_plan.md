# Stage 3 Implementation Plan: ATT-2942 - Restrict in-ride fork route candidate matching to active selected routes

**Ticket**: [ATT-2942](https://atrainingtracker.atlassian.net/browse/ATT-2942)  
**Sub-task**: [ATT-2991](https://atrainingtracker.atlassian.net/browse/ATT-2991) (`[Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2942`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Architectural Design & SWE.2 Boundaries

### 1.1 Candidate Route Filtering & Quiescent Short-Circuit (`ForkNavigationRepository.kt`)
* **Layer**: Navigation / Repository (`com.atrainingtracker.trainingtracker.routes`).
* **Problem**: Currently, `ForkNavigationRepository.onLocationChanged()` scans `routesRepository.allRoutes.value` without checking `summary.isSelected`. For an athlete with dozens or hundreds of routes, this triggers spurious fork prompts for unselected historical routes sharing initial road sections and wastes CPU cycles.
* **Solution**:
  1. Retrieve `allRoutes = routesRepository.allRoutes.value`.
  2. Filter `selectedRoutes = allRoutes.filter { it.summary.isSelected }`.
  3. **$O(1)$ Short-Circuit**: If `selectedRoutes.size < 2`:
     - If an alert is active (`_forkDecisionState.value != null`), dismiss it (`_forkDecisionState.value = null`).
     - Immediately return, bypassing quiescent throttling math, bounding-box checks, and polyline projections.
  4. When `selectedRoutes.size >= 2`:
     - Evaluate quiescent tracking throttling (`QUIESCENT_THROTTLE_INTERVAL_MS = 3000L`, `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0`).
     - Pass `selectedRoutes` to `ForkRouteMatcher.findCandidateRoutes(allRoutes = selectedRoutes, ..., requireSelected = true)`.

### 1.2 Parameterized Selection Enforcement (`ForkRouteMatcher.kt`)
* **Layer**: Pure Geodesic Domain Utility (`com.atrainingtracker.trainingtracker.routes`).
* **Solution**:
  - Add optional parameter `requireSelected: Boolean = false` to `ForkRouteMatcher.findCandidateRoutes()`.
  - Inside the candidate iteration loop, insert:
    ```kotlin
    // 0. Active/Selected Route Filter (REQ-MAP-041)
    if (requireSelected && !route.summary.isSelected) {
        continue
    }
    ```
  - By defaulting `requireSelected = false`, existing callers and raw geometrical unit tests remain 100% backward compatible without breakage.

---

## 2. Atomic Implementation Steps

### Step 1: Update `ForkRouteMatcher.kt`
* Add `requireSelected: Boolean = false` to `findCandidateRoutes`.
* Filter out routes where `requireSelected && !route.summary.isSelected`.
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcher.kt`.

### Step 2: Update `ForkNavigationRepository.kt`
* Extract `selectedRoutes = allRoutes.filter { it.summary.isSelected }`.
* Implement the $O(1)$ short-circuit when `selectedRoutes.size < 2` (clearing `_forkDecisionState.value` if not null and returning).
* Pass `selectedRoutes` with `requireSelected = true` to `ForkRouteMatcher.findCandidateRoutes`.
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepository.kt`.

### Step 3: Expand Unit Tests in `ForkRouteMatcherTest.kt`
* Add test `findCandidates_requireSelected_filtersOutUnselectedRoutes()`.
* Add test `findCandidates_requireSelectedFalse_evaluatesAllRoutes()`.
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcherTest.kt`.

### Step 4: Expand Unit Tests in `ForkNavigationRepositoryTest.kt`
* Update `dummySummary()` to provide default `isSelected = true`.
* Add test `onLocationChanged_fewerThanTwoSelectedRoutes_skipsEvaluationAndClearsAlert()`.
* Add test `onLocationChanged_twoSelectedRoutesWithUnselectedCompetitor_onlyConsidersSelectedRoutes()`.
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepositoryTest.kt`.

### Step 5: Targeted Unit Test Verification
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.ForkRouteMatcherTest"`.
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.ForkNavigationRepositoryTest"`.

### Step 6: Clean-Room Full Regression Verification
* Execute full clean-room unit test suite: `./gradlew testDebugUnitTest`.

---

## 3. Invariants & Risk Mitigation

* **No Spurious Decision Alerts**: Athletes will only receive fork prompts between routes they have deliberately checked/selected in the application.
* **$O(1)$ Quiescent Short-Circuit**: When riding with 0 or 1 route selected (the vast majority of workouts), fork matching consumes virtually 0 CPU cycles.
* **Autonomous Snapping Preserved**: When navigating along a selected branch ($\ge 50\text{ m}$ past fork), auto-binding to `activeNavigatedRouteId` remains fully operational.
* **Zero Regressions**: 100% full-suite unit test pass rate.
