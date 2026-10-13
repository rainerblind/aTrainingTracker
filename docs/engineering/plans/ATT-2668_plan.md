# Stage 3: Implementation Plan - ATT-2668: Filter route selector by active sport type and display sport icon in route list

**Ticket**: [ATT-2668](https://atrainingtracker.atlassian.net/browse/ATT-2668)  
**Sub-task**: [ATT-2844](https://atrainingtracker.atlassian.net/browse/ATT-2844) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-310`  
**Test Mapping**: `TST-UI-270`  
**Branch**: `feature/ATT-2668`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

The quick route selector currently filters candidate routes solely by start-point proximity radius without filtering by sport discipline. In cycling workouts, athletes see running routes; in running workouts, athletes see road cycling routes. Furthermore, `RouteCard` in `RouteSelectorSheet.kt` displays route titles without sport badges or iconography.

This implementation introduces:
1. Active sport discipline filtering in `RouteProximityRanker.filterAndRankRoutes(...)` and `RouteSelectorViewModel.kt` following strict filtering invariants (matching sport + UNKNOWN fallback).
2. Active sport state wiring in `TrackingTabsScreen.kt` and `SensorGridScreen.kt`.
3. Vector sport icon rendering in `RouteCard` (`RouteSelectorSheet.kt`) preceding route names.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-310` (*Sport-Discipline Filtering in Quick Route Selector and Route List Sport Iconography*)
* **Test Mapping**: `TST-UI-270` (*Sport-Discipline Filtering in Quick Route Selector and Route List Sport Iconography Verification*)
  - `TST-UI-270.1`: `RouteProximityRanker.filterAndRankRoutes` sport filtering unit tests.
  - `TST-UI-270.2`: `RouteSelectorViewModel.setActiveSport` reactive state updates unit tests.
  - `TST-UI-270.3`: `RouteCard` sport icon contract test.
  - `TST-UI-270.4`: 9-language localization parity verification.
  - `TST-UI-270.5`: Clean-room full-suite regression test (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Untagged Route Fallback Invariant**: Routes with `bSportType == BSportType.UNKNOWN` are always visible and selectable, regardless of active sport discipline.
3. **Multisport / Unknown Sport Fallback Invariant**: When the active sport is `BSportType.UNKNOWN`, `BSportType.CONFLICT`, or null, all routes within proximity are returned.
4. **Thread Safety & Dispatcher Affinity**: Database queries and flows remain bound to designated background dispatchers.
5. **Parent Human Decision Gate**: The parent ticket `ATT-2668` remains terminal at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `RouteProximityRanker.kt`
- Add helper function:
  ```kotlin
  fun matchesSport(routeSport: BSportType, activeSport: BSportType?): Boolean {
      if (activeSport == null || activeSport == BSportType.UNKNOWN || activeSport == BSportType.CONFLICT) {
          return true
      }
      return routeSport == activeSport || routeSport == BSportType.UNKNOWN
  }
  ```
- Extend `filterAndRankRoutes`:
  ```kotlin
  fun filterAndRankRoutes(
      routes: List<RouteWithPath>,
      currentLocation: LatLng?,
      radiusMeters: Float = 1000.0f,
      activeSport: BSportType? = null
  ): List<RouteWithPath>
  ```
  Filter candidate routes by checking `matchesSport(route.summary.bSportType, activeSport)`.

### Component 2: `RouteSelectorViewModel.kt`
- Introduce `_activeSportType = MutableStateFlow<BSportType>(BSportType.UNKNOWN)`.
- Expose `fun setActiveSport(sport: BSportType)`.
- Expand `RouteContext` to include `activeSport: BSportType`.
- Combine `_activeSportType` into `routeContextFlow`.
- In `uiState` combine block:
  - For pre-tracking: pass `context.activeSport` to `RouteProximityRanker.filterAndRankRoutes(..., activeSport = context.activeSport)`.
  - For in-tracking: filter candidate routes evaluated by `autoDetector.evaluateMatchingRoutes` with `matchesSport(it.summary.bSportType, context.activeSport)`.
- In `onLocationChanged`: filter routes evaluated for `autoDetector.evaluate(...)` by `matchesSport(it.summary.bSportType, _activeSportType.value)`.

### Component 3: Integration Sites (`TrackingTabsScreen.kt` & `SensorGridScreen.kt`)
- `TrackingTabsScreen.kt`:
  ```kotlin
  LaunchedEffect(bSportType) {
      routeSelectorViewModel.setActiveSport(bSportType)
  }
  ```
- `SensorGridScreen.kt`:
  ```kotlin
  LaunchedEffect(state.bSportType) {
      actualRouteSelectorViewModel.setActiveSport(state.bSportType)
  }
  ```

### Component 4: `RouteSelectorSheet.kt` (`RouteCard`)
- In `RouteCard(route: RouteWithPath, isActive: Boolean, onClick: () -> Unit)`:
  - Add leading `Icon` in the `Row`:
    ```kotlin
    Icon(
        painter = painterResource(id = route.summary.bSportType.iconResId),
        contentDescription = stringResource(id = route.summary.bSportType.stringResId),
        modifier = Modifier.size(24.dp),
        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.width(12.dp))
    ```

### UI Consistency (Rule 23)
* **Reference screen / component**: `RouteSelectorSheet.kt` (`RouteCard`) and `SportTypeSelector.kt`.
* **Reused components**: Standard Compose `Icon`, `Text`, `Spacer`, `MaterialTheme.typography.titleMedium`, `MaterialTheme.typography.bodySmall`.
* **Theme tokens**:
  - Size: `24.dp` matching standard icon dimensions in `SportTypeSelector.kt`.
  - Margin: `12.dp` horizontal spacer separating icon from route metadata.
  - Colors: `MaterialTheme.colorScheme.onSurfaceVariant` for unselected/inactive state, `MaterialTheme.colorScheme.primary` for active state.
* **New one-off styles & justification**: None; 100% compliant with standard tokens and existing drawable assets.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `RouteProximityRanker.kt` & Unit Tests
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteProximityRanker.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteProximityRankerTest.kt`
* Actions:
  - Add `matchesSport` and default parameter `activeSport: BSportType? = null` in `filterAndRankRoutes`.
  - Add test methods in `RouteProximityRankerTest.kt` verifying filtering for `BIKE`, `RUN`, and `UNKNOWN`.
* Verification: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteProximityRankerTest"`

### Step 2: Update `RouteSelectorViewModel.kt` & Unit Tests
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModel.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* Actions:
  - Add `_activeSportType`, `setActiveSport`, and update `RouteContext` and `uiState` evaluation.
  - Add unit tests in `RouteSelectorViewModelTest.kt` testing dynamic discipline switching.
* Verification: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorViewModelTest"`

### Step 3: Update `RouteSelectorSheet.kt` (`RouteCard`) & Contract Tests
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetContractTest.kt`
* Actions:
  - Insert sport `Icon` with 24.dp size and 12.dp spacing in `RouteCard`.
  - Add/update contract assertions for icon presence and content description.
* Verification: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`

### Step 4: Wire Integration Sites in `TrackingTabsScreen.kt` and `SensorGridScreen.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Actions:
  - Add `LaunchedEffect(bSportType)` in `TrackingTabsScreen.kt` to update `routeSelectorViewModel`.
  - Add `LaunchedEffect(state.bSportType)` in `SensorGridScreen.kt` to update `actualRouteSelectorViewModel`.
* Verification: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run all route-related unit and contract tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`.
  - Run full test suite: `./gradlew testDebugUnitTest`.
* **Rollback**: Clean git commit separation allows atomic reversal if any regressions appear.
