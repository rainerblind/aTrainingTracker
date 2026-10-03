# Stage 2 Test Specification: ATT-1835 - Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection

**Ticket**: [ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835)  
**Sub-task**: [ATT-2269](https://rainerblind.atlassian.net/browse/ATT-2269) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1835`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Overview & Verification Strategy

This test specification defines the verification procedures for `REQ-MAP-024` under ticket [ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835).

The verification strategy ensures that:
1. Athletes in the tracking cockpit can open an interactive `RouteSelectorBottomSheet` in 1 tap without leaving active tracking.
2. A deterministic multi-stage proximity ranker (`RouteProximityRanker`) resolves tie-breaking when multiple routes start from the same location (e.g. at home, $d \approx 0\,\text{m}$), prioritizing active sport match, departing heading alignment ($\Delta\text{bearing} \le 45^\circ$), and recency.
3. Adaptive UI logic cleanly hides filter chips when fewer than 5 routes exist ($< 5$), and displays filter chips (distance, sort) when $\ge 5$ routes exist.
4. Tapping a route activates navigation immediately; tapping "Route beenden" deselects navigation.
5. The background `RouteAutoDetector` detects when an athlete is following a saved route without having selected it ($\le 30\,\text{m}$ distance for $\ge 200\,\text{m}$, heading $\Delta\theta \le 35^\circ$), prompting the user or auto-joining according to user settings.
6. Settings for auto-detection and join mode persist in `TuningPreferencesDataStore`.
7. All user-facing strings maintain 100% translation parity across all 9 supported application locales.

---

## 2. Requirement Traceability Matrix

| Requirement ID | Test Specification ID | Test Classes / Suites | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-024` (1: Cockpit Entry Point) | `TST-MAP-026` (Group 1) | `RouteSelectorSheetTest.kt` | Robolectric / Compose Test | Defined |
| `REQ-MAP-024` (2: Multi-Stage Proximity Sorting) | `TST-MAP-026` (Group 2) | `RouteProximityRankerTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-024` (3: Adaptive Filter Chips UI) | `TST-MAP-026` (Group 3) | `RouteSelectorSheetTest.kt` | Robolectric / Compose Test | Defined |
| `REQ-MAP-024` (4: Automated Route Detection) | `TST-MAP-026` (Group 4) | `RouteAutoDetectorTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-024` (5: Advanced Tuning Preferences) | `TST-MAP-026` (Group 5) | `RouteAutoDetectPreferencesTest.kt` | Robolectric / DataStore Test | Defined |
| `REQ-MAP-024` (6: Localization Parity) | `TST-MAP-026` (Group 6) | `TranslationParityTest.kt` | JUnit 4 Resource Test | Defined |
| `REQ-ALL` (Regression Invariant) | `TST-MAP-026` (Group 7) | Full `./gradlew testDebugUnitTest` | Clean-Room CI Suite | Defined |

---

## 3. Concrete Test Cases (`TST-MAP-026`)

### Group 1: 1-Tap Cockpit Entry Point & Sheet Lifecycle
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetTest`
* **Test Cases**:
  1. `testRouteActionButton_whenNoActiveRoute_displaysRouteWaehlen`:
     - Given active navigation is null.
     - Asserts the cockpit route action button displays the route icon and localized text "Route wählen".
  2. `testRouteActionButton_whenActiveRoutePresent_displaysRouteNameAndCheckmark`:
     - Given active navigated route ID 42L with name "Alpen-Runde 65 km".
     - Asserts the action button displays "Alpen-Runde 65 km" and active indicator.
  3. `testRouteActionButton_onClick_opensRouteSelectorSheet`:
     - Tapping the action button triggers bottom sheet presentation without navigating away from the cockpit.

### Group 2: Multi-Stage Route Ranking & "Home Hub" Tie-Breaking
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.RouteProximityRankerTest`
* **Test Cases**:
  1. `testTier1_proximityGrouping`:
     - Routes starting within 250m form the immediate top group over routes starting > 250m away.
  2. `testTier2_sportProfileMatching`:
     - Given 4 routes starting at the same coordinates ($d = 0\,\text{m}$), 2 for Road Cycling and 2 for Trail Running.
     - With active sport Road Cycling, cycling routes rank strictly before running routes.
  3. `testTier3_headingMovementAlignment`:
     - Given 2 cycling routes starting at the same coordinates ($d = 0\,\text{m}$): Route A departs East ($90^\circ$), Route B departs West ($270^\circ$).
     - When athlete moves East ($90^\circ \pm 30^\circ$), Route A ranks ahead of Route B.
  4. `testTier4_recencyAndFrequency`:
     - Given 2 routes identical in proximity, sport, and departure heading: Route A was ridden yesterday, Route B was ridden 6 months ago.
     - Route A ranks ahead of Route B.
  5. `testTier5_fallbackSorting`:
     - Routes identical across tiers 1-4 are sorted by ascending length, then alphabetically.
  6. `testNullLocationFallback`:
     - When GPS fix is null or unavailable, routes sort gracefully by recency, then length/alphabetical without throwing exceptions.

### Group 3: Adaptive Filter Chips UI & 1-Tap Action
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetTest`
* **Test Cases**:
  1. `testAdaptiveFilterChips_whenFewerThan5Routes_hidesFilterChips`:
     - Given a list of 4 routes.
     - Asserts filter chips row is not composed / is hidden (`assertDoesNotExist`).
  2. `testAdaptiveFilterChips_when5OrMoreRoutes_displaysFilterChips`:
     - Given a list of 5 routes.
     - Asserts filter chips (Distance brackets, Sort options) are visible and selectable.
  3. `testRouteCard_onClick_activatesNavigationAndDismisses`:
     - Tapping a route card calls `RoutesRepository.toggleRouteSelection` / `setActiveNavigatedRoute`, setting the active route and dismissing the sheet.
  4. `testDeselectAction_clearsActiveRoute`:
     - Tapping "Route beenden / abwählen" sets active route to null.

### Group 4: Automated Route Detection Engine
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.RouteAutoDetectorTest`
* **Test Cases**:
  1. `testAutoDetect_whenMatchingPolylineFor200m_detectsRoute`:
     - Athlete records GPS track following a saved route within 30m distance and $\le 35^\circ$ heading alignment for 200m.
     - Asserts candidate route is emitted with match confidence.
  2. `testAutoDetect_whenDivergingHeading_doesNotDetect`:
     - Athlete is within 30m of a route but traveling in reverse or perpendicular direction ($\Delta\theta > 35^\circ$).
     - Asserts no detection triggered.
  3. `testAutoDetect_whenDistanceExceeds30m_doesNotDetect`:
     - Athlete is parallel but 50m away from the polyline.
     - Asserts no detection triggered.
  4. `testAutoDetect_whenUnderThresholdDistance_doesNotDetect`:
     - Athlete matches route for only 100m (< 200m threshold).
     - Asserts no detection triggered.

### Group 5: Advanced Settings & DataStore Persistence
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.RouteAutoDetectPreferencesTest`
* **Test Cases**:
  1. `testDefaultPreferences`:
     - Asserts `routeAutoDetectEnabled == true`, `routeAutoJoinMode == PROMPT`, `routeAutoDetectThresholdMeters == 200`.
  2. `testPreferencesMutationAndPersistence`:
     - Toggling auto-detect off, changing join mode to `AUTO`, and updating threshold to `300` persists in DataStore and restores correctly across app restart.

### Group 6: 9-Language Localization Parity
* **Test Class**: `com.atrainingtracker.trainingtracker.TranslationParityTest`
* **Test Cases**:
  1. `testRouteSelectorStringsParity`:
     - Verifies string keys (`route_select_title`, `route_action_select`, `route_action_stop`, `route_auto_detect_title`, `route_auto_detect_prompt`, `route_filter_near`, `route_filter_recent`, `route_filter_length`) exist across all 9 locales:
       `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### Group 7: Full Clean-Room Regression Invariant
* **Execution**: `./gradlew testDebugUnitTest`
* **Success Criteria**: 100% of all existing and new unit tests pass with zero regressions.

---

## 4. Next Steps & Stage 3 Transition
1. Register `TST-MAP-026` in `docs/tests.md`.
2. Post this Stage 2 Test Specification to subtask `ATT-2269`.
3. Move `ATT-2269` to `In Überprüfung`.
4. Run Gate 2 audit (`python3 tools/review_agent.py audit ATT-2269`).
5. Upon Gate 2 approval, advance to Stage 3 (`[Impl-Plan]`).
