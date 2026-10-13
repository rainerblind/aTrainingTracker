# Stage 1 Analysis: ATT-1835 - Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection (Rework Cycle 2: Cockpit UI Wiring & In-Ride Auto-Detection HUD Integration)

**Ticket**: [ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835)  
**Sub-task**: [ATT-2411](https://rainerblind.atlassian.net/browse/ATT-2411) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.40` (assigned upon completion per Rule 19)  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-024` (*Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection*)  
**Test Spec ID**: `TST-MAP-026`  
**Branch**: `feature/ATT-1835`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During Joint Review / Acceptance Testing of Sprint 2026-40.15 on physical test hardware (Google Pixel 10), the Quick Route Selector feature could not be triggered, inspected, or utilized by the athlete. The user rejected the implementation with the following forensic finding:

> **Joint Review Finding**:  
> "During user verification, the Quick Route Selector could not be triggered or viewed on-device.  
> Code investigation revealed that while the underlying components (`RouteSelectorSheet.kt`, `RouteSelectorViewModel.kt`, `RouteProximityRanker.kt`, `RouteAutoDetector.kt`) and unit tests were created, Step 6 of the implementation plan was never completed:  
> - Neither the 1-tap route action button in the Cockpit/Map header nor the auto-detection prompt banner was wired into `SensorGridScreen.kt`.  
> - `RouteSelectorSheet` / `RouteSelectorContent` is currently orphaned and never invoked in the active tracking UI.  
> Moving ticket back to 'Zu erledigen' to finish the Cockpit UI wiring and in-ride auto-detection integration in `SensorGridScreen.kt`."

### Objective for Rework Cycle 2
Eliminate the gap between the domain/viewmodel components and the active Cockpit UI:
1. Provide a visible, high-accessibility 1-tap Route Action Button / Chip in `SensorGridScreen.kt` (directly in the tracking HUD / action area) to launch the `RouteSelectorModalBottomSheet`.
2. Seamlessly display active navigation state: show *"Route wählen"* (`R.string.route_action_select`) when inactive, and *"✓ <Route Name>"* when a route is actively followed, with instant tap-to-change/clear affordance.
3. Wire the `AutoDetectedRouteBanner` directly into `SensorGridScreen.kt` above the sensor grid, enabling athletes to accept or dismiss route match suggestions in real time during live rides.
4. Bridge live GPS telemetry (`currentLocationFlow`, user bearing, user speed) from the Cockpit tracking state into `RouteSelectorViewModel.onLocationChanged(...)` to feed the background auto-detector continuously.
5. Create architectural contract tests (`SensorGridScreenRouteIntegrationTest.kt`) to ensure that `SensorGridScreen` permanently invokes `RouteSelectorContent` and `AutoDetectedRouteBanner`.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 Root Cause Analysis of Cycle 1 Omission
In Rework Cycle 1, implementation focused extensively on:
- Math and polyline algorithms in `RouteProximityRanker.kt` (5-tier tie-breaking: 250m proximity hub, active sport profile match, 45° departure bearing alignment, recency `syncedAt`, fallback length/name).
- Stateful candidate detection in `RouteAutoDetector.kt` (30m cross-track corridor, 200m sustained progression, 35° heading alignment, 15-minute dismissal cooldown).
- Presentation logic in `RouteSelectorViewModel.kt` and composables in `RouteSelectorSheet.kt` (`RouteSelectorContent`, `RouteCard`, `ActiveRouteBanner`, `AutoDetectedRouteBanner`).
- Unit tests in `RouteProximityRankerTest.kt`, `RouteAutoDetectorTest.kt`, and `RouteSelectorViewModelTest.kt`.

However, the final integration step (Step 6 in `docs/engineering/plans/ATT-1835_plan.md`) was omitted from the git commit. As a result:
- No composable call site ever instantiated or displayed `RouteSelectorContent` in production.
- `SensorGridScreen.kt` only contained `TurnPromptBanner` (for ATT-1450 turn cues) and `LiveSegmentSheet` / `LiveClimbSheet`, leaving the route selector disconnected.
- The physical device tester saw no button to pick routes or respond to route auto-detection banners.

### 2.2 Call Site Architecture in `SensorGridScreen.kt`
`SensorGridScreen.kt` serves as the central composable hosting the live sensor grid, pick-and-place configuration, turn HUD, map, and elevation profile.
Within `SensorGridScreen`:
```
+-------------------------------------------------------------------------+
| SensorGridScreen (Tracking Mode)                                        |
|                                                                         |
|  [Cockpit Header Row / HUD Bar]                                         |
|    - 1-Tap Route Action Chip:                                           |
|      * Inactive: [ 📍 Route wählen ]                                     |
|      * Active:   [ ✓ Alpen-Runde 65 km (Tap to change) ]                 |
|                                                                         |
|  [Auto-Detected Route Banner] (Dynamic visibility)                       |
|    - Appears when uiState.isAutoPromptVisible && candidate != null      |
|    - "Befindest du dich auf 'Hausrunde 45 km'?" [Aktivieren] [Ablehnen] |
|                                                                         |
|  [TurnPromptBanner] (ATT-1450 Turn-by-Turn cues)                         |
|                                                                         |
|  [Sensor Grid (Scrollable Tiles)]                                       |
|                                                                         |
|  [Map (Expanded) & Elevation Profile]                                   |
+-------------------------------------------------------------------------+
```

### 2.3 Modal Bottom Sheet Integration
To present the route catalog cleanly without cluttering the screen or navigating away from the active recording:
- A `showRouteSelectorSheet: MutableState<Boolean>` manages sheet expansion.
- Material 3 `ModalBottomSheet` renders `RouteSelectorContent`:
  - Shows dynamic filter chips (Distance `< 40 km`, `40–80 km`, `> 80 km`; Sort *Nähe*, *Zuletzt gefahren*, *Länge*) when available routes $\ge 5$.
  - Shows compact route list when $< 5$ routes.
  - Tapping a route activates it via `viewModel.selectRoute(routeId)` and automatically dismisses the sheet.
  - Tapping "Route beenden" clears the active route and returns to free-ride mode.

### 2.4 Continuous GPS & Trajectory Feeding
`SensorGridScreen` receives `currentLocationFlow: StateFlow<LatLng?>` and `state: TrackingScreenState` (which provides `userBearing: Float?` and `userSpeed: Double?`).
To keep the `RouteAutoDetector` updated:
- A `LaunchedEffect(currentLocation, state.userBearing, state.userSpeed)` observes incoming fixes.
- Converts `LatLng` into an Android `Location` object with bearing and speed.
- Dispatches to `routeSelectorViewModel.onLocationChanged(location)`.
- If a route match occurs while the athlete is riding without an active route, `uiState.autoDetectedCandidate` is populated and triggers the `AutoDetectedRouteBanner` immediately.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals
1. **Cockpit Route Entry Point**:
   - Add a dedicated 1-tap route action chip / button to `SensorGridScreen.kt` in `ScreenMode.TRACKING`.
   - Distinct visual states:
     - Inactive navigation: Displays route icon with label *"Route wählen"* (`R.string.route_action_select`).
     - Active navigation: Displays route icon, active route name, checkmark, and subtle elevation/distance info.
2. **Auto-Detected Route Banner in Cockpit**:
   - Host `AutoDetectedRouteBanner` inside `SensorGridScreen.kt` directly above the sensor grid.
   - Wire callbacks:
     - `onActivate`: Calls `viewModel.activateCandidate(id)` / `selectRoute(id)`.
     - `onDismiss`: Calls `viewModel.dismissCandidate(id)`.
3. **Route Selector Modal Bottom Sheet**:
   - Implement `RouteSelectorModalBottomSheet` in `RouteSelectorSheet.kt` or host it via `ModalBottomSheet` in `SensorGridScreen.kt`.
   - Embeds `RouteSelectorContent(viewModel, onRouteSelected = { showSheet = false })`.
4. **Live Location Feed**:
   - Feed `currentLocationFlow`, `state.userBearing`, and `state.userSpeed` into `RouteSelectorViewModel.onLocationChanged(location)` in `SensorGridScreen.kt`.
5. **Contract & Architectural Testing**:
   - Create `SensorGridScreenRouteIntegrationTest.kt` to verify that `SensorGridScreen` directly references, displays, and wires `RouteSelectorContent` and `AutoDetectedRouteBanner`.
   - Add `RouteSelectorSheetTest.kt` for UI component validation.

### Out-of-Scope Non-Goals (Scope Bounding)
- No modifications to the 5-tier ranking math (`RouteProximityRanker.kt`), which is already verified and tested.
- No modifications to `RouteAutoDetector.kt` geometry algorithms.
- No changes to `RoutesDatabaseManager` SQLite schemas or Strava sync routines.
- No modification of `TurnByTurnNavigationRepository.kt` or `TurnPromptBanner.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement only (`REQ-MAP-024`). No existing requirements in `docs/requirements.md` are modified or weakened.
* **Historical Origin & Commit Trace**: Ticket `ATT-1835`, sprint `2026-40.16` (rework of `2026-40.14`), target release `V4.9.40`, Epic `ATT-66` (*[Epic] Improve Routes*).
* **Root Reason for Existing Formulation**: In Cycle 1, all domain components were specified and built, but the final integration into the active cockpit UI (`SensorGridScreen.kt`) was missed. This rework cycle strictly completes the missing integration.
* **Preservation of Core Invariants**: Existing sensor grid layout, reordering logic (`GridActions`), live climb/segment sheets, and 100% full-suite test pass rate remain fully preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Component Wiring Diagram
```
+----------------------------------------------------------------------------------+
| TrackingTabGridContent.kt                                                        |
|   - Passes currentLocationFlow, activeLiveSegments to SensorGridScreen           |
+----------------------------------------------------------------------------------+
                                         |
                                         v
+----------------------------------------------------------------------------------+
| SensorGridScreen.kt                                                              |
|   - Holds RouteSelectorViewModel (remembered with RoutesRepository.getInstance)  |
|   - Observes routeSelectorUiState: StateFlow<RouteSelectorUiState>               |
|                                                                                  |
|   [HUD Row]                                                                      |
|     - Route Action Chip:                                                         |
|       * Inactive: "Route wählen" -> onClick { showRouteSheet = true }            |
|       * Active:   "✓ <Route Name>" -> onClick { showRouteSheet = true }          |
|                                                                                  |
|   [Auto-Detected Route Banner] (when isAutoPromptVisible)                        |
|     - AutoDetectedRouteBanner(candidate, onActivate, onDismiss)                  |
|                                                                                  |
|   [Modal Bottom Sheet] (when showRouteSheet == true)                             |
|     - ModalBottomSheet(onDismissRequest = { showRouteSheet = false })            |
|         RouteSelectorContent(viewModel, onRouteSelected = { ... })               |
|                                                                                  |
|   [GPS Location Observer]                                                        |
|     - LaunchedEffect(currentLocation, bearing, speed) ->                         |
|         viewModel.onLocationChanged(location)                                    |
+----------------------------------------------------------------------------------+
```

### 5.2 SensorGridScreen Layout Harmonization
The Route Action Chip will be rendered in `SensorGridScreen` right below the status bar padding and above the Sensor Grid (or integrated into a clean HUD bar with `TurnPromptBanner`), ensuring:
- Clear visibility without obstructing sensor tiles or the map.
- AMOLED dark mode and light theme compatibility.
- Instant 1-tap access to switch, stop, or select routes during preparation or mid-ride.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Sensor telemetry: 1Hz UI rendering of sensor tiles MUST NOT experience any jank or stuttering.
  2. Map rendering: ATrainingTrackerMap layer rendering and camera follow-me modes remain unchanged.
  3. Memory safety: Bottom sheet dismissal and location observers must cleanly dispose without leaking coroutine scopes.
  4. Localization: 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Risk Rating**: **LOW**
  - Justification: The underlying ranking engine and auto-detector are already fully tested and working. This rework cycle strictly connects the existing UI components into `SensorGridScreen.kt`.

---

## 7. Stage Gate 1 Recommendation

* **Gate 1 Status**: **READY FOR AUDIT**
* **Recommendation**: **RECOMMEND PASS**
