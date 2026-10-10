# Stage 3: Implementation Plan - ATT-3092: Automatically set selected sport type when selecting route (Bike / Run)

**Ticket**: [ATT-3092](https://atrainingtracker.atlassian.net/browse/ATT-3092)  
**Sub-task**: [ATT-3115](https://atrainingtracker.atlassian.net/browse/ATT-3115) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-MAP-045` (*Automatic Active Workout Sport Type Synchronization on Route Selection (Bike / Run)*)  
**Test Mapping**: `TST-MAP-047`  
**Branch**: `feature/ATT-3092`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

When an athlete prepares or navigates a workout and selects a route via the Quick Route Selector sheet or auto-detection candidate prompt, the app currently maintains whatever sport was previously active in `BANALServiceRepository`.
Because routes have a definitive sport classification (`BSportType.BIKE` or `BSportType.RUN`), athletes selecting a cycling route while the app was left on Run or Unknown must manually navigate back to the Control tab, click the sport dropdown, and select Bike (and vice-versa).
Failure to manually switch the sport leads to:
1. Recording workouts under the wrong sport profile, polluting stats and analytics.
2. Inappropriate sensor scanning policies (e.g. power meters and cycling cadence sensors not being activated if left on Run).

To streamline user workflow and eliminate friction, the app SHALL automatically synchronize the active workout sport type whenever a route classified as `BSportType.BIKE` or `BSportType.RUN` is selected or activated.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-045` (*Automatic Active Workout Sport Type Synchronization on Route Selection (Bike / Run)*)
* **Test Mapping**: `TST-MAP-047` (*Automatic Active Workout Sport Type Synchronization on Route Selection (Bike / Run) Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Untyped & Conflict Route Neutrality**: Routes with `BSportType.UNKNOWN` or `BSportType.CONFLICT` must never override or clear the athlete's current active sport.
2. **Decoupled Route Clearing / Stop Preservation**: Stopping or clearing a route (`stopRoute()`, `clearRoute()`) cancels active navigation but strictly preserves the active workout sport type.
3. **Dynamic Sensor Scanning Integrity**: Automatic sport transition triggers `BANALServiceRepository.setUserSelectedSportType(bSportType)`, which notifies `BANALService` to perform dynamic sensor search (`startSearchWhenUserChangesSport`) and update active sensor filters.
4. **Proximity & Candidate Auto-Detection**: Proximity ranking, candidate auto-detection, and radius filtering logic remain 100% intact.
5. **100% Clean-Room Unit Test Pass Rate**: Full test suite pass rate must be preserved with zero regressions.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket ATT-3092 remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. UI Consistency (Rule 23)

* **UI Impact**: Does this ticket add or modify UI screens/components?
  * No new UI screens or composables are created.
  * The feature connects the existing `RouteSelectorSheet.kt` selection events to `BANALServiceRepository.setUserSelectedSportType(bSportType)`.
  * The active sport badge and selector on the `ControlTrackingTab` immediately and reactively reflect the updated sport (`BSportType.BIKE` or `BSportType.RUN`) using existing Material 3 theme colors, typography, and iconography.

---

## 5. Proposed Architectural Changes (SWE.2)

### Component 1: `RouteSelectorViewModel.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModel.kt`)
* Add optional constructor parameter `banalServiceRepository: BANALServiceRepository? = null`.
* Update secondary constructor for backward compatibility.
* In `selectRoute(routeId: Long)`:
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
* Because `activateCandidate(routeId: Long)` delegates directly to `selectRoute(routeId)`, candidate route activation automatically synchronizes sport type without duplication.

### Component 2: `TrackingTabsScreen.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`)
* In the `RouteSelectorViewModel` factory definition (line 190), pass `banalServiceRepository = controlViewModel.banalServiceRepository`:
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

### Component 3: `BANALServiceRepository.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`)
* In `setUserSelectedSportType(bSportType: BSportType)`:
  ```kotlin
  fun setUserSelectedSportType(bSportType: BSportType) {
      _bSportType.value = bSportType
      _serviceBinder.value?.setUserSelectedSportType(bSportType)
  }
  ```
* Immediately updating `_bSportType.value` ensures synchronous reactive state propagation to Compose UI and unit tests without waiting for periodic polling.

---

## 6. Atomic Step Sequencing (Order-Dependent)

### Step 1: Update `BANALServiceRepository.kt` Synchronous State Emission
* Modify `setUserSelectedSportType(bSportType: BSportType)` to assign `_bSportType.value = bSportType`.
* Verify with unit tests or existing repository tests.

### Step 2: Update `RouteSelectorViewModel.kt` Constructor & Selection Logic
* Inject `banalServiceRepository: BANALServiceRepository? = null`.
* Implement sport auto-sync in `selectRoute(routeId: Long)`.

### Step 3: Wire Repository in `TrackingTabsScreen.kt`
* Pass `controlViewModel.banalServiceRepository` in the ViewModel factory.

### Step 4: Implement Unit Tests in `RouteSelectorViewModelTest.kt`
* Add tests verifying:
  * Selecting a `BIKE` route invokes `banalServiceRepository.setUserSelectedSportType(BSportType.BIKE)`.
  * Selecting a `RUN` route invokes `banalServiceRepository.setUserSelectedSportType(BSportType.RUN)`.
  * Selecting `UNKNOWN` or `CONFLICT` route does NOT invoke `setUserSelectedSportType`.
  * Activating candidate route triggers sport synchronization.
  * Clearing or stopping route does NOT invoke `setUserSelectedSportType`.
* Execute: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorViewModelTest"`

### Step 5: Full Clean-Room Regression Verification
* Execute: `./gradlew testDebugUnitTest` verifying 100% clean test run.

---

## 7. Rollback & Invariant Protection

* If any unexpected behavioral conflict occurs, `banalServiceRepository` can be defaulted to null with zero breaking changes to existing callers.
* The change is fully backwards compatible and additive.

---

## 8. Gate 3 Sign-off Checklist

- [x] Traceability mapped to `REQ-MAP-045` and `TST-MAP-047`.
- [x] Clear architectural boundaries defined (SWE.2: ViewModel, Repository, UI).
- [x] System invariants and Chesterton's Fence constraints explicitly protected.
- [x] UI Consistency (Rule 23) assessed and documented.
- [x] Atomic step sequencing detailed with targeted test commands.
