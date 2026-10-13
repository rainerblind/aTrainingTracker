# Stage 1 Analysis: ATT-2460 - Limit route selector to routes within configurable radius (expert setting, default 1 km) sorted by last ridden

**Ticket**: [ATT-2460](https://atrainingtracker.atlassian.net/browse/ATT-2460)  
**Sub-task**: [ATT-2572](https://atrainingtracker.atlassian.net/browse/ATT-2572) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2460`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

During on-device review of the quick route selector workflow (`RouteSelectorSheet.kt`, `RouteSelectorViewModel.kt`, `RouteSelectionButton.kt`), following the removal of redundant tabs and the pinned Heimweg card (ATT-2459), several athlete usability gaps remain:
1. **Unbounded Route Inclusion**: Previously, `RouteProximityRanker` included all saved routes across the entire globe, ranking nearby routes first and appending distant routes sorted by ascending distance. For athletes with dozens of imported or synchronized routes from other regions, displaying distant tracks in a pre-ride launch pad creates unwanted cognitive load.
2. **Missing Distance Proximity Parameter**: Athletes start workouts under diverse geographical conditions (e.g. from their front door, trailheads, or adjacent parking lots). Hardcoding a fixed proximity window (such as the legacy 250m home hub) does not accommodate varying sport types and start conditions. A user-configurable radius threshold is needed in the app's advanced settings.
3. **Absence of Dedicated Navigation Preferences**: While Advanced Settings (`AdvancedTuningDialog.kt`) encapsulates Cockpit typography, AMOLED battery saving, sensors/GPS filtering, aftermath analysis, and workout cards, there is currently no dedicated "Navigation" tuning section.
4. **Lack of Visual Proximity Feedback on Entry Button**: On `ControlTrackingScreen`, the `RouteSelectionButton` is rendered identically regardless of whether qualifying routes are available at the athlete's current location, providing no immediate cue before tapping.

The goal of ATT-2460 is to:
- Introduce a dedicated "Navigation" settings category in Advanced Settings (Experten-Einstellungen) with a configurable "Routenauswahl-Radius" (Route Selection Radius, default 1 km).
- Limit the pre-ride route selector strictly to routes whose start point is within the configured radius from the athlete's current GPS position (distance <= radius).
- Sort qualifying routes strictly by "Zuletzt gefahren" (most recently ridden first, `syncedAt` descending).
- Provide dimmed visual feedback (reduced alpha) on the `RouteSelectionButton` on `ControlTrackingScreen` when no routes qualify within the radius (or no routes are imported yet).
- Provide 100% 9-language localization parity across all new strings.

---

## 2. Forensic Investigation & Gap Analysis

1. **`TuningPreferencesDataStore.kt` & `AdvancedTuningAccordion.kt`**:
   - `TuningSection` currently defines 5 sections: `COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `AFTERMATH_ANALYSIS`, `WORKOUT_MASKS_CARDS`.
   - Adding `TuningSection.NAVIGATION` enables a clean 6th collapsible category.
   - `TuningPreferencesDefaults` already contains navigation-related constants (`TURN_PROMPTS_ENABLED`, `OFF_ROUTE_CORRIDOR_THRESHOLD_METERS`, etc.). We introduce:
     - `DEFAULT_ROUTE_SELECTION_RADIUS_KM = 1.0f`
     - `MIN_ROUTE_SELECTION_RADIUS_KM = 0.5f`
     - `MAX_ROUTE_SELECTION_RADIUS_KM = 10.0f`
   - `KEY_ROUTE_SELECTION_RADIUS_KM` is registered in `ALL_KEYS` so `resetToDefaults()` automatically resets it back to 1.0 km on factory reset.
   - `AdvancedTuningDialog.kt` integrates the `NavigationSection` composable with `TuningSubtitleFormatter.formatNavigationSubtitle(routeSelectionRadiusKm)`.

2. **`RouteProximityRanker.kt`**:
   - Currently, `RouteProximityRanker.rankRoutes(...)` groups routes into `inRadius` (<= 250m) and `outOfRadius` (> 250m), returning `sortedInRadius + sortedOutOfRadius`.
   - Under ATT-2460, the selector must strictly filter out-of-radius routes. A route qualifies if and only if:
     `currentLocation != null` AND `route.path.isNotEmpty()` AND `geodesicDistance(currentLocation, route.path.first().latLng) <= radiusMeters`.
   - If `currentLocation == null`, straight-line distance to start point cannot be determined; 0 routes qualify within the radius.
   - Qualifying routes are sorted by recency: `compareByDescending<RouteWithPath> { it.summary.syncedAt }`, with deterministic tie-breaking by distance to start point ascending, then name.

3. **`RouteSelectorViewModel.kt`**:
   - Currently, `RouteSelectorViewModel` only receives `RoutesRepository`.
   - By injecting `tuningPreferencesDataStore: TuningPreferencesDataStore? = null`, the ViewModel observes `tuningPreferencesDataStore.tuningConfigFlow.map { it.routeSelectionRadiusKm }` (defaulting to 1.0 km if null).
   - In `uiState`, combining `routesRepository.allRoutes`, `activeNavigatedRouteId`, `_lastLocation`, `_autoDetectedCandidate`, and `radiusKmFlow` triggers immediate, reactive filtering and sorting upon location or radius preference changes.

4. **`RouteSelectionButton.kt` & `TrackingTabsScreen.kt`**:
   - `RouteSelectionButton` can be dimmed (reduced alpha, e.g. 0.45f) when `activeRoute == null && uiState.routes.isEmpty()`.
   - When an active route is set or qualifying routes are present within the radius, `RouteSelectionButton` is rendered at full opacity (1.0f).
   - The button remains interactive so athletes can tap to inspect the sheet or view the empty state explaining that no routes are within the active radius.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `TuningSection.NAVIGATION` to `AdvancedTuningAccordion.kt` and `NavigationSection` to `ui/settings/tuning/categories`.
  2. Add `routeSelectionRadiusKm` preference (default 1.0 km, range 0.5–10.0 km) to `TuningPreferencesDataStore` and include it in `resetToDefaults()`.
  3. Filter route selector candidate list strictly by straight-line distance from current GPS position to route start point <= radius.
  4. Sort qualifying routes strictly by "Zuletzt gefahren" (recency, `syncedAt` descending).
  5. Dim `RouteSelectionButton` (reduced alpha) on `ControlTrackingScreen` when no routes qualify within radius and no active route is selected.
  6. 9-language localization parity for navigation category title, radius setting title, and setting description.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying in-ride route matching or turn-by-turn guidance algorithms (`TurnByTurnNavigationRepository`).
  - Modifying standalone "Heimweg" return navigation HUD (explicitly covered in ATT-2462).
  - Modifying route database schema or `RoutesRepository` tables.
  - Adding route search filtering in the bottom sheet.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - Amends `REQ-MAP-024` Clause 2 (*Multi-Stage Route Ranking & Tie-Breaking Engine*).
  - Expands `REQ-SET-073` (*Advanced Settings & Tuning System*).
  - Refines `REQ-UI-279` (*Cockpit Route Action Button Visual States*).
* **Historical Origin & Commit Trace**:
  - `ATT-1835` (Sprint `2026-40.14`, commit `11702dd2`): Created `RouteProximityRanker` and `RouteSelectorSheet`.
  - `ATT-1957` (Sprint `2026-40.15`): Established 5-section Advanced Tuning accordion in `AdvancedTuningAccordion.kt`.
  - `ATT-2458` (Sprint `2026-41.1`, commit `f22b6b61`): Introduced `RouteSelectionButton` on `ControlTrackingScreen`.
  - `ATT-2459` (Sprint `2026-41.1`, commit `cf4aafd8`): Removed tabs and Heimweg card from route selector.
* **Root Reason for Existing Formulation**:
  - In `ATT-1835`, all routes were retained in the ranked list because the route selector was viewed as a general browser; out-of-radius routes were appended at the end.
  - In `ATT-1957`, only 5 tuning categories were created because navigation tuning parameters were planned for later turn-by-turn epics.
  - In `ATT-2458`, `RouteSelectionButton` was created with constant opacity regardless of route proximity.
* **Preservation of Core Invariants**:
  - In-ride automated route detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) remains fully active.
  - Active route cancellation (`ActiveRouteBanner`, `onClearRoute`) remains fully functional.
  - All existing 5 Advanced Tuning sections and their persistence keys remain untouched.
  - Factory reset atomically clears all keys including `KEY_ROUTE_SELECTION_RADIUS_KM`.
  - 100% full-suite unit and integration test pass rate is preserved.

---

## 4. Architectural Strategy & High-Level Solution

1. **Preference Layer (`com.atrainingtracker.trainingtracker.settings`)**:
   - In `TuningPreferencesDefaults.kt`:
     - Add `ROUTE_SELECTION_RADIUS_KM = 1.0f`, `MIN_ROUTE_SELECTION_RADIUS_KM = 0.5f`, `MAX_ROUTE_SELECTION_RADIUS_KM = 10.0f`.
   - In `TuningConfig`:
     - Add `val routeSelectionRadiusKm: Float = TuningPreferencesDefaults.ROUTE_SELECTION_RADIUS_KM`.
   - In `TuningPreferencesDataStore.kt`:
     - Add `KEY_ROUTE_SELECTION_RADIUS_KM`.
     - Include in `ALL_KEYS`.
     - Read with clamping in `tuningConfigFlow`.
     - Write in `saveTuningConfig`.

2. **UI Settings Layer (`com.atrainingtracker.trainingtracker.ui.settings.tuning`)**:
   - In `AdvancedTuningAccordion.kt`:
     - Add `NAVIGATION` to `TuningSection`.
     - Add `TuningSubtitleFormatter.formatNavigationSubtitle(radiusKm: Float): String`.
   - In `ui/settings/tuning/categories/NavigationSection.kt`:
     - Implement `NavigationSection(routeSelectionRadiusKm: Float, onRadiusChange: (Float) -> Unit)`.
     - Embed `TuningSliderItem` with 0.5 km steps (range 0.5–10.0 km).
   - In `AdvancedTuningDialog.kt`:
     - Integrate `NavigationSection` with icon `Icons.Default.Navigation`.

3. **Domain & Ranking Layer (`com.atrainingtracker.trainingtracker.ui.routes`)**:
   - In `RouteProximityRanker.kt`:
     - Provide `filterAndRankRoutes(routes: List<RouteWithPath>, currentLocation: LatLng?, radiusMeters: Float): List<RouteWithPath>`.
     - Calculate straight-line distance from `currentLocation` to `startPoint`.
     - Filter strictly `dist <= radiusMeters`.
     - Sort qualifying routes by `syncedAt` descending (most recently ridden first).
   - In `RouteSelectorViewModel.kt`:
     - Inject `tuningPreferencesDataStore: TuningPreferencesDataStore? = null`.
     - Observe radius and evaluate `filterAndRankRoutes`.

4. **Cockpit Button Visual State (`com.atrainingtracker.trainingtracker.ui.components`)**:
   - In `RouteSelectionButton.kt`:
     - Add `isDimmed: Boolean = false` parameter (applied as `Modifier.alpha(if (isDimmed) 0.45f else 1.0f)`).
   - In `TrackingTabsScreen.kt`:
     - Pass `isDimmed = routeSelectorUiState.activeRoute == null && routeSelectorUiState.routes.isEmpty()`.

5. **Localization Parity (9 Languages)**:
   - Provide translations for `tuning_cat_navigation`, `tuning_route_selection_radius_title`, and `tuning_route_selection_radius_desc` in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/`.

---

## 5. System Invariants & Risk Assessment

* **System Invariants**:
  1. Zero regressions in `./gradlew testDebugUnitTest`.
  2. Factory reset atomically restores all settings including the new radius parameter to default (1.0 km).
  3. Route selector accurately updates in real time when location updates or radius setting changes.
  4. Active route tracking and cancellation lifecycle remain 100% operational.
* **Risk Rating**: **LOW**
  - Self-contained UI and preference enhancements without modifying database schemas or background GPS tracking services.
