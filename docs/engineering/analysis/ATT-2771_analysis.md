# Stage 1 Analysis: ATT-2771 - Remove in-ride auto-detected route banner from tracking sensor grid

**Ticket**: [ATT-2771](https://rainerblind.atlassian.net/browse/ATT-2771)  
**Sub-task**: [ATT-2852](https://rainerblind.atlassian.net/browse/ATT-2852) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `4.9.38.4`  
**Active Sprint**: `Sprint 2026-41.4`  
**Branch**: `improvement/ATT-2771`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During active workout tracking (`ScreenMode.TRACKING`), `SensorGridScreen.kt` currently renders the `AutoDetectedRouteBanner` composable directly above the primary sensor tiles whenever GPS proximity matches an existing route corridor:
```kotlin
// Auto-Detected Route Banner (REQ-MAP-024 / ATT-1835)
if (screenMode == ScreenMode.TRACKING && routeSelectorUiState.isAutoPromptVisible && routeSelectorUiState.autoDetectedCandidate != null) {
    routeSelectorUiState.autoDetectedCandidate?.let { candidate ->
        AutoDetectedRouteBanner(
            route = candidate,
            onActivate = { actualRouteSelectorViewModel.activateCandidate(candidate.summary.id) },
            onDismiss = { actualRouteSelectorViewModel.dismissCandidate(candidate.summary.id) }
        )
    }
}
```
When triggered, a card titled *"Routenerkennung: Befindest du dich auf '<Route>'? [Ablehnen] [Aktivieren]"* appears at the very top of the cockpit display.

**Current Issues:**
1. **Cockpit Layout Occlusion**: The banner abruptly pushes down critical real-time telemetry tiles (heart rate, power, cadence, speed), causing sudden visual shifting while an athlete is cycling or running.
2. **Redundancy with Route Selector Sheet**: Under ticket ATT-2627 (`REQ-UI-289`), in-ride detected routes directly populate the candidate route list inside the `RouteSelectorSheet`. Athletes can select recognized route corridors on demand via `RouteSelectionButton` on `ControlTrackingScreen`. Consequently, an unsolicited banner interrupting the telemetry grid during high-intensity workouts is unwanted, distracting, and redundant.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Origin in ATT-1835 (`REQ-MAP-024`)**: In early iterations, before `ControlTrackingScreen` had a dedicated `RouteSelectionButton` (`ATT-2458`), prompt banners were injected directly into the sensor grid to notify the user of detected routes.
2. **Partial Decoupling in ATT-2458 (`REQ-UI-279`)**: ATT-2458 removed `RouteActionChipRow` from `SensorGridScreen.kt` but preserved `AutoDetectedRouteBanner` as an interim measure.
3. **Completing the Decoupling**: With ATT-2627, `RouteAutoDetector` feeds recognized corridors straight into `RouteSelectorViewModel`'s candidate list. The banner widget itself was already excised from `RouteSelectorSheet.kt` and exists solely to serve `SensorGridScreen.kt`.
4. **Obsolete Resource Accumulation**: Because `AutoDetectedRouteBanner` was excised from `RouteSelectorSheet` in ATT-2627, removing it from `SensorGridScreen` leaves `AutoDetectedRouteBanner` with zero callers across the application. The 4 associated string resources (`route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, `route_auto_detect_dismiss`) become completely dead across all 9 localized `strings.xml` files.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Remove the `AutoDetectedRouteBanner` conditional block and unused imports from `SensorGridScreen.kt`.
  2. Clean up unused `actualRouteSelectorViewModel` and `routeSelectorUiState` inside `SensorGridScreen.kt`.
  3. Remove the unused `AutoDetectedRouteBanner` composable declaration from `RouteSelectorSheet.kt`.
  4. Remove obsolete string resources (`route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, `route_auto_detect_dismiss`) across all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  5. Update contract and integration tests (`SensorGridScreenRouteIntegrationTest.kt`, `ControlTrackingRouteSelectionContractTest.kt`, `RouteSelectorSheetTest.kt`) to assert that the banner is NOT hosted or defined.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT disable `RouteAutoDetector` corridor matching: it is still used by `RouteSelectorViewModel` to populate candidates in the route selection sheet.
  * Do NOT modify `ForkNavigationDecisionCard`: turn/fork decision prompts remain active during active route navigation.
  * Do NOT alter `RouteSelectionButton` or `RouteSelectorModalBottomSheet`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-UI-279` Clause 1 (*Removal from Cockpit Sensor Grid Tabs*) and `REQ-MAP-024` Clause 3 (*Adaptive Route Selector Sheet UI*).
* **Historical Origin & Commit Trace**:
  - `REQ-MAP-024`: Ticket `ATT-1835` (Sprint 2026-40.14).
  - `REQ-UI-279`: Ticket `ATT-2458` (commit `69dfa539`, Sprint 2026-41.1).
* **Root Reason for Existing Formulation**: ATT-2458 preserved `AutoDetectedRouteBanner` in `SensorGridScreen.kt` because at the time, `RouteSelectorSheet` only supported start-proximity ranking. Athletes needed some way to be alerted to in-ride route matches.
* **Preservation of Core Invariants**:
  - ATT-2627 (`REQ-UI-289`) subsequently upgraded `RouteSelectorViewModel` to dynamically recognize in-ride route corridors and surface them in the route list when `isTrackingActive == true`.
  - Removing `AutoDetectedRouteBanner` from the cockpit sensor grid prevents telemetry occlusion without sacrificing the athlete's ability to activate detected routes via `RouteSelectionButton`.
  - Core navigation, sensor recording, and fork decision cards remain 100% operational.

---

## 5. Architectural Strategy & High-Level Solution

1. **`SensorGridScreen.kt`**:
   - Delete lines 518–531 (the `AutoDetectedRouteBanner` conditional render).
   - Delete `actualRouteSelectorViewModel` and `routeSelectorUiState` collection if not used by any other UI elements.
   - Clean up unused imports (`com.atrainingtracker.trainingtracker.ui.routes.AutoDetectedRouteBanner`).
2. **`RouteSelectorSheet.kt`**:
   - Delete `fun AutoDetectedRouteBanner(...)` composable definition.
3. **`strings.xml` (all 9 locales)**:
   - Excise `route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, and `route_auto_detect_dismiss`.
4. **Architectural & Contract Unit Tests**:
   - In `SensorGridScreenRouteIntegrationTest.kt`: assert `SensorGridScreen.kt` does NOT contain `AutoDetectedRouteBanner(`.
   - In `ControlTrackingRouteSelectionContractTest.kt`: assert `SensorGridScreen.kt` does NOT contain `AutoDetectedRouteBanner(`.
   - In `RouteSelectorSheetTest.kt`: assert `RouteSelectorSheet.kt` does NOT contain `fun AutoDetectedRouteBanner(`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Top sensor tiles in `SensorGridScreen` remain fixed at the top of the viewport without layout jumping or occlusion during active tracking.
  2. `RouteSelectorViewModel` and `RouteAutoDetector` continue to evaluate route corridors for the route sheet.
  3. Zero regressions across the full unit test suite (`./gradlew testDebugUnitTest`).
  4. Parent ticket Human Decision Gate remains strictly enforced (`Final Review (Human)`).
* **Risk Rating**: **LOW**
  - Dead code and UI clutter removal; simplifies component architecture and removes unused string resources without altering data persistence or background tracking services.
