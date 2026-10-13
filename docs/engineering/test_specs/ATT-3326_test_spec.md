# Stage 2: Requirement & Test Specification - ATT-3326: Automatically unselect route when user is no longer on an automatically selected route

**Ticket**: [ATT-3326](https://rainerblind.atlassian.net/browse/ATT-3326)  
**Sub-task**: [ATT-3373](https://rainerblind.atlassian.net/browse/ATT-3373) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MAP-031` (amending Clause 4)  
**Test Mapping**: `TST-MAP-033` (extending Clause 7)  
**Branch**: `feature/ATT-3326`  
**Author**: AI Agent 1 (Requirement & Test Specification Engineer)  
**Date**: 2026-10-13  

---

## 1. Functional Requirement Specification

### REQ-MAP-031 (Amended Clause 4): In-Ride Fork Route Selection, Autonomous Binding & Auto-Unselection on Departure

*The system SHALL detect upcoming route divergence points along shared departure corridors during active tracking, present directional decision cards in the Cockpit HUD, provide 1-tap and autonomous route binding with configurable auto-follow, and automatically unselect an auto-bound route when the athlete departs from the route corridor (ATT-1955, ATT-3320, ATT-3326):*

1. **Route Selection Provenance Tracking (`RoutesRepository.kt`)**:
   - `RoutesRepository` SHALL track whether the active navigated route was bound automatically or selected manually:
     - `fun setActiveNavigatedRoute(routeId: Long?, isAutoSelected: Boolean = false)`
     - `val isRouteAutoSelected: StateFlow<Boolean>`
   - When `routeId == null`, `isRouteAutoSelected` SHALL evaluate to `false`.
   - When `routeId != null` and `isAutoSelected == true`, `isRouteAutoSelected` SHALL evaluate to `true`.
   - When `routeId != null` and `isAutoSelected == false`, `isRouteAutoSelected` SHALL evaluate to `false`.

2. **Autonomous Route Binding (`ForkNavigationRepository.kt`)**:
   - When the athlete progresses $\ge 50\text{ m}$ past a divergence vertex and cross-track error to a candidate route is $< 25\text{ m}$ while alternatives are $> 50\text{ m}$ away:
     - If `autoFollowUniqueRouteEnabled == true`: the system SHALL invoke `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id, isAutoSelected = true)`.
     - The fork decision card prompt SHALL be dismissed (`_forkDecisionState.value = null`).
   - When the athlete taps a route manually in `ForkDecisionCard` (`selectRouteManually`), the system SHALL invoke `routesRepository.setActiveNavigatedRoute(routeId, isAutoSelected = false)`.

3. **Manual Route Selection Invariant (`RouteSelectorViewModel.kt`, UI)**:
   - When the athlete manually selects a route from the Route Selector Sheet or map button (`selectRoute`), `isAutoSelected` SHALL evaluate to `false`.
   - Manually selected routes SHALL NOT be marked as auto-selected under any circumstances.

4. **Off-Route Detection & Auto-Unselection (`TurnByTurnNavigationRepository.kt`)**:
   - In `TurnByTurnNavigationRepository.kt`, when evaluating location updates against the active route:
     - If `newState.isOffRoute == true` AND `routesRepository.isRouteAutoSelected.value == true`:
       - The system SHALL automatically clear the active navigated route: `routesRepository.setActiveNavigatedRoute(null)`.
       - The internal navigation engine state SHALL be reset to `TurnNavigationState()`.
       - Off-route warning banners in `TurnPromptBanner.kt` SHALL be dismissed immediately.
     - If `newState.isOffRoute == true` AND `routesRepository.isRouteAutoSelected.value == false`:
       - The system SHALL retain the active route selection (`activeNavigatedRouteId` remains unchanged).
       - Standard off-route warning banners and auditory recovery guidance SHALL be maintained.

5. **Fork Candidate Monitoring Resumption**:
   - Clearing the active navigated route (`activeNavigatedRouteId == null`) upon leaving an auto-selected corridor SHALL automatically re-enable candidate monitoring in `ForkNavigationRepository` across all remaining active selected routes (`isSelected == true`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

### Requirement Archaeology & Chesterton's Fence Audit

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

## 3. Acceptance Criteria (Given-When-Then)

### Criterion 1: Auto-Unselection of Auto-Bound Route on Departure
* **Given** an active route that was automatically bound by the system (`routesRepository.isRouteAutoSelected.value == true`),
* **When** the athlete departs from the route corridor and `isOffRoute` triggers (debounced across 3 consecutive fixes $> 50\text{ m}$),
* **Then** `TurnByTurnNavigationRepository` SHALL invoke `routesRepository.setActiveNavigatedRoute(null)`,
* **And** `routesRepository.activeNavigatedRouteId` SHALL emit `null`,
* **And** `routesRepository.isRouteAutoSelected` SHALL emit `false`,
* **And** `TurnPromptBanner` off-route warning banner SHALL be dismissed.

### Criterion 2: Retention of Manually Selected Route on Departure
* **Given** an active route that was explicitly chosen by the athlete (`routesRepository.isRouteAutoSelected.value == false`),
* **When** the athlete departs from the route corridor and `isOffRoute` triggers,
* **Then** the route SHALL remain active (`routesRepository.activeNavigatedRouteId` remains populated),
* **And** `routesRepository.setActiveNavigatedRoute(null)` SHALL NOT be invoked,
* **And** standard off-route warning banner and auditory alerts SHALL continue to function.

### Criterion 3: Autonomous Binding Provenance Flagging
* **Given** an active multi-route tracking session with candidate divergence detected,
* **When** the athlete travels $\ge 50\text{ m}$ past the divergence vertex on branch A,
* **Then** `ForkNavigationRepository` SHALL call `routesRepository.setActiveNavigatedRoute(chosenRoute.summary.id, isAutoSelected = true)`.

### Criterion 4: Resumption of Multi-Route Candidate Monitoring
* **Given** an auto-selected route unselected due to departure,
* **When** subsequent GPS locations are received,
* **Then** `ForkNavigationRepository` SHALL resume evaluating candidate routes among any remaining selected routes.

---

## 4. Test Specification (`TST-MAP-033` Extension)

### 4.1 Unit Test Specification

1. **`RoutesRepositoryProvenanceTest.kt`**:
   - `testDefaultState_hasNullRouteAndFalseAutoSelected`: Verify initial state has `activeNavigatedRouteId == null` and `isRouteAutoSelected == false`.
   - `testSetManualRoute_setsRouteAndFalseAutoSelected`: Call `setActiveNavigatedRoute(42L)` (or `isAutoSelected = false`), assert `activeNavigatedRouteId == 42L` and `isRouteAutoSelected == false`.
   - `testSetAutoRoute_setsRouteAndTrueAutoSelected`: Call `setActiveNavigatedRoute(42L, isAutoSelected = true)`, assert `activeNavigatedRouteId == 42L` and `isRouteAutoSelected == true`.
   - `testClearRoute_resetsBothToNullAndFalse`: After setting auto-selected route, call `setActiveNavigatedRoute(null)`, assert `activeNavigatedRouteId == null` and `isRouteAutoSelected == false`.

2. **`ForkNavigationRepositoryProvenanceTest.kt`**:
   - `onLocationChanged_pastForkOnBranchA_callsSetActiveNavigatedRouteWithAutoSelectedTrue`: Verify `mockRoutesRepo.setActiveNavigatedRoute(1L, isAutoSelected = true)` is invoked upon autonomous binding.
   - `selectRouteManually_callsSetActiveNavigatedRouteWithAutoSelectedFalse`: Verify `mockRoutesRepo.setActiveNavigatedRoute(2L, isAutoSelected = false)` is invoked upon manual tap.

3. **`TurnByTurnNavigationRepositoryAutoUnselectTest.kt`**:
   - `onLocationChanged_whenAutoSelectedAndOffRoute_unselectsRoute`:
     - Configure `mockRoutesRepo.isRouteAutoSelected` to emit `true`.
     - Feed 3 consecutive GPS fixes $> 50\text{ m}$ off route corridor to trigger `isOffRoute`.
     - Verify `mockRoutesRepo.setActiveNavigatedRoute(null)` is invoked.
   - `onLocationChanged_whenManualSelectedAndOffRoute_retainsRoute`:
     - Configure `mockRoutesRepo.isRouteAutoSelected` to emit `false`.
     - Feed 3 consecutive GPS fixes $> 50\text{ m}$ off route corridor.
     - Verify `mockRoutesRepo.setActiveNavigatedRoute(null)` is NEVER invoked (`verify(exactly = 0)`).

### 4.2 Clean-Room Full-Suite Regression Verification
- Run `./gradlew testDebugUnitTest` and assert 100% pass rate with zero failures.

---

## 5. Traceability Matrix

| Requirement Clause | Test Case | Target Component |
| :--- | :--- | :--- |
| `REQ-MAP-031` Clause 4.1 | `RoutesRepositoryProvenanceTest` | `RoutesRepository.kt` |
| `REQ-MAP-031` Clause 4.2 | `ForkNavigationRepositoryProvenanceTest` | `ForkNavigationRepository.kt` |
| `REQ-MAP-031` Clause 4.3 | `RouteSelectorViewModelTest` | `RouteSelectorViewModel.kt` |
| `REQ-MAP-031` Clause 4.4 | `TurnByTurnNavigationRepositoryAutoUnselectTest` | `TurnByTurnNavigationRepository.kt` |
| `REQ-MAP-031` Clause 4.5 | `ForkNavigationRepositoryTest` | `ForkNavigationRepository.kt` |
