# Stage 3: Implementation Plan - ATT-3170: Revert ATT-3100: Restore top-of-tab anchoring for navigation hints in tracking view

**Ticket**: [ATT-3170](https://atrainingtracker.atlassian.net/browse/ATT-3170)  
**Sub-task**: [ATT-3247](https://atrainingtracker.atlassian.net/browse/ATT-3247) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-UI-348`  
**Test Mapping**: `TST-UI-308`  
**Branch**: `feature/ATT-3170`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

In `ATT-3100` (Sprint 2026-41.8), navigation prompt overlays (`TurnPromptBanner`, `ReturnNavigationHud`, `ForkDecisionCard`) were moved into the embedded map container `Box` when `state.mapState.showMap == true` to prevent turn cues from occluding the topmost sensor grid tiles.
On-device evaluation during the Sprint 2026-41.8 Joint Review demonstrated that having navigation prompts split the middle of the screen above the map canvas compromised at-a-glance readability while cycling, caused cognitive inconsistency when switching between tracking tabs, and obstructed the upcoming route on the map.
The athlete explicitly requested reverting `ATT-3100` and restoring unified top-of-tab anchoring across all tracking tabs (both with and without maps).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-348` (*Restoration of Unified Top-of-Tab Navigation Hints Overlay Anchoring Across Tracking Tabs*)
* **Test Mapping**: `TST-UI-308` (*Unified Top-of-Tab Navigation Hints Overlay Anchoring Contract & Reversion Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Telemetry Displacement**: Navigation cues float as an ambient overlay (`Alignment.TopCenter`) without altering sensor field dimensions, elevation profile height, or map canvas boundaries.
2. **Recomposition Barrier Isolation**: `@Immutable TrackingMapState` and `TrackingMapContainer` remain isolated from unrelated HUD recompositions.
3. **Encapsulation Integrity**: `NavigationHintsOverlay` cleanly encapsulates `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`.
4. **Cue Transparency & Tuning**: `tuningConfig.navigationCueTransparency` and dismissal interactions are 100% preserved.
5. **Clean-Room Regression**: 100% full-suite test pass rate.

---

## 4. Proposed Architectural Changes

### Component 1: `SensorGridScreen.kt`
- Remove the nested `NavigationHintsOverlay` call from inside `if (state.mapState.showMap) { Box { ... } }`.
- In the root tracking `Box` (which encloses the base `Column` and overlays), render `NavigationHintsOverlay` once, guarded solely by `if (state.showNavigationHints)`:
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
- Remove the now redundant fallback `if (!state.mapState.showMap && state.showNavigationHints)` block at the bottom of the root `Box`.

### Component 2: `SensorGridScreenRouteIntegrationTest.kt`
- Update contract tests:
  1. `testSensorGridScreen_navigationOverlay_anchorsAtRootTopCenter`: Asserts that `SensorGridScreen.kt` anchors `NavigationHintsOverlay` at `Alignment.TopCenter` within the root tracking `Box`, conditioned only on `state.showNavigationHints`.
  2. `testSensorGridScreen_mapContainer_doesNotHostNavigationOverlay`: Asserts that the map container `Box` (`if (state.mapState.showMap)`) does not contain `NavigationHintsOverlay`.
  3. `testSensorGridScreen_definesNavigationHintsOverlay_encapsulatingAllNavigationAids`: Asserts `NavigationHintsOverlay` continues to encapsulate `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`.

### UI Consistency (Rule 23)
* **Reference screen / component**: Live tracking cockpit (`SensorGridScreen.kt`), top overlay model established in `ATT-2941` (`REQ-UI-324`).
* **Reused components**: `NavigationHintsOverlay`, `TurnPromptBanner`, `ReturnNavigationHud`, `ForkDecisionCard`.
* **Theme tokens**: Standard surface and alpha tokens via `tuningConfig.navigationCueTransparency`, `Alignment.TopCenter` overlay positioning.
* **New one-off styles & justification**: None. Reverts map-relative displacement back to the established top-of-tab overlay standard.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Revert Map-Relative Anchoring in `SensorGridScreen.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  1. Remove `NavigationHintsOverlay` from inside the `if (state.mapState.showMap)` `Box`.
  2. Replace `if (!state.mapState.showMap && state.showNavigationHints)` with `if (state.showNavigationHints)` at the root tracking `Box` level.
  3. Clean up comments referencing `ATT-3100` and document restoration of top-of-tab overlay per `ATT-3170` / `REQ-UI-348`.

### Step 2: Update Architectural Contract Tests in `SensorGridScreenRouteIntegrationTest.kt`
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt`
* **Changes**:
  1. Replace `testSensorGridScreen_navigationOverlay_anchorsInsideMapContainerWhenMapPresent` with `testSensorGridScreen_mapContainer_doesNotHostNavigationOverlay`.
  2. Update `testSensorGridScreen_navigationOverlay_anchorsAtRootWhenMapAbsent` to `testSensorGridScreen_navigationOverlay_anchorsAtRootTopCenter`, verifying root positioning invariant to map presence.
  3. Keep `testSensorGridScreen_definesNavigationHintsOverlay_encapsulatingAllNavigationAids`.

### Step 3: Targeted Unit Test Verification
* **Command**: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenRouteIntegrationTest`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted contract test execution during construction, followed by full-suite `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-3170`) allows full clean reset via `git checkout sprint/2026-41.9` without affecting existing commits.
