# Stage 1 Analysis: ATT-3100

## Metadata
* **Ticket ID**: ATT-3100
* **Title**: Anchor navigation hints over top of map view when map is present in tracking tab
* **Parent Epic**: ATT-2564 (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Target Requirement ID**: `REQ-UI-335`
* **Target Test ID**: `TST-UI-295`
* **Status**: In Progress

---

## 1. Problem Statement & User Observation
During on-device testing and the Sprint 2026-41.7 Joint Review (evaluating `ATT-2941`), navigation prompts (`TurnPromptBanner`, `ReturnNavigationHud`, `ForkDecisionCard`) were refactored into a top-center ambient overlay (`Alignment.TopCenter` within the root tracking layout `Box`).

While this eliminated in-flow telemetry card displacement during turn cues on pure sensor grid screens, on tracking tabs configured with both telemetry sensor tiles and an embedded map view (e.g. Map + Sensor Grid), the navigation prompts overlay the topmost sensor fields (typically primary metrics like Speed, Heart Rate, or Power). 

The user observed that contextually, visually, and ergonomically:
1. When a tab contains an embedded map view, navigation cues should anchor directly on top of the map view itself rather than occluding the upper sensor tiles. The map view provides the spatial context for navigation, and anchoring turn cues over the top edge of the map preserves immediate visibility of all sensor telemetry.
2. When a tab does not contain a map component (e.g. pure sensor grid tabs), the navigation hints should remain anchored at the very top of the tab (`Alignment.TopCenter`).
3. The layout must strictly preserve the zero-telemetry-displacement invariant—neither the sensor grid nor the map should shift or resize when cues appear, transition, or dismiss.

---

## 2. Chesterton's Fence Forensics & Root Cause Analysis
### 2.1 Historical Precedent
* **ATT-2874 (`REQ-MAP-031`)**: Originally introduced `TurnPromptBanner` inside `SensorGridScreen.kt` as an in-flow component at the top of the main `Column`.
* **ATT-2941 (`REQ-UI-324`)**: Moved `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` to float inside the top-level spatial `Box` at `Modifier.align(Alignment.TopCenter).fillMaxWidth()`. This resolved in-flow sensor jitter/displacement, but universally anchored the cues at screen $y=0$ (the top of the sensor grid).

### 2.2 Root Cause Analysis
In `SensorGridScreen.kt` (lines 472–576):
```kotlin
Box(modifier = Modifier.fillMaxSize().padding(...)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Scrollable Sensor Grid
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(...)) { ... }

        // 2. Map Container
        TrackingMapContainer(
            mapState = state.mapState,
            currentLocationFlow = currentLocationFlow,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        // 3. Elevation Profile
        ...
    }

    // Top-Level Ambient Navigation Overlays
    if (state.showNavigationHints) {
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            TurnPromptBanner(...)
            ReturnNavigationHud(...)
            ForkDecisionCard(...)
        }
    }
}
```
* The navigation overlay `Column` is anchored to the outer `Box` at `Alignment.TopCenter` unconditionally.
* `TrackingMapContainer` is a weighted child (`weight(1f)`) within the inner `Column`, placed directly underneath the sensor grid.
* When `state.mapState.showMap == true`, the map starts below the sensor grid, but the navigation cues render at $y=0$ in the root coordinate system, covering the top row of sensors.

---

## 3. Scope Definition & Boundary Conditions
### 3.1 In-Scope
1. **Dynamic Map-Aware Navigation Overlay Placement**:
   - When `state.mapState.showMap == true`: Host the navigation overlay within a dedicated overlay container anchored at the top edge of the map viewport (`Alignment.TopCenter` of the map container). The sensor grid above remains 100% visible and unoccluded.
   - When `state.mapState.showMap == false`: Anchor the navigation overlay at the top of the tab (`Alignment.TopCenter` of the root tracking `Box`), identical to the current behavior.
2. **Unified Navigation Overlay Composable**:
   - Extract a cohesive, reusable `NavigationHintsOverlay` composable encapsulating `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` along with their gating logic (`showNavigationHints`, `tuningConfig.turnPromptsEnabled`, etc.).
3. **Preservation of Invariants**:
   - Zero telemetry displacement: Overlay rendering must float over map or screen content without causing re-layout or resizing of sensor fields or map containers.
   - Recomposition barrier isolation: Map recomposition isolation (`@Immutable TrackingMapState`) must not be invalidated.

### 3.2 Out-of-Scope
- Modifications to `TurnPromptBanner`, `ReturnNavigationHud`, or `ForkDecisionCard` internal design tokens or layout.
- Modifications to route calculation, turn detection algorithms, or background navigation services.
- Map camera zoom or tilt physics.

---

## 4. Architectural Impact & Integration Points
* **`SensorGridScreen.kt`**:
  - Wrap `TrackingMapContainer` inside a `Box(modifier = Modifier.fillMaxWidth().weight(1f))` when `state.mapState.showMap == true`.
  - When `state.mapState.showMap == true`, render `NavigationHintsOverlay` inside the map `Box` with `Modifier.align(Alignment.TopCenter).fillMaxWidth()`.
  - When `state.mapState.showMap == false`, render `NavigationHintsOverlay` in the root `Box` with `Modifier.align(Alignment.TopCenter).fillMaxWidth()`.
* **`SensorGridScreenRouteIntegrationTest.kt`**:
  - Verify that when map is enabled, navigation hints are rendered over the map container rather than the top-level sensor grid.
  - Verify that when map is disabled, navigation hints float at the top of the screen.

---

## 5. Risk Assessment & Invariant Safeguards
* **Risk 1: Double-rendering of navigation cues**: If the conditional branch does not mutually exclude map and no-map placement, hints could appear twice.
  - *Safeguard*: Use mutually exclusive conditional logic: `if (state.mapState.showMap) { ... in map box ... }` and `if (!state.mapState.showMap) { ... in root box ... }`.
* **Risk 2: Clipped cards or overflow on compact map views**: If the map height is very small, large cues (like fork decision cards) could extend beyond map boundaries.
  - *Safeguard*: Compose overlays within the map's bounds with standard padding; cards retain intrinsic sizing and transparent backdrop.
* **Risk 3: Regression in existing contract tests**:
  - *Safeguard*: Update `SensorGridScreenRouteIntegrationTest.kt` assertions to accommodate the map-aware container structure while maintaining strict checks that cues are never in the in-flow sensor grid.

---

## 6. Acceptance Criteria (Given-When-Then)
1. **Map Present**:
   - *Given* an active tracking tab configured with `showMap == true` and sensor fields,
   - *When* a turn prompt, return HUD, or fork decision is active,
   - *Then* the navigation overlay renders anchored directly at the top of the map view beneath the sensor grid, leaving all sensor grid tiles completely unobstructed.
2. **Map Absent (Pure Sensor Grid)**:
   - *Given* an active tracking tab configured with `showMap == false`,
   - *When* a turn prompt, return HUD, or fork decision is active,
   - *Then* the navigation overlay renders anchored at the top of the screen (`Alignment.TopCenter`).
3. **Zero Displacement**:
   - *Given* any tracking tab (with or without map),
   - *When* a navigation cue appears, updates, or dismisses,
   - *Then* neither sensor grid cells nor the map view experience layout shifts, resizing, or jumping.
