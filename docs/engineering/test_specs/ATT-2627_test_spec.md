# Stage 2: Requirement & Test Specification - ATT-2627: Context-aware route selection: proximity routes before start and active route detection during tracking

**Ticket**: [ATT-2627](https://atrainingtracker.atlassian.net/browse/ATT-2627)  
**Sub-task**: [ATT-2681](https://atrainingtracker.atlassian.net/browse/ATT-2681) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-UI-289` (*Context-Aware Route Selection: Pre-Tracking Start Proximity and In-Ride Corridor Auto-Detection Alignment*)  
**Test Spec ID**: `TST-UI-249`  
**Branch**: `feature/ATT-2627`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-UI-289)

### 1.1 Problem Statement & Rationale
Prior to this requirement, `RouteSelectorViewModel` evaluated route candidates using a single static pipeline (`RouteProximityRanker.filterAndRankRoutes`), which only calculated geodesic distance to each route's *start coordinate*. During active tracking (in-ride), athletes are traveling along route corridors rather than sitting at start points. Additionally, auto-detected routes were rendered in an isolated prompt banner widget (`AutoDetectedRouteBanner`) inside `RouteSelectorSheet.kt`, creating visual fragmentation. When no routes qualified, the route selection button on `ControlTrackingScreen` rendered dimmed without informing the athlete of the underlying reason (e.g., no routes nearby vs no matching route detected along the corridor).

### 1.2 Functional & Architectural Requirements
The system SHALL adapt route candidate selection and UI feedback dynamically based on workout tracking lifecycle state (`isTrackingActive`), excising redundant detection prompt banners from the route sheet, providing informative empty state rationale, and ensuring seamless mid-ride "Take Me Home" access (ATT-2627):

1. **Lifecycle-Driven State Switching in Presentation & ViewModel (`RouteSelectorViewModel.kt`, `TrackingTabsScreen.kt`)**:
   - `RouteSelectorViewModel` SHALL expose `val isTrackingActive = MutableStateFlow(false)` and `fun setTrackingActive(isActive: Boolean)`.
   - `TrackingTabsScreen.kt` SHALL evaluate active tracking (`trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED`) and forward it to `RouteSelectorViewModel.setTrackingActive(isTrackingActive)`.
   - In **State 1: Pre-Tracking (`isTrackingActive == false`)**: Candidate routes SHALL evaluate strictly to routes starting within `radiusMeters` from the athlete's current location via `RouteProximityRanker.filterAndRankRoutes(allRoutes, currentLatLng, radiusMeters)`.
   - In **State 2: In-Ride (`isTrackingActive == true`)**: Candidate routes SHALL evaluate dynamically to routes the athlete is currently riding on along path segments via multi-candidate corridor matching in `RouteAutoDetector.evaluateMatchingRoutes(location, allRoutes, activeRouteId)`.
   - `RouteSelectorUiState` SHALL expose `val isTrackingActive: Boolean = false` and `val contextEmptyHintRes: Int = R.string.route_no_routes_nearby`.
2. **Multi-Candidate Route Corridor Detection (`RouteAutoDetector.kt`)**:
   - `RouteAutoDetector` SHALL expose `fun evaluateMatchingRoutes(location: Location, routes: List<RouteWithPath>, currentlyActiveRouteId: Long?): List<RouteWithPath>`.
   - It SHALL evaluate all non-active, non-dismissed routes whose start point (`dist <= startProximityThresholdMeters`) or any path segment (`distToSegment <= pathProximityThresholdMeters`) matches the athlete's location and heading, ordered by proximity.
3. **Streamlined In-Ride Sheet & Redundant Prompt Excision (`RouteSelectorSheet.kt`)**:
   - The separate `AutoDetectedRouteBanner` prompt widget inside `RouteSelectorSheet.kt` SHALL be excised. In-ride detected routes SHALL directly populate the candidate route list.
   - When no candidates match, the empty state placeholder in the sheet SHALL display context-aware rationale text (`contextEmptyHintRes`).
4. **Contextual Button Subtitle & Inactive Feedback (`RouteSelectionButton.kt`)**:
   - When no route is active and candidate routes are empty, `RouteSelectionButton` SHALL render with dimmed opacity (`alpha = 0.45f`, `isDimmed = true`) while remaining interactive upon tap (`REQ-UI-281`).
   - In State 1 (Pre-Tracking), the button subtitle SHALL display `@string/route_no_routes_nearby` ("Keine Strecken in der Nähe").
   - In State 2 (In-Ride), the button subtitle SHALL display `@string/route_no_matching_route_detected` ("Keine passende Strecke erkannt").
   - When candidate routes are available, the button subtitle SHALL display `@string/route_action_select_desc` ("Aus Datei oder Verlauf wählen").
5. **Seamless Mid-Ride "Take Me Home" (Heimweg) Integration (`RouteSelectorSheet.kt`, `RouteSelectionButton.kt`)**:
   - Mid-Ride Heimweg Card (`REQ-UI-282`) SHALL remain prominently accessible at the top of `RouteSelectorSheet` whenever `isMidRide == true`. Even when dimmed due to no route being recognized, tapping `RouteSelectionButton` opens the sheet, granting instant 1-tap access to return navigation.
   - When return navigation is actively running, `RouteSelectionButton` SHALL display active return navigation metrics (`returnNavState`) and a clear/stop button.
6. **100% 9-Language Localization Parity**:
   - String resources `route_no_routes_nearby` and `route_no_matching_route_detected` SHALL be externalized and translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Pre-Tracking State)**:
  * *Given* an athlete before starting a workout (`TrackingMode.IDLE` or `READY`),
  * *When* `RouteSelectorViewModel` computes state,
  * *Then* candidate routes SHALL be filtered by start point proximity within `radiusMeters` (`isTrackingActive == false`).
  * *When* no routes start within `radiusMeters`,
  * *Then* `RouteSelectionButton` SHALL display subtitle "Keine Strecken in der Nähe" and render dimmed (`alpha = 0.45f`).
* **Criterion 2 (In-Ride State & Corridor Matching)**:
  * *Given* an active workout tracking session (`TrackingMode.TRACKING` or `PAUSED`),
  * *When* `RouteSelectorViewModel` computes state,
  * *Then* candidate routes SHALL be evaluated by `RouteAutoDetector.evaluateMatchingRoutes` along path segments (`isTrackingActive == true`).
  * *When* the athlete is riding along a saved route corridor,
  * *Then* the recognized route SHALL appear directly in the route list inside `RouteSelectorSheet.kt` without any separate `AutoDetectedRouteBanner`.
* **Criterion 3 (In-Ride Empty State & Subtitle)**:
  * *Given* an active workout tracking session where the athlete is not on any known route,
  * *When* viewing `ControlTrackingScreen`,
  * *Then* `RouteSelectionButton` SHALL display subtitle "Keine passende Strecke erkannt" and render dimmed (`alpha = 0.45f`).
* **Criterion 4 (Interactive Clickability & Take Me Home)**:
  * *Given* an active workout session with `isDimmed == true` ("Keine passende Strecke erkannt"),
  * *When* the athlete taps `RouteSelectionButton`,
  * *Then* `RouteSelectorModalBottomSheet` SHALL open, displaying `MidRideHeimwegCard` prominently at the top.
* **Criterion 5 (9-Language Parity)**:
  * *Given* all 9 supported application locales,
  * *When* `TranslationParityTest` executes,
  * *Then* `route_no_routes_nearby` and `route_no_matching_route_detected` SHALL be defined with zero missing translations.

### 1.4 System Invariants
* Single-route active navigation invariant in `RoutesRepository`.
* Interactive clickability of dimmed button (`REQ-UI-281`).
* In-ride passive auto-detection prompt in `SensorGridScreen.kt` remains operational.
* SQLite schema version 10 in `Routes.db` remains untouched.
* 100% clean-room test suite pass rate.

---

## 2. Test Specification (TST-UI-249)

### Test Case 1: `testRouteSelectorViewModel_preTracking_filtersByStartPointProximity` (`TST-UI-249.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: `isTrackingActive == false`, location set to `(48.137, 11.576)`. Route A starts 300m away; Route B starts 5000m away; Route C has midpoint 10m away but start 6000m away.
* **Action**: Observe `uiState`.
* **Expected Result**:
  - `uiState.routes` contains only Route A.
  - `uiState.isTrackingActive` is `false`.
  - `uiState.contextEmptyHintRes` is `R.string.route_no_routes_nearby`.

### Test Case 2: `testRouteSelectorViewModel_inRide_evaluatesCorridorMatchingRoutes` (`TST-UI-249.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: `isTrackingActive == true`, location set to midpoint of Route C.
* **Action**: `viewModel.setTrackingActive(true)` and update location.
* **Expected Result**:
  - `uiState.routes` contains Route C (matched by path segment).
  - `uiState.isTrackingActive` is `true`.
  - When no route matches, `uiState.routes` is empty and `uiState.contextEmptyHintRes` is `R.string.route_no_matching_route_detected`.

### Test Case 3: `testRouteAutoDetector_evaluateMatchingRoutes_returnsAllCorridorMatches` (`TST-UI-249.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteAutoDetectorTest.kt`
* **Preconditions**: Two overlapping routes (Route 1 and Route 2) along a river bike path.
* **Action**: Call `autoDetector.evaluateMatchingRoutes(location, listOf(route1, route2), currentlyActiveRouteId = null)`.
* **Expected Result**: Both routes are returned in the candidate list, ordered by closest segment distance. Currently active route ID is excluded.

### Test Case 4: `testRouteSelectionButton_rendersContextAwareSubtitles` (`TST-UI-249.4`)
* **Scope**: Contract / Layout Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButtonContractTest.kt`
* **Preconditions**: `RouteSelectionButton` rendered with `activeRoute = null`, `isDimmed = true`, and `emptySubtitleRes`.
* **Action**: Verify text rendering for both `R.string.route_no_routes_nearby` and `R.string.route_no_matching_route_detected`.
* **Expected Result**: Contextual subtitle text is correctly rendered, button alpha is 0.45f, and click action is enabled.

### Test Case 5: `testRouteSelectorSheet_excisesAutoDetectedRouteBanner` (`TST-UI-249.5`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
* **Action**: Inspect AST / composition structure of `RouteSelectorSheet.kt`.
* **Expected Result**: `AutoDetectedRouteBanner` is NOT called within `RouteSelectorContent` in `RouteSelectorSheet.kt`.

### Test Case 6: 9-Language Localization Audit (`TST-UI-249.6`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/translations/TranslationParityTest.kt`
* **Goal**: Verify string resources `route_no_routes_nearby` and `route_no_matching_route_detected` across all 9 locales:
  - `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`
* **Expected Result**: 100% translation parity, zero missing entries.

### Test Case 7: Full Clean-Room Regression Suite (`TST-UI-249.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-249.1` | Unit | `RouteSelectorViewModel.uiState` (Pre-Tracking) | `REQ-UI-289.1` | Specified |
| `TST-UI-249.2` | Unit | `RouteSelectorViewModel.uiState` (In-Ride) | `REQ-UI-289.1` | Specified |
| `TST-UI-249.3` | Unit | `RouteAutoDetector.evaluateMatchingRoutes` | `REQ-UI-289.2` | Specified |
| `TST-UI-249.4` | UI Contract | `RouteSelectionButton` | `REQ-UI-289.4` | Specified |
| `TST-UI-249.5` | Contract | `RouteSelectorSheet.kt` | `REQ-UI-289.3` | Specified |
| `TST-UI-249.6` | Localization | `TranslationParityTest` | `REQ-UI-289.6`, `REQ-UI-106` | Specified |
| `TST-UI-249.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
