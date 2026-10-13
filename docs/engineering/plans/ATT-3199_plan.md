# Stage 3: Implementation Plan - ATT-3199: Display full route elevation profile with position marker and climbs during route tracking

**Ticket**: [ATT-3199](https://atrainingtracker.atlassian.net/browse/ATT-3199)  
**Sub-task**: [ATT-3307](https://atrainingtracker.atlassian.net/browse/ATT-3307) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker and Categorized Climbs During Tracking*)  
**Test Mapping**: `TST-UI-315` (*Active Route Full Elevation Profile with Map-Matched Position Marker and Categorized Climbs During Tracking Verification*)  
**Branch**: `feature/ATT-3199`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

Currently, the live elevation profile displayed on the tracking screen (`SensorGridScreen.kt` when `showElevationProfile == true`) renders exclusively the recorded workout track points accumulated so far (`state.pathPoints`, with `currentDistance = state.pathPoints.lastOrNull()?.distance`). Consequently, the position marker is permanently pegged to the right edge of the chart canvas, giving the athlete zero forward visibility into upcoming hills, summits, or remaining climbing meters.

When the athlete is following an actively navigated route (`MapRoute.isActiveNavigation == true`), the elevation profile must dynamically shift context: instead of displaying only past history, it must render the complete elevation profile of the active route (spanning from 0 km to total route distance), display categorized climbs along the baseline in standardized category colors (CAT 4/3/2/1/HC), and smoothly advance a position marker along the route using orthogonal GPS map-matching (`PolylineProgressTracker.projectLocation`). When tracking freely without a route, or when the active route lacks elevation data, the system seamlessly falls back to displaying the recorded workout track.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker and Categorized Climbs During Tracking*)
* **Test Mapping**: `TST-UI-315` (*Active Route Full Elevation Profile with Map-Matched Position Marker and Categorized Climbs During Tracking Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Canonical Adaptive Height Preservation**: Canonical adaptive height calculation ($70\text{dp} \dots 200\text{dp}$) from `calculateElevationProfileHeight(range)` and non-weighted wrapping `Surface` per `REQ-UI-346` (`ATT-3173`) MUST remain strictly preserved.
2. **Sensor Grid Scrollability Preservation**: Sensor grid `Column` with `Modifier.weight(1f, fill = false)` MUST remain strictly preserved.
3. **Free Tracking Non-Regression**: When tracking without an active route, or when an active route lacks elevation data, elevation profile behavior remains identical to prior implementation (renders `state.pathPoints` with marker at leading edge).
4. **Zoom Control Suppression**: In-flight zoom controls remain suppressed during live tracking (`showZoomControls = false`).
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TrackingElevationProfileResolver.kt` (Domain Heuristic Tier)
- Create a pure, deterministic resolver helper in `com.atrainingtracker.trainingtracker.ui.tracking.tracking`:
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
          val hasRouteElevation = activeNavigatedRoute != null &&
              activeNavigatedRoute.path.size >= 2 &&
              activeNavigatedRoute.path.any { it.altitude != 0.0 }

          return if (hasRouteElevation && activeNavigatedRoute != null) {
              val alongTrackDist = currentLocation?.let { loc ->
                  PolylineProgressTracker.projectLocation(activeNavigatedRoute.path, loc)?.alongTrackDistance
              } ?: 0.0
              ResolvedElevationProfileData(
                  pathPoints = activeNavigatedRoute.path,
                  climbs = activeNavigatedRoute.climbs,
                  markerDistance = alongTrackDist,
                  isRouteElevation = true
              )
          } else {
              ResolvedElevationProfileData(
                  pathPoints = recordedPathPoints,
                  climbs = emptyList(),
                  markerDistance = recordedPathPoints.lastOrNull()?.distance,
                  isRouteElevation = false
              )
          }
      }
  }
  ```

### Component 2: `SensorGridScreen.kt` (Presentation Tier)
- Collect `currentLocation` from `currentLocationFlow`:
  ```kotlin
  val currentLocation by currentLocationFlow.collectAsState()
  ```
- Resolve profile data reactively:
  ```kotlin
  val activeNavigatedRoute = state.mapRoutes.firstOrNull { it.isActiveNavigation }
  val resolvedProfile = remember(activeNavigatedRoute, state.pathPoints, currentLocation) {
      TrackingElevationProfileResolver.resolve(
          activeNavigatedRoute = activeNavigatedRoute,
          recordedPathPoints = state.pathPoints,
          currentLocation = currentLocation
      )
  }
  ```
- Render `ElevationProfile` with resolved data:
  ```kotlin
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

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: Live tracking cockpit (`SensorGridScreen.kt`), `ElevationProfile.kt`.
* **Reused components**: Reuses existing `ElevationProfile` composable, standard `Surface` container, and existing `calculateElevationProfileHeight` heuristics.
* **Theme tokens**: Standard `Modifier.fillMaxWidth().padding(top = 8.dp)`. No hardcoded colors or ad-hoc margins.
* **New one-off styles & justification**: None. Reuses existing climb category coloring (`TTColor.ClimbCat*`) and elevation profile canvas.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `TrackingElevationProfileResolver.kt`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingElevationProfileResolver.kt`
* Changes:
  - Implement `ResolvedElevationProfileData` data class.
  - Implement `TrackingElevationProfileResolver.resolve(...)` evaluating route presence, elevation validity, and map-matched orthogonal position projection via `PolylineProgressTracker.projectLocation`.

### Step 2: Wire Resolution in `SensorGridScreen.kt`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Changes:
  - Collect `currentLocation` from `currentLocationFlow`.
  - Wire `TrackingElevationProfileResolver.resolve(...)`.
  - Pass `resolvedProfile.pathPoints`, `resolvedProfile.markerDistance`, and `resolvedProfile.climbs` to `ElevationProfile`.

### Step 3: Author Unit Tests (`TrackingElevationProfileResolutionTest.kt`)
* Target File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingElevationProfileResolutionTest.kt`
* Tests:
  - Active route with valid elevation points resolves route path, route climbs, and map-matched distance.
  - Active route with 0.0 elevation points falls back to recorded track points.
  - Active route with < 2 points falls back to recorded track points.
  - No active route falls back to recorded track points and leading-edge distance.
  - Null `currentLocation` on active route defaults marker distance to 0.0m.
  - Map-matching midway along the route correctly resolves along-track distance.

### Step 4: Author UI Contract Tests (`SensorGridScreenElevationProfileTest.kt`)
* Target File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorGridScreenElevationProfileTest.kt`
* Tests:
  - Contract check verifying `SensorGridScreen.kt` collects `currentLocationFlow`, uses `TrackingElevationProfileResolver`, and maintains canonical unweighted Surface padding.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted test execution: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` (must pass 100%).
  2. Full clean-room regression: `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Clean Git branch isolation (`feature/ATT-3199`). Reverting the commit restores legacy tracking elevation profile without schema changes or side effects.
