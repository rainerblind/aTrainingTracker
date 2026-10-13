# Stage 2 Test Specification: ATT-1835 - Quick Route Selector from Cockpit with GPS Proximity Sorting and Automated Route Detection (Rework Cycle 2)

**Ticket**: [ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835)  
**Sub-task**: [ATT-2412](https://rainerblind.atlassian.net/browse/ATT-2412) (`[Test-Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.40` (assigned upon completion per Rule 19)  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-024`  
**Test Spec ID**: `TST-MAP-026`  
**Branch**: `feature/ATT-1835`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Overview & Verification Strategy

This test specification defines the verification procedures for `REQ-MAP-024` under ticket [ATT-1835](https://rainerblind.atlassian.net/browse/ATT-1835) for Rework Cycle 2.

During the Sprint 2026-40.15 review, the PO identified that while domain components (`RouteProximityRanker`, `RouteAutoDetector`, `RouteSelectorViewModel`, `RouteSelectorSheet`) were implemented, the UI entry points and HUD banners were never wired into `SensorGridScreen.kt`, leaving the feature uninvoked.

The Rework Cycle 2 verification strategy ensures that:
1. **Cockpit Route Entry Point (`SensorGridScreen.kt`)**: Athletes in tracking mode can access a prominent 1-tap route action chip/button that displays "Route wählen" (`R.string.route_action_select`) when navigation is inactive, or the active route name + checkmark when active.
2. **Modal Bottom Sheet Presentation (`RouteSelectorSheet.kt`)**: Tapping the action chip triggers `ModalBottomSheet` displaying `RouteSelectorContent`, supporting dynamic filter chips ($\ge 5$ routes), route selection, and "Route beenden".
3. **In-Ride Auto-Detection Banner (`AutoDetectedRouteBanner`)**: When `RouteAutoDetector` detects a candidate polyline during active recording, the prompt banner is rendered immediately in the Cockpit above the sensor grid with functional "Aktivieren" and "Ablehnen" callbacks.
4. **Live GPS Telemetry Integration**: Real-time GPS location, bearing, and speed from `currentLocationFlow` and tracking state are converted and fed continuously into `RouteSelectorViewModel.onLocationChanged(...)`.
5. **Architectural & Integration Contract Tests**: New test class `SensorGridScreenRouteIntegrationTest.kt` verifies that `SensorGridScreen.kt` explicitly references and renders `RouteSelectorContent` and `AutoDetectedRouteBanner`.
6. **Multi-Stage Ranking & Auto-Detection Algorithms**: Preserves 100% test pass rate across `RouteProximityRankerTest.kt`, `RouteAutoDetectorTest.kt`, and `RouteSelectorViewModelTest.kt`.
7. **9-Language Localization Parity**: Verified across EN, DE, ES, FR, IT, JA, NL, PL, PT by `TranslationParityTest.kt`.

---

## 2. Requirement Traceability Matrix

| Requirement ID | Test Specification ID | Test Classes / Suites | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-024` (1: Cockpit Entry Point & Wiring) | `TST-MAP-026` (Group 1) | `SensorGridScreenRouteIntegrationTest.kt`, `RouteSelectorSheetTest.kt` | JUnit 4 / Compose Contract Test | Specified |
| `REQ-MAP-024` (2: Multi-Stage Proximity Sorting) | `TST-MAP-026` (Group 2) | `RouteProximityRankerTest.kt` | JUnit 4 Unit Test | Specified |
| `REQ-MAP-024` (3: Adaptive Filter Chips UI) | `TST-MAP-026` (Group 3) | `RouteSelectorViewModelTest.kt`, `RouteSelectorSheetTest.kt` | JUnit 4 Unit & Contract Test | Specified |
| `REQ-MAP-024` (4: In-Ride Auto-Detection HUD) | `TST-MAP-026` (Group 4) | `RouteAutoDetectorTest.kt`, `SensorGridScreenRouteIntegrationTest.kt` | JUnit 4 / Compose Contract Test | Specified |
| `REQ-MAP-024` (5: Tuning Preferences) | `TST-MAP-026` (Group 5) | `RouteAutoDetectPreferencesTest.kt` | JUnit 4 Unit Test | Specified |
| `REQ-MAP-024` (6: Localization Parity) | `TST-MAP-026` (Group 6) | `TranslationParityTest.kt` | JUnit 4 Resource Test | Specified |
| `REQ-ALL` (Regression Invariant) | `TST-MAP-026` (Group 7) | `./gradlew testDebugUnitTest` | Clean-Room Suite (100% pass) | Specified |

---

## 3. Concrete Test Cases (`TST-MAP-026`)

### Group 1: Cockpit Route Entry Point & Sheet Presentation (`SensorGridScreenRouteIntegrationTest.kt`)
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenRouteIntegrationTest`
* **Test Cases**:
  1. `testSensorGridScreen_containsRouteActionAffordance`:
     - Inspects `SensorGridScreen.kt` source code to verify presence of route action button/chip invoking route selector.
     - Verifies string resource usage `R.string.route_action_select` and route icon.
  2. `testSensorGridScreen_hostsModalBottomSheet_withRouteSelectorContent`:
     - Verifies `SensorGridScreen.kt` declares a `ModalBottomSheet` wrapping `RouteSelectorContent`.
     - Verifies sheet dismisses when a route is selected or dismissed.
  3. `testSensorGridScreen_displaysActiveRouteState`:
     - Verifies when `activeRoute != null`, the UI reflects active route name and checkmark affordance.

### Group 2: Multi-Stage Route Ranking & "Home Hub" Tie-Breaking (`RouteProximityRankerTest.kt`)
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.RouteProximityRankerTest`
* **Test Cases**:
  1. `testTier1_proximityGrouping`:
     - Routes starting within 250m form the immediate top group over routes starting > 250m away.
  2. `testTier2_sportProfileMatching`:
     - Given routes starting at the same coordinates ($d = 0\,\text{m}$), routes matching active workout sport rank ahead of non-matching sports.
  3. `testTier3_headingMovementAlignment`:
     - Given 2 routes starting at $d = 0\,\text{m}$: East ($90^\circ$) vs West ($270^\circ$).
     - Moving East ($90^\circ \pm 45^\circ$) elevates the eastbound route to the top.
  4. `testTier4_recencyAndFrequency`:
     - Routes identical in tiers 1–3 are ranked by `syncedAt` (most recent first).
  5. `testTier5_fallbackSorting`:
     - Fallback sorting by ascending distance and alphabetical name.
  6. `testNullLocationFallback`:
     - Null GPS coordinates sort gracefully without `NullPointerException`.

### Group 3: Adaptive Filter Chips & Route Selection (`RouteSelectorViewModelTest.kt`)
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorViewModelTest`
* **Test Cases**:
  1. `testShowFilterTabs_whenRoutesLessThan5_isFalse`:
     - Asserts `uiState.showFilterTabs == false` when total route count $< 5$.
  2. `testShowFilterTabs_whenRoutes5OrMore_isTrue`:
     - Asserts `uiState.showFilterTabs == true` when total route count $\ge 5$.
  3. `testSelectRoute_updatesRepositoryActiveRoute`:
     - Calling `viewModel.selectRoute(routeId)` delegates to `routesRepository.setActiveNavigatedRoute(routeId)`.
  4. `testStopRoute_clearsRepositoryActiveRoute`:
     - Calling `viewModel.stopRoute()` sets active navigated route ID to `null`.

### Group 4: In-Ride Auto-Detection HUD & Geometry (`RouteAutoDetectorTest.kt`, `SensorGridScreenRouteIntegrationTest.kt`)
* **Test Classes**: `RouteAutoDetectorTest.kt`, `SensorGridScreenRouteIntegrationTest.kt`
* **Test Cases**:
  1. `testSensorGridScreen_hostsAutoDetectedRouteBanner`:
     - Verifies `SensorGridScreen.kt` invokes `AutoDetectedRouteBanner` conditionally based on auto-detected candidate visibility.
     - Verifies banner provides activate and dismiss action hooks.
  2. `testSensorGridScreen_feedsLocationUpdatesToRouteSelectorViewModel`:
     - Verifies `SensorGridScreen.kt` passes incoming coordinates from `currentLocationFlow` to `RouteSelectorViewModel.onLocationChanged`.
  3. `testRouteAutoDetector_matchingPolyline_detectsCandidate`:
     - Follows polyline within 30m and $\le 35^\circ$ heading for 200m; asserts candidate route returned.
  4. `testRouteAutoDetector_divergingHeadingOrCrossTrack_doesNotDetect`:
     - Cross-track $> 50\text{m}$ or reverse heading suppresses detection.
  5. `testRouteAutoDetector_dismissCooldown_suppressesPromptFor15Minutes`:
     - Dismissing a candidate suppresses re-detection for 15 minutes.

### Group 5: Advanced Tuning Preferences (`RouteAutoDetectPreferencesTest.kt`)
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.RouteAutoDetectPreferencesTest`
* **Test Cases**:
  1. `testPreferences_defaultsAndMutation`:
     - Verifies `routeAutoDetectEnabled` (default: `true`), `routeAutoJoinMode` (default: `PROMPT`), and `routeAutoDetectThresholdMeters` (default: `200`) persist in `TuningPreferencesDataStore`.

### Group 6: 9-Language Localization Parity (`TranslationParityTest.kt`)
* **Test Class**: `com.atrainingtracker.trainingtracker.TranslationParityTest`
* **Test Cases**:
  1. `testRouteSelectorStrings_acrossAll9Locales`:
     - Validates tokens (`route_select_title`, `route_action_select`, `route_action_stop`, `route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, `route_auto_detect_dismiss`, `route_filter_near`, `route_filter_recent`, `route_filter_length`, `route_empty_title`, `route_empty_desc`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Group 7: Full Clean-Room Regression Suite
* **Execution**: `./gradlew testDebugUnitTest`
* **Assertion**: 100% pass rate with zero test failures across all project modules.

---

## 4. Next Steps & Stage 3 Transition
1. Post this Stage 2 Test Specification to subtask `ATT-2412`.
2. Move `ATT-2412` to `In Überprüfung`.
3. Run Gate 2 audit (`python3 tools/review_agent.py audit ATT-2412`).
4. Upon Gate 2 approval, advance to Stage 3 (`[Impl-Plan]`).
