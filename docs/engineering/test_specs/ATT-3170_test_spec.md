# Stage 2: Requirement & Test Specification - ATT-3170: Revert ATT-3100: Restore top-of-tab anchoring for navigation hints in tracking view

**Ticket**: [ATT-3170](https://atrainingtracker.atlassian.net/browse/ATT-3170)  
**Sub-task**: [ATT-3246](https://atrainingtracker.atlassian.net/browse/ATT-3246) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-UI-348` (*Restoration of Unified Top-of-Tab Navigation Hints Overlay Anchoring Across Tracking Tabs*)  
**Test Spec ID**: `TST-UI-308`  
**Branch**: `feature/ATT-3170`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-UI-348)

### 1.1 Problem Statement & Rationale
During Sprint 2026-41.8 (`ATT-3100`), navigation prompt overlays (`TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`) were moved into the embedded map container `Box` when `state.mapState.showMap == true` to avoid occluding topmost sensor tiles.
However, on-device evaluation during the Sprint 2026-41.8 Joint Review demonstrated that having navigation cues appear in the middle of the screen above the map canvas compromised at-a-glance readability, caused cognitive inconsistency between tracking tabs, and obstructed the upcoming route on the map. The athlete explicitly mandated reverting `ATT-3100` and restoring unified top-of-tab anchoring across all tracking tabs.

### 1.2 Functional & Architectural Requirements
1. **Unified Root Overlay Anchoring (`SensorGridScreen.kt`)**:
   - In `SensorGridScreen.kt`, the navigation hints overlay SHALL be anchored at `Alignment.TopCenter` within the root tracking `Box` container, floating over the top of the tab content.
   - The positioning SHALL be invariant to the presence or absence of the embedded map view (`state.mapState.showMap`).
2. **Decoupling from Map Container**:
   - The system SHALL remove the duplicated `NavigationHintsOverlay` call inside the `TrackingMapContainer` `Box`. The map container SHALL NOT host or anchor navigation hint overlays.
3. **Unified Navigation Hints Overlay Architecture**:
   - The encapsulation of `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` inside `NavigationHintsOverlay` SHALL be strictly preserved.
   - Navigation cues SHALL render only when `state.showNavigationHints == true`.
4. **Zero-Telemetry-Displacement Invariant**:
   - Navigation cues SHALL float as non-displacing ambient overlays without shifting or resizing in-flow sensor grid tiles, elevation profile surfaces, or map canvases.
5. **Preservation of System Invariants**:
   - Isolated recomposition barrier (`@Immutable TrackingMapState`), cue transparency tokens (`tuningConfig.navigationCueTransparency`), dismiss durations, and 100% clean-room test pass rate MUST be strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* an active tracking tab with `showMap == true` and `showNavigationHints == true`,
  * *When* a navigation cue (turn prompt, return HUD, or fork decision) is active,
  * *Then* the navigation overlay renders anchored at `Alignment.TopCenter` of the root tracking container,
  * *And* the map container does NOT host a nested navigation overlay.
* **Criterion 2**:
  * *Given* an active tracking tab with `showMap == false` and `showNavigationHints == true`,
  * *When* navigation hints are displayed,
  * *Then* the overlay renders at `Alignment.TopCenter` of the root tracking container.
* **Criterion 3**:
  * *Given* any tracking tab,
  * *When* navigation prompts appear or dismiss,
  * *Then* neither the sensor grid nor the map view experience layout displacement or resizing.

### 1.4 System Invariants
- Zero telemetry displacement: HUD popups float as ambient overlays and never resize underlying columns or canvases.
- Isolated recomposition barrier: `@Immutable TrackingMapState` prevents map rerendering on unrelated state emissions.
- Clean-room test pass rate: 100% across the complete test suite.

---

## 2. Test Specification (TST-UI-308)

### Test Case 1: Root Container Overlay Placement Contract Test (`TST-UI-308.1`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source code loaded.
* **Action**: Parse Compose hierarchy structure in `SensorGridScreen.kt`.
* **Expected Result**:
  - `SensorGridScreen.kt` renders `NavigationHintsOverlay` anchored at `Alignment.TopCenter` of the root tracking `Box` container.
  - Root `NavigationHintsOverlay` is conditioned solely on `state.showNavigationHints` (independent of `state.mapState.showMap`).

### Test Case 2: Map Container Decoupling Contract Test (`TST-UI-308.2`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source code loaded.
* **Action**: Inspect map container `Box` (`if (state.mapState.showMap) { ... }`).
* **Expected Result**:
  - The map container `Box` does NOT contain `NavigationHintsOverlay` and does not define a nested navigation overlay.

### Test Case 3: Navigation Hints Encapsulation Verification (`TST-UI-308.3`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source code loaded.
* **Action**: Inspect `NavigationHintsOverlay` composable body.
* **Expected Result**:
  - Encapsulates `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-UI-308.4`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching specifiers across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries (no new UI strings introduced).

### Test Case 5: Clean-Room Full Suite Regression (`TST-UI-308.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-308.1` | Contract | `testSensorGridScreen_navigationOverlay_anchorsAtRootTopCenter` | `REQ-UI-348` | Specified |
| `TST-UI-308.2` | Contract | `testSensorGridScreen_mapContainer_doesNotHostNavigationOverlay` | `REQ-UI-348` | Specified |
| `TST-UI-308.3` | Contract | `testSensorGridScreen_definesNavigationHintsOverlay_encapsulatingAllNavigationAids` | `REQ-UI-348` | Specified |
| `TST-UI-308.4` | Localization | `TranslationParityTest` | `REQ-UI-348`, `REQ-UI-106` | Specified |
| `TST-UI-308.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
