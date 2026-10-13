# Stage 3: Implementation Plan - ATT-2940: Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-3110](https://atrainingtracker.atlassian.net/browse/ATT-3110) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-MAP-040`  
**Test Mapping**: `TST-MAP-042`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

During Sprint 2026-41.7 Joint Review on Google Pixel 10, the user observed that after selecting and activating a route, it remained indistinguishable from unselected routes on the map:
> *"In on-device Sprint Review (Pixel 10), user activated and selected a route, but it remained not more prominent than unselected routes. Forensic investigation required on MapFragmentWithTrack, RouteDetail/RoutesScreen, and TrackingViewModel to ensure selected/activated routes properly receive prominent styling."*

Forensic analysis revealed:
1. `MapFragmentWithTrackViewModel.kt` (powers the central "Karte" bottom navigation tab) converted routes with `it.toMapRoute()` defaulting to `isActiveNavigation = false` without observing `routesRepository.activeNavigatedRouteId`.
2. Dedicated route inspection screens (`RouteDetailSheet.kt` and `RoutesScreen.kt`) similarly passed `route = selectedRoute.toMapRoute()` with `isActiveNavigation = false`, leaving the inspected route rendered with the passive dashed baseline.
3. Ambient active routes must maintain their standard 10f dashed line, while the single selected route the athlete is actively following must render as a prominent 26f solid ribbon.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-040` (*High-Glanceability Route Polyline Scaling and Solid Line Rendering for Actively Navigated Route*)
* **Test Mapping**: `TST-MAP-042` (*High-Glanceability Route Polyline Scaling and Solid Line Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All existing map tests, segment overlays, and climb representations continue to pass cleanly.
2. **Layer Separation & Z-Index Stacking**: `ROUTE_BASE_Z_INDEX (20f) < ROUTE_ACTIVE_BASE_Z_INDEX (24f) < CLIMB_Z_INDEX (28f) < SEGMENT_Z_INDEX (30f) < USER_LOCATION_Z_INDEX (100f)` strictly preserved.
3. **Active Background Routes Untouched**: Routes toggled visible on the map without active navigation retain their baseline width (10f) and dashed overlay (`Dash(15f)`, `Gap(15f)`).
4. **Directional Chevrons**: `ActiveRouteDecorations` continues to draw forward chevrons over the 26f solid ribbon.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket ATT-2940 remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Map Models (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`)
* In `MapStyle` and `MapVisualization`:
  * `routeWidth = 10f` (baseline for active/visible routes).
  * `routeActiveNavigationWidth = 26f` (prominent ribbon for actively followed route).
  * `routeUnselectedWidth = 6f` (unselected background routes).
* In `MapRoute`:
  ```kotlin
  override val pattern: List<com.google.android.gms.maps.model.PatternItem>?
      get() = if (isActiveNavigation) {
          null
      } else {
          listOf(
              com.google.android.gms.maps.model.Dash(MapVisualization.ROUTE_DASH_LENGTH),
              com.google.android.gms.maps.model.Gap(MapVisualization.ROUTE_GAP_LENGTH)
          )
      }
  ```

### Component 2: ViewModel Data Flow (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModel.kt`)
* Observe `routesRepository.activeNavigatedRouteId` in the `combine` flow:
  ```kotlin
  val uiState: StateFlow<MapFragmentUIState> = combine(
      banalRepository.bSportType,
      banalRepository.currentTrack,
      segmentsRepository.allSegmentsWithPath,
      routesRepository.allRoutes,
      routesRepository.activeNavigatedRouteId,
      knownLocationsRepository.locationsFlow
  ) { bSportType, currentTrack, liveSegments, allRoutes, activeNavigatedRouteId, knownLocations ->
      ...
      MapFragmentUIState(
          ...
          routes = allRoutes.map { 
              it.toMapRoute(isActiveNavigation = (it.summary.id == activeNavigatedRouteId)) 
          },
          ...
      )
  }
  ```

### Component 3: Route Inspection Prominence (`RouteDetailSheet.kt`, `RoutesScreen.kt`)
* In `RouteDetailSheet.kt` (line 80):
  ```kotlin
  RouteOnMapScreen(
      route = routeWithPath.toMapRoute(isActiveNavigation = true),
      routeSummary = routeWithPath.summary,
      ...
  )
  ```
* In `RoutesScreen.kt` (line 133):
  ```kotlin
  RouteOnMapScreen(
      route = selectedRoute.toMapRoute(isActiveNavigation = true),
      routeSummary = selectedRoute.summary,
      ...
  )
  ```

### UI Consistency (Rule 23 — Mandatory UI Audit)
* **Reference screen / component**: `ATrainingTrackerMap.kt` / `MapLayers.kt` (map route polyline rendering)
* **Reused components**: `XRayPolyline` in `MapLayers.kt`, `RouteOnMapScreen`
* **Theme tokens**: `TTColor.RouteActiveNavigation` (Royal Blue #1E88E5), `TTColor.RouteSelected` (Navy Blue #1565C0), `TTColor.RouteUnselected`
* **New one-off styles & justification**: None. Reusing existing semantic tokens and dimension constants.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Verify Polyline Constants & Pattern in `MapModels.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`
* Changes:
  * Verify `MapStyle.routeWidth = 10f`, `routeActiveNavigationWidth = 26f`.
  * Verify `MapVisualization.ROUTE_WIDTH = 10f`, `ROUTE_ACTIVE_NAVIGATION_WIDTH = 26f`.
  * Verify `MapRoute.pattern`: returns `null` if `isActiveNavigation == true`, otherwise returns `listOf(Dash(15f), Gap(15f))`.

### Step 2: Connect `activeNavigatedRouteId` in `MapFragmentWithTrackViewModel.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModel.kt`
* Changes:
  * Add `routesRepository.activeNavigatedRouteId` to `combine()`.
  * Map `routes = allRoutes.map { it.toMapRoute(isActiveNavigation = (it.summary.id == activeNavigatedRouteId)) }`.

### Step 3: Propagate Active Navigation to Route Inspection Composables
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`
* Changes:
  * Pass `route = routeWithPath.toMapRoute(isActiveNavigation = true)` in `RouteDetailSheet.kt`.
  * Pass `route = selectedRoute.toMapRoute(isActiveNavigation = true)` in `RoutesScreen.kt`.

### Step 4: Unit Test Coverage for MapRoute and MapFragmentWithTrackViewModel
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModelTest.kt`
* Changes:
  * Assert `activeRoute.width == 26f` and `activeRoute.pattern == null`.
  * Assert `passiveSelectedRoute.width == 10f` and `passiveSelectedRoute.pattern != null`.
  * Assert `MapFragmentWithTrackViewModel` sets `isActiveNavigation = true` on the matching route.

### Step 5: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrackViewModelTest"
  ```
* Expected: 100% pass rate in ~5–15 seconds.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-2940`) allows full revert without affecting `sprint/2026-41.8` or `develop`.
