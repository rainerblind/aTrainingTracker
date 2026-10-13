# Stage 1 Analysis: ATT-2668 - Filter route selector by active sport type and display sport icon in route list

**Ticket**: [ATT-2668](https://atrainingtracker.atlassian.net/browse/ATT-2668)  
**Sub-task**: [ATT-2842](https://atrainingtracker.atlassian.net/browse/ATT-2842) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Branch**: `feature/ATT-2668`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

When an athlete opens the quick route selector bottom sheet (`RouteSelectorSheet.kt`) from the Control Tracking screen (`ControlTrackingScreen.kt`) or Cockpit (`SensorGridScreen.kt`), all candidate routes within the configured proximity radius (default 1.0 km) are returned regardless of the currently selected sport discipline.

Specifically:
- An athlete preparing for a cycling ride (`BSportType.BIKE`) is presented with nearby running routes alongside bike routes.
- A runner preparing for a running workout (`BSportType.RUN`) is presented with road cycling courses.
- Furthermore, list items in `RouteSelectorSheet.kt` (`RouteCard`) currently render the route title and distance/elevation metrics without any visual sport badge or iconography. Athletes cannot distinguish route disciplines at a glance without reading route names.

Expected Behavior:
1. When an active sport type is selected in the tracking setup or session (`BSportType.BIKE` or `BSportType.RUN`), the route selector candidate list must only display routes matching that sport discipline, while preserving access to generic/untagged routes (`BSportType.UNKNOWN`).
2. When the active sport is untagged or multisport (`BSportType.UNKNOWN`), all candidate routes within the radius must be displayed.
3. Each route card in `RouteSelectorSheet.kt` must display the route's sport icon (`bSportType.getIconResId()`, e.g., `R.drawable.bsport_bike`, `R.drawable.bsport_run`, `R.drawable.bsport_other`) directly preceding the route title.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 RouteProximityRanker
In `RouteProximityRanker.kt`, two ranking methods exist:
- `rankRoutes(routes, currentLocation, currentBearing, activeSport)`: Contains Tier 2 sport profile matching within the 250m home radius, but does not hard-filter routes; routes of all sports are preserved.
- `filterAndRankRoutes(routes, currentLocation, radiusMeters)`: Added in REQ-UI-281 (ATT-2460) to filter candidate routes within `radiusMeters`. It currently does not accept an `activeSport: BSportType?` parameter and evaluates only start-point distance and recency.

### 2.2 RouteSelectorViewModel & RouteContext
In `RouteSelectorViewModel.kt`:
- `RouteContext` tracks `(location: Location?, isTracking: Boolean, radiusMeters: Float)`. It lacks an `activeSport: BSportType` field.
- In pre-tracking mode (`!context.isTracking`), `candidateRoutes` are derived via `RouteProximityRanker.filterAndRankRoutes(allRoutes, currentLatLng, context.radiusMeters)`, leaving out sport type filtering.
- In in-tracking mode (`context.isTracking`), `RouteAutoDetector.evaluateMatchingRoutes(...)` is invoked on `allRoutes` without sport filtering.
- `RouteSelectorViewModel` currently has no API method or state flow to receive or observe the active sport type from callers.

### 2.3 Integration Call Sites
- `TrackingTabsScreen.kt`: Owns `ControlTrackingViewModel` and `TrackingTabsViewModel`. It observes `val bSportType by controlViewModel.bSportType.collectAsState()` and `val activityType by trackingTabsViewModel.activityType.collectAsState()`, but does not pass the active sport to `routeSelectorViewModel`.
- `SensorGridScreen.kt`: Instantiates/uses `routeSelectorViewModel` and receives `state.bSportType`, but does not forward it to `actualRouteSelectorViewModel`.

### 2.4 RouteCard in RouteSelectorSheet
In `RouteSelectorSheet.kt`:
- `RouteCard(route, isActive, onClick)` lays out a `Row` containing a `Column` (route name and distance/elevation) and an optional `ACTIVE` badge.
- No `Icon` or `Image` is rendered for `route.summary.bSportType.iconResId`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Extend `RouteProximityRanker.filterAndRankRoutes` to accept `activeSport: BSportType? = null` and filter out incompatible routes according to sport invariants.
  * Add `_activeSportType: MutableStateFlow<BSportType>` and `fun setActiveSport(sport: BSportType)` to `RouteSelectorViewModel`, incorporating active sport into `RouteContext`.
  * Ensure in-ride auto-detection candidates in `RouteSelectorViewModel` also honor active sport filtering.
  * Wire `routeSelectorViewModel.setActiveSport(bSportType)` in `TrackingTabsScreen.kt` and `SensorGridScreen.kt`.
  * Render the sport icon (`bSportType.iconResId`) in `RouteCard` (`RouteSelectorSheet.kt`) with proper semantic tinting (`onSurfaceVariant` or `primary` when active).
  * Comprehensive unit tests in `RouteProximityRankerTest.kt`, `RouteSelectorViewModelTest.kt`, and UI contract tests.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to database schemas (`RouteTable`, `Routes.db`).
  * No changes to turn-by-turn navigation or HUD algorithms (`TurnByTurnNavigationRepository`).
  * No changes to sport type definitions in `BSportType.java` or `ActivityType.java`.
  * No alteration of `HomeLocationResolver` or `ReturnCorridorSnapper`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-UI-281` Clause 3 (*Strict Proximity Filtering Criteria*) and `REQ-MAP-024` Clause 3 (*Adaptive Route Selector Sheet UI*).
* **Historical Origin & Commit Trace**:
  - `REQ-MAP-024`: ATT-1835 (Sprint 2026-40.14) introduced initial multi-stage tie-breaking where sport match was Tier 2 preference.
  - `REQ-UI-281`: ATT-2460 (Sprint 2026-41.1) introduced strict proximity radius filtering (`filterAndRankRoutes`).
* **Root Reason for Existing Formulation**:
  - ATT-1835 treated the route selector as a global route browser where out-of-discipline routes could still be selected if the athlete wanted to follow a bike route while running.
  - However, user testing revealed that mixing sports in the quick selector creates cognitive clutter. Athletes selecting "Bike" expect bike routes; athletes selecting "Run" expect run routes. Untagged routes (`UNKNOWN`) must still remain visible as fallback.
* **Preservation of Core Invariants**:
  - When `activeSport` is `UNKNOWN` or null, all routes within the radius remain visible (full backwards compatibility).
  - Routes tagged `UNKNOWN` remain accessible under any active sport.
  - Deterministic sorting by recency (`syncedAt` descending) and tie-breaking by distance to start point ascending is 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Sport Filtering Invariant Formulation**:
   ```kotlin
   fun matchesSport(routeSport: BSportType, activeSport: BSportType?): Boolean {
       if (activeSport == null || activeSport == BSportType.UNKNOWN || activeSport == BSportType.CONFLICT) {
           return true
       }
       return routeSport == activeSport || routeSport == BSportType.UNKNOWN
   }
   ```
2. **`RouteProximityRanker.kt`**:
   - Update `filterAndRankRoutes(routes, currentLocation, radiusMeters, activeSport = null)` to filter with `matchesSport(route.summary.bSportType, activeSport)`.
3. **`RouteSelectorViewModel.kt`**:
   - Add `_activeSportType = MutableStateFlow(BSportType.UNKNOWN)`.
   - Expose `fun setActiveSport(sport: BSportType)`.
   - Combine `_activeSportType` into `routeContextFlow`.
   - Pass `activeSport` to `RouteProximityRanker.filterAndRankRoutes(...)`.
   - Filter `allRoutes` by `matchesSport(it.summary.bSportType, context.activeSport)` when evaluating in-ride candidate matching.
4. **Integration Sites**:
   - `TrackingTabsScreen.kt`: In `LaunchedEffect(bSportType)`, call `routeSelectorViewModel.setActiveSport(bSportType)`.
   - `SensorGridScreen.kt`: In `LaunchedEffect(state.bSportType)`, call `actualRouteSelectorViewModel.setActiveSport(state.bSportType)`.
5. **UI Rendering in `RouteSelectorSheet.kt`**:
   - In `RouteCard`: Add `Icon` with `painterResource(route.summary.bSportType.iconResId)` with size `24.dp`, tinted with `MaterialTheme.colorScheme.onSurfaceVariant` (or `primary` when `isActive`). Place with an 8.dp horizontal margin before the route name column.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing route selection, auto-detection, and navigation features.
  2. Fallback to all routes when active sport is `BSportType.UNKNOWN`.
  3. Parent ticket Human Decision Gate remains strictly enforced (`Final Review (Human)` is agent terminal status).
* **Risk Rating**: **LOW**
  - Isolated logic in route selection presentation layers.
  - Fully verifiable via fast, deterministic unit and UI contract tests.
