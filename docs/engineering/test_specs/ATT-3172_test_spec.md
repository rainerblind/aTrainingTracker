# Stage 2: Requirement & Test Specification - ATT-3172

## 1. Requirement Specification (`REQ-UI-347`)

### Formal Requirement Clause
| Field | Value |
| :--- | :--- |
| **Requirement ID** | `REQ-UI-347` |
| **Title** | Canonical Route Preview Polyline Styling & Selection State Decoupling for RoutesScreen and RouteDetailSheet |
| **Primary Artifacts** | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt` |
| **Amends** | `REQ-MAP-040` (specifically Clause 5) |
| **Target Release** | `V4.9.39` |
| **Status** | In Progress |

### Architectural Specification & Constraints
The system SHALL decouple route map preview rendering in `RoutesScreen.kt` and `RouteDetailSheet.kt` from unconditional active navigation styling, rendering route polylines according to their actual selection status (`RouteSummary.isSelected`) and active navigation state (`activeNavigatedRouteId`), ensuring that toggling route selection provides immediate dynamic visual feedback on the map preview (ATT-3172, amending `REQ-MAP-040` clause 5):

1. **RoutesViewModel Active Route Exposure (`RoutesViewModel.kt`)**:
   - `RoutesViewModel` SHALL expose `val activeNavigatedRouteId: StateFlow<Long?> = routesRepository.activeNavigatedRouteId`.

2. **Dynamic Route State Propagation (`RoutesScreen.kt`)**:
   - `RoutesScreen` SHALL observe `viewModel.activeNavigatedRouteId`.
   - In the route preview branch, `selectedRoute.toMapRoute()` SHALL receive `isActiveNavigation = (selectedRoute.summary.id == activeNavigatedRouteId)`.
   - When not actively navigated, `selectedRoute.toMapRoute()` reflects `selectedRoute.summary.isSelected`.
   - When `onToggleSelection` is invoked, `viewModel.toggleRouteSelection(selectedRoute.summary.id, isSelected)` mutates SQLite state, causing `selectedRoute.summary.isSelected` to update and dynamically re-render the polyline.

3. **Decoupled Route Inspection in RouteDetailSheet (`RouteDetailSheet.kt`)**:
   - `RouteDetailSheet` SHALL accept optional `isActiveNavigation: Boolean = false` and `onToggleSelection: ((Boolean) -> Unit)? = null` parameters (defaulting to `false` and no-op).
   - `RouteOnMapScreen` SHALL be invoked with `routeWithPath.toMapRoute(isActiveNavigation = isActiveNavigation)`, eliminating hardcoded `isActiveNavigation = true` and accurately reflecting `routeWithPath.summary.isSelected`.

4. **Visual Attribute Resolution Invariants (`MapModels.kt`)**:
   - When `isActiveNavigation == true`: Color = `TTColor.RouteActiveNavigation` (0xFF1E88E5), Width = 26f, Pattern = `null` (solid ribbon).
   - When `isActiveNavigation == false && isSelected == true`: Color = `TTColor.RouteSelected` (0xFF1565C0), Width = 10f, Pattern = `Dash(15f)`, `Gap(15f)`.
   - When `isActiveNavigation == false && isSelected == false`: Color = `TTColor.RouteUnselected` (0xFF90CAF9), Width = 6f, Pattern = `Dash(15f)`, `Gap(15f)`.

5. **Preservation of System Invariants**:
   - Active turn-by-turn workout navigation styling (`REQ-MAP-040`) remains fully intact.
   - Stacking z-index order (`ROUTE_BASE_Z_INDEX < ROUTE_ACTIVE_BASE_Z_INDEX < CLIMB_Z_INDEX < SEGMENT_Z_INDEX < USER_LOCATION_Z_INDEX`) remains preserved.
   - Clean-room unit test suite pass rate MUST remain 100%.

---

### Chesterton's Fence Archaeology (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Amends `REQ-MAP-040` (*High-Glanceability Route Polyline Scaling and Solid Line Rendering for Actively Navigated Route*, ATT-2940), specifically clause 5.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.6 (`ATT-2940`, commit `a1dc652e`).
3. **Root Reason for Existing Formulation**:
   - In ATT-2940, the developer sought to highlight the inspected route on `RoutesScreen` and `RouteDetailSheet` so it would stand out. However, hardcoding `isActiveNavigation = true` conflated passive inspection with active workout navigation, permanently rendering 26f solid blue and masking `summary.isSelected` changes.
4. **Preservation of Core Invariants**:
   - Live workout navigation rendering (26f solid blue ribbon), 3-tier polyline resolution, z-index hierarchy, and 100% clean-room test pass rate remain strictly preserved.

---

### Acceptance Criteria (Given-When-Then)
* **AC-1 (Unselected Route Preview Appearance)**:
  - *Given* `RoutesScreen` previewing a route with `isSelected = false` and not actively navigating,
  - *When* `RouteOnMapScreen` renders,
  - *Then* the polyline color is `TTColor.RouteUnselected` (0xFF90CAF9), width is `6f`, and pattern is dashed (`Dash(15f)`, `Gap(15f)`).
* **AC-2 (Dynamic Route Selection Toggle)**:
  - *Given* `RoutesScreen` previewing a route,
  - *When* the athlete toggles the switch in `RouteSummaryHeader` from off to on,
  - *Then* the polyline dynamically updates to `TTColor.RouteSelected` (0xFF1565C0), width `10f`, and pattern dashed.
* **AC-3 (Active Workout Navigation Integrity)**:
  - *Given* an active workout with a route actively navigated (`activeNavigatedRouteId == route.id`),
  - *When* inspected in `RoutesScreen`,
  - *Then* the polyline renders with `TTColor.RouteActiveNavigation` (0xFF1E88E5), width `26f`, and solid pattern (`pattern == null`).
* **AC-4 (RouteDetailSheet Decoupled Preview)**:
  - *Given* `RouteDetailSheet` invoked on a passive route,
  - *When* composed,
  - *Then* `routeWithPath.toMapRoute(isActiveNavigation = false)` renders according to `routeWithPath.summary.isSelected` (unselected 6f dashed vs selected 10f dashed).

---

## 2. Test Specification (`TST-UI-307`)

### Verification Procedures
1. **Contract Tests for RoutesScreen (`RoutesScreenContractTest.kt`)**:
   - Verify `RoutesScreen.kt` does NOT hardcode `toMapRoute(isActiveNavigation = true)`.
   - Verify `RoutesScreen.kt` passes `isActiveNavigation = (selectedRoute.summary.id == activeNavigatedRouteId)`.
   - Verify `RoutesViewModel.kt` exposes `val activeNavigatedRouteId: StateFlow<Long?>`.

2. **Contract Tests for RouteDetailSheet (`RouteDetailSheetContractTest.kt`)**:
   - Verify `RouteDetailSheet.kt` does NOT hardcode `toMapRoute(isActiveNavigation = true)`.
   - Verify `RouteDetailSheet.kt` defaults `isActiveNavigation = false` and propagates `routeWithPath.toMapRoute(isActiveNavigation = isActiveNavigation)`.

3. **Three-Tier Polyline Visual Resolution Unit Tests (`MapRouteActiveNavigationTest.kt`)**:
   - Verify that when `isActiveNavigation = false` and `isSelected = false`, `color` is `RouteUnselected`, `width` is `6f`, and pattern is dashed.
   - Verify that when `isActiveNavigation = false` and `isSelected = true`, `color` is `RouteSelected`, `width` is `10f`, and pattern is dashed.
   - Verify that when `isActiveNavigation = true`, `color` is `RouteActiveNavigation`, `width` is `26f`, and pattern is solid.

4. **Localization Parity Audit (`TranslationParityTest.kt`)**:
   - Verify 100% parity across all 9 supported locales (0 new UI strings introduced).

5. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Verification Target | Test Case / Procedure |
| :--- | :--- | :--- |
| `REQ-UI-347.1` (RoutesViewModel Active Route Exposure) | `RoutesViewModel.kt` | `RoutesScreenContractTest.testRoutesViewModel_exposesActiveNavigatedRouteId` |
| `REQ-UI-347.2` (Dynamic Route State in RoutesScreen) | `RoutesScreen.kt` | `RoutesScreenContractTest.testRoutesScreen_doesNotHardcodeActiveNavigation` |
| `REQ-UI-347.3` (Decoupled Inspection in RouteDetailSheet) | `RouteDetailSheet.kt` | `RouteDetailSheetContractTest.testRouteDetailSheet_doesNotHardcodeActiveNavigation` |
| `REQ-UI-347.4` (3-Tier Polyline Invariants) | `MapModels.kt` | `MapRouteActiveNavigationTest.testMapRoute_whenSelectedPassive_usesStandardStyling`, `testMapRoute_whenUnselected_usesSubordinateStyling` |
| `REQ-UI-347.5` (Clean-Room Suite Integrity) | Entire App | Full `./gradlew testDebugUnitTest` run |
