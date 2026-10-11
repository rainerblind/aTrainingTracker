# Stage 2: Requirement & Test Specification - ATT-3092: Automatically set selected sport type when selecting route (Bike / Run)

**Ticket**: [ATT-3092](https://atrainingtracker.atlassian.net/browse/ATT-3092)  
**Sub-task**: [ATT-3114](https://atrainingtracker.atlassian.net/browse/ATT-3114) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-MAP-045` (*Automatic Active Workout Sport Type Synchronization on Route Selection (Bike / Run)*)  
**Test Spec ID**: `TST-MAP-047`  
**Branch**: `feature/ATT-3092`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Requirement Specification (REQ-MAP-045)

### 1.1 Problem Statement & Rationale
When an athlete prepares or begins a workout and selects a pre-planned route via the Quick Route Selector sheet or auto-detection candidate prompt, the app currently maintains whatever sport was previously active in `BANALServiceRepository`.
Because routes have a definitive sport classification (`BSportType.BIKE` or `BSportType.RUN`), athletes selecting a bike route while the app was left on Run or Unknown must manually navigate back to the Control tab, click the sport dropdown, and select Bike (and vice-versa).
Failure to manually switch the sport leads to:
1. Recording workouts under the wrong sport profile, polluting stats and analytics.
2. Inappropriate sensor scanning policies (e.g. power meters and cycling cadence sensors not being activated if left on Run).

To streamline user workflow and eliminate friction, the app SHALL automatically synchronize the active workout sport type whenever a route classified as `BSportType.BIKE` or `BSportType.RUN` is selected or activated.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends and complements `REQ-UI-280` (*Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking*, ATT-2459) and `REQ-TRK-001` / `REQ-UI-259` (*Sport Type Selection in Control Tracking*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.1 (`ATT-2459`, commit `3168d18b`). Route selection originally decoupled navigation polyline binding from the workout sport state machine.
* **Root Reason for Existing Formulation**: Originally, sport selection was manually controlled exclusively on the Control tracking tab. However, because routes contain definitive sport classification (`bSportType`), requiring manual sport switching after selecting a bike or run route created unnecessary friction and risk of wrong sport tracking.
* **Preservation of Core Invariants**: Routes with `UNKNOWN` or `CONFLICT` sport types must never override active sport; clearing routes must never reset sport; full-suite test pass rate remains strictly preserved.

### 1.2 Functional & Architectural Requirements
1. **Automatic Sport Synchronization on Route Selection (`RouteSelectorViewModel.kt`)**:
   * In `RouteSelectorViewModel.selectRoute(routeId: Long)`, after invoking `routesRepository.setActiveNavigatedRoute(routeId)`:
     * Lookup the selected route by `routeId` in `routesRepository.allRoutes.value`.
     * If `route.summary.bSportType == BSportType.BIKE`, invoke `banalServiceRepository?.setUserSelectedSportType(BSportType.BIKE)`.
     * If `route.summary.bSportType == BSportType.RUN`, invoke `banalServiceRepository?.setUserSelectedSportType(BSportType.RUN)`.
     * If `route.summary.bSportType == BSportType.UNKNOWN` or `BSportType.CONFLICT`, do NOT invoke `banalServiceRepository?.setUserSelectedSportType(...)`, strictly preserving the athlete's current sport selection.
2. **Auto-Detected Candidate Route Sport Synchronization (`RouteSelectorViewModel.kt`)**:
   * In `RouteSelectorViewModel.activateCandidate(routeId: Long)`, candidate activation SHALL delegate to `selectRoute(routeId)`, guaranteeing identical automatic sport type synchronization when accepting an auto-detected candidate route.
3. **Decoupled Route Clearing / Stop Preservation (`RouteSelectorViewModel.kt`)**:
   * Invoking `stopRoute()` or `clearRoute()` in `RouteSelectorViewModel` SHALL clear the active navigated route (`setActiveNavigatedRoute(null)`) but SHALL NOT alter or reset the active workout sport type.
4. **Immediate State Flow Emission (`BANALServiceRepository.kt`)**:
   * In `BANALServiceRepository.setUserSelectedSportType(bSportType: BSportType)`, the repository SHALL immediately assign `_bSportType.value = bSportType` in addition to invoking `_serviceBinder.value?.setUserSelectedSportType(bSportType)`, guaranteeing instantaneous reactive UI update without 1-second background sync lag.
5. **Tracking Screen Wiring (`TrackingTabsScreen.kt`)**:
   * In `TrackingTabsScreen.kt`, the `RouteSelectorViewModel` factory SHALL provide `banalServiceRepository = controlViewModel.banalServiceRepository`.
6. **Preservation of System Invariants**:
   * Dynamic sensor scanning on sport changes (`startSearchWhenUserChangesSport`), proximity route ranking, radius filtering, and 100% full clean-room unit test pass rate MUST be strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Bike Route Selection Auto-Sync)**:
  * *Given* the athlete is on the tracking screen with sport set to `UNKNOWN` or `RUN`,
  * *When* the athlete selects a route with `bSportType == BSportType.BIKE` from `RouteSelectorSheet`,
  * *Then* `banalServiceRepository.bSportType` automatically transitions to `BSportType.BIKE`,
  * *And* `BANALService.setUserSelectedSportType(BSportType.BIKE)` is called.
* **Criterion 2 (Run Route Selection Auto-Sync)**:
  * *Given* the athlete is on the tracking screen with sport set to `BIKE` or `UNKNOWN`,
  * *When* the athlete selects a route with `bSportType == BSportType.RUN` from `RouteSelectorSheet`,
  * *Then* `banalServiceRepository.bSportType` automatically transitions to `BSportType.RUN`.
* **Criterion 3 (Auto-Detected Candidate Route Acceptance)**:
  * *Given* an auto-detected candidate route prompt with `bSportType == BSportType.BIKE`,
  * *When* the athlete clicks to activate the candidate via `activateCandidate(routeId)`,
  * *Then* the active route is set and `banalServiceRepository.bSportType` automatically transitions to `BSportType.BIKE`.
* **Criterion 4 (Untyped / Conflict Route Selection Preservation)**:
  * *Given* an active workout tracking session with active sport set to `RUN`,
  * *When* the athlete selects a route with `bSportType == BSportType.UNKNOWN` or `BSportType.CONFLICT`,
  * *Then* the active workout sport type remains `RUN` without change.
* **Criterion 5 (Route Cancellation / Stop Non-Reset)**:
  * *Given* an active navigated route and active sport `BIKE`,
  * *When* the athlete clears or stops the route via `stopRoute()`,
  * *Then* the active navigated route is cleared (`null`), but the active workout sport remains `BIKE`.
* **Criterion 6 (Synchronous State Flow Emission)**:
  * *Given* `BANALServiceRepository`,
  * *When* `setUserSelectedSportType(BSportType.BIKE)` is invoked,
  * *Then* `bSportType.value` immediately emits `BSportType.BIKE` synchronously without waiting for periodic polling.

### 1.4 System Invariants
* Dynamic sensor search, route ranking, candidate auto-detection, and 100% clean-room test pass rate MUST NOT be broken.
* Parent ticket Human Decision Gate strictly guarded.

---

## 2. Test Specification (TST-MAP-047)

### Test Case 1: Route Selection Sport Type Synchronization - Bike (`TST-MAP-047.1`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: `RouteSelectorViewModel` initialized with mocked `RoutesRepository` and mocked `BANALServiceRepository`. Current sport is `UNKNOWN`.
* **Action**: Invoke `selectRoute(bikeRouteId)` where route has `summary.bSportType = BSportType.BIKE`.
* **Expected Result**:
  * `routesRepository.setActiveNavigatedRoute(bikeRouteId)` is invoked.
  * `banalServiceRepository.setUserSelectedSportType(BSportType.BIKE)` is invoked exactly once.

### Test Case 2: Route Selection Sport Type Synchronization - Run (`TST-MAP-047.2`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: `RouteSelectorViewModel` initialized with mocked dependencies. Current sport is `BIKE`.
* **Action**: Invoke `selectRoute(runRouteId)` where route has `summary.bSportType = BSportType.RUN`.
* **Expected Result**:
  * `routesRepository.setActiveNavigatedRoute(runRouteId)` is invoked.
  * `banalServiceRepository.setUserSelectedSportType(BSportType.RUN)` is invoked exactly once.

### Test Case 3: Preserving Current Sport on Unknown or Conflict Route Selection (`TST-MAP-047.3`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: Current sport is `BIKE`.
* **Action**:
  1. Invoke `selectRoute(unknownRouteId)` where route has `summary.bSportType = BSportType.UNKNOWN`.
  2. Invoke `selectRoute(conflictRouteId)` where route has `summary.bSportType = BSportType.CONFLICT`.
* **Expected Result**:
  * `routesRepository.setActiveNavigatedRoute(...)` is invoked for each route.
  * `banalServiceRepository.setUserSelectedSportType(...)` is NEVER invoked.

### Test Case 4: Candidate Auto-Detection Route Sport Synchronization (`TST-MAP-047.4`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: An auto-detected candidate route with `bSportType = BSportType.BIKE` is present in `_autoDetectedCandidate`.
* **Action**: Invoke `activateCandidate(candidateRouteId)`.
* **Expected Result**:
  * `routesRepository.setActiveNavigatedRoute(candidateRouteId)` is invoked.
  * `banalServiceRepository.setUserSelectedSportType(BSportType.BIKE)` is invoked.
  * `autoDetectedCandidate` is cleared to `null`.

### Test Case 5: Route Clearing / Stopping Sport Preservation (`TST-MAP-047.5`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: An active route is selected and sport is `BIKE`.
* **Action**: Invoke `stopRoute()`.
* **Expected Result**:
  * `routesRepository.setActiveNavigatedRoute(null)` is invoked.
  * `banalServiceRepository.setUserSelectedSportType(...)` is NEVER invoked during route clearing.

### Test Case 6: Immediate State Flow Assignment in BANALServiceRepository (`TST-MAP-047.6`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/banalservice/BANALServiceRepositoryTest.kt`
* **Preconditions**: `BANALServiceRepository` instantiated.
* **Action**: Call `setUserSelectedSportType(BSportType.RUN)`.
* **Expected Result**:
  * `bSportType.value` immediately equals `BSportType.RUN` synchronously.

### Test Case 7: Clean-Room Full Suite Regression Execution (`TST-MAP-047.7`)
* **Scope**: Full Clean-Room Regression Test
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate with zero regressions across all modules.

---

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Test Implementation Target | Target Component / File |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-045.1` | `TST-MAP-047.1`, `TST-MAP-047.2` | `RouteSelectorViewModelTest.kt` | `RouteSelectorViewModel.kt` |
| `REQ-MAP-045.1` | `TST-MAP-047.3` | `RouteSelectorViewModelTest.kt` | `RouteSelectorViewModel.kt` |
| `REQ-MAP-045.2` | `TST-MAP-047.4` | `RouteSelectorViewModelTest.kt` | `RouteSelectorViewModel.kt` |
| `REQ-MAP-045.3` | `TST-MAP-047.5` | `RouteSelectorViewModelTest.kt` | `RouteSelectorViewModel.kt` |
| `REQ-MAP-045.4` | `TST-MAP-047.6` | `BANALServiceRepositoryTest.kt` | `BANALServiceRepository.kt` |
| `REQ-MAP-045.5` | Architectural Contract | `TrackingTabsScreen.kt` | `TrackingTabsScreen.kt` |
| `REQ-MAP-045.6` | `TST-MAP-047.7` | Clean-Room Suite | All Modules |

---

## 4. Gate 2 Sign-off Checklist
- [x] Formal Requirement `REQ-MAP-045` articulated with 4-field Chesterton's Fence archaeology audit.
- [x] Given-When-Then Acceptance Criteria specified for all sport variants (`BIKE`, `RUN`, `UNKNOWN`, `CONFLICT`) and route clearing.
- [x] Test cases `TST-MAP-047.1` through `TST-MAP-047.7` detailed with preconditions, actions, and expected results.
- [x] Traceability matrix links requirements, tests, and target code files.
- [x] Zero architectural violations; clean separation of navigation and sport tracking invariants.
