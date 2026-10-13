# Stage 2: Requirement & Test Specification - ATT-2460: Limit route selector to routes within configurable radius (expert setting, default 1 km) sorted by last ridden

**Ticket**: [ATT-2460](https://atrainingtracker.atlassian.net/browse/ATT-2460)  
**Sub-task**: [ATT-2573](https://atrainingtracker.atlassian.net/browse/ATT-2573) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-281` (*Configurable Route Selector Proximity Radius and Recency Sorting*)  
**Test Spec ID**: `TST-UI-241`  
**Branch**: `feature/ATT-2460`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-UI-281)

### 1.1 Problem Statement & Rationale
Following the streamlining of `RouteSelectorSheet.kt` (ATT-2459), candidate routes displayed in the pre-ride bottom sheet were ranked by proximity and recency but unbounded by distance, appending routes from distant geographic regions. In addition, athletes starting workouts from diverse locations require an adjustable proximity threshold rather than a fixed cutoff. Furthermore, athletes on `ControlTrackingScreen` currently lack visual feedback indicating whether any candidate routes are available within their vicinity prior to tapping the route selection button.

The system SHALL limit the pre-ride route selector strictly to routes within a configurable radius from the athlete's current location, sort qualifying routes by recency, introduce a dedicated "Navigation" category in Advanced Settings, and dim the route selection button when no routes qualify.

### 1.2 Functional & Architectural Requirements
1. *Dedicated "Navigation" Section in Advanced Settings*:
   • The system SHALL introduce a 6th semantic category `TuningSection.NAVIGATION` in `AdvancedTuningAccordion.kt`.
   • The system SHALL render a dedicated collapsible `NavigationSection` in `AdvancedTuningDialog.kt` using `Icons.Default.Navigation` (or standard navigation icon) with title `@string/tuning_cat_navigation`.
   • The accordion header SHALL display a live active summary subtitle via `TuningSubtitleFormatter.formatNavigationSubtitle(routeSelectionRadiusKm)`.
2. *Configurable "Routenauswahl-Radius" Setting*:
   • The system SHALL provide a slider setting "Routenauswahl-Radius" (`routeSelectionRadiusKm`) in `NavigationSection.kt`.
   • Default value SHALL be `1.0 km` (`TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM = 1.0f`).
   • Permissible range SHALL be `0.5 km` to `10.0 km` with 0.5 km increments.
   • The setting SHALL be persisted in `TuningPreferencesDataStore` under `KEY_ROUTE_SELECTION_RADIUS_KM`.
   • The setting SHALL be atomically cleared and restored to default upon factory reset (`resetToDefaults()`).
3. *Strict Proximity Filtering Criteria*:
   • The system SHALL filter candidate routes strictly by the straight-line (geodesic) distance from the athlete's current GPS position to the route's start point (`distance <= radiusMeters`, where `radiusMeters = routeSelectionRadiusKm * 1000f`).
   • Routes whose start point is further than `radiusMeters`, routes with missing path points, and all routes when GPS location is `null` SHALL be excluded from the selector list.
4. *Recency Sorting ("Zuletzt gefahren")*:
   • Qualifying routes within the radius SHALL be sorted strictly by "Zuletzt gefahren" (`syncedAt` timestamp descending, most recently ridden first).
   • Ties SHALL be broken deterministically by distance to start point ascending, followed by alphabetical route name.
5. *Dimmed Button Visual Feedback on ControlTrackingScreen*:
   • When no routes qualify within the radius (or no routes are imported yet in the app) AND no route is currently active, the `RouteSelectionButton` on `ControlTrackingScreen` SHALL be dimmed by reducing its alpha (`alpha = 0.45f`).
   • When at least one route qualifies within the radius OR an active navigated route is set, the button SHALL be displayed at full opacity (`alpha = 1.0f`).
   • The dimmed button SHALL remain interactive so tapping still opens `RouteSelectorModalBottomSheet`.
6. *Clean Empty State*:
   • When the route list is empty, `RouteSelectorSheet.kt` SHALL render a centered empty state placeholder.
7. *100% 9-Language Localization Parity*:
   • All new user-facing strings (`tuning_cat_navigation`, `tuning_route_selection_radius_title`, `tuning_route_selection_radius_desc`) SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Dedicated Navigation Settings & Radius Default)**:
  * *Given* the athlete opens Advanced Settings (Experten-Einstellungen),
  * *When* viewing the categories accordion,
  * *Then* the "Navigation" section SHALL be present, and "Routenauswahl-Radius" SHALL display a default value of "1.0 km".
* **Criterion 2 (Radius Adjustment & Persistence)**:
  * *Given* the athlete adjusts "Routenauswahl-Radius" to 2.5 km and saves,
  * *When* reopening Advanced Settings or restarting the app,
  * *Then* the value 2.5 km SHALL be preserved.
* **Criterion 3 (Factory Reset Restores Default)**:
  * *Given* the athlete modified "Routenauswahl-Radius" to 5.0 km,
  * *When* tapping "Auf Werkseinstellungen zurücksetzen",
  * *Then* "Routenauswahl-Radius" SHALL be restored to 1.0 km.
* **Criterion 4 (Strict Proximity Filtering)**:
  * *Given* an athlete at location `(48.137, 11.576)`, route A starting 500m away, and route B starting 1500m away, with radius configured to 1.0 km,
  * *When* opening the Route Selector,
  * *Then* route A SHALL appear in the list and route B SHALL be strictly excluded.
* **Criterion 5 (Recency Sorting)**:
  * *Given* multiple routes starting within the configured radius,
  * *When* opening the Route Selector,
  * *Then* routes SHALL be sorted with the most recently ridden route (`syncedAt` descending) at the top.
* **Criterion 6 (Dimmed Button Feedback on Empty Candidates)**:
  * *Given* an athlete with no qualifying routes within the configured radius and no active route,
  * *When* viewing `ControlTrackingScreen`,
  * *Then* the `RouteSelectionButton` SHALL be rendered with reduced alpha (0.45f).
  * *When* an active route is selected or a qualifying route enters the radius,
  * *Then* `RouteSelectionButton` SHALL be rendered at full opacity (1.0f).

### 1.4 System Invariants
* **Invariant 1**: Automated in-ride route detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) and active route cancellation (`ActiveRouteBanner`, `onClearRoute`) MUST NOT be altered.
* **Invariant 2**: All existing 5 Advanced Settings categories and their persistence keys MUST NOT be modified or lost.
* **Invariant 3**: Factory reset MUST atomically clear all settings including the new radius key.
* **Invariant 4**: Full clean-room test suite (`./gradlew testDebugUnitTest`) MUST maintain a 100% pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-MAP-024` Clause 2 (*Multi-Stage Route Ranking & Tie-Breaking Engine*), expands `REQ-SET-073` (*Advanced Settings & Tuning System*), and refines `REQ-UI-279` (*Cockpit Route Action Button Visual States*).
* `Historical Origin & Commit Trace`: Tickets `ATT-1835` (Sprint `2026-40.14`), `ATT-1957` (Sprint `2026-40.15`), `ATT-2458` (Sprint `2026-41.1`), and `ATT-2459` (Sprint `2026-41.1`).
* `Root Reason for Existing Formulation`: In `ATT-1835`, all routes were retained in the selector because it functioned as a global route browser with out-of-radius routes appended at the end. In `ATT-1957`, only 5 tuning sections were introduced prior to navigation preferences. In `ATT-2458`, `RouteSelectionButton` was created with static opacity regardless of nearby route availability.
* `Preservation of Core Invariants`: In-ride route detection and active route cancellation remain fully active; all 5 existing tuning categories and factory reset logic are preserved; 100% full-suite test pass rate is strictly preserved.

---

## 2. Test Specification (TST-UI-241)

### Test Case 1: `TuningPreferencesDataStore` Navigation Radius Persistence & Factory Reset (`TST-UI-241.1`)
* **Scope**: DataStore Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreTest.kt`
* **Preconditions**: Test DataStore instance.
* **Action**:
  1. Verify default `routeSelectionRadiusKm` is `1.0f`.
  2. Save custom radius `2.5f`, collect `tuningConfigFlow`, and assert value equals `2.5f`.
  3. Verify clamping bounds: saving `0.1f` clamps to `0.5f`; saving `15.0f` clamps to `10.0f`.
  4. Invoke `resetToDefaults()`, collect `tuningConfigFlow`, and assert value resets to `1.0f`.
* **Expected Result**: Radius value is correctly bounded, persisted, and reset.

### Test Case 2: `RouteProximityRanker` Radius Filtering & Recency Sorting Unit Tests (`TST-UI-241.2`)
* **Scope**: Algorithm & Pure Kotlin Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteProximityRankerTest.kt`
* **Preconditions**: Test routes with defined start coordinates and `syncedAt` timestamps.
* **Action**:
  1. Given athlete location and radius 1000m: Route A (dist 400m) and Route B (dist 1200m). Assert only Route A is returned.
  2. Given athlete location and radius 1000m: Route A (dist 400m, syncedAt 1000L) and Route C (dist 600m, syncedAt 5000L). Assert Route C is returned first, followed by Route A.
  3. Given `currentLocation == null`: Assert empty list is returned.
  4. Given empty route list: Assert empty list is returned.
* **Expected Result**: Routes outside radius are strictly excluded; qualifying routes are sorted by recency descending.

### Test Case 3: `RouteSelectorViewModel` Dynamic Radius Reactive Pipeline Tests (`TST-UI-241.3`)
* **Scope**: ViewModel StateFlow Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: Mock `RoutesRepository` and `TuningPreferencesDataStore`.
* **Action**:
  1. Verify `uiState` filters routes according to the radius flow.
  2. Update radius preference from 1.0 km to 3.0 km; verify previously out-of-radius routes now appear in `uiState.routes`.
  3. Update athlete GPS location via `onLocationChanged(...)`; verify routes re-filter and re-rank dynamically.
* **Expected Result**: State updates reactively upon both location updates and preference changes.

### Test Case 4: `RouteSelectionButton` Dimmed State & Visual Feedback Contract Tests (`TST-UI-241.4`)
* **Scope**: UI Component & Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButtonTest.kt`
* **Preconditions**: Test theme and Compose environment.
* **Action**:
  1. Inspect `RouteSelectionButton`: assert support for `isDimmed` parameter.
  2. Assert reduced alpha modifier when `isDimmed == true`.
  3. Assert full alpha (1.0f) when `isDimmed == false`.
  4. Verify `TrackingTabsScreen.kt` passes `isDimmed = routeSelectorUiState.activeRoute == null && routeSelectorUiState.routes.isEmpty()`.
* **Expected Result**: Visual dimming reflects absence of qualifying routes.

### Test Case 5: 9-Language Localization Parity Audit (`TST-UI-241.5`)
* **Scope**: Localization Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Preconditions**: All 9 XML resources loaded.
* **Action**:
  1. Verify `tuning_cat_navigation`, `tuning_route_selection_radius_title`, and `tuning_route_selection_radius_desc` are present and non-empty in EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% localization parity across all 9 languages.

### Test Case 6: Full Clean-Room Regression Execution (`TST-UI-241.6`)
* **Scope**: Regression Suite
* **Action**: Execute `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% passing across all project unit tests (0 failures, 0 errors).

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Verification | Status |
|:---|:---|:---|:---|
| `REQ-UI-281` (1: Navigation Section) | `TST-UI-241.1`, `TST-UI-241.5` | `AdvancedTuningDialog`, `AdvancedTuningAccordion`, `strings.xml` | Specified |
| `REQ-UI-281` (2: Radius Setting & Defaults) | `TST-UI-241.1` | `TuningPreferencesDataStoreTest.kt` | Specified |
| `REQ-UI-281` (3: Proximity Filtering) | `TST-UI-241.2`, `TST-UI-241.3` | `RouteProximityRankerTest.kt`, `RouteSelectorViewModelTest.kt` | Specified |
| `REQ-UI-281` (4: Recency Sorting) | `TST-UI-241.2`, `TST-UI-241.3` | `RouteProximityRankerTest.kt` | Specified |
| `REQ-UI-281` (5: Dimmed Button Visual State) | `TST-UI-241.4` | `RouteSelectionButtonTest.kt` | Specified |
| `REQ-UI-281` (6: Clean Empty State) | `TST-UI-241.3` | `RouteSelectorViewModelTest.kt` | Specified |
| `REQ-UI-281` (7: Localization Parity) | `TST-UI-241.5` | `TranslationParityTest.kt` | Specified |
| System Regression Invariants | `TST-UI-241.6` | Full suite `./gradlew testDebugUnitTest` | Specified |
