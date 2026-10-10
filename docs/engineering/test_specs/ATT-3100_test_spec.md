# Stage 2: Requirement & Test Specification: ATT-3100

## Metadata
* **Ticket ID**: ATT-3100
* **Title**: Anchor navigation hints over top of map view when map is present in tracking tab
* **Parent Epic**: ATT-2564 (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Requirement ID**: `REQ-UI-335`
* **Test Specification ID**: `TST-UI-295`
* **Status**: Specified

---

## 1. Requirement Specification (REQ-UI-335)

### 1.1 Summary
The system SHALL dynamically position active navigation hints (turn-by-turn prompts, return navigation HUD, and fork decision cards) based on the presence of an embedded map view in the tracking tab:
1. **Map-Present Tracking Tabs**: When a tracking tab is configured with an embedded map component (`state.mapState.showMap == true`), the navigation hints overlay SHALL be anchored directly over the top edge of the map component (`Alignment.TopCenter` of the map container), situated directly below the sensor grid tiles. This guarantees that all sensor grid metrics remain 100% visible and unoccluded during navigation cues.
2. **Pure Sensor Grid Tabs (Map Absent)**: When a tracking tab does not contain an embedded map component (`state.mapState.showMap == false`), the navigation hints overlay SHALL remain anchored at the very top of the tab (`Alignment.TopCenter` of the root tracking layout `Box`).
3. **Zero-Telemetry-Displacement Invariant**: Navigation hint presentation, state transitions, and dismissals SHALL float over the host container (as a non-displacing overlay) without resizing, re-measuring, or shifting sensor grid cells or the map viewport.
4. **Single-Source Navigation Overlay Architecture**: Navigation overlays (`TurnPromptBanner`, `ReturnNavigationHud`, `ForkDecisionCard`) SHALL be unified into a dedicated `NavigationHintsOverlay` composable with mutually exclusive placement gating to prevent duplicate rendering or memory leaks.

### 1.2 Chesterton's Fence Audit
* **Historical Precedent**: `REQ-MAP-031` (ATT-2874) introduced `TurnPromptBanner` in-flow, which caused jitter on sensor fields. `REQ-UI-324` (ATT-2941) addressed jitter by moving all navigation cues to a top-level Box overlay at `Alignment.TopCenter`.
* **Root Reason for Existing Formulation**: In ATT-2941, the focus was eliminating in-flow displacement. In doing so, the cues were unconditionally anchored at screen $y=0$, which occluded the top sensor tiles on hybrid Map + Sensor Grid screens.
* **Preservation of Intent**: The zero-displacement guarantee of ATT-2941 is strictly preserved; the overlay anchor point is simply contextualized to the top of the map view when a map is present.

---

## 2. Test Specification (TST-UI-295)

### 2.1 Test Categories & Verification Scope

| Test ID | Category | Target File | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-UI-295.1** | Structural & Layout Contract | `SensorGridScreenRouteIntegrationTest.kt` | Verify that when `state.mapState.showMap == true`, navigation overlay is hosted inside the weighted map `Box` with `Alignment.TopCenter`. |
| **TST-UI-295.2** | Fallback Root Overlay Contract | `SensorGridScreenRouteIntegrationTest.kt` | Verify that when `state.mapState.showMap == false`, navigation overlay is hosted at the top of the root `Box` with `Alignment.TopCenter`. |
| **TST-UI-295.3** | Component Encapsulation | `SensorGridScreenRouteIntegrationTest.kt` | Verify `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` are unified within `NavigationHintsOverlay` and not duplicated in layout hierarchy. |
| **TST-UI-295.4** | Clean-Room Full Suite Regression | Full Unit Test Suite | Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions. |

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Tab with Map and Active Turn Cue
* **Given** an athlete tracking an activity on a tab where `state.mapState.showMap == true` and 4 sensor fields are displayed above the map,
* **When** an approaching turn cue or navigation state is emitted (`navState.currentCue != null`),
* **Then** the turn prompt banner appears anchored directly over the top of the map view beneath the sensor grid,
* **And** all 4 sensor fields remain completely unobstructed and readable.

### Scenario 2: Pure Sensor Grid Tab (Map Disabled)
* **Given** an athlete tracking an activity on a tab where `state.mapState.showMap == false` (e.g., full-screen sensor grid),
* **When** a navigation cue or return HUD is active,
* **Then** the navigation overlay appears floating at the very top of the tab (`Alignment.TopCenter` of the root container).

### Scenario 3: Non-Displacing Overlay Behavior
* **Given** any tracking tab actively navigating a route,
* **When** a turn cue transitions from approaching to "TURN NOW" or is dismissed,
* **Then** neither the sensor grid rows nor the map canvas change height, re-layout, or experience displacement.

---

## 4. ASPICE Traceability Matrix
* **Parent Epic**: ATT-2564
* **User Story**: ATT-3100
* **System Requirement**: `REQ-UI-335`
* **Test Specification**: `TST-UI-295`
* **Implementation Artifacts**: `SensorGridScreen.kt`, `SensorGridScreenRouteIntegrationTest.kt`
