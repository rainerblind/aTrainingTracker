# Stage 3: Implementation Plan - ATT-2460: Limit route selector to routes within configurable radius (expert setting, default 1 km) sorted by last ridden

**Ticket**: [ATT-2460](https://atrainingtracker.atlassian.net/browse/ATT-2460)  
**Sub-task**: [ATT-2574](https://atrainingtracker.atlassian.net/browse/ATT-2574) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-281`  
**Test Mapping**: `TST-UI-241`  
**Branch**: `feature/ATT-2460`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

Following the removal of sort tabs and the pinned Heimweg card (ATT-2459), candidate routes displayed in the pre-ride bottom sheet were ranked by proximity and recency but unbounded by distance, appending routes from distant geographic regions. In addition, athletes starting workouts from diverse locations require an adjustable proximity threshold rather than a fixed cutoff. Furthermore, athletes on `ControlTrackingScreen` currently lack visual feedback indicating whether any candidate routes are available within their vicinity prior to tapping the route selection button.

The goal of ATT-2460 is to:
1. Introduce a dedicated "Navigation" settings category in Advanced Settings (Experten-Einstellungen) with a configurable "Routenauswahl-Radius" (Route Selection Radius, default 1 km, range 0.5–10 km).
2. Limit the pre-ride route selector strictly to routes whose start point is within the configured radius from the athlete's current GPS position (distance <= radius).
3. Sort qualifying routes strictly by "Zuletzt gefahren" (most recently ridden first, `syncedAt` descending).
4. Provide dimmed visual feedback (reduced alpha, 0.45f) on `RouteSelectionButton` on `ControlTrackingScreen` when no routes qualify within the radius (or no routes are imported yet).
5. Ensure 100% 9-language localization parity across all new strings.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-281` (*Configurable Route Selector Proximity Radius and Recency Sorting*)
* **Test Mapping**: `TST-UI-241` (*Configurable Route Selector Proximity Radius and Recency Sorting Verification*)
* **Traceability Matrix**:
  - `REQ-UI-281` Clause 1 (Navigation Section) -> `TST-UI-241.1`, `TST-UI-241.5`
  - `REQ-UI-281` Clause 2 (Radius Setting & Defaults) -> `TST-UI-241.1`
  - `REQ-UI-281` Clause 3 (Proximity Filtering) -> `TST-UI-241.2`, `TST-UI-241.3`
  - `REQ-UI-281` Clause 4 (Recency Sorting) -> `TST-UI-241.2`, `TST-UI-241.3`
  - `REQ-UI-281` Clause 5 (Dimmed Button Feedback) -> `TST-UI-241.4`
  - `REQ-UI-281` Clause 6 (Clean Empty State) -> `TST-UI-241.3`
  - `REQ-UI-281` Clause 7 (Localization Parity) -> `TST-UI-241.5`
  - Regression Invariants -> `TST-UI-241.6` (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly (0 failures).
2. **Route Selection & Cancellation Integrity**: 1-tap route selection (`selectRoute`) and cancellation (`stopRoute` / `clearRoute`) remain fully functional.
3. **In-Ride Auto-Detection Banner Intact**: In-ride automated route detection engine (`RouteAutoDetector`) and candidate banner (`AutoDetectedRouteBanner`) in both `SensorGridScreen.kt` and `RouteSelectorSheet.kt` remain completely intact.
4. **Existing Tuning Sections Intact**: All 5 existing categories (`COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `AFTERMATH_ANALYSIS`, `WORKOUT_MASKS_CARDS`) and their settings remain untouched.
5. **Factory Reset Invariance**: Invoking `resetToDefaults()` clears all preferences including `KEY_ROUTE_SELECTION_RADIUS_KM` and restores the 1.0 km default.
6. **File Size Bound**: `AdvancedTuningDialog.kt` and `NavigationSection.kt` remain under 400 lines of code.

---

## 4. Proposed Architectural Changes

### Component 1: `TuningPreferencesDataStore.kt` & `TuningPreferencesDefaults.kt` (Preferences Layer)
* In `TuningPreferencesDefaults.kt`:
  - `DEFAULT_ROUTE_SELECTION_RADIUS_KM = 1.0f`
  - `MIN_ROUTE_SELECTION_RADIUS_KM = 0.5f`
  - `MAX_ROUTE_SELECTION_RADIUS_KM = 10.0f`
* In `TuningConfig`:
  - Add property `val routeSelectionRadiusKm: Float = TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM`.
* In `TuningPreferencesDataStore.kt`:
  - Add `KEY_ROUTE_SELECTION_RADIUS_KM: Preferences.Key<Float> = floatPreferencesKey("tuning_route_selection_radius_km")`.
  - Add `KEY_ROUTE_SELECTION_RADIUS_KM` to `ALL_KEYS`.
  - Read and clamp in `tuningConfigFlow` with `coerceIn(MIN_ROUTE_SELECTION_RADIUS_KM, MAX_ROUTE_SELECTION_RADIUS_KM)`.
  - Persist in `saveTuningConfig(...)`.
  - Add convenience helper `suspend fun updateRouteSelectionRadiusKm(radiusKm: Float)`.

### Component 2: Advanced Settings Navigation UI (UI Layer)
* In `AdvancedTuningAccordion.kt`:
  - Add `NAVIGATION` to `enum class TuningSection`.
  - Add pure formatter `TuningSubtitleFormatter.formatNavigationSubtitle(radiusKm: Float): String`.
* In `ui/settings/tuning/categories/NavigationSection.kt` (New File):
  - Composable `NavigationSection(routeSelectionRadiusKm: Float, onRadiusChange: (Float) -> Unit)`.
  - Render `TuningSliderItem` for "Routenauswahl-Radius", displaying value formatted as `%.1f km`, helper text, and default text `Default: 1.0 km`. Range: `0.5f .. 10.0f` with 19 steps (0.5 km steps).
* In `AdvancedTuningDialog.kt`:
  - Add state `routeSelectionRadiusKm` loaded from `persistedConfig.routeSelectionRadiusKm`.
  - Render Section 6 with `TuningAccordionSection` using `Icons.Default.Navigation`, title `stringResource(R.string.tuning_cat_navigation)`, subtitle `TuningSubtitleFormatter.formatNavigationSubtitle(routeSelectionRadiusKm)`.
  - Save `routeSelectionRadiusKm` in `TuningConfig`.

### Component 3: Route Proximity & Recency Filtering (`RouteProximityRanker.kt`)
* Implement pure function:
  `filterAndRankRoutes(routes: List<RouteWithPath>, currentLocation: LatLng?, radiusMeters: Float = 1000.0f): List<RouteWithPath>`:
  - Return `emptyList()` when `routes.isEmpty()` or `currentLocation == null`.
  - Filter candidate routes where straight-line distance to `startPoint` (`route.path.firstOrNull()?.latLng`) is `<= radiusMeters`.
  - Sort qualifying routes strictly by:
    1. Recency (`syncedAt` descending, most recently ridden first).
    2. Distance to start point ascending.
    3. Route name alphabetical.
* Retain `rankRoutes` for backward compatibility.

### Component 4: RouteSelectorViewModel Reactive Pipeline (`RouteSelectorViewModel.kt`)
* Constructor accepts optional `tuningPreferencesDataStore: TuningPreferencesDataStore? = null`.
* Observe `radiusKmFlow`:
  `tuningPreferencesDataStore?.tuningConfigFlow?.map { it.routeSelectionRadiusKm } ?: flowOf(TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM)`.
* In `uiState`:
  Combine `routesRepository.allRoutes`, `activeNavigatedRouteId`, `_lastLocation`, `_autoDetectedCandidate`, and `radiusKmFlow`.
  Filter and rank routes via `RouteProximityRanker.filterAndRankRoutes(allRoutes, currentLatLng, radiusMeters)`.

### Component 5: Cockpit Route Selection Button Visual State (`RouteSelectionButton.kt` & `TrackingTabsScreen.kt`)
* In `RouteSelectionButton.kt`:
  - Add parameter `isDimmed: Boolean = false`.
  - Apply `Modifier.alpha(if (isDimmed) 0.45f else 1.0f)`.
  - Keep button fully clickable and interactive.
* In `TrackingTabsScreen.kt`:
  - Pass `tuningDataStore` to `RouteSelectorViewModel` factory.
  - Set `isDimmed = routeSelectorUiState.activeRoute == null && routeSelectorUiState.routes.isEmpty()`.
* In `SensorGridScreen.kt`:
  - Pass `tuningDataStore` to fallback `RouteSelectorViewModel`.

### Component 6: Localization Parity (9 Languages)
* Add strings in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`:
  - `tuning_cat_navigation`
  - `tuning_route_selection_radius_title`
  - `tuning_route_selection_radius_desc`

---

## 5. UI Consistency (Rule 23)

* **Reference Screen / Component**:
  - `AdvancedTuningDialog.kt` and `SensorsGpsFilterSection.kt` (established accordion category pattern with `TuningAccordionSection` and `TuningSliderItem`).
  - `RouteSelectionButton.kt` on `ControlTrackingScreen.kt` (established in ATT-2458).
* **Reused Components**:
  - `TuningAccordionSection` from `ui/settings/tuning/AdvancedTuningAccordion.kt`.
  - `TuningSliderItem` from `ui/settings/tuning/TuningFormControls.kt`.
  - `RouteSelectionButton` from `ui/components/RouteSelectionButton.kt`.
* **Theme Tokens**:
  - Shapes: `RoundedCornerShape(12.dp)` for cards and accordion sections.
  - Spacing: 14.dp vertical spacing between items in category sections.
  - Colors: `MaterialTheme.colorScheme.primary`, `surfaceVariant`, `onSurface`, `onSurfaceVariant`.
  - Dimming: `0.45f` alpha for inactive empty state feedback.
* **New One-off Styles & Justification**: None. Reuses existing design system tokens and components directly.

---

## 6. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization
* Target Files: `app/src/main/res/values*/strings.xml` (all 9 locales).
* Add `tuning_cat_navigation`, `tuning_route_selection_radius_title`, `tuning_route_selection_radius_desc`.

### Step 2: DataStore Preferences & Defaults
* Target Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Add `DEFAULT_ROUTE_SELECTION_RADIUS_KM`, `MIN_ROUTE_SELECTION_RADIUS_KM`, `MAX_ROUTE_SELECTION_RADIUS_KM` to `TuningPreferencesDefaults`.
* Add `routeSelectionRadiusKm` to `TuningConfig`.
* Register `KEY_ROUTE_SELECTION_RADIUS_KM` in `TuningPreferencesDataStore` and `ALL_KEYS`.
* Support reading in `tuningConfigFlow` and writing in `saveTuningConfig`.

### Step 3: Advanced Settings Navigation UI
* Target Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt` (New)
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* Add `TuningSection.NAVIGATION` and `TuningSubtitleFormatter.formatNavigationSubtitle`.
* Implement `NavigationSection.kt` using `TuningSliderItem`.
* Integrate into `AdvancedTuningDialog.kt` with state and save handling.

### Step 4: Proximity & Recency Filtering Engine
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteProximityRanker.kt`
* Implement `filterAndRankRoutes(routes, currentLocation, radiusMeters)`.
* Filter distance <= radiusMeters; sort by `syncedAt` descending (most recently ridden first).

### Step 5: RouteSelectorViewModel Integration
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModel.kt`
* Inject `TuningPreferencesDataStore?`.
* Combine `radiusKmFlow` and filter routes using `RouteProximityRanker.filterAndRankRoutes`.

### Step 6: RouteSelectionButton Dimmed State & Call Sites
* Target Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButton.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Add `isDimmed` to `RouteSelectionButton`.
* Pass `tuningDataStore` to `RouteSelectorViewModel` in `TrackingTabsScreen` and `SensorGridScreen`.
* Pass `isDimmed = activeRoute == null && routes.isEmpty()`.

### Step 7: Unit & Contract Tests
* Target Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteProximityRankerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButtonTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* Run targeted tests to verify 100% pass rate.
