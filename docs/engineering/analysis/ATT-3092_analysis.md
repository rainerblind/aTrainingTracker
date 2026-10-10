# Stage 1 Analysis: ATT-3092 - Automatically set selected sport type when selecting route (Bike / Run)

**Ticket**: [ATT-3092](https://atrainingtracker.atlassian.net/browse/ATT-3092)  
**Sub-task**: [ATT-3113](https://atrainingtracker.atlassian.net/browse/ATT-3113) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3092`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

### 1.1 The User Problem
When preparing for a workout or during active tracking, an athlete selects a route from the Quick Route Selector sheet (`RouteSelectorSheet.kt`) or through candidate auto-detection. In aTrainingTracker, routes are tagged with explicit sport metadata (`RouteSummary.bSportType`), designating whether a route is for cycling (`BSportType.BIKE`) or running (`BSportType.RUN`).

Currently, selecting a route only binds the route polyline to active navigation (`routesRepository.setActiveNavigatedRoute(routeId)`). It does not synchronize or update the active workout sport type. Consequently:
- If an athlete is on default `BSportType.UNKNOWN` or has `BSportType.RUN` active from a previous workout, and selects a cycling route, the workout sport remains `RUN` or `UNKNOWN`.
- The athlete must perform redundant manual steps: open the sport selector dialog or control tab and manually switch the sport to Cycling / Bike.
- Failure to do so leads to incorrect sensor searching (e.g. searching for running footpods instead of bike power meters / cadence sensors), wrong calorie / telemetry estimation, and improper Strava activity categorization upon export.

### 1.2 User Intent
When an athlete explicitly selects a route typed as `BSportType.BIKE` or `BSportType.RUN`, the application should automatically switch the active workout sport type to match the route's sport discipline.

---

## 2. Forensic Investigation & Current Architecture

### 2.1 Route Selection Path
Route selection occurs primarily in `RouteSelectorViewModel.kt` via:
```kotlin
fun selectRoute(routeId: Long) {
    routesRepository.setActiveNavigatedRoute(routeId)
    if (_autoDetectedCandidate.value?.summary?.id == routeId) {
        _autoDetectedCandidate.value = null
    }
}
```
This is triggered from:
1. `RouteSelectorSheet.kt`: When the athlete clicks a `RouteCard`, invoking `viewModel.selectRoute(route.summary.id)`.
2. `RouteSelectorViewModel.activateCandidate(routeId)`: When an auto-detected candidate prompt is accepted by the athlete.

### 2.2 Active Sport Type Management in `BANALServiceRepository` and `BANALService`
The single source of truth for the active workout sport type is managed by `BANALService` via `BANALServiceRepository`:
- `BANALServiceRepository.setUserSelectedSportType(bSportType: BSportType)` delegates to `_serviceBinder.value?.setUserSelectedSportType(bSportType)`.
- In `BANALService.java`:
  ```java
  private void setUserSelectedSportTypeId(long sportTypeId) {
      mHaveUserSelectedSportType = true;
      mUserSelectedSportTypeId = sportTypeId;
      sendBroadcast(new Intent(SPORT_TYPE_CHANGED_BY_USER_INTENT)
              .setPackage(this.getPackageName()));
      if (TrainingApplication.startSearchWhenUserChangesSport()) {
          sendBroadcast(new Intent(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
                  .setPackage(this.getPackageName()));
      }
  }
  ```
  Setting the user-selected sport type immediately updates `mHaveUserSelectedSportType`, notifies `TrackerService`, and initiates sensor searching for paired devices corresponding to the selected sport.

### 2.3 Architectural Gap
`RouteSelectorViewModel` currently injects:
- `routesRepository: RoutesRepository`
- `autoDetector: RouteAutoDetector`
- `tuningPreferencesDataStore: TuningPreferencesDataStore?`

It has no reference to `BANALServiceRepository`, and `selectRoute(routeId: Long)` does not query the selected route's `bSportType` or trigger `setUserSelectedSportType`. Furthermore, in `BANALServiceRepository.kt`, `setUserSelectedSportType()` only forwards to the binder without immediately updating `_bSportType.value`, relying on the 1-second background observation loop to sync back from the binder.

---

## 3. Proposed Architectural Solution

### 3.1 `RouteSelectorViewModel` Integration
1. Extend `RouteSelectorViewModel` constructor to accept `banalServiceRepository: BANALServiceRepository? = null`.
2. In `selectRoute(routeId: Long)`:
   - Call `routesRepository.setActiveNavigatedRoute(routeId)`.
   - Retrieve the route from `routesRepository.allRoutes.value.firstOrNull { it.summary.id == routeId }`.
   - If `route.summary.bSportType == BSportType.BIKE` or `route.summary.bSportType == BSportType.RUN`:
     - Invoke `banalServiceRepository?.setUserSelectedSportType(route.summary.bSportType)`.
   - If `route.summary.bSportType == BSportType.UNKNOWN` or `CONFLICT`:
     - Do not modify the active sport type; retain athlete's existing selection.
3. In `activateCandidate(routeId: Long)`:
   - Retain existing delegation to `selectRoute(routeId)`, ensuring candidate route auto-binding also automatically syncs the sport type.

### 3.2 `TrackingTabsScreen.kt` Composition Wiring
In `TrackingTabsScreen.kt`, provide `banalServiceRepository = controlViewModel.banalServiceRepository` into the `RouteSelectorViewModel` factory.

### 3.3 `BANALServiceRepository.kt` Immediate State Update
In `BANALServiceRepository.setUserSelectedSportType(bSportType: BSportType)`:
```kotlin
fun setUserSelectedSportType(bSportType: BSportType) {
    _serviceBinder.value?.setUserSelectedSportType(bSportType)
    _bSportType.value = bSportType
}
```
This guarantees immediate reactive emission in `bSportType` StateFlow for Compose UI without 1-second polling lag.

---

## 4. User Scope Grounding (`ATT-1250`)

### In-Scope
- Automatically switching workout sport type to `BSportType.BIKE` when selecting a route with `bSportType == BSportType.BIKE`.
- Automatically switching workout sport type to `BSportType.RUN` when selecting a route with `bSportType == BSportType.RUN`.
- Preserving current sport type when selecting a route with `bSportType == BSportType.UNKNOWN` or `CONFLICT`.
- Preserving current sport type when stopping or clearing an active route (`stopRoute()`, `clearRoute()`).
- Wiring `RouteSelectorViewModel` with `BANALServiceRepository` in `TrackingTabsScreen.kt`.
- Immediate `_bSportType.value` update in `BANALServiceRepository.setUserSelectedSportType`.
- Comprehensive unit tests in `RouteSelectorViewModelTest.kt` verifying automatic sport switching and edge-case invariants.

### Out-of-Scope
- Modifying sport type database schemas or adding new sport enum values.
- Automatic sport switching when merely browsing or previewing routes in `RoutesScreen` without navigating them.
- Changing `ForkNavigationRepository` divergence auto-snapping (which already enforces sport compatibility).

---

## 5. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   Amends and complements `REQ-UI-280` (*Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking*, ATT-2459) and `REQ-TRK-001` / `REQ-UI-259` (*Sport Type Selection in Control Tracking*).
2. **Historical Origin & Commit Trace**:
   Ticket `ATT-2459` (Sprint 2026-41.1, commit `3168d18b`) introduced `RouteSelectorViewModel` and the unified quick route selector sheet. At the time, route selection focused strictly on coordinate loading and active navigation polyline binding. Sport selection remained an isolated manual operation on the Control tab.
3. **Root Reason for Existing Formulation**:
   Separation of concerns between route guidance and telemetry sensor tracking kept the initial implementation simple and decoupled. However, because routes contain definitive sport classification, requiring manual sport switching is redundant and error-prone for athletes.
4. **Preservation of Core Invariants**:
   - Routes with `UNKNOWN` or `CONFLICT` sport types must never override the active sport type.
   - Stopping/clearing a route must never reset or alter the user's active sport type.
   - Sensor scanning and pairing routines (`startSearchWhenUserChangesSport`) triggered by sport changes must remain 100% operational.
   - 100% clean-room unit test pass rate must be preserved.

---

## 6. Non-Regression & Invariant Analysis

- **Sensor Reconnection Safety**: When `setUserSelectedSportType` is called, `BANALService` triggers sensor scanning for paired devices matching the new sport. Existing connected sensors compatible with the new sport remain connected.
- **Route Filtering Invariants**: `RouteProximityRanker.matchesSport` and radius filtering in `RouteSelectorViewModel` remain completely unaffected.
- **Auto-Detection Invariants**: Proximity auto-detection and dismissal logic in `RouteAutoDetector` remain completely intact.
- **Clean Architecture**: `RouteSelectorViewModel` coordinates between `RoutesRepository` and `BANALServiceRepository` cleanly at the UI / presentation layer.
