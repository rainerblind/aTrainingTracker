# Stage 2: Requirement & Test Specification - ATT-3199: Display full route elevation profile with position marker and climbs during route tracking

**Ticket**: [ATT-3199](https://atrainingtracker.atlassian.net/browse/ATT-3199)  
**Sub-task**: [ATT-3306](https://atrainingtracker.atlassian.net/browse/ATT-3306) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker and Categorized Climbs During Tracking*)  
**Test Spec ID**: `TST-UI-315`  
**Branch**: `feature/ATT-3199`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-UI-355)

### 1.1 Problem Statement & Rationale
Currently, the live elevation profile on the tracking screen (`SensorGridScreen.kt`) renders exclusively the recorded workout track points accumulated so far (`state.pathPoints`, with `currentDistance = state.pathPoints.lastOrNull()?.distance`). Consequently, the position marker is permanently pegged to the right edge of the chart canvas, giving the athlete zero forward visibility into upcoming hills, summits, or remaining climbing meters.

When an athlete is actively following a planned route (`MapRoute.isActiveNavigation == true`), the elevation profile must dynamically shift context: instead of displaying only past history, it must render the complete elevation profile of the active route (spanning from 0 km to total route distance), display categorized climbs along the baseline in standardized category colors (CAT 4/3/2/1/HC), and smoothly advance a position marker along the route using orthogonal GPS map-matching (`PolylineProgressTracker.projectLocation`). When tracking freely without a route, or when the active route lacks elevation data, the system seamlessly falls back to displaying the recorded workout track.

### 1.2 Functional & Architectural Requirements
The system SHALL dynamically switch the live tracking elevation profile data source to the active navigated route when a route is actively followed, displaying the complete route elevation profile, categorized climbs, and a map-matched position progress marker (ATT-3199, extending `REQ-UI-346` and aligning with Design Guidelines §2.3):

1. *Contextual Elevation Profile Switching (`SensorGridScreen.kt`)*:
   - The system SHALL identify whether an active navigated route exists:
     `val activeRoute = state.mapRoutes.firstOrNull { it.isActiveNavigation }`
   - The system SHALL evaluate whether the active route contains valid elevation data:
     `val hasRouteElevation = activeRoute != null && activeRoute.path.size >= 2 && activeRoute.path.any { it.altitude != 0.0 }`
   - When `hasRouteElevation == true`:
     - The profile path points SHALL be set to `activeRoute.path` (0 km to total route distance).
     - The profile climbs SHALL be set to `activeRoute.climbs`.
   - When `hasRouteElevation == false`:
     - The profile path points SHALL fall back to `state.pathPoints`.
     - The profile climbs SHALL fall back to `emptyList()`.
   - The elevation profile SHALL be rendered if and only if `state.showElevationProfile && profilePoints.size >= 2`.

2. *Map-Matched Progress Marker Projection (`SensorGridScreen.kt`)*:
   - When following an active route with elevation data (`hasRouteElevation == true`):
     - The system SHALL collect the athlete's current location from `currentLocationFlow: StateFlow<LatLng?>`.
     - When `currentLocation != null`, the marker distance SHALL be computed via orthogonal segment projection:
       $$d = \text{PolylineProgressTracker.projectLocation}(\text{activeRoute.path}, \text{currentLocation})\text{.alongTrackDistance}$$
     - When `currentLocation == null`, the marker distance SHALL default to $0.0\text{m}$.
   - When tracking without an active route (`hasRouteElevation == false`):
     - The marker distance SHALL default to `state.pathPoints.lastOrNull()?.distance`.

3. *Categorized Route Climbs Overlay*:
   - The system SHALL pass `climbs = profileClimbs` to `ElevationProfile`, rendering detected climbs along the baseline with their standardized category colors (CAT 4, CAT 3, CAT 2, CAT 1, HC) per `REQ-UI-299` and `REQ-UI-307`.

4. *Preservation of System Invariants*:
   - Canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) from `calculateElevationProfileHeight(range)` and non-weighted wrapping `Surface` per `REQ-UI-346` MUST remain strictly preserved.
   - Sensor grid flexible scrolling (`Modifier.weight(1f, fill = false)`) MUST remain strictly preserved.
   - Free workout tracking without an active route MUST function identically to prior implementation.
   - Zoom controls remain suppressed during live tracking (`showZoomControls = false`).
   - 100% clean-room test pass rate across all test tasks.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends and extends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, `ATT-3173`), targeting `SensorGridScreen.kt` and `TrackingScreenState`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.10 (`ATT-3173`, commit `b2c58dd8`) established canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) and non-weighted `Surface`. Sprint 2026-41.8 (`ATT-3107`, commit `8d6ef4e1`) anchored `ElevationProfile` beneath the map.
3. *Root Reason for Existing Formulation*: Historical tracking tabs plotted raw `state.pathPoints` because integrated route navigation was not yet connected to the Compose tracking cockpit. With `activeNavigatedRouteId` and `MapRoute` containing `path` and `climbs`, full route elevation and categorized climbs are available in memory.
4. *Preservation of Core Invariants*: Canonical adaptive height, non-weighted `Surface`, scrollable sensor grid layout, zoom suppression, and 100% full-suite unit test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
- *Given* an active workout tracking tab with `showElevationProfile = true` and no active route,
- *When* viewing the tracking screen with recorded track points,
- *Then* the elevation profile displays the recorded workout track so far with the marker at the leading right edge.
- *Given* an active workout tracking tab with `showElevationProfile = true` and an active navigated route with elevation data,
- *When* viewing the tracking screen,
- *Then* the elevation profile displays the complete route from 0 km to finish.
- *Given* an active route being navigated with elevation data,
- *When* the athlete progresses along the course,
- *Then* the position marker advances smoothly from left to right at the athlete's map-matched route distance.
- *Given* an active navigated route containing categorized climbs,
- *When* viewing the route elevation profile during tracking,
- *Then* the climbs are highlighted with their respective category colors (CAT 4/3/2/1/HC) along the baseline.
- *Given* an active route without elevation data (all altitudes are 0.0 or path has fewer than 2 points),
- *When* viewing the tracking screen,
- *Then* the app gracefully falls back to displaying the recorded workout's elevation profile without crashing or flatlining.

---

## 2. Test Specification (TST-UI-315)

### 2.1 Scope & Test Architecture
The test suite validates:
1. **Route Elevation Profile Selection & Fallback Unit Tests (`TrackingElevationProfileResolutionTest.kt`)**:
   - Verify that when an active navigated route with altitude data is present, the resolved path points match the full route path (0 km to total distance) and climbs match the route climbs.
   - Verify that when no route is active (`isActiveNavigation == false` across all routes), resolved path points match `state.pathPoints` and climbs are empty.
   - Verify that when an active route has flat 0.0 altitude data across all points, resolved path points fall back to `state.pathPoints` and climbs are empty.
   - Verify that when an active route has fewer than 2 path points, resolved path points fall back to `state.pathPoints`.
2. **Orthogonal Map-Matched Progress Projection Tests (`TrackingElevationProfileResolutionTest.kt`)**:
   - Verify that when `currentLocation` is at the route start coordinate, the resolved marker distance is approximately $0.0\text{m}$.
   - Verify that when `currentLocation` is midway along the route, the resolved marker distance matches the along-track distance of the projected segment.
   - Verify that when `currentLocation == null`, the resolved marker distance defaults to $0.0\text{m}$.
   - Verify that for free tracking (fallback mode), the marker distance is `state.pathPoints.lastOrNull()?.distance`.
3. **UI Contract & Layout Tests (`SensorGridScreenElevationProfileTest.kt`)**:
   - Verify that `ElevationProfile` is rendered when `hasRouteElevation == true` even if `state.pathPoints.isEmpty()`.
   - Verify that `ElevationProfile` is hidden when `hasRouteElevation == false` and `state.pathPoints.size < 2`.
   - Verify that `ElevationProfile` receives `showZoomControls = false` and `fillParentHeight = false`.
4. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. ASPICE Traceability Matrix

| Requirement | Test Spec | Target Component | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-355.1` | `TST-UI-315.1` | `SensorGridScreen.kt` (Contextual Elevation Selection & Fallback) | Specified |
| `REQ-UI-355.2` | `TST-UI-315.2` | `SensorGridScreen.kt` & `PolylineProgressTracker` (Map-Matched Projection) | Specified |
| `REQ-UI-355.3` | `TST-UI-315.1` | `SensorGridScreen.kt` & `ElevationProfile.kt` (Climb Category Shading) | Specified |
| `REQ-UI-355.4` | `TST-UI-315.3` | `SensorGridScreen.kt` (Canonical Adaptive Height & Layout Invariants) | Specified |
| `REQ-PRO-001`   | `TST-UI-315.4` | Clean-Room Regression (`./gradlew testDebugUnitTest`) | Specified |
