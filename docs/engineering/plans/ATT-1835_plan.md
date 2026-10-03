# Stage 3 Implementation Plan: ATT-1835 - Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection

**Ticket**: [ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835)  
**Sub-task**: [ATT-2270](https://rainerblind.atlassian.net/browse/ATT-2270) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1835`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architectural Design (SWE.2)

```
+-----------------------------------------------------------------------------------+
| UI Layer (Jetpack Compose)                                                        |
|                                                                                   |
|  [SensorGridScreen]                                                               |
|    - Route Action Badge: [Route wählen] / [✓ Alpen-Runde 65 km]                   |
|    - Auto-Detect Banner: ["Befindest du dich auf 'Hausrunde'? [Aktivieren] [x]"]  |
|    - Opens [RouteSelectorSheet] (Modal Bottom Sheet)                              |
|                                                                                   |
|  [RouteSelectorSheet]                                                             |
|    - Filter Chips: (< 40km, 40-80km, > 80km / Nähe, Zuletzt, Länge)               |
|      * Dynamically hidden when total routes < 5                                   |
|      * Visible when total routes >= 5                                             |
|    - Route Cards: Mini-map, sport badge, distance, elevation gain                 |
|    - 1-Tap Select / "Route beenden" Deselect Action                               |
+-----------------------------------------------------------------------------------+
                                         ^
                                         | Observes / Triggers
+----------------------------------------+------------------------------------------+
| Domain / Evaluation Engine                                                        |
|                                                                                   |
|  [RouteProximityRanker] (Pure Kotlin / Math)                                      |
|    - 5-Tier Tie-Breaking for shared start locations (e.g. Home Hub d ≈ 0m):       |
|      * Tier 1: Proximity Group (< 250m)                                           |
|      * Tier 2: Active Sport Profile Match                                         |
|      * Tier 3: Movement Heading Alignment (Δbearing <= 45°)                       |
|      * Tier 4: Recency & Frequency (Last ridden date)                             |
|      * Tier 5: Fallback Length / Alphabetical                                     |
|                                                                                   |
|  [RouteAutoDetector] (Stateful Trajectory Matcher)                                |
|    - Matches GPS stream against saved polylines:                                  |
|      * Cross-track distance <= 30m for >= 200m                                    |
|      * Heading alignment Δθ <= 35°                                                |
|    - Dispatches Detection Candidate (Prompt vs Auto-Join)                         |
+-----------------------------------------------------------------------------------+
                                         ^
                                         | Observes state
+----------------------------------------+------------------------------------------+
| Data / Settings Layer                                                             |
|  [RoutesRepository]                                                               |
|    - allRoutes: StateFlow<List<RouteWithPath>>                                    |
|    - activeNavigatedRouteId: StateFlow<Long?>                                     |
|    - toggleRouteSelection(routeId, isSelected)                                    |
|    - setActiveNavigatedRoute(routeId)                                             |
|                                                                                   |
|  [TuningPreferencesDataStore]                                                     |
|    - routeAutoDetectEnabled: Boolean (default: true)                              |
|    - routeAutoJoinMode: RouteAutoJoinMode (PROMPT vs AUTO, default: PROMPT)       |
|    - routeAutoDetectThresholdMeters: Int (default: 200)                           |
+-----------------------------------------------------------------------------------+
```

---

## 2. Order-Dependent Construction Steps

### Step 1: String Resources & 9-Language Localization
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Tokens**:
  - `route_select_title`: "Route wählen" / "Select Route"
  - `route_action_select`: "Route wählen" / "Select Route"
  - `route_action_stop`: "Route beenden" / "Stop Route"
  - `route_auto_detect_title`: "Routenerkennung" / "Route Detection"
  - `route_auto_detect_prompt`: "Befindest du dich auf '%1$s'?" / "Are you following '%1$s'?"
  - `route_auto_detect_activate`: "Aktivieren" / "Activate"
  - `route_auto_detect_dismiss`: "Ablehnen" / "Dismiss"
  - `route_filter_near`: "In der Nähe" / "Nearby"
  - `route_filter_recent`: "Zuletzt gefahren" / "Recently Ridden"
  - `route_filter_length`: "Länge" / "Length"
  - `route_empty_title`: "Keine Routen vorhanden" / "No routes available"
  - `route_empty_desc`: "Importiere GPX-Dateien oder synchronisiere mit Strava." / "Import GPX files or sync with Strava."

### Step 2: Advanced Tuning Preferences (`TuningPreferencesDataStore.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* **Changes**:
  - Add `enum class RouteAutoJoinMode { PROMPT, AUTO }`.
  - Add keys: `KEY_ROUTE_AUTO_DETECT_ENABLED`, `KEY_ROUTE_AUTO_JOIN_MODE`, `KEY_ROUTE_AUTO_DETECT_THRESHOLD_METERS`.
  - Add fields to `TuningConfig` with defaults: `routeAutoDetectEnabled = true`, `routeAutoJoinMode = RouteAutoJoinMode.PROMPT`, `routeAutoDetectThresholdMeters = 200`.
  - Add setters: `setRouteAutoDetectEnabled`, `setRouteAutoJoinMode`, `setRouteAutoDetectThresholdMeters`.

### Step 3: Pure Multi-Stage Proximity Sorting Engine (`RouteProximityRanker.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteProximityRanker.kt`
* **Changes**:
  - Implement function:
    ```kotlin
    fun rankRoutes(
        routes: List<RouteWithPath>,
        currentLocation: LatLng?,
        currentBearing: Float?,
        activeSport: BSportType?,
        sortCriteria: RouteSortCriteria = RouteSortCriteria.PROXIMITY
    ): List<RouteWithPath>
    ```
  - Implement 5-Tier Tie-Breaking:
    1. Distance to route start point ($\le 250\text{m}$ proximity group).
    2. Sport type equality (`route.summary.bSportType == activeSport`).
    3. Departure heading alignment: Calculate initial departure bearing of the route (first 500m / first segments). Compare with `currentBearing`: bonus rank if $|\Delta\text{bearing}| \le 45^\circ$.
    4. Recency & frequency: `route.summary.syncedAt` / last activity timestamp.
    5. Fallback length / name.
  - Safe null handling: If `currentLocation == null`, sort by recency and name.

### Step 4: Automated Route Trajectory Matcher (`RouteAutoDetector.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteAutoDetector.kt`
* **Changes**:
  - Implement stateful trajectory matcher:
    ```kotlin
    class RouteAutoDetector(
        private val thresholdMeters: Int = 200,
        private val maxCrossTrackMeters: Double = 30.0,
        private val maxHeadingDeltaDeg: Double = 35.0
    ) {
        fun onLocationUpdate(
            location: LatLng,
            bearing: Float?,
            speedMps: Double?,
            availableRoutes: List<RouteWithPath>,
            activeRouteId: Long?
        ): RouteAutoDetectResult?
    }
    ```
  - Accumulates consecutive distance tracked along polyline segments.
  - Emits candidate route when cumulative matching distance $\ge \text{thresholdMeters}$.
  - Resets accumulation if athlete moves $> 30\text{m}$ off route or changes heading $> 35^\circ$.

### Step 5: Route Selector Bottom Sheet UI (`RouteSelectorSheet.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
* **Changes**:
  - Implement `@Composable fun RouteSelectorSheet`:
    - Shows title and "Route beenden" button if active route exists.
    - If `routes.size >= 5`: renders adaptive filter chips (Distance chips: `< 40 km`, `40–80 km`, `> 80 km`; Sorting chips: *Nähe*, *Zuletzt gefahren*, *Länge*).
    - If `routes.size < 5`: filter chips row is completely hidden.
    - Renders scrollable list of route cards: Mini-map preview, name, sport badge, distance, elevation.
    - Tapping a route invokes selection callback and dismisses sheet.
    - Tapping "Route beenden" deselects route.

### Step 6: SensorGridScreen Integration & Cockpit Action Button
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  - Add floating or map-header Route Action Badge:
    - Displays route icon with *"Route wählen"* or active route name with checkmark.
    - Tapping badge opens `RouteSelectorSheet`.
  - Add Auto-Detect Prompt Banner:
    - When `RouteAutoDetector` detects a route and `routeAutoJoinMode == PROMPT`, displays prompt banner.
    - Tapping *"Aktivieren"* starts navigation immediately.
    - Tapping *"Ablehnen"* suppresses prompt for current session.
    - When `routeAutoJoinMode == AUTO`, seamlessly selects route without prompt.

### Step 7: Unit & Contract Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteProximityRankerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteAutoDetectorTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteAutoDetectPreferencesTest.kt`
* **Execution**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

### Step 8: Clean-Room Full Suite Regression Execution
* **Execution**: `./gradlew testDebugUnitTest`

---

## 3. Invariants & Safety Measures

1. **Non-Blocking Background Computation**:
   - `RouteProximityRanker` and `RouteAutoDetector` polyline math execute on `Dispatchers.Default`, never on `Dispatchers.Main`.
2. **GPS Puck & Sensor Telemetry Integrity**:
   - Route selection and auto-detection never interrupt or drop 1Hz sensor telemetry updates.
3. **Null & Degenerate Polyline Safety**:
   - Routes with empty path points or 0 coordinates are safely ignored during proximity ranking and auto-detection.
4. **Human Decision Control**:
   - Default auto-join mode is `PROMPT`, ensuring athlete confirmation before activating turn-by-turn navigation.
5. **Localization Parity**:
   - 100% translation parity across all 9 application locales.
