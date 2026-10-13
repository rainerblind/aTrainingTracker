# Stage 4: Implementation Summary - ATT-3326: Automatically unselect route when user is no longer on an automatically selected route

**Ticket**: [ATT-3326](https://rainerblind.atlassian.net/browse/ATT-3326)  
**Sub-task**: [ATT-3375](https://rainerblind.atlassian.net/browse/ATT-3375) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MAP-031` (Clause 4)  
**Test Mapping**: `TST-MAP-033` (Clause 7)  
**Branch**: `feature/ATT-3326`  
**Author**: AI Agent 1 (Software Construction Engineer)  
**Date**: 2026-10-13  

---

## 1. Summary of Changes

### 1.1 `RoutesRepository.kt`
- Added `_isRouteAutoSelected = MutableStateFlow(false)` and exposed `val isRouteAutoSelected: StateFlow<Boolean> = _isRouteAutoSelected.asStateFlow()`.
- Updated `setActiveNavigatedRoute(routeId: Long?, isAutoSelected: Boolean = false)`:
  - Updates `_activeNavigatedRouteId.value = routeId`.
  - Sets `_isRouteAutoSelected.value = if (routeId != null) isAutoSelected else false`.
  - Manual callers continue to default to `isAutoSelected = false`, while setting `routeId = null` cleanly resets `isRouteAutoSelected` to `false`.

### 1.2 `ForkNavigationRepository.kt`
- In `onLocationChanged`:
  - When automatically following a unique route past a divergence vertex, passed `isAutoSelected = true`:
    `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id, isAutoSelected = true)`.
- In `selectRouteManually`:
  - Explicitly passed `isAutoSelected = false`:
    `routesRepository.setActiveNavigatedRoute(routeId, isAutoSelected = false)`.

### 1.3 `TurnByTurnNavigationRepository.kt`
- In `init` location collection flow:
  - Evaluated `newState = navigationEngine.onLocationChanged(latLng, activeConfig)`.
  - Dispatched audio alerts.
  - Added auto-unselection check:
    ```kotlin
    if (newState.isOffRoute && routesRepository.isRouteAutoSelected.value) {
        routesRepository.setActiveNavigatedRoute(null)
        previousState = TurnNavigationState()
    } else {
        previousState = newState
    }
    ```
  - When `isRouteAutoSelected == false` (manually chosen route), the route remains active, continuing standard return guidance and off-route banners.

### 1.4 Test Suite Enhancements
- **`RoutesRepositoryActiveNavigationTest.kt`**:
  - Added `testIsRouteAutoSelected_defaultsToFalse()` verifying initial state.
  - Added `testSetActiveNavigatedRoute_withAutoSelectedTrue_updatesProvenance()` verifying manual override and null reset.
- **`ForkNavigationRepositoryTest.kt`**:
  - Updated verify assertions to assert `setActiveNavigatedRoute(1L, isAutoSelected = true)` on auto-binding and `setActiveNavigatedRoute(2L, isAutoSelected = false)` on manual branch choice.
- **`TurnAlertTriggersTest.kt`**:
  - Added `testOffRoute_whenRouteIsAutoSelected_automaticallyUnselectsRoute()` verifying route is cleared upon off-route deviation.
  - Added `testOffRoute_whenRouteIsManuallySelected_retainsRoute()` verifying manual routes remain selected.

---

## 2. Targeted Verification Results

1. `RoutesRepositoryActiveNavigationTest`: **PASSED** (100%)
2. `ForkNavigationRepositoryTest`: **PASSED** in 14s (100%)
3. `TurnAlertTriggersTest`: **PASSED** in 14s (100%)
4. `TranslationParityTest`: **PASSED** in 7s (100%)
