# Stage 1 Analysis: ATT-2940 - Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-3098](https://atrainingtracker.atlassian.net/browse/ATT-3098) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

During on-device testing on the Google Pixel 10 during the Sprint 2026-41.7 Joint Review, the athlete activated and selected a route, but the route remained indistinguishable from standard routes on the map:
> *"In on-device Sprint Review (Pixel 10), user activated and selected a route, but it remained not more prominent than unselected routes. Forensic investigation required on MapFragmentWithTrack, RouteDetail/RoutesScreen, and TrackingViewModel to ensure selected/activated routes properly receive prominent styling."*

This builds upon the human user's earlier requirement clarification:
> *"Unfortunately, there was a misunderstanding. We have active routes and one selected route. The selected one is the one the user wants to follow. Only this one must be highlighted as requested by this ticket. The active routes should not have been touched."*

### Route Concept Delineation
1. **Active Routes (Visible on Map)**:
   Routes that the athlete has toggled on via the visibility switch in the routes list (`isSelected == true` in database / `RouteSummary`). These routes provide ambient contextual awareness of nearby courses. They must retain their standard line width (`10f`) and standard dashed pattern (`Dash(15f)`, `Gap(15f)`).
2. **Selected Route (Actively Followed Route)**:
   The single specific route that the athlete has chosen to navigate or follow (`routesRepository.activeNavigatedRouteId == route.id` or `isActiveNavigation == true`). Only this route must receive prominent visual highlighting: a bold solid polyline (`26f`, `pattern = null`), high-contrast Electric Emerald color (`TTColor.RouteActiveNavigation`), and directional chevrons (`ActiveRouteDecorations`).

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic tracing across the navigation and mapping architecture revealed three distinct failure points explaining why the selected route failed to render prominently on the physical Pixel 10:

### 2.1 Missing `activeNavigatedRouteId` in `MapFragmentWithTrackViewModel`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModel.kt` (lines 85–125):
```kotlin
val uiState: StateFlow<MapFragmentUIState> = combine(
    banalRepository.bSportType,
    banalRepository.currentTrack,
    segmentsRepository.allSegmentsWithPath,
    routesRepository.allRoutes,
    knownLocationsRepository.locationsFlow
) { bSportType, currentTrack, liveSegments, allRoutes, knownLocations ->
    ...
    MapFragmentUIState(
        ...
        routes = allRoutes.map { it.toMapRoute() }, // Default parameter isActiveNavigation = false!
        ...
    )
}
```
* **Failure Mechanism**: `MapFragmentWithTrackViewModel` powers the primary full-screen "Karte" bottom navigation tab (`MapScreenWithTrack`). It observed `allRoutes`, but completely omitted `routesRepository.activeNavigatedRouteId`.
* As a result, every route on the map was unconditionally converted via `it.toMapRoute()` with the default `isActiveNavigation = false`. Even when the user actively selected a route to follow in `RouteSelector`, the "Karte" screen never received `isActiveNavigation = true`, rendering the navigated route identically to background routes.

### 2.2 Passive Defaulting in Dedicated Route Inspection Views
In `RouteDetailSheet.kt` (line 80) and `RoutesScreen.kt` (line 133):
```kotlin
RouteOnMapScreen(
    route = selectedRoute.toMapRoute(), // isActiveNavigation defaults to false!
    routeSummary = selectedRoute.summary,
    ...
)
```
* When an athlete opens a route to inspect its details or taps on a route card in `RoutesScreen`, the inspected route is explicitly the focus of user attention. However, it was passed with `isActiveNavigation = false`, causing `MapRoute.width` to evaluate to `10f` with dashed patterning rather than the bold solid route style.

### 2.3 Conflated Width and Pattern Settings in `MapModels.kt`
In `MapModels.kt`:
* In commit `61218f1a`, `routeWidth` was previously scaled from `10f` to `18f` and `pattern` set to `null` across all routes.
* When that change was rolled back to address the user's feedback ("active routes should not have been touched"), `routeWidth` returned to `10f` and `pattern` returned to `Dash(15f), Gap(15f)`.
* Because `isActiveNavigation` was never propagated from `routesRepository` to `MapFragmentWithTrackViewModel`, the selected route was rendered with the passive `10f` dashed styling, failing to fulfill the glanceability requirement.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Propagate Navigated Route to Map Tab**: Inject `routesRepository.activeNavigatedRouteId` into `MapFragmentWithTrackViewModel.uiState`, setting `isActiveNavigation = (it.summary.id == activeNavigatedRouteId)`.
  2. **Highlight Route on Detail/Inspection Screens**: In `RouteDetailSheet.kt` and `RoutesScreen.kt`, pass `route = selectedRoute.toMapRoute(isActiveNavigation = true)` to `RouteOnMapScreen` so the route being inspected renders prominently.
  3. **Preserve Baseline for Active Routes**:
     - Active background routes (`isSelected == true && !isActiveNavigation`): `width = 10f`, `pattern = Dash(15f), Gap(15f)`.
     - Actively navigated/selected route (`isActiveNavigation == true`): `width = 26f`, `pattern = null` (clean solid line), directional travel chevrons.
     - Unselected routes: `width = 6f`, `alpha = 0.3f`.
  4. **Regression & Contract Tests**: Update and expand JVM tests in `MapRouteActiveNavigationTest.kt` and `MapFragmentWithTrackViewModelTest.kt` to verify active route propagation and styling invariants.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying Strava segment polylines or Live Climb color grading.
  * Altering route auto-detection algorithms in `RouteSelectorViewModel` or `AutoRouteDetector`.
  * Modifying SQLite database schemas in `RoutesDatabaseManager`.
  * Changing waypoint pin iconography or Z-index stacking orders.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-MAP-040` (*High-Glanceability Route Polyline Scaling and Solid Line Rendering*).
* **Historical Origin & Commit Trace**:
  - `ATT-1841` (commit `e25d2b78`): Initial implementation of active route navigation styling.
  - `ATT-2940` (commit `61218f1a`): Initial attempt to increase route thickness, which accidentally scaled all active routes instead of solely the navigated route.
  - Sprint 2026-41.7 Review: User observed that selecting a route did not produce a prominent polyline on Pixel 10 due to missing ViewModel propagation.
* **Root Reason for Existing Formulation**:
  The existing code had established `isActiveNavigation` on `MapRoute`, but failed to thread `activeNavigatedRouteId` through `MapFragmentWithTrackViewModel`.
* **Preservation of Core Invariants**:
  - The Z-index hierarchy (`ROUTE_BASE_Z_INDEX (20f) < ROUTE_ACTIVE_BASE_Z_INDEX (24f) < CLIMB_Z_INDEX (28f) < SEGMENT_Z_INDEX (30f) < USER_LOCATION_Z_INDEX (100f)`) is strictly maintained.
  - Active routes remain dashed and 10f wide, preserving clear visual differentiation between ambient routes and the active course.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 ViewModel Data Flow in `MapFragmentWithTrackViewModel.kt`
Combine `routesRepository.activeNavigatedRouteId` with `allRoutes`:
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

### 5.2 Prominent Inspection in `RouteDetailSheet.kt` & `RoutesScreen.kt`
When presenting `RouteOnMapScreen` for a specific focused route:
```kotlin
RouteOnMapScreen(
    route = selectedRoute.toMapRoute(isActiveNavigation = true),
    routeSummary = selectedRoute.summary,
    ...
)
```

### 5.3 Polyline Constants & Styling Verification in `MapModels.kt`
Ensure:
* `MapStyle.routeWidth = 10f`, `MapVisualization.ROUTE_WIDTH = 10f`
* `MapStyle.routeActiveNavigationWidth = 26f`, `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH = 26f`
* `MapRoute.pattern`: returns `null` when `isActiveNavigation == true`, `Dash(15f), Gap(15f)` when `isActiveNavigation == false`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing map and tracking test suites.
  2. Ambient active routes remain dashed and unobtrusive.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * Justification: Minimal, surgical data flow connection in `MapFragmentWithTrackViewModel` and inspection composables. Verified via isolated JVM unit tests and Compose UI tests without schema or network dependencies.
