# Stage 3: Implementation Plan - ATT-2771: Remove in-ride auto-detected route banner from tracking sensor grid

**Ticket**: [ATT-2771](https://rainerblind.atlassian.net/browse/ATT-2771)  
**Sub-task**: [ATT-2854](https://rainerblind.atlassian.net/browse/ATT-2854) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `4.9.38.4`  
**Active Sprint**: `Sprint 2026-41.4`  
**Requirement Mapping**: `REQ-UI-311` (*Removal of In-Ride Auto-Detected Route Banner from Cockpit Sensor Grid & Obsolete Resource Cleanup*)  
**Test Spec ID**: `TST-UI-271` (*Removal of In-Ride Auto-Detected Route Banner and Sensor Grid Decoupling Verification*)  
**Branch**: `improvement/ATT-2771`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

In `SensorGridScreen.kt`, during active tracking (`ScreenMode.TRACKING`), an `AutoDetectedRouteBanner` is conditionally rendered directly above the primary sensor telemetry tiles whenever GPS proximity matches a known route corridor. This banner abruptly shifts the viewport and occludes primary telemetry tiles (heart rate, power, cadence, speed). 
Following ATT-2627 (`REQ-UI-289`), in-ride recognized routes already directly populate the candidate list in `RouteSelectorSheet` accessible via `RouteSelectionButton` on `ControlTrackingScreen`. Consequently, an unsolicited banner interrupting the telemetry grid during workouts is redundant and distracting.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-311` (*Removal of In-Ride Auto-Detected Route Banner from Cockpit Sensor Grid & Obsolete Resource Cleanup*)
* **Test Mapping**: `TST-UI-271` (*Removal of In-Ride Auto-Detected Route Banner and Sensor Grid Decoupling Verification*)
  - `TST-UI-271.1`: `SensorGridScreenRouteIntegrationTest` verifies absence of `AutoDetectedRouteBanner` in `SensorGridScreen.kt`.
  - `TST-UI-271.2`: `ControlTrackingRouteSelectionContractTest` verifies absence of `AutoDetectedRouteBanner` in `SensorGridScreen.kt`.
  - `TST-UI-271.3`: `RouteSelectorSheetTest` verifies absence of `AutoDetectedRouteBanner` in `RouteSelectorSheet.kt`.
  - `TST-UI-271.4`: `TranslationParityTest` verifies clean removal of obsolete string keys across all 9 locales.
  - `TST-UI-271.5`: Clean-room test suite execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Telemetry Stability**: Primary sensor tiles remain anchored at the top of the cockpit content viewport during active tracking without layout jumps or occlusion.
2. **Route Auto-Detection Engine Integrity**: `RouteAutoDetector` continues to evaluate route corridor matches for `RouteSelectorViewModel` and the `RouteSelectorSheet`.
3. **Turn/Fork Decision Integrity**: `ForkNavigationDecisionCard` remains active and unaffected for in-ride fork decisions.
4. **100% Full-Suite Pass Rate**: Zero regressions across unit and integration tests.
5. **Human Decision Gate**: Parent ticket `ATT-2771` terminal status is `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `SensorGridScreen.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)
- Remove `AutoDetectedRouteBanner` invocation and its enclosing conditional block.
- Remove internal instantiation of `actualRouteSelectorViewModel` and `routeSelectorUiState` collection if not used for any other UI elements.
- Remove import `com.atrainingtracker.trainingtracker.ui.routes.AutoDetectedRouteBanner`.

### Component 2: `RouteSelectorSheet.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)
- Remove obsolete `fun AutoDetectedRouteBanner(...)` composable function.
- Clean up unused imports.

### Component 3: Localization String Resources (`res/values*/strings.xml`)
- Remove obsolete string keys across all 9 locales:
  - `route_auto_detect_title`
  - `route_auto_detect_prompt`
  - `route_auto_detect_activate`
  - `route_auto_detect_dismiss`

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `SensorGridScreen.kt` (primary tracking cockpit)
* **Reused components**: Clean presentation of top sensor tiles, unchanged `ForkNavigationDecisionCard`.
* **Theme tokens**: Standard `MaterialTheme.colorScheme` and spacing.
* **New one-off styles & justification**: None. (Pure cleanup and excision of intrusive overlay card).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Clean Up `SensorGridScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Changes:
  - Delete `AutoDetectedRouteBanner` conditional block.
  - Delete unused `actualRouteSelectorViewModel` and `routeSelectorUiState`.
  - Remove unused import.

### Step 2: Clean Up `RouteSelectorSheet.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
* Changes:
  - Delete `AutoDetectedRouteBanner` composable declaration.

### Step 3: Excise Obsolete String Resources across 9 Locales
* Files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* Changes:
  - Delete `route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, `route_auto_detect_dismiss`.

### Step 4: Update Contract & Integration Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingRouteSelectionContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
* Changes:
  - Update tests to assert that `SensorGridScreen.kt` does NOT host `AutoDetectedRouteBanner` and `RouteSelectorSheet.kt` does NOT define `AutoDetectedRouteBanner`.

### Step 5: Execute Targeted Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenRouteIntegrationTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingRouteSelectionContractTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetTest" \
    --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction followed by full clean-room `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Branch isolation on `improvement/ATT-2771` enables clean rollback without impacting other sprint deliverables.
