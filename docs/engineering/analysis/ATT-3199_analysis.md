# Stage 1 Analysis: ATT-3199 - Display full route elevation profile with position marker and climbs during route tracking

**Ticket**: [ATT-3199](https://atrainingtracker.atlassian.net/browse/ATT-3199)  
**Sub-task**: [ATT-3305](https://atrainingtracker.atlassian.net/browse/ATT-3305) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Branch**: `feature/ATT-3199`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

During active workout tracking in `aTrainingTracker`, athletes can configure tracking tabs to display an elevation profile (`ShowElevationProfile == true`). Currently, the elevation profile in `SensorGridScreen.kt` (lines 537–549) strictly renders the recorded workout track points accumulated so far (`state.pathPoints`, with `currentDistance = state.pathPoints.lastOrNull()?.distance`).

This introduces critical UX limitations when following an active route:
1. **No Forward Visibility**: Because only historical recorded points are plotted, the elevation curve only shows where the athlete has already been. The position marker is permanently pegged to the far-right edge of the chart canvas. The athlete has zero visual anticipation of upcoming terrain, hill gradients, remaining climbing meters, or approaching summits.
2. **Context Disconnect During Navigation**: When an athlete selects and navigates a planned route with complete elevation telemetry and categorized climbs, this rich route data is completely absent from the tracking tab's elevation profile.
3. **Missing Categorized Climbs Overlay**: Although `ElevationProfile.kt` already supports drawing categorized climb overlays (`climbs: List<Climb>`) along the baseline in standardized category colors (CAT 4/3/2/1/HC), `SensorGridScreen.kt` never passes climbs to the component during tracking.

**Value Proposition**:
When an athlete is actively following a navigated route with elevation data, the tracking screen's elevation profile should dynamically shift context: display the complete route elevation profile from 0 km to destination, render categorized climbs directly on the profile, and advance a position marker smoothly along the route based on orthogonal GPS map-matching. When tracking freely without a route (or if the route lacks elevation data), the system seamlessly retains the recorded workout track profile.

---

## 2. Root Cause & Architectural Investigation

### 2.1. Current Profile Invocation in `SensorGridScreen.kt`
In `SensorGridScreen.kt` lines 536–550:
```kotlin
// 3. The Elevation Profile (Below the Map or anchored with canonical adaptive height when map is hidden, REQ-UI-346 / ATT-3173)
if (state.showElevationProfile && state.pathPoints.size >= 2) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        ElevationProfile(
            pathPoints = state.pathPoints,
            currentDistance = state.pathPoints.lastOrNull()?.distance,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```
Key observations:
1. It conditions rendering strictly on `state.pathPoints.size >= 2`. Before an athlete starts moving or in the first few seconds of recording, the profile is completely hidden even if an active route is loaded.
2. It hardcodes `pathPoints = state.pathPoints` and `currentDistance = state.pathPoints.lastOrNull()?.distance`, locking the data source to recorded history.
3. It omits the `climbs` parameter, ignoring route climb metadata entirely.

### 2.2. Route Telemetry Availability in `TrackingScreenState`
In `TrackingViewModel.kt`:
```kotlin
val mapDataFlow = combine(
    liveSegmentsRepository.liveSegments,
    activeLiveSegments,
    routesRepository.allRoutes,
    routesRepository.activeNavigatedRouteId
) { allSegments, activeSegments, allRoutes, activeNavigatedRouteId ->
    MapData(allSegments, activeSegments, allRoutes, activeNavigatedRouteId)
}
```
`allRoutes` is mapped into `mapRoutes: List<MapRoute>` where:
```kotlin
mapRoutes = allRoutes.map { it.toMapRoute(isActiveNavigation = (it.summary.id == activeNavigatedRouteId)) }
```
In `MapModels.kt`, each `MapRoute` contains:
- `id: Long`
- `name: String`
- `isActiveNavigation: Boolean`
- `path: List<PathPoint>` (full polyline path points with `distance`, `altitude`, `latLng`)
- `climbs: List<Climb>` (detected categorized climbs along the route)

Therefore, the active navigated route (`state.mapRoutes.firstOrNull { it.isActiveNavigation }`) is already fully loaded and available in memory inside `TrackingScreenState`!

### 2.3. Orthogonal GPS Map-Matching via `PolylineProgressTracker`
In `com.atrainingtracker.trainingtracker.geo.PolylineProgressTracker.kt`:
```kotlin
fun projectLocation(
    path: List<PathPoint>,
    location: LatLng
): PolylineProjection?
```
This engine (standardized in `ATT-3266` / `REQ-GEO-001`) takes an arbitrary GPS fix `LatLng` and projects it orthogonally onto the nearest polyline segment, returning:
- `alongTrackDistance: Double`: cumulative distance along the polyline from route start to the projected point in meters.
- `progressFraction: Float`: progress in `[0.0f, 1.0f]`.
- `interpolatedAltitude: Double?`: altitude at projected point.
- `crossTrackDistanceMeters: Double`: off-route distance error.

Because `currentLocationFlow: StateFlow<LatLng?>` is already passed directly to `SensorGridScreen`, projecting the athlete's current location onto `activeRoute.path` is instantaneous, robust against GPS drift, and requires zero new math libraries.

### 2.4. Resilient Fallback Logic for Degenerate or Missing Altitude Data
Legacy 2D GPX imports or certain route sources lack elevation data (all points have `altitude == 0.0`).
- If an active route has fewer than 2 points or all points have `altitude == 0.0` (`path.none { it.altitude != 0.0 }`), the route has no usable elevation profile.
- In this scenario, the app must gracefully fall back to the recorded workout profile (`state.pathPoints`), preventing flatline rendering or divisions by zero.

---

## 3. Scope Bounding (In-Scope vs. Out-of-Scope)

### In-Scope:
1. **Contextual Elevation Data Source Selection**:
   - Determine whether an active navigated route with elevation data is present:
     `val activeRoute = state.mapRoutes.firstOrNull { it.isActiveNavigation }`
     `val hasRouteElevation = activeRoute != null && activeRoute.path.size >= 2 && activeRoute.path.any { it.altitude != 0.0 }`
   - When `hasRouteElevation` is true:
     - Profile path points: `activeRoute.path` (0 km to total route distance).
     - Climbs: `activeRoute.climbs`.
     - Marker distance: map-matched `alongTrackDistance` from `PolylineProgressTracker.projectLocation(activeRoute.path, currentLocation)` (or `0.0` if `currentLocation == null`).
   - When `hasRouteElevation` is false:
     - Fall back to existing behavior: `state.pathPoints`, `state.pathPoints.lastOrNull()?.distance`, and empty climbs.
2. **Pre-Start & Zero-Track Route Visibility**:
   - When an active route with elevation data is selected, display the route profile immediately (`profilePoints.size >= 2`) even if `state.pathPoints.isEmpty()`, allowing athletes to review terrain before pressing start.
3. **Map-Matched Location Projection**:
   - Collect `currentLocationFlow` in `SensorGridScreen` and calculate progress along route cleanly via `PolylineProgressTracker.projectLocation`.
4. **Climb Category Shading & Baseline Indicators**:
   - Pass `activeRoute.climbs` to `ElevationProfile`, automatically rendering climb category colors and span lines per `REQ-UI-299` / `REQ-UI-307`.
5. **Automated Unit Tests**:
   - Verify active route elevation selection, fallback to workout points when no route is active or route has 0m altitude, orthogonal progress projection, and climb parameter propagation.

### Out-of-Scope:
- Modifying Turn-by-Turn navigation banners or audio chimes (`TurnByTurnNavigationRepository`).
- Modifying `PolylineProgressTracker` core projection math.
- Modifying route database schema or route import parsers.
- Modifying Aftermath / workout history elevation profiles.

---

## 4. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

### 4.1. Affected Requirements
- `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, `ATT-3173`).
- `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, `ATT-3107`).
- `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Multi-Metric Scrubbing Badge Architecture*).

### 4.2. Mandatory Chesterton's Fence Audit
1. **Original Requirement ID & Target**:
   - Refines `REQ-UI-346`, targeting `SensorGridScreen.kt` and `TrackingScreenState`.
2. **Historical Origin & Commit Trace**:
   - `ATT-3173` (Sprint `2026-41.10`, commit `b2c58dd8`) established canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) via `calculateElevationProfileHeight(range)` and eliminated `weight(1f)` stretching when map is hidden.
   - `ATT-3107` (Sprint `2026-41.8`) originally positioned `ElevationProfile` within `SensorGridScreen.kt`.
3. **Root Reason for Existing Formulation**:
   - `state.pathPoints` was originally passed because tracking screens predated the integrated navigation and route mapping engine. With the introduction of active route navigation (`activeNavigatedRouteId`), route path points and detected climbs are available in memory and ready for contextual presentation.
4. **Preservation of Core Invariants**:
   - Canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) from `calculateElevationProfileHeight(range)` is 100% preserved.
   - The non-weighted wrapping `Surface` and scrollable sensor grid layout are 100% preserved.
   - Zoom controls remain suppressed during live tracking (`showZoomControls = false`).
   - Free tracking without a route functions identically to the previous implementation.

---

## 5. Proposed Net-New Formal Requirement (`REQ-UI-355`)

### REQ-UI-355: Active Route Full Elevation Profile with Map-Matched Position Marker and Categorized Climbs During Tracking
The system SHALL dynamically switch the live tracking elevation profile data source to the active navigated route when a route is actively followed, displaying the complete route elevation profile, categorized climbs, and a map-matched position progress marker:
1. *Contextual Elevation Profile Switching*:
   - When an active navigated route exists (`state.mapRoutes.firstOrNull { it.isActiveNavigation }`) and possesses valid elevation data (`path.size >= 2 && path.any { it.altitude != 0.0 }`), the system SHALL render the active route's complete path (`activeRoute.path`) spanning from 0 km to total route distance.
   - When no route is actively navigated, or when the active route lacks elevation data (all altitudes are 0.0 or path has fewer than 2 points), the system SHALL fall back to rendering the recorded workout track (`state.pathPoints`), provided `state.pathPoints.size >= 2`.
2. *Map-Matched Progress Marker Projection*:
   - When following an active route with elevation data, the current position marker SHALL be rendered at the athlete's orthogonal projection distance along the route:
     $d = \text{PolylineProgressTracker.projectLocation}(\text{activeRoute.path}, \text{currentLocation})\text{.alongTrackDistance}$.
   - If `currentLocation` is null or the athlete has not yet acquired GPS, $d$ SHALL default to $0.0\text{m}$.
   - The marker SHALL advance smoothly from left to right along the route profile as the athlete progresses along the course.
3. *Categorized Route Climbs Overlay*:
   - The system SHALL forward `activeRoute.climbs` to `ElevationProfile`, rendering categorized climbs along the baseline in their respective category colors (CAT 4, CAT 3, CAT 2, CAT 1, HC).
4. *Layout & Height Invariants*:
   - The elevation profile SHALL maintain its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) and non-weighted `Surface` layout per `REQ-UI-346`.

---

## 6. Verification Strategy & Gate 1 Readiness

### Automated Unit Test Plan (`TST-UI-315`):
1. *Route Elevation Selection*:
   - With active navigated route containing altitude data, verify profile points match route path (0 km to total distance) and climbs match route climbs.
2. *Fallback to Recorded Track*:
   - When `activeNavigatedRoute == null`, verify profile points match `state.pathPoints` and climbs are empty.
   - When active route has `altitude == 0.0` across all points, verify profile points fall back to `state.pathPoints`.
3. *Orthogonal Projection Marker Distance*:
   - Verify GPS coordinates at route start yield $d \approx 0.0\text{m}$.
   - Verify GPS coordinates midway along route project accurately to corresponding route distance.
   - Verify null location yields $d = 0.0\text{m}$.
4. *Clean-Room Regression*:
   - Execute full test suite `./gradlew testDebugUnitTest` ensuring 0 regressions.

Gate 1 Audit decision: **READY FOR REVIEW**.
