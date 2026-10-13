# Stage 5: Walkthrough & Verification - ATT-3199: Active Route Full Elevation Profile with Position Marker and Categorized Climbs During Tracking

**Ticket**: [ATT-3199](https://atrainingtracker.atlassian.net/browse/ATT-3199)  
**Sub-task**: [ATT-3309](https://atrainingtracker.atlassian.net/browse/ATT-3309) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-355`  
**Test Mapping**: `TST-UI-315`  
**Branch**: `feature/ATT-3199`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Executive Summary & Verification Overview

In sprint `2026-41.10`, **ATT-3199** addressed a core gap in the live tracking screen's elevation profile. Previously, whenever `showElevationProfile` was enabled in a tracking tab, `ElevationProfile` rendered only the athlete's recorded workout track points accumulated so far (`state.pathPoints`). Consequently, the current position marker was permanently pegged to the leading right edge of the chart, giving the athlete zero forward visibility into upcoming terrain, summits, or categorized climbs along an actively navigated route.

Under **REQ-UI-355**, the live tracking elevation profile now dynamically shifts context:
1. **Dynamic Route Data Source Selection (`REQ-UI-355.1`)**:
   - When an active navigated route exists (`activeRoute.isActiveNavigation == true`) and contains valid elevation data (`path.size >= 2 && path.any { it.altitude != 0.0 }`), the elevation profile renders the complete route path spanning from $0\text{ km}$ to the total route distance.
   - Categorized route climbs (`activeRoute.climbs`) are seamlessly passed to `ElevationProfile`, rendering standardized category colors (CAT 4/3/2/1/HC) along the profile baseline per `REQ-UI-299` and `REQ-UI-307`.
2. **Orthogonal Map-Matched Progress Projection (`REQ-UI-355.2`)**:
   - The athlete's instantaneous GPS location (`currentLocationFlow: StateFlow<LatLng?>`) is projected onto the route polyline via `PolylineProgressTracker.projectLocation()`.
   - The resolved along-track distance drives the vertical progress marker, smoothly advancing from left to right as the athlete progresses along the course.
   - Graceful fallback defaults: when `currentLocation` is null, marker distance is $0.0\text{ m}$; when off-route, projection clamps to the nearest polyline segment.
3. **Robust Fallback for Free Workout Tracking (`REQ-UI-355.1`, `REQ-UI-355.4`)**:
   - When no route is actively navigated, or if the active route lacks valid altitude data (e.g. flat $0.0\text{ m}$ elevation), the profile seamlessly falls back to the cumulative recorded workout track points (`state.pathPoints`) with marker at `state.pathPoints.lastOrNull()?.distance`.
4. **Preservation of System Layout & Architectural Invariants (`REQ-UI-355.4`)**:
   - Canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) from `calculateElevationProfileHeight(range)` and non-weighted wrapping `Surface` established in `REQ-UI-346` (`ATT-3173`) remain strictly preserved.
   - Flexible sensor grid scrolling (`Modifier.weight(1f, fill = false)`) remains intact.
   - Zero regression across all clean-room regression test suites (100% pass rate).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-355.1` (Route Elevation Profile Selection & Fallback) | `TST-UI-315.1` | Automated Unit Tests (`TrackingElevationProfileResolutionTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-355.2` (Orthogonal Map-Matched Progress Projection) | `TST-UI-315.2` | Automated Unit Tests (`TrackingElevationProfileResolutionTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-355.3` (Categorized Route Climbs Overlay) | `TST-UI-315.1` | Automated Unit Tests (`TrackingElevationProfileResolutionTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-355.4` (UI Contract & Layout Invariants) | `TST-UI-315.3` | Automated Unit Tests (`SensorGridScreenElevationProfileTest.kt`, `SensorGridScreenLayoutTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` (Clean-Room Full Suite Regression) | `TST-UI-315.4` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Implementation Architecture & Key Code Changes

### 3.1 Pure Domain Resolver: `TrackingElevationProfileResolver.kt`
A pure domain resolver decouples elevation profile data source selection, altitude validation, climb binding, and orthogonal map-matched projection from the Compose UI layer:

```kotlin
data class ResolvedElevationProfileData(
    val pathPoints: List<PathPoint>,
    val climbs: List<Climb>,
    val markerDistance: Double?,
    val isRouteElevation: Boolean
)

object TrackingElevationProfileResolver {

    fun resolve(
        activeNavigatedRoute: MapRoute?,
        recordedPathPoints: List<PathPoint>,
        currentLocation: LatLng?
    ): ResolvedElevationProfileData {
        if (activeNavigatedRoute != null &&
            activeNavigatedRoute.path.size >= 2 &&
            activeNavigatedRoute.path.any { it.altitude != 0.0 }
        ) {
            val alongTrackDist = currentLocation?.let { loc ->
                PolylineProgressTracker.projectLocation(activeNavigatedRoute.path, loc)?.alongTrackDistance
            } ?: 0.0
            return ResolvedElevationProfileData(
                pathPoints = activeNavigatedRoute.path,
                climbs = activeNavigatedRoute.climbs,
                markerDistance = alongTrackDist,
                isRouteElevation = true
            )
        }
        return ResolvedElevationProfileData(
            pathPoints = recordedPathPoints,
            climbs = emptyList(),
            markerDistance = recordedPathPoints.lastOrNull()?.distance,
            isRouteElevation = false
        )
    }
}
```

### 3.2 Reactive UI Integration in `SensorGridScreen.kt`
In `SensorGridScreen.kt`, the elevation profile section reactively resolves the profile data source using `remember` keyed to `(activeNavigatedRoute, state.pathPoints, currentLocation)`:

```kotlin
// 3. The Elevation Profile (Below the Map or anchored with canonical adaptive height when map is hidden, REQ-UI-346 / ATT-3173 / REQ-UI-355 / ATT-3199)
val activeNavigatedRoute = state.mapRoutes.firstOrNull { it.isActiveNavigation }
val resolvedProfile = remember(activeNavigatedRoute, state.pathPoints, currentLocation) {
    TrackingElevationProfileResolver.resolve(
        activeNavigatedRoute = activeNavigatedRoute,
        recordedPathPoints = state.pathPoints,
        currentLocation = currentLocation
    )
}

if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        ElevationProfile(
            pathPoints = resolvedProfile.pathPoints,
            currentDistance = resolvedProfile.markerDistance,
            climbs = resolvedProfile.climbs,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

---

## 4. Test Evidence & Results

### 4.1 Unit Test Executions
Targeted unit test suites passed with 100% success rate:
- **`TrackingElevationProfileResolutionTest.kt`** (7 test cases):
  - `testResolve_whenActiveRouteWithElevation_returnsFullRouteAndClimbs`: Verified route path points and categorized climbs are returned when active route has valid altitude data.
  - `testResolve_whenCurrentLocationProjectsOntoRoute_computesAlongTrackDistance`: Verified orthogonal segment projection resolves accurate marker distance ($500\text{ m}$ midway along route).
  - `testResolve_whenActiveRouteHasZeroAltitudes_fallsBackToRecordedPath`: Verified flat $0.0\text{ m}$ elevation routes fall back gracefully to recorded workout path.
  - `testResolve_whenActiveRouteHasFewerThan2Points_fallsBackToRecordedPath`: Verified degenerate single-point routes fall back to recorded workout path.
  - `testResolve_whenNoActiveNavigatedRoute_fallsBackToRecordedPath`: Verified unnavigated routes fall back to recorded workout path.
  - `testResolve_whenCurrentLocationIsNull_defaultsMarkerDistanceToZero`: Verified null location fix defaults marker distance to $0.0\text{ m}$ without crash.
  - `testResolve_whenRecordedPointsEmptyAndNoActiveRoute_returnsEmptyAndNullDistance`: Verified clean empty state handling for initial pre-workout state.
- **`SensorGridScreenElevationProfileTest.kt`** (4 test cases):
  - `testSensorGridScreen_collectsCurrentLocationFlow`: Confirms collection of `currentLocationFlow`.
  - `testSensorGridScreen_wiresTrackingElevationProfileResolver`: Confirms invocation of resolver and guard `resolvedProfile.pathPoints.size >= 2`.
  - `testSensorGridScreen_passesResolvedDataToElevationProfile`: Confirms `pathPoints`, `currentDistance`, and `climbs` binding.
  - `testSensorGridScreen_maintainsCanonicalSurfaceAndModifierInvariants`: Confirms `Modifier.fillMaxWidth()` and absence of unconstrained height.
- **`SensorGridScreenLayoutTest.kt`**: Verified regression-free preservation of adaptive layout constraints.

### 4.2 Full Clean-Room Regression Run
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: `BUILD SUCCESSFUL in 2m 30s`, 0 test failures, 100% clean across all 88 test tasks.

---

## 5. Artifact Traceability
- Living Requirements: [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L731) (`REQ-UI-355`, status `Verified`)
- Living Tests: [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L671) (`TST-UI-315`, status `Verified`)
- Analysis Deliverable: [docs/engineering/analysis/ATT-3199_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-3199_analysis.md)
- Test Spec Deliverable: [docs/engineering/test_specs/ATT-3199_test_spec.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/test_specs/ATT-3199_test_spec.md)
- Implementation Plan: [docs/engineering/plans/ATT-3199_plan.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/plans/ATT-3199_plan.md)

---

## 6. Conclusion & Gate 5 Sign-Off
All acceptance criteria for `ATT-3199` are verified. The elevation profile during live tracking dynamically displays full route elevation, categorized climbs, and orthogonal map-matched position progress when navigating a route, while preserving all existing workout tracking fallback behavior. The ticket is ready to be audited, closed, merged into `sprint/2026-41.10`, and submitted to `Final Review (Human)`.
