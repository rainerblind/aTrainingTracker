# Stage 1: Problem Domain & Root Cause Analysis - ATT-3326: Automatically unselect route when user is no longer on an automatically selected route

**Ticket**: [ATT-3326](https://rainerblind.atlassian.net/browse/ATT-3326)  
**Sub-task**: [ATT-3372](https://rainerblind.atlassian.net/browse/ATT-3372) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MAP-031` (amending Clause 4)  
**Test Mapping**: `TST-MAP-033` (extending test coverage)  
**Branch**: `feature/ATT-3326`  
**Author**: AI Agent 1 (Problem Domain & Root Cause Analyst)  
**Date**: 2026-10-13  

---

## 1. Problem Statement & Value Proposition

In the live workout tracking experience, athletes can navigate along pre-selected routes. During multi-route tracking along shared departure corridors, the app detects trail forks via `RouteDivergenceDetector` and `ForkNavigationRepository`. When the athlete diverges onto a specific branch and alternative candidate branches fall behind (> 50m cross-track separation), `ForkNavigationRepository` automatically binds the chosen route (`routesRepository.setActiveNavigatedRoute(...)`) to provide hands-free turn cues and elevation profiles.

However, once a route is automatically selected by the system, if the athlete subsequently takes a different trail, makes a turn away from the route, or deviates outside the route corridor, the app currently treats the route as locked indefinitely. The athlete is subjected to persistent "Off Route" audio alerts, warning chimes, and high-visibility red banners in the Cockpit HUD for a route they did not intentionally select. To silence these alerts, the athlete must stop cycling/running and fumble with the screen to unselect or stop the route.

When a route was selected automatically (rather than explicitly by the athlete), the system should automatically unselect the route (setting active navigated route to null) as soon as the athlete is no longer on the route corridor (`isOffRoute == true`). Conversely, routes that were manually selected by the athlete must preserve their selection during temporary detours, retaining standard off-route prompts and return guidance.

---

## 2. Forensic Investigation & Architectural Gap Analysis

### 2.1 Route Selection State in `RoutesRepository.kt`
Currently, `RoutesRepository.kt` manages active route navigation through a single primitive `StateFlow`:
```kotlin
private val _activeNavigatedRouteId = MutableStateFlow<Long?>(null)
val activeNavigatedRouteId: StateFlow<Long?> = _activeNavigatedRouteId.asStateFlow()

fun setActiveNavigatedRoute(routeId: Long?) {
    _activeNavigatedRouteId.value = routeId
}
```
**Architectural Gap**:
`RoutesRepository` has zero provenance tracking indicating *how* the route became active:
- Did the user explicitly choose it (e.g. from the Route Selector Sheet or by tapping a branch card on `ForkDecisionCard`)?
- Or did the application autonomously bind it at a fork via `ForkNavigationRepository`?

### 2.2 Route Binding in `ForkNavigationRepository.kt`
In `ForkNavigationRepository.kt:159-165`:
```kotlin
if (alternativesFar) {
    if (autoFollowUniqueRouteEnabled) {
        routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)
    }
    _forkDecisionState.value = null
    return
}
```
Here, `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id)` is called autonomously. In contrast, manual selection occurs at line 192:
```kotlin
fun selectRouteManually(routeId: Long) {
    routesRepository.setActiveNavigatedRoute(routeId)
    _forkDecisionState.value = null
    isDismissed = false
}
```
Both paths currently invoke the exact same method without signaling provenance.

### 2.3 Off-Route Observation & Debounce in `TurnByTurnNavigationRepository.kt`
In `TurnByTurnNavigationRepository.kt:74-84`:
```kotlin
scope.launch {
    banalRepository.currentLocation.collect { latLng ->
        if (latLng != null) {
            val newState = navigationEngine.onLocationChanged(latLng, activeConfig)
            dispatchAlertsIfTriggered(previousState, newState, activeConfig)
            previousState = newState
        }
    }
}
```
`TurnByTurnNavigationEngine` debounces off-route detection requiring 3 consecutive GPS fixes (> 50m cross-track error) before toggling `newState.isOffRoute = true`.
When `newState.isOffRoute` triggers, `TurnByTurnNavigationRepository` dispatches the audio alert (`audioAlertManager.playOffRouteAlert()`). However, it never checks whether the active route was auto-selected, nor does it unselect the route.

### 2.4 State Reset & Candidate Resumption in `ForkNavigationRepository.kt`
In `ForkNavigationRepository.kt:91-96`:
```kotlin
if (routesRepository.activeNavigatedRouteId.value != null) {
    if (_forkDecisionState.value != null) {
        _forkDecisionState.value = null
    }
    return
}
```
Because fork candidate monitoring short-circuits whenever `activeNavigatedRouteId.value != null`, automatically unselecting the route (`routesRepository.setActiveNavigatedRoute(null)`) will naturally re-enable fork candidate monitoring on subsequent GPS fixes among all remaining active selected routes.

---

## 3. Scope Bounding & Invariants (`ATT-1250`)

### 3.1 In-Scope Objectives
1. **Provenance Tracking in `RoutesRepository`**:
   - Add `isAutoSelected: Boolean = false` parameter to `setActiveNavigatedRoute(routeId: Long?, isAutoSelected: Boolean = false)`.
   - Expose `val isRouteAutoSelected: StateFlow<Boolean>`.
   - Reset `isRouteAutoSelected` to `false` whenever `routeId == null` or a manual route selection occurs.
2. **Autonomous Binding in `ForkNavigationRepository`**:
   - Update line 161 to invoke `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id, isAutoSelected = true)`.
   - Ensure manual selection at line 193 retains `isAutoSelected = false`.
3. **Auto-Unselection in `TurnByTurnNavigationRepository`**:
   - When `newState.isOffRoute` is `true` AND `routesRepository.isRouteAutoSelected.value` is `true`:
     - Clear active navigation: `routesRepository.setActiveNavigatedRoute(null)`.
     - Update `previousState = TurnNavigationState()`.
   - When `isRouteAutoSelected.value` is `false` (manual selection):
     - Retain existing behavior (route remains active, off-route warning banner and auditory alerts trigger).
4. **Unit & Integration Tests**:
   - Add tests verifying auto-unselection when departing from auto-selected routes.
   - Add tests verifying manual routes remain selected when going off route.
   - Verify candidate monitoring resumption in `ForkNavigationRepository`.

### 3.2 Out-of-Scope (To Prevent Scope Creep)
- Modifying cross-track error thresholds (25m / 50m) or debounce counts in `TurnByTurnNavigationEngine`.
- Changing UI styling of `OffRouteCard` or `ForkDecisionCard`.
- Changing SQLite database schemas or route persistence.

---

## 4. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clause 4), targeting `RoutesRepository.kt`, `ForkNavigationRepository.kt`, and `TurnByTurnNavigationRepository.kt`.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-1955` (Sprint 2026-40.16) introduced autonomous fork route binding (`routesRepository.setActiveNavigatedRoute(...)`) when progressing $\ge 50\text{ m}$ past a divergence vertex.
   - Ticket `ATT-3320` (Sprint 2026-42.1) made autonomous route binding configurable via `autoFollowUniqueRouteEnabled`.
3. **Root Reason for Existing Formulation**:
   - In ATT-1955, routes were considered "locked" once bound. The system did not distinguish between a route explicitly chosen by the athlete and a route automatically selected at a trail fork.
   - In real-world outdoor usage, an athlete who takes a branch at a fork might later change their mind, take a detour, or depart from the route corridor. Because the route was automatically locked, the athlete was bombarded with persistent "Off Route" audio alerts and red HUD cards for a route they did not manually choose, forcing them to manually dismiss or stop the route during the ride.
4. **Preservation of Core Invariants**:
   - Manually selected routes (via Route Sheet, Quick Selector, or `selectRouteManually` on `ForkDecisionCard`) MUST remain active when going off route (`isRouteAutoSelected == false`), displaying standard off-route prompts and return guidance.
   - When an auto-selected route is cleared upon leaving the corridor (`isRouteAutoSelected == true` + `isOffRoute == true`), `ForkNavigationRepository` must cleanly re-enable candidate monitoring for any remaining selected routes.
   - Clean-room test suite 100% pass rate.

---

## 5. Risk Assessment & Mitigation

| Identified Risk | Severity | Mitigation Strategy |
| :--- | :--- | :--- |
| Race condition between route observer and location observer in `TurnByTurnNavigationRepository` | Low | Clear `routesRepository.setActiveNavigatedRoute(null)` synchronously and reset local `previousState = TurnNavigationState()`. |
| Unintended unselection of manually chosen routes | Critical | Ensure default parameter for `isAutoSelected` is `false`, and all UI manual selection call sites (`RouteSelectorViewModel.selectRoute`, `ForkNavigationRepository.selectRouteManually`) pass `false`. |
| Flapping route selection at fork boundaries | Medium | Existing 3-fix debounce in `TurnByTurnNavigationEngine` prevents spurious off-route unselection from single GPS noise points. |
