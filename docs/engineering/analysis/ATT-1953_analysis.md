# Stage 1 Analysis: ATT-1953 - "Take Me Home" Return Navigation, Remaining Distance & ETA HUD

**Ticket**: [ATT-1953](https://rainerblind.atlassian.net/browse/ATT-1953)  
**Sub-task**: [ATT-2421](https://rainerblind.atlassian.net/browse/ATT-2421) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Branch**: `feature/ATT-1953`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

Endurance cyclists and runners often ride long distances and become fatigued or encounter deteriorating weather mid-workout. When exhausted or pressed for time, athletes need instantaneous, reliable answers to two vital questions:
1. *"How much further to get home along my familiar route?"*
2. *"What is my estimated time of arrival (ETA)?"*

Currently, in `aTrainingTracker`:
1. **No "Take Me Home" Guidance**: When tracking without an active route or deviating from a planned route, the athlete has no one-tap mechanism to initiate return guidance to their home base (`KnownLocationsDatabaseManager`).
2. **Missing Remaining Distance on Active Routes**: Even when following an active route (`activeNavigatedRouteId != null`), the Cockpit HUD only renders the next turn cue countdown (via `TurnPromptBanner`, e.g. "Abbiegen in 150m"), but does not display total remaining route distance to the destination or return home point.
3. **Flawed Flat-Only ETA in Hilly Terrain**: A naive flat speed calculation ($T = D / V$) is wildly inaccurate in undulating terrain. For example, climbing $300\,\text{m}$ over the final $5\,\text{km}$ takes more than twice as long as descending or traversing flat terrain at the same nominal cadence/power. Athletes require an elevation-aware ETA that scientifically incorporates remaining positive vertical ascent ($H_{\text{climb}}$) alongside flat distance.

---

## 2. Root Cause & Architectural Gap Analysis

### 2.1 Architectural Gap in Navigation & Progress Tracking
- `TurnByTurnNavigationEngine.kt` computes orthogonal projection onto the active route polyline (`bestDistanceAlongRoute`) and identifies upcoming cues, but its exposed state `TurnNavigationState` only holds `distanceToNextCueMeters` without `remainingDistanceMeters`, `remainingElevationGainMeters`, or dynamic ETA.
- In `BANALServiceRepository.kt`, real-time telemetry exposes `currentLocation: StateFlow<LatLng?>`, `currentSpeed: StateFlow<Double?>` (in $\text{m/s}$), and `currentDistance: StateFlow<Double?>`. However, no repository or calculation engine currently derives return trip metrics or combines horizontal speed with vertical climb rate.

### 2.2 Home Base Identification via `KnownLocationsDatabaseManager`
- `KnownLocationsDatabaseManager` manages familiar user locations in SQLite database `StartLocation2Altitude.db` (table `StartLocation2Altitude`).
- Locations have `ExtremaType` (`START`, `MAX`, `MIN`), `hitCount` (incremented every time a workout starts near that location via `recordWorkoutStart`), and user-assigned `name`.
- In practice, an athlete's home base is either:
  1. A location explicitly named *"Zu Hause"*, *"Zuhause"*, or *"Home"* (case-insensitive substring match).
  2. The start location (`ExtremaType.START`) with the highest `hitCount`, representing the athlete's most frequent departure and return origin.
- Current gap: There is no query or resolver method that exposes this primary home location to the navigation and routing layer.

### 2.3 Mathematical Climbing ETA Model (Naismith & VAM Formulation)
As established in athletic exercise physiology and mountaineering:
$$T_{\text{remaining}} = \frac{D_{\text{remaining}}}{V_{\text{flat}}} + \frac{H_{\text{climb}}}{\text{VAM}_{\text{climb}}}$$
Where:
- $D_{\text{remaining}}$: Geodesic or along-path remaining distance in meters.
- $V_{\text{flat}}$: Rolling flat ground speed in meters per second ($\text{m/s}$). When athlete is moving at a valid pace ($> 1.5\,\text{m/s}$), smoothed current speed is used; when stationary or starting, defaults to sport profile baseline ($20\,\text{km/h} = 5.56\,\text{m/s}$ for cycling, $10\,\text{km/h} = 2.78\,\text{m/s}$ for running, $5\,\text{km/h} = 1.39\,\text{m/s}$ for walking).
- $H_{\text{climb}}$: Total positive vertical ascent (in meters) along the remaining path segments:
  $$H_{\text{climb}} = \sum_{k} \max(0.0, \text{altitude}_{k+1} - \text{altitude}_{k})$$
- $\text{VAM}_{\text{climb}}$: Effective vertical ascent rate (Velocità Ascensionale Media) in $\text{m/s}$ (standard default: $500\,\text{m/h} \approx 0.1389\,\text{m/s}$ for cycling; $400\,\text{m/h} \approx 0.1111\,\text{m/s}$ for running/hiking, consistent with Naismith's classic rule of 1 hour per 600m ascent).
- Clock ETA: $\text{ETA}_{\text{clock}} = \text{System.currentTimeMillis()} + (T_{\text{remaining}} \times 1000)$.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Home Base Resolution**: Introduce `HomeLocationResolver` querying `KnownLocationsDatabaseManager` to deterministically resolve the athlete's primary Home destination (by name or highest start `hitCount`).
  2. **Return Corridor Snapping**:
     - *Active Route Mode*: If a route is actively navigated, snap to the remaining path towards the route destination (or reverse back to start if returning).
     - *Unrouted "Take Me Home" Mode*: Evaluate saved routes in `RoutesRepository.allRoutes` to select the best return corridor that leads to Home, or project geodesic return vector towards Home.
  3. **Elevation-Aware ETA Engine (`ElevationAwareEtaCalculator`)**:
     - Calculate remaining distance ($D_{\text{remaining}}$) and remaining climb ($H_{\text{climb}}$) along the return path.
     - Implement the athletic $T = D / V + H / \text{VAM}$ equation with sensible speed and VAM defaults per sport profile.
     - Output both formatted clock time (e.g. `"18:42"`) and remaining duration (e.g. `"~34 min"`).
  4. **Cockpit HUD Banner (`ReturnNavigationHud`)**:
     - Render a glanceable, high-contrast HUD widget in `SensorGridScreen.kt` displaying: Destination icon & name ("🏠 Zu Hause" or route name), Remaining Distance (km), Remaining Climb (+m), and Elevation-adjusted ETA.
     - Provide a 1-tap "Take Me Home" affordance in `RouteActionChipRow` and `RouteSelectorSheet.kt`.
  5. **100% 9-Language Localization Parity**:
     - Provide complete translations across EN, DE, ES, FR, IT, JA, NL, PL, PT.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Third-party cloud routing API calls (e.g. Google Directions or Mapbox online turn generation). All routing remains 100% offline and sovereign within stored routes and local locations.
  2. Modifying the underlying SQLite database schema of `Routes.db` or `StartLocation2Altitude.db`.
  3. Modifying Strava Live Segments priority or Climbs live sheet.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

Net-new requirement only (**`REQ-MAP-029`**). No existing requirements modified.

* **Original Requirement ID & Target**: Net-new `REQ-MAP-029` targeting `HomeLocationResolver.kt`, `ElevationAwareEtaCalculator.kt`, `ReturnNavigationHud.kt`, `SensorGridScreen.kt`, `RouteSelectorSheet.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-1953`, Sprint `2026-40.16`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
* **Root Reason for Existing Formulation**: Athletes tracking workouts lacked real-time visibility into remaining distance, remaining climb, and dynamic elevation-aware arrival times, leaving them vulnerable to pacing miscalculations and exhaustion in hilly terrain.
* **Preservation of Core Invariants**: Existing route database schema v10, `RoutesRepository`, `TurnByTurnNavigationRepository`, `ClimbsDatabaseManager`, and 100% full-suite test pass rate MUST NOT be broken.

---

## 5. Architectural Strategy & High-Level Solution

```
┌─────────────────────────────────────────────────────────────┐
│                 BANALServiceRepository                      │
│   (currentLocation, currentSpeed, currentDistance)          │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│              RoutesRepository & KnownLocations              │
│    (activeNavigatedRouteId, allRoutes, HomeLocation)        │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│            ReturnNavigationEngine / Manager                 │
│  1. Resolve Home Location (KnownLocationsDatabaseManager)   │
│  2. Snap to Return Path / Inbound Route Polyline            │
│  3. Calculate D_remain & H_climb (Elevation Profile)        │
│  4. Compute Elevation-Aware Dynamic ETA (Naismith / VAM)    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           │ StateFlow<ReturnNavigationState>
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                SensorGridScreen (Cockpit)                   │
│  - ReturnNavigationHud Banner (Distance, Climb, ETA)        │
│  - RouteActionChipRow (1-Tap "Take Me Home" trigger)        │
│  - RouteSelectorSheet ("Take Me Home" action card)          │
└─────────────────────────────────────────────────────────────┘
```

1. **`HomeLocationResolver`**:
   - Query `KnownLocationsDatabaseManager.getAllLocations()`.
   - Inspect locations: First priority: name contains `"haus"` or `"home"`. Second priority: location with `ExtremaType.START` and maximum `hitCount`.
   - Returns resolved `HomeDestination(name, latLng, altitude)`.

2. **`ElevationAwareEtaCalculator`**:
   - Pure, standalone calculation utility suitable for 100% unit test coverage.
   - Calculates $D_{\text{remaining}}$, $H_{\text{climb}}$ by summing positive altitude deltas along the remaining path segments.
   - Computes $T_{\text{remaining}} = D / V + H / \text{VAM}$ and formats time / duration strings cleanly.

3. **`ReturnNavigationEngine` / `ReturnNavigationRepository`**:
   - Integrates `currentLocation`, `currentSpeed`, active route, and home location.
   - Exposes reactive `StateFlow<ReturnNavigationState>` with fields:
     - `isActive: Boolean`
     - `destinationName: String`
     - `remainingDistanceMeters: Double`
     - `remainingElevationGainMeters: Double`
     - `etaClockTimeString: String`
     - `etaDurationString: String`

4. **UI Integration**:
   - `ReturnNavigationHud`: Composable HUD banner positioned below `TurnPromptBanner` in `SensorGridScreen.kt`.
   - `RouteSelectorSheet.kt`: "Take Me Home" shortcut card.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Production Regressions**: Full clean-room unit test suite (`./gradlew testDebugUnitTest`) must pass 100%.
  2. **Offline Calculation Sovereignty**: All calculations execute locally on device without network dependencies.
  3. **100% 9-Language Localization Parity**: All labels and HUD text must be translated across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  4. **Human Decision Gate Primacy (Rule 1)**: AI agent strictly advances parent ticket to `Final Review (Human)`.

* **Risk Rating**: **LOW**
  - Completely non-breaking addition to the existing navigation and cockpit stack.
  - Builds cleanly on established `RouteWithPath.path` (`PathPoint`), `KnownLocationsDatabaseManager`, and `SensorGridScreen`.
