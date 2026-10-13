# Stage 5 Walkthrough: ATT-3092 - Automatically set selected sport type when selecting route (Bike / Run)

**Ticket**: [ATT-3092](https://atrainingtracker.atlassian.net/browse/ATT-3092)  
**Sub-task**: [ATT-3117](https://atrainingtracker.atlassian.net/browse/ATT-3117) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)  
**Sprint**: `Sprint 2026-41.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Problem Domain

### 1.1 The Issue
When an athlete prepares or begins a workout and selects a pre-planned route via the Quick Route Selector sheet or auto-detection candidate prompt, the app previously maintained whatever sport was previously active in `BANALServiceRepository`.
Because routes have a definitive sport classification (`BSportType.BIKE` or `BSportType.RUN`), athletes selecting a bike route while the app was left on Run or Unknown were forced to manually navigate back to the Control tab, click the sport dropdown, and select Bike (and vice-versa).
Failure to manually switch the sport resulted in:
1. Recording workouts under the wrong sport profile, polluting stats and analytics.
2. Inappropriate sensor scanning policies (e.g. power meters and cycling cadence sensors not being activated if left on Run).

### 1.2 Forensic Root Cause Analysis
1. In `RouteSelectorViewModel.kt`, `selectRoute(routeId: Long)` invoked `routesRepository.setActiveNavigatedRoute(routeId)` and dismissed candidate prompts, but had no reference or bridge to `BANALServiceRepository`.
2. `BANALServiceRepository` had a `setUserSelectedSportType(bSportType)` method, but it did not immediately update its internal `_bSportType.value` StateFlow, relying instead on a background polling observation loop.
3. In `TrackingTabsScreen.kt`, the `RouteSelectorViewModel` factory instantiated `RouteSelectorViewModel` without providing `BANALServiceRepository`.

---

## 2. Implemented Architecture & Data Flow Solution (`REQ-MAP-045`)

### 2.1 Reactive State & Sport Auto-Synchronization in `RouteSelectorViewModel.kt`
- Injected `banalServiceRepository: BANALServiceRepository? = null` into the `RouteSelectorViewModel` constructor, preserving backward compatibility via overloaded secondary constructors.
- In `selectRoute(routeId: Long)`:
  ```kotlin
  fun selectRoute(routeId: Long) {
      routesRepository.setActiveNavigatedRoute(routeId)
      val selectedRoute = routesRepository.allRoutes.value.firstOrNull { it.summary.id == routeId }
      val routeSport = selectedRoute?.summary?.bSportType
      if (routeSport == BSportType.BIKE || routeSport == BSportType.RUN) {
          banalServiceRepository?.setUserSelectedSportType(routeSport)
      }
      if (_autoDetectedCandidate.value?.summary?.id == routeId) {
          _autoDetectedCandidate.value = null
      }
  }
  ```
- Because `activateCandidate(routeId: Long)` delegates to `selectRoute(routeId)`, candidate route activation automatically synchronizes sport type without duplication.
- `stopRoute()` and `clearRoute()` clear the active navigated route (`setActiveNavigatedRoute(null)`) but strictly preserve the active workout sport type.
- Routes classified as `BSportType.UNKNOWN` or `BSportType.CONFLICT` strictly preserve the current sport type without overriding it.

### 2.2 Synchronous State Emission in `BANALServiceRepository.kt`
- Updated `setUserSelectedSportType(bSportType: BSportType)`:
  ```kotlin
  fun setUserSelectedSportType(bSportType: BSportType) {
      _bSportType.value = bSportType
      _serviceBinder.value?.setUserSelectedSportType(bSportType)
  }
  ```
  Immediately updating `_bSportType.value` guarantees instantaneous reactive UI updates without waiting for the 1-second polling observation loop.

### 2.3 Dependency Injection in `TrackingTabsScreen.kt`
- In `TrackingTabsScreen.kt`, passed `banalServiceRepository = controlViewModel.banalServiceRepository` in the ViewModel factory:
  ```kotlin
  val routeSelectorViewModel: RouteSelectorViewModel = viewModel(
      factory = object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
              return RouteSelectorViewModel(
                  routesRepository = routesRepo,
                  tuningPreferencesDataStore = tuningDataStore,
                  banalServiceRepository = controlViewModel.banalServiceRepository
              ) as T
          }
      }
  )
  ```

---

## 3. Verification & Test Evidence (`TST-MAP-047`)

### 3.1 Targeted Unit Tests (`RouteSelectorViewModelTest.kt`)
1. `testSelectRoute_bikeRoute_automaticallySynchronizesSportTypeToBike`:
   - Verifies selecting a `BSportType.BIKE` route sets the active navigated route and invokes `banalServiceRepository.setUserSelectedSportType(BSportType.BIKE)`.
2. `testSelectRoute_runRoute_automaticallySynchronizesSportTypeToRun`:
   - Verifies selecting a `BSportType.RUN` route sets the active navigated route and invokes `banalServiceRepository.setUserSelectedSportType(BSportType.RUN)`.
3. `testSelectRoute_unknownOrConflictRoute_doesNotAlterSportType`:
   - Verifies selecting `BSportType.UNKNOWN` or `BSportType.CONFLICT` routes sets active route but NEVER invokes `setUserSelectedSportType`.
4. `testActivateCandidate_bikeRoute_automaticallySynchronizesSportTypeToBike`:
   - Verifies candidate auto-detection acceptance sets active route and synchronizes sport to Bike.
5. `testStopRoute_and_clearRoute_doesNotAlterSportType`:
   - Verifies stopping and clearing routes clears active route (`null`) but NEVER alters active sport type.

Targeted Test Execution Result: **All tests passed cleanly in 16s (0 failures)**.

### 3.2 Clean-Room Full Regression Suite Execution
Executed complete unit test suite from clean state:
```bash
./gradlew testDebugUnitTest
```
Result: **BUILD SUCCESSFUL (100% pass rate across entire repository, 0 regressions)**.

---

## 4. Requirement Traceability Matrix

| Requirement ID | Test Case ID | Scope | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-045` | `TST-MAP-047` | Automatic Active Workout Sport Type Synchronization on Route Selection (Bike / Run) | **Verified** |

---

## 5. Living Documentation & Git Integration
- `docs/requirements.md` (`REQ-MAP-045`) transitioned from `Specified` to `Verified`.
- `docs/tests.md` (`TST-MAP-047`) transitioned from `Specified` to `Verified`.
- Feature branch `feature/ATT-3092` merged into `sprint/2026-41.8` with `--no-ff`.
- Parent ticket `ATT-3092` advanced to `Final Review (Human)` with Fix Version `V4.9.39`.
