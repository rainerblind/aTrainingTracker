# Stage 5 Walkthrough: ATT-2940 - Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-3112](https://atrainingtracker.atlassian.net/browse/ATT-3112) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)  
**Sprint**: `Sprint 2026-41.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Problem Domain

### 1.1 The Issue
During on-device testing and Sprint 2026-41.7 review on Google Pixel 10:
1. When an athlete activated and selected a route to follow, the route polyline on the main map ("Karte" tab) remained at the standard 10f dashed baseline, failing to stand out prominently against background routes.
2. In route inspection and detail preview sheets (`RouteDetailSheet`, `RoutesScreen`), focused routes were rendered passively without prominent navigation emphasis.
3. The athlete feedback clarified: *"We have active routes and one selected route. The selected one is the one the user wants to follow. Only this one must be highlighted as requested by this ticket. The active routes should not have been touched."*

### 1.2 Forensic Root Cause Analysis
1. **Disconnected ViewModel Observation**: In `MapFragmentWithTrackViewModel.kt`, `uiState` combined `banalRepository.bSportType`, `banalRepository.currentTrack`, `segmentsRepository.allSegmentsWithPath`, `routesRepository.allRoutes`, and `knownLocationsRepository.locationsFlow`. It mapped routes using `allRoutes.map { it.toMapRoute() }`, where `isActiveNavigation` defaulted to `false`. Crucially, `routesRepository.activeNavigatedRouteId` was never observed.
2. **Hardcoded Passive Mapping in Preview Views**: `RouteDetailSheet.kt` and `RoutesScreen.kt` invoked `.toMapRoute()` without arguments, defaulting `isActiveNavigation = false` during focused inspection.
3. **Domain Layer Distinction**:
   - *Active routes*: Routes toggled visible on the map via switches in the routes list (`isSelected == true`). These must maintain their baseline width (`10f`) and standard dashed pattern (`Dash(15f)`, `Gap(15f)`).
   - *Actively navigated route*: The single route the user wants to follow (`isActiveNavigation == true`, matching `activeNavigatedRouteId`). Only this route receives prominent high-contrast scaling (`26f`), clean solid ribbon rendering (`pattern = null`), and forward directional chevrons.

---

## 2. Implemented Architecture & Data Flow Solution (`REQ-MAP-040`)

### 2.1 Reactive Route Flow in `MapFragmentWithTrackViewModel.kt`
Introduced reactive combination of `routesRepository.allRoutes` with `routesRepository.activeNavigatedRouteId`:
```kotlin
val activeNavigatedRouteId = routesRepository.activeNavigatedRouteId

private val mapRoutesFlow = combine(
    routesRepository.allRoutes,
    routesRepository.activeNavigatedRouteId
) { allRoutes, activeRouteId ->
    allRoutes.map { it.toMapRoute(isActiveNavigation = (it.summary.id == activeRouteId)) }
}
```
Passed `mapRoutesFlow` into `uiState` composition, ensuring that whenever an active route is set or changed, the route polyline on the main navigation map immediately updates its styling reactively.

### 2.2 Prominent Inspection Screen Delegation
- In `RouteDetailSheet.kt`: Passed `route = routeWithPath.toMapRoute(isActiveNavigation = true)` to `RouteOnMapScreen`.
- In `RoutesScreen.kt`: Passed `route = selectedRoute.toMapRoute(isActiveNavigation = true)` to `RouteOnMapScreen`.

### 2.3 Exact Visual Specifications Preserved (`MapModels.kt` & `MapVisualization.kt`)
- Actively Navigated Route (`isActiveNavigation == true`):
  - Base polyline width: `26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`)
  - Base color: `TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`)
  - Pattern: `null` (clean solid ribbon, no dashed overlay clutter)
  - Z-index: `24.0f` (base), `26.0f` (overlay)
  - Forward chevrons: rendered along travel path
- Selected Non-Navigated Active Routes (`isSelected == true`, `isActiveNavigation == false`):
  - Base polyline width: `10f` (`MapVisualization.ROUTE_WIDTH`)
  - Base color: `TTColor.RouteSelected` (`Color(0xFF1565C0)`)
  - Pattern: `listOf(Dash(15f), Gap(15f))`
  - Z-index: `20.0f` (base), `22.0f` (overlay)
- Unselected Routes:
  - Base polyline width: `6f`
  - Base color: `TTColor.RouteUnselected` (`Color(0xFF90CAF9)`)
  - Z-index: `5.0f`

---

## 3. Verification & Test Evidence (`TST-MAP-042`)

### 3.1 Targeted Unit Tests
1. **`MapRouteActiveNavigationTest.kt`**:
   - `testMapRoute_whenActiveNavigation_usesProminentStyling`: Verifies `isActiveNavigation == true`, `width == 26f`, `pattern == null`, and Royal Blue colors.
   - `testMapRoute_whenSelectedPassive_usesStandardStyling`: Verifies `isActiveNavigation == false`, `width == 10f`, `pattern` contains `Dash(15f)` and `Gap(15f)`.
   - `testMapRoute_whenUnselected_usesSubordinateStyling`: Verifies `width == 6f` and subordinate styling.
   - `testXRayPolylineHierarchy_preservesSegmentInterleavingInvariants`: Verifies stacking order: $ROUTE\_BASE\_Z\_INDEX (20f) < ROUTE\_ACTIVE\_BASE\_Z\_INDEX (24f) < CLIMB\_Z\_INDEX (28f) < SEGMENT\_Z\_INDEX (30f) < USER\_LOCATION\_Z\_INDEX (100f)$.
   - `testToMapRoute_propagatesIsActiveNavigationFlag`: Verifies `toMapRoute(isActiveNavigation = true/false)` propagation.
2. **`MapFragmentWithTrackViewModelTest.kt`**:
   - `testUiState_combinesKnownLocationsFlow`: Verifies location flow combination.
   - `testUpdateKnownLocation_delegatesToRepository`: Verifies location repository calls.
   - `testUiState_mapsActiveNavigatedRouteWithProminence`: Verifies that setting `activeNavigatedRouteId = 101L` causes route 101 to map with `isActiveNavigation = true`, `width = 26f`, and `pattern = null`, while route 102 maps with `isActiveNavigation = false`, `width = 10f`, and `pattern != null`.

Targeted Test Execution Result: **8 tests passed, 0 failures**.

### 3.2 Clean-Room Full Regression Suite Execution
Executed complete unit test suite from clean state:
```bash
./gradlew testDebugUnitTest
```
Result: **BUILD SUCCESSFUL in 2m 31s (0 failures, 100% pass rate across entire repository)**.

---

## 4. Requirement Traceability Matrix

| Requirement ID | Test Case ID | Scope | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-040` | `TST-MAP-042` | High-Glanceability Route Polyline Scaling (26f) & Solid Line Rendering for Actively Navigated Route | **Verified** |

---

## 5. Non-Regression & Invariant Audit

- **Baseline Active Route Preservation**: Active visible routes not being navigated remain 100% untouched at their standard 10f dashed baseline.
- **Strava Live Segments Interleaving**: Orange dashed live segment polylines remain at `SEGMENT_Z_INDEX = 30f`, cleanly overlaying the active route line.
- **Climb Categories Interleaving**: Climb category polylines remain at `CLIMB_Z_INDEX = 28f`.
- **GPS Location Puck**: Renders at `USER_LOCATION_Z_INDEX = 100f` above all polylines.
- **Directional Chevrons**: Forward travel chevrons remain rendered along the solid polyline.
- **Zero Architectural Drift**: State flow observation confined to `MapFragmentWithTrackViewModel` and preview screen composables.
