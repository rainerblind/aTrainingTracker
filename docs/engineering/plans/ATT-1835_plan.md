# Stage 3 Implementation Plan: ATT-1835 - Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection (Rework Cycle 2: Cockpit UI Wiring & In-Ride Auto-Detection HUD Integration)

**Ticket**: [ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835)  
**Sub-task**: [ATT-2413](https://rainerblind.atlassian.net/browse/ATT-2413) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.40` (assigned upon completion per Rule 19)  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-024`  
**Test Spec ID**: `TST-MAP-026`  
**Branch**: `feature/ATT-1835`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Design (SWE.2)

```
+-----------------------------------------------------------------------------------+
| Tracking UI Layer (Jetpack Compose)                                               |
|                                                                                   |
|  [SensorGridScreen.kt]                                                            |
|    - RouteSelectorViewModel instance (scoped via remember / viewModel factory)    |
|    - Observes routeSelectorUiState: StateFlow<RouteSelectorUiState>               |
|                                                                                   |
|    1. RouteActionChip (Tracking Mode HUD / Header):                               |
|       - Inactive: [ 📍 Route wählen ] (opens modal sheet)                         |
|       - Active:   [ ✓ Alpen-Runde 65 km ] (opens modal sheet to switch/stop)      |
|                                                                                   |
|    2. AutoDetectedRouteBanner (In-Ride HUD Prompt):                               |
|       - Renders when uiState.isAutoPromptVisible && candidate != null             |
|       - Actions: [Aktivieren] -> selectRoute(id), [Ablehnen] -> dismiss(id)       |
|                                                                                   |
|    3. RouteSelectorModalBottomSheet (when showRouteSelectorSheet == true):        |
|       - Material 3 ModalBottomSheet wrapping RouteSelectorContent                |
|       - Filter chips (<40km, 40-80km, >80km / Nähe, Zuletzt, Länge for N >= 5)    |
|       - Route cards with 1-tap activation and "Route beenden" button              |
|                                                                                   |
|    4. Live GPS Telemetry Forwarding:                                              |
|       - LaunchedEffect(currentLocation, state.userBearing, state.userSpeed)       |
|       - Converts to Location("GPS") and dispatches to viewModel.onLocationChanged |
+-----------------------------------------------------------------------------------+
                                         ^
                                         | Observes / Dispatches
+----------------------------------------+------------------------------------------+
| Domain & Evaluation Engine (Already constructed & verified in Cycle 1)            |
|                                                                                   |
|  [RouteProximityRanker] (Pure Kotlin / Math)                                      |
|    - 5-Tier Tie-Breaking for shared start locations (e.g. Home Hub d ≈ 0m):       |
|      * Tier 1: Proximity Group (< 250m)                                           |
|      * Tier 2: Active Sport Profile Match                                         |
|      * Tier 3: Movement Heading Alignment (Δbearing <= 45°)                       |
|      * Tier 4: Recency & Frequency (Last ridden date syncedAt)                    |
|      * Tier 5: Fallback Length / Alphabetical                                     |
|                                                                                   |
|  [RouteAutoDetector] (Stateful Trajectory Matcher)                                |
|    - Matches GPS stream against saved polylines:                                  |
|      * Cross-track distance <= 30m for >= 200m                                    |
|      * Heading alignment Δθ <= 35°                                                |
|      * 15-minute dismissal cooldown                                               |
+-----------------------------------------------------------------------------------+
                                         ^
                                         | Reads / Mutates
+----------------------------------------+------------------------------------------+
| Data Layer                                                                        |
|  [RoutesRepository] (Singleton)                                                   |
|    - allRoutes: StateFlow<List<RouteWithPath>>                                    |
|    - activeNavigatedRouteId: StateFlow<Long?>                                     |
|    - setActiveNavigatedRoute(routeId: Long?)                                      |
+-----------------------------------------------------------------------------------+
```

---

## 2. Order-Dependent Construction Steps

### Step 1: Modal Bottom Sheet Composable in `RouteSelectorSheet.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
* **Changes**:
  - Expose `@OptIn(ExperimentalMaterial3Api::class) @Composable fun RouteSelectorModalBottomSheet(...)`:
    ```kotlin
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteSelectorModalBottomSheet(
        viewModel: RouteSelectorViewModel,
        onDismiss: () -> Unit,
        onRouteSelected: (Long) -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = BottomSheetDesign.SheetShape,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = modifier
        ) {
            RouteSelectorContent(
                viewModel = viewModel,
                onRouteSelected = { routeId ->
                    onRouteSelected(routeId)
                    onDismiss()
                }
            )
        }
    }
    ```
* **Validation**: Code compiles, clean sheet container tokens applied.

### Step 2: Cockpit HUD Route Action Chip in `SensorGridScreen.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  - In `SensorGridScreen.kt`, instantiate `RouteSelectorViewModel` using `RoutesRepository.getInstance(context)`.
  - Collect `val routeSelectorState by routeSelectorViewModel.uiState.collectAsState()`.
  - Add state `var showRouteSelectorSheet by remember { mutableStateOf(false) }`.
  - In `ScreenMode.TRACKING`, render the `RouteActionChip` in the top action/HUD area:
    - Inactive: Displays route icon + *"Route wählen"* (`R.string.route_action_select`).
    - Active: Displays route icon + active route name + checkmark (`routeSelectorState.activeRoute?.summary?.name`).
    - Tapping opens `showRouteSelectorSheet = true`.
* **Validation**: Visible touch target with ripple, high contrast in dark/light modes.

### Step 3: Auto-Detected Route HUD Banner in `SensorGridScreen.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  - In the banner section of `SensorGridScreen.kt` (directly alongside `TurnPromptBanner`):
    - When `screenMode == ScreenMode.TRACKING && routeSelectorState.isAutoPromptVisible && routeSelectorState.autoDetectedCandidate != null`:
      - Render `AutoDetectedRouteBanner`:
        ```kotlin
        routeSelectorState.autoDetectedCandidate?.let { candidate ->
            AutoDetectedRouteBanner(
                route = candidate,
                onActivate = {
                    routeSelectorViewModel.activateCandidate(candidate.summary.id)
                },
                onDismiss = {
                    routeSelectorViewModel.dismissCandidate(candidate.summary.id)
                }
            )
        }
        ```
* **Validation**: Appears dynamically with animated entry when a route match is detected; dismisses cleanly upon activation or user rejection.

### Step 4: Modal Bottom Sheet Invocation in `SensorGridScreen.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  - When `showRouteSelectorSheet == true`:
    ```kotlin
    if (showRouteSelectorSheet) {
        RouteSelectorModalBottomSheet(
            viewModel = routeSelectorViewModel,
            onDismiss = { showRouteSelectorSheet = false }
        )
    }
    ```
* **Validation**: Sheet opens from bottom, lists available routes ranked by proximity/sport/heading, closes upon route selection or outside tap.

### Step 5: Continuous GPS Telemetry Forwarding in `SensorGridScreen.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  - In `SensorGridScreen.kt`, observe incoming `currentLocationFlow`, `state.userBearing`, and `state.userSpeed`:
    ```kotlin
    val currentLatLng by currentLocationFlow.collectAsState()
    LaunchedEffect(currentLatLng, state.userBearing, state.userSpeed) {
        currentLatLng?.let { latLng ->
            val location = Location("GPS").apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
                state.userBearing?.let { bearing = it }
                state.userSpeed?.let { speed = it.toFloat() }
            }
            routeSelectorViewModel.onLocationChanged(location)
        }
    }
    ```
* **Validation**: Location changes trigger candidate evaluation in `RouteAutoDetector` asynchronously.

### Step 6: Architectural Contract & Integration Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
* **Changes**:
  - `SensorGridScreenRouteIntegrationTest.kt`: Validates that `SensorGridScreen.kt` source code includes `RouteSelectorModalBottomSheet`, `RouteActionChip`, `AutoDetectedRouteBanner`, and location forwarding.
  - `RouteSelectorSheetTest.kt`: Validates composable structure, filter chips visibility threshold ($N \ge 5$), and action callbacks.
* **Validation**: Run targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenRouteIntegrationTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`

### Step 7: Clean-Room Full Suite Regression Execution
* **Target**: Full project regression verification.
* **Execution**: `./gradlew testDebugUnitTest` (unsandboxed).
* **Success Criteria**: 100% test pass rate with zero regressions across entire workspace.

---

## 3. Invariants & Safety Measures

1. **Non-Blocking Compose Execution**:
   - Location dispatch to `RouteSelectorViewModel` triggers evaluation within viewModel scope (`viewModelScope.launch`), preventing frame drops in Compose.
2. **GPS Puck & Sensor Telemetry Integrity**:
   - Modal bottom sheet and route banner do not pause or drop 1Hz sensor telemetry updates.
3. **Null-Safety & Disconnection Resilience**:
   - If `currentLocationFlow` is null or routes list is empty, UI falls back gracefully to empty states without throwing exceptions.
4. **Architectural Permanence**:
   - Contract tests prevent future regressions where UI components could become orphaned.

---

## 4. Stage Gate 3 Recommendation

* **Gate 3 Status**: **READY FOR AUDIT**
* **Recommendation**: **RECOMMEND PASS**
