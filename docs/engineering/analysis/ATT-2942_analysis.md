# Stage 1 Analysis: ATT-2942 - Restrict in-ride fork route candidate matching to active selected routes

**Ticket**: [ATT-2942](https://atrainingtracker.atlassian.net/browse/ATT-2942)  
**Sub-task**: [ATT-2989](https://atrainingtracker.atlassian.net/browse/ATT-2989) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2942`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During review and desk testing of `ATT-1955` (In-Ride Fork Route Selection & Decision Alerts) and `ATT-2873` (Fork Route Detection Performance Optimization), it was identified that `ForkNavigationRepository` scans `routesRepository.allRoutes.value` without filtering by user route selection state (`route.summary.isSelected`).

In realistic athletic tracking workflows, an athlete accumulates dozens or hundreds of historical routes in their local database. While `ATT-2873` added spatial bounding-box rejection and sport filtering to avoid polyline projections for geographically distant routes, evaluating all routes creates two significant functional and performance deficiencies:
1. **Spurious Decision Alerts from Historical Corridors**: If an athlete sets out for a ride and selects two candidate route variants (e.g. Route A and Route B), any unselected historical route in the database that shares the initial departure corridor (e.g. an old commute or race course Route X) will also be detected as a fork candidate. When approaching a split, the system will prompt the athlete with choices including Route X—a route the user never activated or intended to ride.
2. **Unnecessary Quiescent Processing**: Athletes frequently ride without selecting multiple candidate routes (either having selected 0 routes, or just 1 single intended route). In this common state, evaluating fork candidate matching on location updates is completely redundant because a "fork decision" conceptually requires at least 2 distinct candidate routes. Evaluating `allRoutes` consumes CPU cycles and battery life during active GPS tracking.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Unfiltered Database Evaluation in `ForkNavigationRepository.kt`
In `ForkNavigationRepository.kt:107-109`:
```kotlin
val allRoutes = routesRepository.allRoutes.value
val candidates = ForkRouteMatcher.findCandidateRoutes(allRoutes, currentPos, recentHistory, activeSportType)
```
- `routesRepository.allRoutes.value` contains all routes stored in the database.
- Each route has a `summary.isSelected: Boolean` flag managed via `RoutesDatabaseManager.setRouteSelected(routeId, isSelected)` and toggled in the Routes tab UI (`RouteSummaryHeader.kt`, `RouteItem.kt`).
- `ForkNavigationRepository` does not check `summary.isSelected`, passing all stored routes into `ForkRouteMatcher.findCandidateRoutes`.

### 2.2 Lack of Early Quiescent Short-Circuit
- Even if the user has selected 0 or 1 route, `ForkNavigationRepository.onLocationChanged` performs throttling calculations and, when throttle intervals expire, queries all routes and passes them to `ForkRouteMatcher`.
- An athlete with 100 historical routes will have 100 bounding-box checks performed repeatedly throughout their ride even when no route or only a single route is selected for navigation.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: Amends `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clauses 1 & 4, `ATT-1955`) and `REQ-MAP-038` (*In-Ride Fork Route Detection Performance Optimization*, `ATT-2873`).
2. **Historical Origin & Commit Trace**:
   - `ATT-1955` introduced in-ride fork-in-the-road route detection. It queried `allRoutes` under the assumption of small test database fixtures without considering multi-route selection semantics.
   - `ATT-2873` optimized performance via bounding-box rejection and sport filtering, but retained `allRoutes` as the input collection.
3. **Root Reason for Existing Formulation**:
   - In initial prototyping, evaluating `allRoutes` allowed automatic route discovery without requiring the user to explicitly select routes ahead of time. However, user feedback and field review showed that users explicitly select routes on the map/routes tab before their workout, and unsolicited prompts for old unselected routes cause confusion and disorientation.
4. **Preservation of Core Invariants**:
   - Quiescent throttling (`QUIESCENT_THROTTLE_INTERVAL_MS = 3000L`, `QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0`) remains intact when 2+ selected routes exist.
   - Autonomous snapping ($\ge 50\text{ m}$ past fork, cross-track $< 25\text{ m}$) remains fully operational between selected candidate routes.
   - 1-tap manual route selection and alert dismissal remain unchanged.
   - Sport-type pre-filtering (`RouteProximityRanker.matchesSport`) and spatial bounding-box rejection ($O(1)$) in `ForkRouteMatcher` remain fully active.
   - 100% full-suite unit test pass rate must be preserved.

---

## 4. Proposed Technical Solution & Architecture

### 4.1 Selected Routes Filtering & $O(1)$ Short-Circuit (`ForkNavigationRepository.kt`)
1. In `ForkNavigationRepository.onLocationChanged`:
   - Retrieve `allRoutes = routesRepository.allRoutes.value`.
   - Filter down to `selectedRoutes = allRoutes.filter { it.summary.isSelected }`.
   - **$O(1)$ Quiescent Short-Circuit**: If `selectedRoutes.size < 2`:
     - If an alert is currently active (`_forkDecisionState.value != null`), dismiss it (`_forkDecisionState.value = null`).
     - Immediately return without executing throttling calculations, bounding-box checks, or polyline projections.
2. When `selectedRoutes.size >= 2`:
   - Proceed with quiescent throttling and pass `selectedRoutes` to `ForkRouteMatcher.findCandidateRoutes(allRoutes = selectedRoutes, ...)`.

### 4.2 Parameterized Filtering in `ForkRouteMatcher.kt`
1. Update `ForkRouteMatcher.findCandidateRoutes()` to accept an optional parameter `requireSelected: Boolean = false`:
   ```kotlin
   fun findCandidateRoutes(
       allRoutes: List<RouteWithPath>,
       currentPos: LatLng,
       recentHistory: List<LatLng>? = null,
       activeSportType: BSportType? = null,
       requireSelected: Boolean = false
   ): List<RouteWithPath> {
       val candidates = mutableListOf<RouteWithPath>()
       ...
       for (route in allRoutes) {
           if (route.path.size < 2) continue

           // 0. Active/Selected Route Filter (REQ-MAP-041)
           if (requireSelected && !route.summary.isSelected) {
               continue
           }
           ...
   ```
2. By defaulting `requireSelected = false`, existing direct geometrical tests in `ForkRouteMatcherTest` remain 100% backward compatible, while `ForkNavigationRepository` explicitly sets `requireSelected = true` and dedicated unit tests verify the filtering behavior.

---

## 5. Scope & Guardrails (`ATT-1250`)

### In-Scope
* Filtering candidate routes in `ForkNavigationRepository` to only evaluate routes with `route.summary.isSelected == true`.
* Short-circuiting candidate matching in $O(1)$ when fewer than 2 active/selected routes exist.
* Adding `requireSelected` support to `ForkRouteMatcher.findCandidateRoutes()`.
* Updating unit tests in `ForkNavigationRepositoryTest.kt` and `ForkRouteMatcherTest.kt`.
* Maintaining 100% full-suite test pass rate.

### Out-of-Scope
* Modifying route selection persistence or UI checkboxes in `RouteTabbedScreen.kt` or `RouteItem.kt`.
* Modifying polyline projection or divergence detection algorithms (`RouteDivergenceDetector.kt`).
* Modifying top-level spatial overlay rendering (`ForkDecisionCard.kt`, `SensorGridScreen.kt`).

---

## 6. Verification & Test Strategy

1. **`ForkNavigationRepositoryTest.kt`**:
   - Update `dummySummary` to provide `isSelected = true` by default so existing fork decision tests evaluate properly.
   - Add test `onLocationChanged_fewerThanTwoSelectedRoutes_skipsEvaluationAndClearsAlert`: Verifies that if 0 or 1 route is selected, no alert is triggered even when approaching a divergence point, and any active alert is dismissed.
   - Add test `onLocationChanged_twoSelectedRoutesWithUnselectedCompetitor_onlyConsidersSelectedRoutes`: Verifies that when 2 routes are selected and an unselected 3rd route shares the corridor, only the selected routes are included in the fork decision branches.
2. **`ForkRouteMatcherTest.kt`**:
   - Add test `findCandidates_requireSelected_filtersOutUnselectedRoutes`: Verifies that `requireSelected = true` discards routes where `isSelected == false`.
3. **Clean-Room Full Suite Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
