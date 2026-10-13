# ATT-3100 Walkthrough: Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View

## Overview
- **Issue**: [ATT-3100](https://atrainingtracker.atlassian.net/browse/ATT-3100)
- **Requirement**: `REQ-UI-335`
- **Test Specification**: `TST-UI-295`
- **Sprint**: `2026-41.8`
- **Status**: Verified

## Problem Addressed
When an embedded map view is enabled in the tracking tab (`state.mapState.showMap == true`), navigation hint overlays (`TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`) were previously anchored at `Alignment.TopCenter` of the root tracking `Box`. This caused navigation popups to sit directly over the topmost sensor metric grid tiles (e.g. Heart Rate, Speed, Cadence), obstructing the athlete's primary cockpit metrics while cycling or running.

## Architectural Changes & Solution
1. **Encapsulated `NavigationHintsOverlay` Composable**:
   - Factored out navigation HUD elements (`TurnPromptBanner`, `ReturnNavigationHud`, `ForkDecisionCard`) into a unified reusable private composable:
     ```kotlin
     @Composable
     private fun BoxScope.NavigationHintsOverlay(
         routeNavigationState: RouteNavigationUiState,
         onRouteAction: (RouteNavigationAction) -> Unit,
         onDismissTurnPrompt: () -> Unit,
         modifier: Modifier = Modifier
     )
     ```
2. **Dynamic Context-Aware Placement**:
   - **Map Present (`state.mapState.showMap == true`)**: The `NavigationHintsOverlay` is placed inside the weighted map `Box` (`weight(1f)`), anchored at `Alignment.TopCenter`. It overlays the top edge of the map canvas directly underneath the sensor metrics grid. The athlete's sensor metrics remain 100% visible and unobstructed at all times.
   - **Map Absent (`state.mapState.showMap == false`)**: The `NavigationHintsOverlay` remains anchored at `Alignment.TopCenter` of the root `Box`, preserving the existing non-map tracking behavior.
3. **Mutual Exclusion**:
   - The overlay is conditionally rendered in either the map container or the root container, never duplicating components or event handlers.
4. **Zero Metrics Displacement**:
   - By rendering as an overlay directly over the map rather than expanding or shrinking column layouts, zero layout shifts or metrics displacement occur during navigation prompt appearances/dismissals.

## Verification & Test Results
- **Unit & Contract Tests**:
  - `SensorGridScreenRouteIntegrationTest.kt` passes with 100% assertions satisfied:
    - `testMapPresent_anchorsNavigationOverlayInsideMapContainerBelowSensorGrid`
    - `testMapAbsent_anchorsNavigationOverlayAtRootTopCenter`
    - `testNavigationHintsOverlay_mutuallyExclusiveBetweenMapAndRootBranches`
- **Clean-Room Full Suite Regression**:
  - `./gradlew testDebugUnitTest` executed with 100% pass rate across all modules (zero failures, zero regressions).

## ASPICE Traceability
- **Requirement**: `REQ-UI-335` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-UI-295` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3100_plan.md`
