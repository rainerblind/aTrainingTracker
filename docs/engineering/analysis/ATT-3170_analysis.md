# Stage 1 Analysis: ATT-3170 - Revert ATT-3100: Restore top-of-tab anchoring for navigation hints in tracking view

**Ticket**: [ATT-3170](https://atrainingtracker.atlassian.net/browse/ATT-3170)  
**Sub-task**: [ATT-3245](https://atrainingtracker.atlassian.net/browse/ATT-3245) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Branch**: `feature/ATT-3170`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.8 (`ATT-3100`), navigation prompt overlays (`TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`) were moved from the root tracking layout `Box` into the embedded map container `Box` whenever `state.mapState.showMap == true`. The motivation at the time was to prevent turn cues from occluding the topmost sensor grid tiles.

However, during real-world on-device testing and the Sprint 2026-41.8 Joint Review, the athlete evaluated this layout while cycling and observed significant usability drawbacks:
1. **Mid-Screen Cognitive Disruption**: On tabs combining sensor telemetry with a live map, anchoring navigation prompts to the top edge of the map placed alerts directly in the middle of the phone screen (between the sensor grid and the map canvas). When glancing down during high-speed riding, the athlete expects critical navigation cues to always appear in a fixed, predictable location at the top of the viewport.
2. **Inconsistent Spatial Anchor Across Tabs**: Swiping between a map-enabled tracking tab and a pure sensor grid tab shifted navigation cues abruptly between the mid-screen map boundary and the top of the tab container.
3. **Map Canvas Occlusion**: Placing the prompt inside the map container covered the upper portion of the route polyline and upcoming junction context on the map.

The athlete explicitly requested reverting the map-relative positioning logic introduced in `ATT-3100` and restoring the unified top-of-tab anchoring established in `ATT-2941` across all tracking tabs (both with and without maps).

---

## 2. Root Cause Analysis (Forensic Investigation)

In `SensorGridScreen.kt` (lines 523–582, introduced in commit `6fa2b961` for `ATT-3100`), the tracking mode composable branches the placement of `NavigationHintsOverlay`:
```kotlin
// Map Present Branch: Anchored inside map Box below sensor grid
if (state.mapState.showMap) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        TrackingMapContainer(...)
        if (state.showNavigationHints) {
            NavigationHintsOverlay(
                ...,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
...
// Map Absent Fallback Branch: Anchored at root Box TopCenter
if (!state.mapState.showMap && state.showNavigationHints) {
    NavigationHintsOverlay(
        ...,
        modifier = Modifier.align(Alignment.TopCenter)
    )
}
```

This dual-branch layout created:
1. **Unnecessary Layout Coupling**: Navigation hints became tightly coupled to the presence and container hierarchy of `TrackingMapContainer`.
2. **Redundant Conditional Invocations**: The identical composable call `NavigationHintsOverlay(...)` was duplicated across two distinct branch conditions (`state.mapState.showMap` and `!state.mapState.showMap`).
3. **Fragmented Architectural Contracts**: Contract tests in `SensorGridScreenRouteIntegrationTest.kt` (`testSensorGridScreen_navigationOverlay_anchorsInsideMapContainerWhenMapPresent` and `testSensorGridScreen_navigationOverlay_anchorsAtRootWhenMapAbsent`) codified this bifurcated behavior, requiring explicit updating upon reversion.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Revert map-relative positioning of `NavigationHintsOverlay` in `SensorGridScreen.kt`.
  * Restore top-of-tab anchoring (`Alignment.TopCenter` within the root tracking layout `Box`) for all tracking screens, regardless of `state.mapState.showMap`.
  * Clean up duplicated `NavigationHintsOverlay` invocation inside the map container `Box`.
  * Preserve the unified `NavigationHintsOverlay` encapsulation of `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`.
  * Maintain zero-telemetry-displacement invariant and transparency tuning settings (`tuningConfig.navigationCueTransparency`).
  * Update living documentation (`docs/requirements.md`, `docs/tests.md`) and architectural contract tests in `SensorGridScreenRouteIntegrationTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter the visual contents, styling, or dismiss logic of `TurnPromptBanner`, `ReturnNavigationHud`, or `ForkDecisionCard`.
  * Do not change sensor field layout, column weights, or elevation profile sizing (`ATT-3173`).
  * Do not modify map state models, GPS location streams, or route navigation repositories.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-335` (*Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View*, Sprint 2026-41.8, `ATT-3100`), amending `REQ-UI-324` (*Align turn-by-turn navigation hints UI with updated design guidelines and floating top-center overlay*, Sprint 2026-41.6, `ATT-2941`).
* **Historical Origin & Commit Trace**: Introduced in commit `6fa2b961` (`feat(ui): anchor navigation hints over top of map view when map is present (ATT-3100)`), author Rainer Blind, 2026-10-10.
* **Root Reason for Existing Formulation**: `ATT-3100` was implemented to test whether moving turn prompts onto the map canvas would solve the occlusion of the topmost sensor tiles.
* **Reason for Invalidation / Revision**: On-device field evaluation by the athlete proved that having navigation prompts split the cockpit in the middle of the screen above the map canvas compromised at-a-glance readability, caused cognitive inconsistency between tracking tabs, and obstructed the upcoming route on the map. The athlete explicitly mandated reverting to top-of-tab anchoring.
* **Preservation of Core Invariants**:
  * *Zero-Telemetry-Displacement*: Navigation hints remain a non-displacing top-level `Box` overlay that floats over the content without shifting sensor grid rows or resizing the map.
  * *Unified Encapsulation*: `NavigationHintsOverlay` encapsulates turn prompts, return HUD, and fork cards cleanly.
  * *Transparency & Dimming*: Semi-transparent background token and touch-to-dismiss behaviors remain intact.

---

## 5. Architectural Strategy & High-Level Solution

1. **Unify `NavigationHintsOverlay` in Root Tracking `Box` (`SensorGridScreen.kt`)**:
   - In `SensorGridScreen.kt`, remove `NavigationHintsOverlay` from inside the `if (state.mapState.showMap)` `Box`.
   - In the root tracking `Box` (directly sibling to the base `Column`), render `NavigationHintsOverlay` conditionally based solely on `state.showNavigationHints`:
     ```kotlin
     if (state.showNavigationHints) {
         NavigationHintsOverlay(
             state = state,
             tuningConfig = tuningConfig,
             navigationState = navState,
             returnNavigationState = returnNavState,
             forkDecisionState = forkDecisionState,
             onDismissReturnNav = { returnNavRepo.dismissHud() },
             onSelectForkRoute = { routeId -> forkNavRepo.selectRouteManually(routeId) },
             onDismissFork = { forkNavRepo.dismissPrompt() },
             modifier = Modifier.align(Alignment.TopCenter)
         )
     }
     ```
2. **Update Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)**:
   - Replace tests that checked for `NavigationHintsOverlay` inside the map container with tests verifying that:
     1. The map container `Box` does NOT contain `NavigationHintsOverlay`.
     2. The root tracking `Box` hosts `NavigationHintsOverlay` anchored at `Alignment.TopCenter` whenever `state.showNavigationHints` is true, independent of `state.mapState.showMap`.
     3. Encapsulation of `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` is preserved.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Zero Telemetry Displacement*: Cues float as an overlay and do not cause recomposition layout shifts or metric tile displacement.
  2. *Recomposition Isolation*: `@Immutable TrackingMapState` and `TrackingMapContainer` remain isolated.
  3. *Clean-Room Regression*: 100% test pass rate across the full test suite.
* **Risk Rating**: **LOW**
  - The change is a clean reversion of a localized Compose hierarchy placement in `SensorGridScreen.kt`. No business logic, database queries, or background services are modified.
