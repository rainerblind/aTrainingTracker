# Stage 1 Analysis: ATT-2627 - Context-aware route selection: proximity routes before start and active route detection during tracking

**Ticket**: [ATT-2627](https://atrainingtracker.atlassian.net/browse/ATT-2627)  
**Sub-task**: [ATT-2680](https://atrainingtracker.atlassian.net/browse/ATT-2680) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2627`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 physical device evaluation (Pixel 10), the branded route selection entry point on `ControlTrackingScreen.kt` (`REQ-UI-279` / `ATT-2458`) was approved. However, the route selection modal bottom sheet and in-ride detection flow present a disjointed user experience:
1. **Disjointed Pre-Ride vs In-Ride Discovery**: Currently, `RouteSelectorViewModel` unconditionally evaluates all routes using `RouteProximityRanker.filterAndRankRoutes`, which strictly computes proximity to each route's *start coordinate* (`route.path.firstOrNull()`). When an athlete is actively tracking mid-ride (`TrackingMode.TRACKING` or `PAUSED`), they are rarely near the route's initial starting coordinate; they are traveling along its path segments.
2. **Redundant "Routenerkennung" Banner**: In `RouteSelectorSheet.kt`, `AutoDetectedRouteBanner` is rendered as an isolated prompt widget above the route list (`R.string.route_auto_detect_title`). This creates visual duplication and confusion: route detection should natively drive the candidate list when tracking is in progress, rather than being an auxiliary prompt above an irrelevant list of start-point-ranked routes.
3. **Missing Contextual Inactive State Feedback**: When no routes qualify in either pre-start or in-ride states, the button simply dims without informing the athlete *why* (e.g., whether no routes start nearby or whether no route corridor has been matched during riding).
4. **Take Me Home (Heimweg) Accessibility**: Athletes need immediate access to "Take Me Home" return navigation from this central route interaction entry point during active workouts, even when no saved route is recognized.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Architectural Decoupling of Lifecycle State
* In `TrackingTabsScreen.kt`, `trackingMode` (`TrackingMode.IDLE`, `READY`, `TRACKING`, `PAUSED`) is tracked in local composition and viewmodel state.
* While `isMidRide` (`trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED`) was piped to `RouteSelectorModalBottomSheet` for `MidRideHeimwegCard` (`REQ-UI-282`), `RouteSelectorViewModel` itself is completely unaware of `trackingMode`.
* As a consequence, `RouteSelectorViewModel.uiState` executes a single static pipeline:
  ```kotlin
  val rankedRoutes = RouteProximityRanker.filterAndRankRoutes(
      routes = allRoutes,
      currentLocation = currentLatLng,
      radiusMeters = radiusMeters
  )
  ```
* This pipeline only queries `route.path.firstOrNull()`. Mid-ride, `dist <= radiusMeters` to the start point is almost always false (or matches an irrelevant route whose start happens to be nearby), while routes whose path segments the athlete is actually riding on are omitted.

### 2.2 Route Auto-Detection Limitations in `RouteAutoDetector`
* `RouteAutoDetector.evaluate()` was designed to find a *single* candidate route (`RouteWithPath?`) and return as soon as the first match is found.
* In real-world cycling or running scenarios, multiple saved routes may share a trail, bike path, or road corridor.
* In-ride route selection should dynamically present *all* matching candidate routes currently being ridden so the athlete can choose between parallel or intersecting route options.

### 2.3 Subtitle and Inactive State Semantics in `RouteSelectionButton`
* `RouteSelectionButton` currently checks `isDimmed = routeSelectorUiState.activeRoute == null && routeSelectorUiState.routes.isEmpty()`.
* When dimmed, it falls back to static string `R.string.route_action_select_desc` ("Choose from file or history" / "Aus Datei oder Verlauf wählen"), giving no hint about proximity or detection status.
* When tracking is idle: the hint must state "Keine Strecken in der Nähe" (`route_no_routes_nearby`).
* When tracking is active: the hint must state "Keine passende Strecke erkannt" (`route_no_matching_route_detected`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Lifecycle-Driven Candidate Querying**: Unify `RouteSelectorViewModel` into two distinct states based on `isTrackingActive`:
     - **State 1: Pre-Tracking (`IDLE` / `READY`)**: Evaluates routes starting within `radiusMeters` via `RouteProximityRanker.filterAndRankRoutes()`.
     - **State 2: In-Ride (`TRACKING` / `PAUSED`)**: Evaluates routes matching the athlete's current GPS location and heading along path segments via multi-candidate corridor matching in `RouteAutoDetector`.
  2. **Streamlined In-Ride Route List & Banner Excision**: In State 2, the recognized routes directly populate the route candidate list in `RouteSelectorSheet.kt`. The separate `AutoDetectedRouteBanner` prompt widget inside the sheet is removed to eliminate visual clutter.
  3. **Contextual Button Subtitles & Empty State Rationale**:
     - Pre-Tracking empty: "Keine Strecken in der Nähe" (`route_no_routes_nearby`).
     - In-Ride empty: "Keine passende Strecke erkannt" (`route_no_matching_route_detected`).
  4. **Seamless "Take Me Home" (Heimweg) Integration**:
     - Maintain interactive clickability of dimmed button (`REQ-UI-281`).
     - In-ride sheet prominently renders `MidRideHeimwegCard` (`REQ-UI-282`) at the top, allowing 1-tap return navigation activation even when no saved route is recognized.
     - When return navigation is active, `RouteSelectionButton` displays live remaining metrics (`returnNavState`) and clear/stop button.
  5. **100% 9-Language Localization Parity**: Externalize and translate all new hint strings across EN, DE, ES, FR, IT, JA, NL, PL, PT.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying turn-by-turn cue generation or cockpit prompt banners (`TurnPromptBanner.kt`).
  * Modifying `Routes.db` SQLite schema.
  * Removing `AutoDetectedRouteBanner` from `SensorGridScreen.kt` (passive mid-ride cockpit notifications over sensor telemetry must remain intact).

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Refines Clause 2 & Clause 3 of `REQ-UI-279` (*Branded Route Selection Entry Point on Control Tracking Screen*) and Clause 3 of `REQ-UI-280` (*Streamlined Route Selector*), and integrates with `REQ-UI-282` (*Mid-Ride Route Selector Return Navigation Entry Point*).
* **Historical Origin & Commit Trace**: Tickets `ATT-2458` (commit `69dfa539`), `ATT-2459` (commit `cf4aafd8`), and `ATT-2462` (commit `2837bc39`).
* **Root Reason for Existing Formulation**:
  - `REQ-UI-279` established `RouteSelectionButton` in the bottom slot of `ControlTrackingScreen`.
  - `REQ-UI-280` excised filter tabs and pinned Heimweg from the pre-ride sheet, using `filterAndRankRoutes` for start proximity.
  - `REQ-UI-282` reintroduced `MidRideHeimwegCard` specifically for mid-ride sessions.
  - However, `RouteSelectorViewModel` was left running start-point-only filtering during live workouts, forcing in-ride route detection to exist as a separate, single-candidate banner widget (`AutoDetectedRouteBanner`).
* **Preservation of Core Invariants**:
  - Interactive clickability of dimmed button (`REQ-UI-281` / `0.45f` alpha).
  - Passive in-ride auto-detection prompts in `SensorGridScreen.kt`.
  - Mid-Ride Heimweg return navigation flow (`REQ-UI-282`).
  - Active route cancellation (`ActiveRouteBanner`, `onStopRoute`).
  - 100% full-suite unit test pass rate.

---

## 5. Architectural Strategy & High-Level Solution

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        TrackingTabsScreen.kt                           │
│  • Observes trackingMode (IDLE, READY, TRACKING, PAUSED)               │
│  • Evaluates isTrackingActive = (trackingMode == TRACKING || PAUSED)   │
│  • Forwards isTrackingActive and location to RouteSelectorViewModel    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     RouteSelectorViewModel.kt                          │
│  • Exposes isTrackingActive StateFlow                                  │
│  • When isTrackingActive == false (Pre-Tracking):                      │
│      candidates = RouteProximityRanker.filterAndRankRoutes(...)        │
│      emptyHint = R.string.route_no_routes_nearby                       │
│  • When isTrackingActive == true (In-Ride):                            │
│      candidates = RouteAutoDetector.evaluateMatchingRoutes(...)        │
│      emptyHint = R.string.route_no_matching_route_detected             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
         ┌──────────────────────────┴──────────────────────────┐
         ▼                                                     ▼
┌──────────────────────────────┐              ┌──────────────────────────────┐
│   RouteSelectionButton.kt    │              │    RouteSelectorSheet.kt     │
│ • Active route: "✓ <Name>"   │              │ • MidRideHeimwegCard (top)   │
│ • Pre-Ride Empty: Dimmed     │              │ • ActiveRouteBanner (if any) │
│   "Keine Strecken in der     │              │ • Candidate Route List:      │
│    Nähe"                     │              │   - Pre: Nearby start routes │
│ • In-Ride Empty: Dimmed      │              │   - In-Ride: Recognized      │
│   "Keine passende Strecke    │              │     corridor routes          │
│    erkannt"                  │              │ • Empty State: Shows         │
│ • Tapping opens sheet!       │              │   context-aware emptyHint    │
└──────────────────────────────┘              └──────────────────────────────┘
```

1. **`RouteAutoDetector.evaluateMatchingRoutes`**:
   - Expose `fun evaluateMatchingRoutes(location: Location, routes: List<RouteWithPath>, currentlyActiveRouteId: Long?): List<RouteWithPath>`.
   - Iterates through all routes and collects all routes that match either start proximity or path segment proximity + heading alignment, sorted by proximity to the current position.
2. **`RouteSelectorViewModel` Context Awareness**:
   - Add `setTrackingActive(isActive: Boolean)`.
   - In `uiState` combine, branch on `isTrackingActive`:
     - If `false`: use `RouteProximityRanker.filterAndRankRoutes(allRoutes, currentLatLng, radiusMeters)`.
     - If `true`: use `autoDetector.evaluateMatchingRoutes(location, allRoutes, activeRouteId)`.
   - Expose `contextEmptyHintRes: Int` in `RouteSelectorUiState`.
3. **`RouteSelectionButton` Contextual Subtitles**:
   - When `activeRoute == null`:
     - If candidates exist: "Route wählen" / "Aus Datei oder Verlauf wählen" (or "Strecken erkannt" mid-ride).
     - If candidates empty: displays `stringResource(emptyHintRes)` ("Keine Strecken in der Nähe" vs "Keine passende Strecke erkannt") with dimmed alpha (0.45f).
4. **`RouteSelectorSheet.kt` Streamlining**:
   - Remove `AutoDetectedRouteBanner` from the sheet (route candidates already populate the list in in-ride state).
   - Display `emptyHintRes` in the empty state view when the candidate list is empty.
   - Retain `MidRideHeimwegCard` at the top of the sheet when `isMidRide == true`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features, route navigation, and unit tests.
  2. Single-route active navigation invariant in `RoutesRepository`.
  3. Interactive clickability of dimmed button preserved.
  4. 100% 9-language translation parity across all locales.
* **Risk Rating**: **LOW**.
  - All changes reside in presentation and viewmodel layers (`RouteSelectionButton.kt`, `RouteSelectorViewModel.kt`, `RouteSelectorSheet.kt`, `RouteAutoDetector.kt`, `TrackingTabsScreen.kt`).
  - No database schema migrations or core tracking engine changes.
