# ATT-3172 Walkthrough: Decouple Route Preview Polyline Styling from Active Navigation

## 1. Overview
- **Issue**: [ATT-3172](https://atrainingtracker.atlassian.net/browse/ATT-3172): `[Bug] Route preview on RoutesScreen and RouteDetailSheet hardcodes isActiveNavigation=true, ignoring active/inactive selection state`
- **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
- **Requirement**: `REQ-UI-347` (amending `REQ-MAP-040` clause 5)
- **Test Specification**: `TST-UI-307`
- **Sprint**: `2026-41.9`
- **Fix Version**: `V4.9.39`
- **Status**: Verified

---

## 2. Problem Addressed
During Sprint 2026-41.6 (`ATT-2940`, `REQ-MAP-040`), the developer added prominent solid ribbon styling (`26f` width, solid pattern, `TTColor.RouteActiveNavigation`) for actively navigated routes during workouts.
However, in `RoutesScreen.kt` and `RouteDetailSheet.kt`, `selectedRoute.toMapRoute(isActiveNavigation = true)` was unconditionally hardcoded:
1. **Conflated Passive Preview with Active Workout Navigation**: Merely inspecting a route in the routes tab or bottom sheet rendered it as if it were actively navigated in a live workout (26f solid blue ribbon).
2. **Masked Selection State (`summary.isSelected`)**: Because `MapModels.kt` prioritizes `isActiveNavigation` above `isSelected`, passive preview polylines permanently rendered at 26f solid blue regardless of whether the route was active/selected or unselected.
3. **No Dynamic Visual Feedback**: When athletes toggled the route selection switch in `RouteSummaryHeader`, the polyline did not change, misleading users into thinking the toggle was broken.

---

## 3. Architectural Changes & Solution

### 1. Active Navigation Route Exposure in `RoutesViewModel` (`RoutesViewModel.kt`)
Exposed the active navigated route ID from `RoutesRepository`:
```kotlin
/**
 * Currently active navigated route ID, if any workout route navigation is active (REQ-UI-347).
 */
val activeNavigatedRouteId: StateFlow<Long?> = routesRepository.activeNavigatedRouteId
```

### 2. Dynamic Route State Propagation in `RoutesScreen` (`RoutesScreen.kt`)
Observed `activeNavigatedRouteId` and dynamically evaluated active navigation in the route preview branch:
```kotlin
val activeNavigatedRouteId by viewModel.activeNavigatedRouteId.collectAsStateWithLifecycle()
...
val isActivelyNavigating = selectedRoute.summary.id == activeNavigatedRouteId
RouteOnMapScreen(
    route = selectedRoute.toMapRoute(isActiveNavigation = isActivelyNavigating),
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

### 3. Decoupled Inspection in `RouteDetailSheet` (`RouteDetailSheet.kt`)
Extended `RouteDetailSheet` to default `isActiveNavigation = false` and support dynamic selection toggles without breaking existing callers:
```kotlin
@Composable
fun RouteDetailSheet(
    routeWithPath: RouteWithPath,
    modifier: Modifier = Modifier,
    isActiveNavigation: Boolean = false,
    onToggleSelection: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    EntityDetailSheetScaffold(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        RouteOnMapScreen(
            route = routeWithPath.toMapRoute(isActiveNavigation = isActiveNavigation),
            routeSummary = routeWithPath.summary,
            onToggleSelection = onToggleSelection ?: {},
            modifier = Modifier.fillMaxSize(),
            useStatusBarsPadding = false
        )
    }
}
```

### 4. Canonical Visual Attribute Resolution Hierarchy
The canonical 3-tier polyline resolution hierarchy in `MapModels.kt` is now fully and dynamically engaged:
- **Active Navigation (`isActiveNavigation == true`)**: `TTColor.RouteActiveNavigation` (0xFF1E88E5), width `26f`, pattern `null` (solid ribbon).
- **Selected Route (`isSelected == true`)**: `TTColor.RouteSelected` (0xFF1565C0), width `10f`, pattern `Dash(15f)`, `Gap(15f)`.
- **Unselected Route (`isSelected == false`)**: `TTColor.RouteUnselected` (0xFF90CAF9), width `6f`, pattern `Dash(15f)`, `Gap(15f)`.

---

## 4. Verification & Clean-Room Regression Results

### 1. Targeted Unit & Contract Tests
- `RoutesScreenContractTest`:
  - `testRoutesScreen_doesNotHardcodeActiveNavigation`: Asserts `RoutesScreen.kt` does not contain `toMapRoute(isActiveNavigation = true)` and evaluates `isActivelyNavigating` dynamically.
  - `testRoutesViewModel_exposesActiveNavigatedRouteId`: Asserts `RoutesViewModel` exposes `activeNavigatedRouteId: StateFlow<Long?>`.
  - `testRoutePreview_whenPassiveUnselected_usesUnselectedDashedStyling`: Asserts passive unselected route renders with `TTColor.RouteUnselected`, width 6f, dashed pattern.
  - `testRoutePreview_whenPassiveSelected_usesSelectedDashedStyling`: Asserts passive selected route renders with `TTColor.RouteSelected`, width 10f, dashed pattern.
  - `testRoutePreview_whenActivelyNavigating_usesProminentSolidStyling`: Asserts actively navigated route renders with `TTColor.RouteActiveNavigation`, width 26f, solid ribbon.
  - `testRoutePreview_dynamicSelectionToggle_updatesPolylineStyling`: Asserts toggling route selection immediately shifts polyline color and width.
- `RouteDetailSheetContractTest`:
  - `testRouteDetailSheet_doesNotHardcodeActiveNavigation`: Asserts `RouteDetailSheet.kt` does not contain `toMapRoute(isActiveNavigation = true)` and passes `isActiveNavigation`.
- All 17 targeted tests passed in 4s.

### 2. Clean-Room Full Suite Regression
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL in 2m 36s** (32 actionable tasks, 100% pass rate across entire unit test suite, zero regressions).

---

## 5. Living Documentation & Traceability
- `docs/requirements.md`: `REQ-UI-347` transitioned from `In Progress` to `Verified`.
- `docs/tests.md`: `TST-UI-307` transitioned from `In Progress` to `Verified`.
- Parent ticket [ATT-3172](https://atrainingtracker.atlassian.net/browse/ATT-3172) assigned Fix Version `V4.9.39` and advanced to `Test`.
