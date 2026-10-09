# Stage 1: Problem Domain & Root Cause Analysis - ATT-2874: Float fork decision prompt as top-level overlay and gate by tab navigation hints toggle

**Ticket**: [ATT-2874](https://rainerblind.atlassian.net/browse/ATT-2874)  
**Sub-task**: [ATT-2926](https://rainerblind.atlassian.net/browse/ATT-2926) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Branch**: `improvement/ATT-2874`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Symptoms

In `SensorGridScreen.kt` (lines 478–487), `ForkDecisionCard` (introduced in ATT-1955 for `REQ-MAP-031`) is placed directly inside the main `Column` immediately above the scrollable sensor grid:
```kotlin
// In-Ride Fork-in-the-Road Route Selection & Decision Alerts (REQ-MAP-031 / ATT-1955)
ForkDecisionCard(
    decisionState = forkDecisionState,
    overlayAlpha = tuningConfig.navigationCueTransparency,
    onRouteSelected = { routeId ->
        forkNavRepo.selectRouteManually(routeId)
    },
    onDismiss = {
        forkNavRepo.dismissPrompt()
    }
)

// 1. The Sensor Grid (Scrollable)
Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    ...
)
```

This arrangement introduces two major architectural and usability defects during active workout tracking:

1. **Displacing Cockpit Telemetry Tiles**: Because `ForkDecisionCard` is an in-flow child of the vertical `Column`, whenever an athlete approaches a fork in the road ($D_{\text{fork}} \le 300\text{ m}$), the card expands dynamically, shoving down the entire sensor grid by ~120–150 dp. Athletes looking at critical real-time telemetry (speed, heart rate, cadence, power) experience abrupt layout jumping mid-ride. This directly violates *Design Guidelines Section 5.7* (*"In-ride navigation hints, turn-by-turn cues, and fork-in-the-road decision prompts must float directly on top of active tracking screen elements rather than displacing or squeezing the cockpit tile layout"*).
2. **Missing Per-Tab Navigation Gating**: Unlike `TurnPromptBanner` (which respects `promptsEnabled = state.showNavigationHints && tuningConfig.turnPromptsEnabled`), `ForkDecisionCard` is rendered unconditionally. On tabs configured with `showNavigationHints = false` (e.g., pure sensor cockpit tabs or interval training screens), the fork decision prompt inappropriately appears and interrupts the rider.

---

## 2. Root Cause Analysis

1. **In-Flow Layout Linear Placement**: In the initial implementation of ATT-1955, `ForkDecisionCard` was added as a sequential composable within the root `Column` of tracking mode. Because Compose `Column` lays out its children one after another along the Y-axis, any change in `ForkDecisionCard`'s measured height directly offsets the Y-coordinate of all subsequent children (the sensor grid and map).
2. **Omission of `state.showNavigationHints` Check**: While turn-by-turn prompts (`TurnPromptBanner`) were wired to `state.showNavigationHints`, fork detection was treated as an ambient alert without verifying the tab-level navigation hints configuration.

---

## 3. Scope Bounding & Chesterton's Fence Archaeology

1. **Original Requirement ID & Target**: `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clause 3).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1955`, Sprint `2026-40.16`.
3. **Root Reason for Existing Formulation**: Quick linear composition without dedicated top-level spatial layering or tab gating check.
4. **Preservation of Core Invariants**:
   * **Telemetry Stationarity**: Sensor tiles must remain strictly stationary at all times without jumping when fork alerts appear or vanish.
   * **Touch Event Pass-Through & Hit Testing**:
     - When `state.showNavigationHints == false` or `forkDecisionState == null`, the overlay wrapper must not consume touches or create an invisible touch barrier; gestures (vertical sensor grid scroll, tile click for edit/reorder) must pass freely to the underlying cockpit.
     - When `ForkDecisionCard` is visible, only the physical card bounds intercept clicks (for route selection button or dismiss button), while the rest of the screen remains interactive.
   * **Semi-Transparency**: Semi-transparent surface background (`tuningConfig.navigationCueTransparency`) per Section 5.7 must be maintained.
   * **Dismissal and Route Selection**: Interactive tap-to-select and dismiss operations via `forkNavRepo` must function identically.
   * **Unchanged Tab Toggle Settings**: Athletes must still be able to toggle `showNavigationHints` per tab in Tracking Configuration mode.

---

## 4. Proposed Solution Architecture

### 4.1 Top-Level Spatial Box Layering in `SensorGridScreen.kt`
In `SensorGridScreen.kt` (under `TRACKING & PREVIEW MODES`), replace the direct `Column` root with a parent `Box`:
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .padding(top = paddingValues.calculateTopPadding())
) {
    // 1. Stationary Main Tracking Content (Base Layer)
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TurnPromptBanner(...)
        ReturnNavigationHud(...)

        // 1. The Sensor Grid (Scrollable, 100% stationary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            ...
        ) { ... }

        // 2. The Map (Expanded)
        if (state.showMap) { ... }

        // 3. The Elevation Profile (Below the Map)
        if (state.showElevationProfile && state.pathPoints.isNotEmpty()) { ... }
    }

    // 2. Floating Fork Decision Overlay (Top Layer)
    // Anchored at TopCenter, strictly gated by state.showNavigationHints
    if (state.showNavigationHints) {
        ForkDecisionCard(
            decisionState = forkDecisionState,
            overlayAlpha = tuningConfig.navigationCueTransparency,
            onRouteSelected = { routeId ->
                forkNavRepo.selectRouteManually(routeId)
            },
            onDismiss = {
                forkNavRepo.dismissPrompt()
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
```

### 4.2 Z-Index & Hit-Testing Behavior
- Because the base `Column` is composed first, it occupies the lower z-order.
- `ForkDecisionCard` uses `AnimatedVisibility` which wraps its content in `wrapContentSize()` without consuming full-screen bounds when collapsed or dismissed (`decisionState == null`).
- When `state.showNavigationHints == false`, `ForkDecisionCard` is not in the composition tree at all, ensuring 0% touch obstruction.
- When `ForkDecisionCard` appears, it floats over the top region of the sensor grid without shifting the sensor grid coordinates.

### 4.3 Contract & Integration Test Harness
1. **`TrackingTabWysiwygContractTest.kt`**:
   - Assert `ForkDecisionCard` is gated by `state.showNavigationHints` alongside `TurnPromptBanner` and `LiveClimbSheet`.
2. **`SensorGridScreenRouteIntegrationTest.kt`**:
   - Assert `ForkDecisionCard` is anchored with `Alignment.TopCenter` within a floating overlay container.
   - Assert `ForkDecisionCard` is NOT positioned inside the scrollable sensor grid `Column`.
   - Assert `ForkDecisionCard` consumes `tuningConfig.navigationCueTransparency` per Design Guidelines Section 5.7.
