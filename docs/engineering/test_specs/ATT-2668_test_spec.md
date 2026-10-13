# Stage 2: Requirement & Test Specification - ATT-2668: Filter route selector by active sport type and display sport icon in route list

**Ticket**: [ATT-2668](https://atrainingtracker.atlassian.net/browse/ATT-2668)  
**Sub-task**: [ATT-2843](https://atrainingtracker.atlassian.net/browse/ATT-2843) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-310` (*Sport-Discipline Filtering in Quick Route Selector and Route List Sport Iconography*)  
**Test Spec ID**: `TST-UI-270`  
**Branch**: `feature/ATT-2668`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-310)

### 1.1 Problem Statement & Rationale
When an athlete opens the quick route selector bottom sheet from the Control Tracking screen or Cockpit, candidate routes are selected solely based on geographic proximity. An athlete preparing for a cycling workout sees nearby running routes, while a runner sees road cycling courses. In addition, route cards display only plain text titles without visual discipline indicators, forcing athletes to read every title to identify route types.

### 1.2 Functional & Architectural Requirements
1. **Discipline Filtering Invariants (`RouteProximityRanker.kt`, `RouteSelectorViewModel.kt`)**:
   - When the active sport is `BSportType.BIKE`: candidate routes within the proximity radius SHALL include only routes where `bSportType == BSportType.BIKE` or `bSportType == BSportType.UNKNOWN`; routes with `bSportType == BSportType.RUN` SHALL be excluded.
   - When the active sport is `BSportType.RUN`: candidate routes within the proximity radius SHALL include only routes where `bSportType == BSportType.RUN` or `bSportType == BSportType.UNKNOWN`; routes with `bSportType == BSportType.BIKE` SHALL be excluded.
   - When the active sport is `BSportType.UNKNOWN` (or `BSportType.CONFLICT` or `null`): all candidate routes within the proximity radius SHALL be included.
   - Untagged routes (`bSportType == BSportType.UNKNOWN`) SHALL always remain accessible across all active sport disciplines.
2. **ViewModel & Context Observation (`RouteSelectorViewModel.kt`)**:
   - `RouteSelectorViewModel` SHALL maintain an active sport state flow (`_activeSportType: MutableStateFlow<BSportType>`) initialized to `BSportType.UNKNOWN`.
   - `RouteSelectorViewModel` SHALL expose `fun setActiveSport(sport: BSportType)` to update the active discipline dynamically.
   - Route candidate evaluation SHALL combine `_activeSportType` into the route context flow, applying the discipline filtering invariants to both pre-tracking proximity ranking and in-ride auto-detection candidate evaluation.
3. **Tracking Integration Sites (`TrackingTabsScreen.kt`, `SensorGridScreen.kt`)**:
   - `TrackingTabsScreen.kt` SHALL observe `controlViewModel.bSportType` and forward updates to `routeSelectorViewModel.setActiveSport(bSportType)`.
   - `SensorGridScreen.kt` SHALL observe `state.bSportType` and forward updates to `actualRouteSelectorViewModel.setActiveSport(state.bSportType)`.
4. **Route List Sport Iconography (`RouteSelectorSheet.kt`)**:
   - In `RouteCard`, a vector icon displaying the route's `bSportType.iconResId` (`R.drawable.bsport_bike`, `R.drawable.bsport_run`, or `R.drawable.bsport_other`) SHALL be rendered immediately preceding the route name.
   - The icon SHALL be sized at `24.dp` and tinted with `MaterialTheme.colorScheme.onSurfaceVariant` (or `MaterialTheme.colorScheme.primary` when `isActive == true`).
   - The icon SHALL include a localized content description referencing `route.summary.bSportType.stringResId`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Cycling Session Candidate Filtering)**:
  * *Given* candidate routes comprising bike routes, running routes, and untagged routes within proximity, and active sport is `BSportType.BIKE`,
  * *When* `RouteSelectorViewModel` computes `uiState.routes`,
  * *Then* all bike routes and untagged routes appear in the list, and all running routes are omitted.
* **Criterion 2 (Running Session Candidate Filtering)**:
  * *Given* candidate routes comprising bike routes, running routes, and untagged routes within proximity, and active sport is `BSportType.RUN`,
  * *When* `RouteSelectorViewModel` computes `uiState.routes`,
  * *Then* all running routes and untagged routes appear in the list, and all bike routes are omitted.
* **Criterion 3 (Unknown / Multisport Session Fallback)**:
  * *Given* candidate routes comprising bike routes, running routes, and untagged routes within proximity, and active sport is `BSportType.UNKNOWN`,
  * *When* `RouteSelectorViewModel` computes `uiState.routes`,
  * *Then* all routes within proximity are returned regardless of sport discipline.
* **Criterion 4 (Sport Iconography in RouteCard)**:
  * *Given* a route item rendered in `RouteSelectorSheet.kt`,
  * *When* `RouteCard` is composed,
  * *Then* an `Icon` component rendering `route.summary.bSportType.iconResId` is positioned before the route title.

### 1.4 System Invariants
- Database schemas for `Routes.db` and existing tables are immutable.
- Backwards compatibility: `filterAndRankRoutes` default parameter `activeSport = null` maintains existing callers.
- Full unit test regression pass rate: 100%.

---

## 2. Test Specification (TST-UI-270)

### Test Case 1: `testFilterAndRankRoutes_filtersBySportType` (`TST-UI-270.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteProximityRankerTest.kt`
* **Preconditions**: Location at (0.0, 0.0). Routes at 100m distance:
  - Route 1: `bSportType = BSportType.BIKE`
  - Route 2: `bSportType = BSportType.RUN`
  - Route 3: `bSportType = BSportType.UNKNOWN`
* **Action**:
  - Call `filterAndRankRoutes(routes, location, 1000f, activeSport = BSportType.BIKE)`.
  - Call `filterAndRankRoutes(routes, location, 1000f, activeSport = BSportType.RUN)`.
  - Call `filterAndRankRoutes(routes, location, 1000f, activeSport = BSportType.UNKNOWN)`.
* **Expected Result**:
  - Bike call returns Route 1 and Route 3 (Route 2 excluded).
  - Run call returns Route 2 and Route 3 (Route 1 excluded).
  - Unknown call returns Route 1, Route 2, and Route 3.

### Test Case 2: `testRouteSelectorViewModel_updatesCandidateRoutesOnSportChange` (`TST-UI-270.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: `RouteSelectorViewModel` initialized with routes of various sports.
* **Action**:
  - Set active sport to `BSportType.BIKE` via `setActiveSport(BSportType.BIKE)`.
  - Set active sport to `BSportType.RUN` via `setActiveSport(BSportType.RUN)`.
  - Set active sport to `BSportType.UNKNOWN` via `setActiveSport(BSportType.UNKNOWN)`.
* **Expected Result**:
  - `uiState.value.routes` dynamically filters matching sport and UNKNOWN routes.

### Test Case 3: `testRouteCard_displaysSportIcon` (`TST-UI-270.3`)
* **Scope**: UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetContractTest.kt`
* **Preconditions**: `RouteSelectorSheet.kt` source inspection.
* **Action**: Verify `RouteCard` declares `Icon` referencing `route.summary.bSportType.iconResId`.
* **Expected Result**: Source contains icon rendering and appropriate modifier sizing/tinting.

### Test Case 4: Localization & Specifier Audit (`TST-UI-270.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/ant/AntStatusLocalizationTest.kt` or `RoutesLocalizationTest.kt`
* **Expected Result**: All 9 locales maintain parity for sport strings (`sport_type_run`, `sport_type_bike`, `sport_type_other`).

### Test Case 5: Clean-Room Regression Suite (`TST-UI-270.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-270.1` | Unit | `RouteProximityRanker.filterAndRankRoutes` | `REQ-UI-310.1` | Specified |
| `TST-UI-270.2` | Unit | `RouteSelectorViewModel.setActiveSport` | `REQ-UI-310.2` | Specified |
| `TST-UI-270.3` | Contract | `RouteCard` (`RouteSelectorSheet.kt`) | `REQ-UI-310.4` | Specified |
| `TST-UI-270.4` | Localization | 9-Language Resource Parity | `REQ-UI-310`, `REQ-UI-106` | Specified |
| `TST-UI-270.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
