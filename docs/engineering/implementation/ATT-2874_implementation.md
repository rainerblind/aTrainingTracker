# Stage 4: Implementation Report - ATT-2874: Float fork decision prompt as top-level overlay and gate by tab navigation hints toggle

**Ticket**: [ATT-2874](https://rainerblind.atlassian.net/browse/ATT-2874)  
**Sub-task**: [ATT-2929](https://rainerblind.atlassian.net/browse/ATT-2929) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-321` (*Top-Level Spatial Overlay & Tab Navigation Hints Gating for In-Ride Fork Route Decision Prompts*)  
**Test Mapping**: `TST-UI-281` (*Fork Decision Prompt Spatial Overlay & Tab Navigation Hints Gating Verification*)  
**Branch**: `improvement/ATT-2874`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Summary of Changes

1. **`SensorGridScreen.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   * Refactored tracking and preview modes container into a root `Box(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()))`.
   * Maintained the base stationary `Column(modifier = Modifier.fillMaxSize())` hosting turn prompts, return HUD, scrollable sensor grid, expanded map, and elevation profile.
   * Extracted `ForkDecisionCard` from inside the linear sensor grid `Column` and placed it as a floating spatial overlay inside the root `Box` anchored with `modifier = Modifier.align(Alignment.TopCenter)`.
   * Conditionally gated `ForkDecisionCard` strictly behind `if (state.showNavigationHints)`, completely excluding the prompt from composition on navigation-free tabs.
   * Bound `overlayAlpha = tuningConfig.navigationCueTransparency` per Design Guidelines Section 5.7.

2. **`TrackingTabWysiwygContractTest.kt` (`com.atrainingtracker.trainingtracker.ui.tracking`)**:
   * Updated `testSensorGridScreen_enforcesRuntimeGating()` to assert that `ForkDecisionCard` is gated by `state.showNavigationHints`.

3. **`SensorGridScreenRouteIntegrationTest.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   * Added `testSensorGridScreen_forkDecisionCard_floatsAsTopCenterOverlay()` verifying `Alignment.TopCenter` overlay positioning, `state.showNavigationHints` gating, and `tuningConfig.navigationCueTransparency` binding.

---

## 2. Verification & Test Results

* Targeted Unit Tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"
  ```
  Result: **BUILD SUCCESSFUL** (100% tests passed across all tracking UI suites).
