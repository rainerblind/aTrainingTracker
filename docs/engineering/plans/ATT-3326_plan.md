# Stage 3: Implementation Plan - ATT-3326: Automatically unselect route when user is no longer on an automatically selected route

**Ticket**: [ATT-3326](https://rainerblind.atlassian.net/browse/ATT-3326)  
**Sub-task**: [ATT-3374](https://rainerblind.atlassian.net/browse/ATT-3374) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MAP-031` (amending Clause 4)  
**Test Mapping**: `TST-MAP-033` (extending Clause 7)  
**Branch**: `feature/ATT-3326`  
**Author**: AI Agent 1 (Software Construction Planner)  
**Date**: 2026-10-13  

---

## 1. Architectural Decomposition (SWE.2)

The implementation spans three core components in clean layered architecture:

```
[ GPS Coordinates / Locations ]
             │
             ▼
[ TurnByTurnNavigationRepository ] ──(reads isOffRoute)──► [ TurnByTurnNavigationEngine ]
             │
             ├─(checks routesRepository.isRouteAutoSelected)
             │
             ▼ (if off-route & auto-selected)
    setActiveNavigatedRoute(null)
             │
             ▼
[ RoutesRepository ] ◄───(setActiveNavigatedRoute(id, isAutoSelected=true))─── [ ForkNavigationRepository ]
   - activeNavigatedRouteId: StateFlow<Long?>
   - isRouteAutoSelected: StateFlow<Boolean>
```

1. **`RoutesRepository` (Domain / State Layer)**:
   - File: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`.
   - Exposes `_isRouteAutoSelected: MutableStateFlow<Boolean>` and public `isRouteAutoSelected: StateFlow<Boolean>`.
   - Modifies `fun setActiveNavigatedRoute(routeId: Long?, isAutoSelected: Boolean = false)` to set `_isRouteAutoSelected.value = if (routeId != null) isAutoSelected else false`.

2. **`ForkNavigationRepository` (Navigation Routing / Fork Domain)**:
   - File: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepository.kt`.
   - Passes `isAutoSelected = true` when binding uniquely chosen branch at line 161.
   - Passes `isAutoSelected = false` when user manually selects branch at line 193 (`selectRouteManually`).

3. **`TurnByTurnNavigationRepository` (Navigation Orchestration Layer)**:
   - File: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnByTurnNavigationRepository.kt`.
   - In the location observer loop, after computing `newState`:
     - If `newState.isOffRoute && routesRepository.isRouteAutoSelected.value`:
       - Invokes `routesRepository.setActiveNavigatedRoute(null)`.
       - Resets `previousState = TurnNavigationState()`.
     - Else:
       - Retains `previousState = newState`.

---

## 2. Atomic Step Sequencing

### Step 1: Add Selection Provenance to `RoutesRepository`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`
* **Changes**:
  * Add `_isRouteAutoSelected = MutableStateFlow(false)`.
  * Expose `val isRouteAutoSelected: StateFlow<Boolean> = _isRouteAutoSelected.asStateFlow()`.
  * Update `fun setActiveNavigatedRoute(routeId: Long?, isAutoSelected: Boolean = false)`:
    ```kotlin
    fun setActiveNavigatedRoute(routeId: Long?, isAutoSelected: Boolean = false) {
        _activeNavigatedRouteId.value = routeId
        _isRouteAutoSelected.value = if (routeId != null) isAutoSelected else false
    }
    ```
* **Verification**: Add unit test `RoutesRepositoryProvenanceTest.kt` validating default state, manual selection, auto selection, and null clearing.

### Step 2: Flag Autonomous Binding in `ForkNavigationRepository`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepository.kt`
* **Changes**:
  * In `onLocationChanged`: update line 161 to:
    ```kotlin
    routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id, isAutoSelected = true)
    ```
  * In `selectRouteManually`: ensure line 193 calls:
    ```kotlin
    routesRepository.setActiveNavigatedRoute(routeId, isAutoSelected = false)
    ```
* **Verification**: Update `ForkNavigationRepositoryTest.kt` verifying `setActiveNavigatedRoute(1L, isAutoSelected = true)` and `setActiveNavigatedRoute(2L, isAutoSelected = false)`.

### Step 3: Implement Departure Auto-Unselection in `TurnByTurnNavigationRepository`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnByTurnNavigationRepository.kt`
* **Changes**:
  * In `init` location collection block:
    ```kotlin
    scope.launch {
        banalRepository.currentLocation.collect { latLng ->
            if (latLng != null) {
                val newState = navigationEngine.onLocationChanged(latLng, activeConfig)
                dispatchAlertsIfTriggered(previousState, newState, activeConfig)
                if (newState.isOffRoute && routesRepository.isRouteAutoSelected.value) {
                    routesRepository.setActiveNavigatedRoute(null)
                    previousState = TurnNavigationState()
                } else {
                    previousState = newState
                }
            }
        }
    }
    ```
* **Verification**: Update `TurnAlertTriggersTest.kt` to assert:
  * Route is cleared when `isRouteAutoSelected == true` and `isOffRoute` triggers.
  * Route is preserved when `isRouteAutoSelected == false` and `isOffRoute` triggers.

### Step 4: Targeted Unit Testing & Full Clean-Room Regression
* Run targeted tests via Gradle:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.RoutesRepositoryProvenanceTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.ForkNavigationRepositoryTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TurnAlertTriggersTest"
  ```
* Run clean-room regression test:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. UI Consistency & Theming (Rule 23)

* **No direct UI layout modifications**:
  * This change operates at the repository and navigation engine level.
  * When `routesRepository.setActiveNavigatedRoute(null)` is called, existing reactive Compose observers in `ATrainingTrackerMap`, `TurnPromptBanner`, and `SensorGridScreen` automatically react by tearing down the off-route warning banner and prominent active polyline, smoothly transitioning back to passive tracking or candidate monitoring without UI flickering.

---

## 4. Invariant Protection & Verification Gates

1. **Manual Selection Immortality**:
   - Manually selected routes (via Route Sheet, Quick Selector, or manual fork card tap) MUST NOT be automatically unselected during detours.
2. **Deterministic Reset**:
   - When active route is cleared (`routeId == null`), `isRouteAutoSelected` MUST reset to `false`.
3. **Full-Suite Test Pass Rate**:
   - Zero regression across all modules.
