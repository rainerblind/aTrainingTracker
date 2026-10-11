# Stage 3: Implementation Plan - ATT-3172

## 1. Overview & Architectural Design (SWE.2)
- **Issue**: [ATT-3172](https://atrainingtracker.atlassian.net/browse/ATT-3172): `[Bug] Route preview on RoutesScreen and RouteDetailSheet hardcodes isActiveNavigation=true, ignoring active/inactive selection state`
- **Requirement**: `REQ-UI-347` (amending `REQ-MAP-040` clause 5)
- **Test Spec**: `TST-UI-307`
- **Sprint**: `2026-41.9`

### Architecture & Data Flow
```
RoutesRepository
       │ (activeNavigatedRouteId: StateFlow<Long?>)
       ▼
RoutesViewModel
       │ (activeNavigatedRouteId: StateFlow<Long?>)
       ▼
RoutesScreen (collectAsStateWithLifecycle)
       │
       ├─► toMapRoute(isActiveNavigation = (selectedRoute.summary.id == activeNavigatedRouteId))
       │
       ▼
RouteOnMapScreen
       │ (MapRoute visual resolution)
       ├─ isActiveNavigation == true  ──► 26f solid blue ribbon (TTColor.RouteActiveNavigation)
       ├─ isSelected == true          ──► 10f dashed line (TTColor.RouteSelected)
       └─ isSelected == false         ──► 6f dashed line (TTColor.RouteUnselected)
```

---

## 2. Atomic Implementation Steps

### Step 1: Expose Active Navigated Route ID in `RoutesViewModel`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt`
- **Changes**:
  - Expose `val activeNavigatedRouteId: StateFlow<Long?> = routesRepository.activeNavigatedRouteId`.
- **Targeted Test**:
  - Verify via `RoutesScreenContractTest.testRoutesViewModel_exposesActiveNavigatedRouteId`.

### Step 2: Dynamic Navigation State Propagation in `RoutesScreen`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`
- **Changes**:
  - Collect `val activeNavigatedRouteId by viewModel.activeNavigatedRouteId.collectAsStateWithLifecycle()`.
  - In the route preview branch (line ~133), replace hardcoded `isActiveNavigation = true` with:
    ```kotlin
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
- **Targeted Test**:
  - Verify via `RoutesScreenContractTest.testRoutesScreen_doesNotHardcodeActiveNavigation`.

### Step 3: Decouple Route Inspection in `RouteDetailSheet`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt`
- **Changes**:
  - Extend `RouteDetailSheet` signature to accept optional parameters:
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
  - This eliminates hardcoded `isActiveNavigation = true` while preserving 100% binary/source compatibility with existing callers like `StarredSegmentsScreen.kt`.
- **Targeted Test**:
  - Verify via `RouteDetailSheetContractTest.testRouteDetailSheet_doesNotHardcodeActiveNavigation`.

### Step 4: Author Contract and Unit Tests
- **Files**:
  - Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt`.
  - Update `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheetContractTest.kt`.
- **Changes**:
  - Assert that `RoutesScreen.kt` does NOT contain `toMapRoute(isActiveNavigation = true)`.
  - Assert that `RoutesScreen.kt` queries `activeNavigatedRouteId`.
  - Assert that `RouteDetailSheet.kt` does NOT contain `toMapRoute(isActiveNavigation = true)`.
  - Assert that `RouteDetailSheet` composable parameters default `isActiveNavigation = false`.
- **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenContractTest \
                              --tests com.atrainingtracker.trainingtracker.ui.routes.RouteDetailSheetContractTest \
                              --tests com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest
  ```

---

## 3. UI Consistency & Living Design Guidelines Compliance (Rule 23)
- **Reference**: `docs/design_guidelines.md` §2.2 (*Polyline Visual Hierarchy*) & §5.8 (*Modal Entity Sheets*).
- **Tokens Reused**:
  - `TTColor.RouteActiveNavigation` (0xFF1E88E5)
  - `TTColor.RouteSelected` (0xFF1565C0)
  - `TTColor.RouteUnselected` (0xFF90CAF9)
  - `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` (26f)
  - `MapVisualization.ROUTE_WIDTH` (10f)
  - `MapVisualization.ROUTE_UNSELECTED_WIDTH` (6f)
- **Rationale**: No custom colors, line widths, or styling overrides are introduced. The fix relies strictly on canonical `MapRoute` styling evaluation.

---

## 4. Invariant Protection & Rollback Strategy
1. **Active Workout Navigation Invariant**:
   - Actively navigated routes in `TrackingViewModel` and `MapFragmentWithTrackViewModel` remain rendered at 26f solid blue ribbon (`ROUTE_ACTIVE_NAVIGATION_WIDTH`, `pattern = null`).
2. **Backward Compatibility**:
   - `RouteDetailSheet` defaults `isActiveNavigation = false` and `onToggleSelection = null`, ensuring existing callers (e.g. `StarredSegmentsScreen`) require zero changes.
3. **Rollback Strategy**:
   - Changes are purely declarative within three Compose files (`RoutesScreen.kt`, `RouteDetailSheet.kt`, `RoutesViewModel.kt`). Reverting is a clean 1-commit git revert without database schema or state migration side effects.
