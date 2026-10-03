# Stage 1 Analysis: ATT-1835 - Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection

**Ticket**: [ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835)  
**Sub-task**: [ATT-2268](https://rainerblind.atlassian.net/browse/ATT-2268) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1835`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

Athletes using aTrainingTracker for outdoor cycling and running workouts frequently rely on pre-planned routes for guidance, climb pacing, and turn navigation. However, the current workflow to select and activate a navigation route incurs excessive operational friction:
1. **Multi-Step Context Switching**: Selecting a route requires leaving the active tracking context (Cockpit), opening the navigation drawer, navigating to the full Routes management screen (`RoutesScreen.kt`), scrolling through a potentially large catalog of routes, toggling a checkbox, and then manually returning to the Cockpit. During pre-ride preparation or while already rolling on a bike, this multi-step navigation is awkward, cumbersome, and distracting.
2. **The "Home Start Location" Tie-Breaking Dilemma**: Athletes commonly start their workouts from a central hub (e.g., home or club meeting point), where dozens of different routes share the exact same start coordinate ($d \approx 0\,\text{m}$). A naive sorting algorithm based solely on distance-to-start fails completely here, returning an arbitrary database list that forces the athlete to hunt for their intended route.
3. **Absence of In-Ride Automated Detection**: When athletes begin an activity without pre-selecting a route, the app remains unaware of the route even if the athlete rides precisely along a saved track. Without automated detection, the athlete misses out on active route highlighting (ATT-1841), turn cues (ATT-1450), and upcoming climb profiles (ATT-1281).

Introducing a 1-tap **Quick Route Selector** directly in the Cockpit with **Multi-Stage Tie-Breaking** (proximity, active sport matching, heading alignment, and recency) combined with an **Automated Route Detector** solves these issues, delivering an intelligent, effortless navigation experience.

---

## 2. Root Cause & Gap Analysis (Forensic Investigation)

### 2.1 Route Selection Architecture Current State
Currently, route selection is decoupled from the tracking Cockpit:
- `RoutesRepository.kt` manages all saved routes (`allRoutes: StateFlow<List<RouteWithPath>>`) and stores persistent selection state in SQLite via `setRouteSelected(routeId, isSelected)`.
- In Sprint 2026-40.14 (ATT-1841), we introduced `activeNavigatedRouteId: StateFlow<Long?>` and `setActiveNavigatedRoute(routeId: Long?)`, establishing the single source of truth for the single route currently followed for active navigation.
- However, there is no direct UI affordance in `TrackingTabsScreen.kt`, `SensorStatus.kt`, or `SensorGridScreen.kt` to inspect, select, or clear the active route without opening the navigation drawer.

### 2.2 Proximity & Heading Geometry
To rank routes effectively when starting from home:
- Geodesic distance $d$ between current GPS position and route start point $P_0$ is computed via `Location.distanceBetween` (or `WorkoutClusterEngine.distanceBetween`).
- For routes within the proximity hub (radius $R \le 250\,\text{m}$), distance alone cannot differentiate between routes.
- Initial departure heading: The initial segment of each route (e.g. first $500\,\text{m}$ from $P_0$) defines a characteristic departure bearing $\theta_{\text{route}}$, computed using `calculateBearing(start, end)` in `MapUtils.kt`.
- When the athlete is moving ($v \ge 1.0\,\text{m/s}$ or user bearing is known), their current heading $\theta_{\text{user}}$ can be compared to $\theta_{\text{route}}$. The angular heading difference is:
  $$\Delta \theta = |\theta_{\text{user}} - \theta_{\text{route}}| \pmod{360^\circ}$$
  $$\Delta \theta = \min(\Delta \theta, 360^\circ - \Delta \theta)$$
  Routes with $\Delta \theta \le 45^\circ$ indicate the athlete is moving along their trajectory, providing a decisive tie-breaking signal.

### 2.3 Automated Route Detection Matching Logic
When recording without an active route:
- The tracking session records GPS points ($T_0, T_1, \dots, T_k$).
- For each candidate route starting nearby or intersecting the athlete's path:
  - Cross-track perpendicular distance from recent trajectory points to the candidate polyline must be $\le 30\,\text{m}$.
  - Sustained continuous matching length along the polyline must be $\ge 200\,\text{m}$ (or configured threshold).
  - Trajectory heading must align with polyline forward direction ($\Delta \theta \le 35^\circ$).
- Once criteria are met, an automated prompt is triggered: *"Befindest du dich auf '<Route Name>'? [Route aktivieren] [Ablehnen]"*.
- If the athlete has configured *Seamless* mode, the route is auto-joined immediately.

### 2.4 Configuration in Tuning Preferences
Per `TuningPreferencesDataStore.kt` and `AdvancedTuningDialog.kt`, we can integrate route auto-detection configuration:
- `routeAutoDetectEnabled: Boolean` (default: `true`)
- `routeAutoJoinMode: RouteAutoJoinMode` (`PROMPT` vs `SEAMLESS`, default: `PROMPT`)
- `routeAutoDetectThresholdMeters: Int` (default: `200`)

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **1-Tap Entry Point in Cockpit & Map**:
     - Provide a prominent Route Action Button in `SensorStatus.kt` / `SensorGridScreen.kt` top action area and/or Map overlay.
     - When navigation is inactive: displays a route icon (`Icons.Default.Route` / `Navigation`) with "Route wählen".
     - When navigation is active: displays active route name with checkmark and provides quick stop/clear.
     - Tapping opens `RouteSelectorBottomSheet`.
  2. **Multi-Stage Route Ranking & Tie-Breaking Engine (`RouteProximityRanker`)**:
     - Pure Kotlin helper with comprehensive unit tests.
     - Tier 1: Proximity group (routes starting $\le 250\,\text{m}$ from user).
     - Tier 2: Active sport match (`route.bSportType == activeSport`).
     - Tier 3: Heading alignment ($\Delta \text{bearing} \le 45^\circ$ on initial $500\,\text{m}$ when moving).
     - Tier 4: Recency & usage frequency (`syncedAt` or last ridden timestamp).
     - Tier 5: Fallback to route distance or alphabetical name.
     - Routes starting $> 250\,\text{m}$ sorted by ascending distance to start point.
  3. **Adaptive Route Selector Sheet UI (`RouteSelectorSheet.kt`)**:
     - Dynamic filter chips: Surface Distance chips (`< 40 km`, `40–80 km`, `> 80 km`) and Sorting chips (*Nähe*, *Zuletzt gefahren*, *Länge*) if $\ge 5$ routes exist. Automatically hide chips if $< 5$ routes.
     - Route cards with name, sport icon, distance, elevation gain, and active status.
     - 1-tap activation via `RoutesRepository.setActiveNavigatedRoute(id)`.
     - Dedicated *"Route beenden / abwählen"* action at top when a route is active.
  4. **Automated Route Detection Engine (`RouteAutoDetector`)**:
     - Background evaluator during recording when no route is actively navigated.
     - Matches user trajectory against candidate routes: distance $\le 30\,\text{m}$ for $\ge 200\,\text{m}$, heading $\Delta \theta \le 35^\circ$.
     - Surfaces Cockpit banner/dialog: *"Befindest du dich auf '<Route Name>'? [Route aktivieren] [Ablehnen]"*.
  5. **Advanced Settings Configuration**:
     - Expose `routeAutoDetectEnabled`, `routeAutoJoinMode`, and `routeAutoDetectThresholdMeters` in `TuningPreferencesDataStore` and `AdvancedTuningDialog`.
  6. **100% 9-Language Localization Parity**:
     - All user-facing strings externalized across EN, DE, ES, FR, IT, JA, NL, PL, and PT, validated by `TranslationParityTest`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Route creation, GPX file import, or route geometry editing (retained in `RoutesScreen` / `EditRouteScreen`).
  - Turn-by-Turn vocal or audio cues (reserved for `ATT-1450`).
  - Live ClimbPro Cockpit sheet computation (reserved for `ATT-1281`).
  - Modifications to SQLite tables or raw sample formats.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement only (`REQ-MAP-024`). No existing requirements in `docs/requirements.md` are modified or weakened.
* **Architectural Synergy**:
  - Builds on `REQ-MAP-023` (ATT-1841) which introduced `RoutesRepository.activeNavigatedRouteId` and prominent active route rendering.
  - Complements `REQ-UI-222` / `REQ-UI-262` (ATT-2033) for modular `AdvancedTuningDialog` settings.
  - Cooperates with `REQ-TRK-004` (automated sport inference).

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Component Architecture
1. **`RouteProximityRanker` (`app/.../repositories/RouteProximityRanker.kt`)**:
   - Pure, stateless ranking engine.
   - Evaluates a list of `RouteWithPath` against:
     - `userLocation: LatLng?`
     - `userBearing: Float?`
     - `userSpeedMps: Float?`
     - `activeSport: BSportType`
     - `selectedFilter: RouteDistanceFilter`
     - `selectedSort: RouteSortOption`
   - Applies the 5-tier tie-breaking algorithm when routes share start coordinates within $250\,\text{m}$.
2. **`RouteAutoDetector` (`app/.../repositories/RouteAutoDetector.kt`)**:
   - Observes GPS location updates during active tracking.
   - Maintains a sliding window of recent GPS fixes.
   - Compares the window against candidate routes starting or passing near the workout start/current point.
   - Emits a detection event (`RouteDetectionEvent(route, confidence)`) when distance $\le 30\,\text{m}$ for $\ge 200\,\text{m}$ and $\Delta \text{bearing} \le 35^\circ$.
3. **`RouteSelectorSheet` (`app/.../ui/routes/RouteSelectorSheet.kt`)**:
   - Material 3 ModalBottomSheet displaying ranked routes.
   - Dynamic chips shown only when $\ge 5$ routes exist.
   - Direct 1-tap activation and navigation stop button.
4. **Cockpit Integration**:
   - Route button in `SensorStatus.kt` and/or `SensorGridScreen.kt`.
   - Auto-detection prompt banner in `SensorGridScreen.kt` when detection event is active.
5. **Preferences**:
   - Add keys to `TuningPreferencesDataStore.kt` and UI controls in `AdvancedTuningDialog.kt`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing route management (`RoutesScreen`, `RoutesDatabaseManager`), tracking lifecycle (`TrackerService`), and map rendering.
  2. Thread safety: Route sorting and trajectory matching executed asynchronously on `Dispatchers.Default` without stalling Compose UI.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW-MEDIUM**
  - Justification: Algorithmic ranking and bottom-sheet UI are modular and self-contained; integration into Cockpit leverages existing reactive state flows (`RoutesRepository.activeNavigatedRouteId`, `currentLocationFlow`).
