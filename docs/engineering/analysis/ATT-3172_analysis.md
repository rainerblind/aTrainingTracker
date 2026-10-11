# Stage 1: Problem Domain & Root Cause Analysis - ATT-3172

## 1. Problem Statement & User Observation
During Sprint 2026-41.8 Joint Review, while inspecting routes on the Google Pixel 10 in `RoutesScreen` and `RouteDetailSheet`, the athlete observed:
1. **Accidental Active Navigation Styling**: The route preview on the map is rendered with a very thick (26f) solid blue line (`TTColor.RouteActiveNavigation`), which is the styling reserved for actively navigated routes during workout tracking.
2. **Broken Toggle Feedback**: Toggling the route between active/selected and not active/unselected in the `RouteSummaryHeader` switch does not change the polyline color, pattern, or thickness.
3. **Loss of Context**: Passive routes appear as if they are currently being followed for turn-by-turn navigation, confusing athletes and preventing them from seeing whether routes are selected for inclusion in the tracking map or background display.

---

## 2. Forensic Investigation & Root Cause (RCA)

### Commit Archaeology & Code Traversal
In commit `a1dc652e` (Sprint 2026-41.6, `ATT-2940`, `REQ-MAP-040`):
1. In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt` (line 80):
   ```kotlin
   RouteOnMapScreen(
       route = routeWithPath.toMapRoute(isActiveNavigation = true),
       routeSummary = routeWithPath.summary,
       onToggleSelection = {},
       modifier = Modifier.fillMaxSize(),
       useStatusBarsPadding = false
   )
   ```
2. In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt` (line 133):
   ```kotlin
   RouteOnMapScreen(
       route = selectedRoute.toMapRoute(isActiveNavigation = true),
       routeSummary = selectedRoute.summary,
       backgroundPaths = backgroundPaths,
       allSegments = allSegments,
       modifier = Modifier,
       onToggleSelection = { isSelected ->
           viewModel.toggleRouteSelection(
               selectedRoute.summary.id,
               isSelected
           )
       }
   )
   ```

### Polyline Attribute Resolution in `MapRoute`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt` (lines 264–303):
```kotlin
override val color: Color get() = when {
    isActiveNavigation -> TTColor.RouteActiveNavigation
    isSelected -> TTColor.RouteSelected
    else -> TTColor.RouteUnselected
}
override val width: Float get() = when {
    isActiveNavigation -> MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH // 26f
    isSelected -> MapVisualization.ROUTE_WIDTH // 10f
    else -> MapVisualization.ROUTE_UNSELECTED_WIDTH // 6f
}
override val pattern: List<com.google.android.gms.maps.model.PatternItem>?
    get() = if (isActiveNavigation) {
        null // Solid ribbon
    } else {
        listOf(
            com.google.android.gms.maps.model.Dash(MapVisualization.ROUTE_DASH_LENGTH),
            com.google.android.gms.maps.model.Gap(MapVisualization.ROUTE_GAP_LENGTH)
        )
    }
```

Because `isActiveNavigation = true` was unconditionally hardcoded in both route inspection screens:
1. `MapRoute.color` evaluates unconditionally to `TTColor.RouteActiveNavigation` (0xFF1E88E5).
2. `MapRoute.width` evaluates unconditionally to `26f`.
3. `MapRoute.pattern` evaluates unconditionally to `null` (solid line).
4. `isSelected` branch in `when` expression is dead code for these screens. When the user flips the toggle in `RouteSummaryHeader`, `viewModel.toggleRouteSelection(selectedRoute.summary.id, isSelected)` successfully updates SQLite and triggers recomposition with updated `summary.isSelected`, but the polyline visuals remain 100% frozen on active navigation styling!

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   Amends `REQ-MAP-040` (*High-Glanceability Route Polyline Scaling and Solid Line Rendering for Actively Navigated Route*, ATT-2940), specifically clause 5.
2. **Historical Origin & Commit Trace**:
   Sprint 2026-41.6 (`ATT-2940`, commit `a1dc652e`).
3. **Root Reason for Existing Formulation**:
   In ATT-2940, the developer sought to highlight the inspected route on `RoutesScreen` and `RouteDetailSheet` so it would stand out against background segments. However, hardcoding `isActiveNavigation = true` was an over-correction that conflated passive inspection with active live tracking. It masked `summary.isSelected` and prevented athletes from seeing selection toggle feedback.
4. **Preservation of Core Invariants**:
   - Live actively navigated routes during workout tracking continue to render with 26f solid `TTColor.RouteActiveNavigation` (REQ-MAP-040 clauses 1, 4).
   - Passive selected routes render with 10f dashed `TTColor.RouteSelected`.
   - Passive unselected routes render with 6f dashed `TTColor.RouteUnselected`.
   - Z-index stacking hierarchy (`ROUTE_BASE_Z_INDEX < ROUTE_ACTIVE_BASE_Z_INDEX < CLIMB_Z_INDEX < SEGMENT_Z_INDEX < USER_LOCATION_Z_INDEX`) remains strictly intact.
   - 100% full-suite unit test pass rate must be preserved.

---

## 4. Scope Bounding (`ATT-1250`)
### In-Scope
1. **Expose Active Route in `RoutesViewModel`**:
   - Expose `val activeNavigatedRouteId: StateFlow<Long?> = routesRepository.activeNavigatedRouteId` in `RoutesViewModel.kt`.
2. **Dynamic Navigation State in `RoutesScreen.kt`**:
   - In `RoutesScreen.kt`, collect `activeNavigatedRouteId` and pass `isActiveNavigation = (selectedRoute.summary.id == activeNavigatedRouteId)` to `selectedRoute.toMapRoute()`.
   - This ensures routes reflect whether they are actively navigated during tracking, and otherwise reflect `summary.isSelected`.
3. **Decoupled Route Inspection in `RouteDetailSheet.kt`**:
   - In `RouteDetailSheet.kt`, add optional `isActiveNavigation: Boolean = false` and `onToggleSelection: ((Boolean) -> Unit)? = null` parameters (defaulting to false / no-op).
   - Pass `routeWithPath.toMapRoute(isActiveNavigation = isActiveNavigation)` so it accurately reflects `routeWithPath.summary.isSelected`.
4. **Contract & Regression Unit Tests**:
   - Add unit tests verifying that `RoutesScreen` and `RouteDetailSheet` do not hardcode `isActiveNavigation = true`.
   - Add unit tests verifying that `toMapRoute` visual attributes (color, width, pattern) transition dynamically when `isSelected` toggles while `isActiveNavigation = false`.

### Out-of-Scope
- Changing `MapRoute` core data model or styling definitions (`MapModels.kt`).
- Modifying `RouteOnMapScreen.kt` internal layout, bottom sheet scaffold, or layer visibility menus.
- Altering live tracking map behavior (`TrackingViewModel.kt` or `MapFragmentWithTrackViewModel.kt`).
