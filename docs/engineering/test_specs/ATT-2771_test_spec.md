# Stage 2: Requirement & Test Specification - ATT-2771: Remove in-ride auto-detected route banner from tracking sensor grid

**Ticket**: [ATT-2771](https://rainerblind.atlassian.net/browse/ATT-2771)  
**Sub-task**: [ATT-2853](https://rainerblind.atlassian.net/browse/ATT-2853) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `4.9.38.4`  
**Active Sprint**: `Sprint 2026-41.4`  
**Requirement Mapping**: `REQ-UI-311` (*Removal of In-Ride Auto-Detected Route Banner from Cockpit Sensor Grid & Obsolete Resource Cleanup*)  
**Test Spec ID**: `TST-UI-271` (*Removal of In-Ride Auto-Detected Route Banner and Sensor Grid Decoupling Verification*)  
**Branch**: `improvement/ATT-2771`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-311)

### 1.1 Problem Statement & Rationale
During workout tracking, `SensorGridScreen.kt` renders an `AutoDetectedRouteBanner` above the primary sensor telemetry tiles whenever GPS proximity matches a known route corridor. This banner abruptly pushes down real-time metrics (heart rate, power, cadence, speed) and clutters the tracking cockpit. Because in-ride recognized routes already directly populate the candidate route list in `RouteSelectorSheet` (via `RouteSelectionButton` on `ControlTrackingScreen` per ATT-2627 / `REQ-UI-289`), this unsolicited in-ride banner on the sensor grid is redundant, disruptive, and undesirable.

### 1.2 Functional & Architectural Requirements
1. **Cockpit Sensor Grid Decoupling (`SensorGridScreen.kt`)**:
   - The system SHALL remove `AutoDetectedRouteBanner` conditional rendering from `SensorGridScreen.kt`.
   - In active tracking (`ScreenMode.TRACKING`), sensor tiles SHALL render directly below the top layout bounds without an intervening route detection banner or layout shifts.
   - The unused import `com.atrainingtracker.trainingtracker.ui.routes.AutoDetectedRouteBanner` and unused internal references to `actualRouteSelectorViewModel` and `routeSelectorUiState` in `SensorGridScreen.kt` SHALL be removed.
2. **Component Simplification (`RouteSelectorSheet.kt`)**:
   - The system SHALL excise the obsolete `AutoDetectedRouteBanner` composable declaration from `RouteSelectorSheet.kt`.
   - In-ride recognized routes SHALL continue to directly populate the candidate list in `RouteSelectorSheet` via `RouteSelectorViewModel` and `RouteAutoDetector` (as established in `REQ-UI-289`).
3. **Obsolete Localization Resource Cleanup (`strings.xml` across all 9 locales)**:
   - The system SHALL remove the four obsolete string resources (`route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, `route_auto_detect_dismiss`) across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
4. **Architectural & Contract Test Realignment**:
   - Architectural contract tests `SensorGridScreenRouteIntegrationTest.kt`, `ControlTrackingRouteSelectionContractTest.kt`, and `RouteSelectorSheetTest.kt` SHALL be updated to assert the absence of `AutoDetectedRouteBanner` from `SensorGridScreen.kt` and `RouteSelectorSheet.kt`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-UI-279` Clause 1 (*Removal from Cockpit Sensor Grid Tabs*) and `REQ-MAP-024` Clause 3 (*Adaptive Route Selector Sheet UI*).
* **Historical Origin & Commit Trace**:
  - `REQ-MAP-024`: Ticket `ATT-1835` (Sprint 2026-40.14).
  - `REQ-UI-279`: Ticket `ATT-2458` (commit `69dfa539`, Sprint 2026-41.1).
* **Root Reason for Existing Formulation**: ATT-2458 removed `RouteActionChipRow` from `SensorGridScreen.kt` but preserved `AutoDetectedRouteBanner` as an interim measure because `RouteSelectorSheet` only supported start-proximity ranking at the time.
* **Preservation of Core Invariants**: ATT-2627 (`REQ-UI-289`) subsequently upgraded `RouteSelectorViewModel` to dynamically recognize in-ride route corridors and surface them in the route list when `isTrackingActive == true`. Removing `AutoDetectedRouteBanner` from the cockpit sensor grid prevents telemetry occlusion without sacrificing the athlete's ability to activate detected routes via `RouteSelectionButton`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Absence from Sensor Grid)**:
  * *Given* an active workout tracking session (`ScreenMode.TRACKING`),
  * *When* the athlete rides or runs along a known route corridor,
  * *Then* no auto-detection banner or prompt is displayed above the sensor grid in `SensorGridScreen`,
  * *And* the top telemetry tiles remain unobstructed at the top of the cockpit.
* **Criterion 2 (Route Candidate Accessibility)**:
  * *Given* an athlete riding along a known route corridor,
  * *When* tapping `RouteSelectionButton` on `ControlTrackingScreen` to open `RouteSelectorSheet`,
  * *Then* the recognized route corridor appears directly in the candidate list as established in `REQ-UI-289`.
* **Criterion 3 (Clean Architecture & Code Hygiene)**:
  * *Given* the codebase after implementation,
  * *When* compiling `RouteSelectorSheet.kt` and `SensorGridScreen.kt`,
  * *Then* zero references to `AutoDetectedRouteBanner` exist, and string resources `route_auto_detect_*` are cleanly removed from all 9 locale directories.

### 1.5 System Invariants
1. Top sensor tiles in `SensorGridScreen` remain fixed at the top of the viewport without layout jumping or occlusion during active tracking.
2. `RouteSelectorViewModel` and `RouteAutoDetector` continue to evaluate route corridors for the route sheet.
3. `ForkNavigationDecisionCard` remains fully operational for in-ride fork decisions.
4. Zero regressions across the full unit test suite (`./gradlew testDebugUnitTest`).

---

## 2. Test Specification (TST-UI-271)

### Test Case 1: `testSensorGridScreen_doesNotIntegrateAutoDetectedRouteBanner` (`TST-UI-271.1`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source file parsed.
* **Action**: Verify `content.contains("AutoDetectedRouteBanner(")` returns `false`.
* **Expected Result**: Assert `SensorGridScreen.kt` does NOT host `AutoDetectedRouteBanner`.

### Test Case 2: `testControlTrackingContract_assertsAbsenceOfAutoDetectedRouteBanner` (`TST-UI-271.2`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingRouteSelectionContractTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source file parsed.
* **Action**: Verify `content.contains("AutoDetectedRouteBanner(")` returns `false`.
* **Expected Result**: Assert `SensorGridScreen.kt` does NOT host `AutoDetectedRouteBanner`.

### Test Case 3: `testRouteSelectorSheet_doesNotDefineAutoDetectedRouteBanner` (`TST-UI-271.3`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
* **Preconditions**: `RouteSelectorSheet.kt` source file parsed.
* **Action**: Verify `content.contains("fun AutoDetectedRouteBanner(")` returns `false`.
* **Expected Result**: Assert `RouteSelectorSheet.kt` does NOT expose `AutoDetectedRouteBanner`.

### Test Case 4: 9-Language Localization Audit (`TST-UI-271.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Action**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`
* **Expected Result**: 100% parity across all 9 supported locales with obsolete string keys removed uniformly.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-271.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-271.1` | Contract | `SensorGridScreenRouteIntegrationTest.testSensorGridScreen_integratesAutoDetectedRouteBanner` | `REQ-UI-311` | Specified |
| `TST-UI-271.2` | Contract | `ControlTrackingRouteSelectionContractTest.testSensorGridScreen_preservesAutoDetectedRouteBanner` | `REQ-UI-311` | Specified |
| `TST-UI-271.3` | Contract | `RouteSelectorSheetTest.testRouteSelectorSheet_exposesModalBottomSheetAndContent` | `REQ-UI-311` | Specified |
| `TST-UI-271.4` | Localization | `TranslationParityTest` | `REQ-UI-311`, `REQ-LOC-001` | Specified |
| `TST-UI-271.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
