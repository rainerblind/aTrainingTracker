# Stage 2: Requirement & Test Specification - ATT-52: Landscape Dual-Pane Cockpit & Widescreen Sensor Grid

**Ticket**: [ATT-52](https://rainerblind.atlassian.net/browse/ATT-52)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*Landscape Mode*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-52`  
**Requirement Mapping**: `REQ-TRK-040` (*Landscape Cockpit Dual-Pane Layout, Widescreen Sensor Grid & Sub-Pane Overlay Docking*)  
**Test Spec ID**: `TST-TRK-040`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-TRK-040`)

### 1.1 Problem Statement & Rationale
Cyclists and athletes mounting devices horizontally (aero-bars, stems, smart trainers) experience compressed vertical layouts in portrait-only cockpit designs. In landscape viewports (~360–400dp height), a single vertical `Column` squishes sensor cells, starves map navigation, and allows full-width bottom sheets (`LiveSegmentSheet`, `LiveClimbSheet`) to completely mask telemetry. `REQ-TRK-040` introduces a responsive horizontal architecture: a 50/50 dual-pane split when maps are enabled, full-width widescreen grid when maps are disabled, sub-sheet docking within the map pane, and strict enforcement of the `forcePortrait` display preference.

### 1.2 Functional & Architectural Requirements
1. **Orientation Branching & Preference Enforcement (`REQ-TRK-040.1`)**: The Cockpit SHALL adapt dynamically to `Configuration.ORIENTATION_LANDSCAPE` via `LocalConfiguration.current.orientation`. When *"Hochformat erzwingen"* (`forcePortrait`) is enabled in Display Settings (`SettingsDataStore` / `TrainingApplication.forcePortrait()`), the screen SHALL remain locked in portrait mode (`ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`).
2. **Dual-Pane Map & Telemetry Split Layout (`REQ-TRK-040.2`)**: When `showMap == true` in landscape mode, `SensorGridScreen` SHALL render a horizontal split layout (`Row(Modifier.fillMaxSize())`) allocating equal 50/50 flexible width (`weight(1f)`) between telemetry (left) and map (right) panes. The left pane SHALL display configured sensor fields in their regular rows/columns, bounded within the viewport and scrolling vertically if exceeding available screen height. The right pane SHALL host `TrackingMapContainer` and `ElevationProfile`.
3. **Sub-Pane Sheet Docking (`REQ-TRK-040.3`)**: When a Live Segment (`LiveSegmentSheet`) or ClimbPro (`LiveClimbSheet`) sheet is active in landscape mode with map enabled, the bottom sheet container SHALL dock exclusively within the right (map) pane. Crucial Invariant: The left telemetry pane (Speed, HR, Power, Cadence) SHALL remain 100% visible, unoccluded, and interactive at all times.
4. **Widescreen Sensor Grid (`REQ-TRK-040.4`)**: When `showMap == false` in landscape mode, the sensor grid SHALL occupy the entire widescreen canvas (`fillMaxSize()`). Grid cells SHALL expand horizontally with proportional padding, rendering scalable high-contrast numerals (`SensorFieldView` view sizes `XSMALL` through `XXXHUGE`) optimized for glanceability.
5. **Configuration Mode Operability & Ambient Overlays (`REQ-TRK-040.5`)**: In `ScreenMode.CONFIGURATION`, sensor field editing, row/col addition, deletion, and pick-and-place reordering SHALL remain fully functional in landscape orientation. Ambient overlays (`TurnPromptBanner`, `ForkDecisionCard`, `ReturnNavigationHud`, `AutoPauseHudBanner`) SHALL remain centered and elevated above the active tracking viewport without displacing pane allocations.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-040` under Epic `ATT-2144` (*Landscape Mode*), complementing `REQ-UI-358` (*Cockpit Viewport Allocation*), `REQ-UI-359` (*Live Segment/Climb Bottom Sheet*), and `REQ-SET-052` (*Display Settings*). Targets `SensorGridScreen.kt`, `SensorFieldView.kt`, `TrackingTabsScreen.kt`, and `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-52`, Sprint `2026-43.9`. Extends previous portrait-only viewport bounds (`f48e24c7`, `ATT-3343`) and full-screen bottom sheet scaffold (`69e4f1a2`, `ATT-3341`).
* **Root Reason for Existing Formulation**: Cockpit layout was originally designed exclusively for vertical portrait viewports. Mounting smartphones on aero handlebars, out-front bike mounts, and indoor smart trainers requires a horizontal layout that avoids compressing sensor cells or occluding telemetry during climb and segment alerts.
* **Preservation of Core Invariants**: 100% byte-for-byte portrait layout fidelity, `forcePortrait` setting enforcement, zero callback suppression (Rule 34), and 100% test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Dual-Pane Layout with Map)**: *Given* an active cockpit with map enabled (`showMap == true`), *When* the device is rotated to landscape orientation, *Then* `SensorGridScreen` splits into a dual-pane horizontal row with sensor fields on the left (50%) and the map on the right (50%).
* **Criterion 2 (Widescreen Pure Sensor Grid)**: *Given* an active cockpit with map disabled (`showMap == false`), *When* the device is rotated to landscape orientation, *Then* the sensor grid spans the full screen width with scalable numerals.
* **Criterion 3 (Unmasked Telemetry During Overlays)**: *Given* an athlete riding in landscape mode with map active, *When* approaching a Live Segment or ClimbPro hill, *Then* the bottom sheet displays exclusively within the right pane, keeping left telemetry values permanently visible.
* **Criterion 4 (Force Portrait Setting Enforcement)**: *Given* *"Hochformat erzwingen"* is toggled ON in Display Settings, *When* the device is physically rotated to landscape, *Then* the screen remains locked in portrait orientation.
* **Criterion 5 (Configuration Mode Compatibility)**: *Given* `SensorGridScreen` in `ScreenMode.CONFIGURATION` in landscape orientation, *When* interacting with sensor tiles, *Then* field editing, row/col additions, and pick-and-place reordering work without error.

---

## 2. Test Specification (`TST-TRK-040`)

### Test Cases
* `TST-TRK-040.1` (**Dual-Pane Split Layout Composition**): Compose UI test asserting that when `orientation == Configuration.ORIENTATION_LANDSCAPE` and `showMap == true`, `SensorGridScreen` renders a horizontal `Row` containing a 50% left telemetry column and a 50% right map column (`TrackingMapContainer`).
* `TST-TRK-040.2` (**Widescreen Sensor Grid Scaling**): Compose UI test asserting that when `orientation == Configuration.ORIENTATION_LANDSCAPE` and `showMap == false`, `SensorGridScreen` allocates the full screen width to sensor cells with scalable `SensorFieldView` typography.
* `TST-TRK-040.3` (**Right-Pane Bottom Sheet Docking Invariant**): Compose UI test asserting that when `LiveSegmentSheet` or `LiveClimbSheet` is displayed in landscape mode, the bottom sheet container is anchored exclusively within the right pane, and left sensor telemetry fields remain 100% visible and unmasked.
* `TST-TRK-040.4` (**Display Setting Force Portrait Enforcement**): Unit test asserting that when `forcePortrait` is enabled in `SettingsDataStore` / `TrainingApplication`, screen orientation requests lock to portrait and ignore configuration rotation.
* `TST-TRK-040.5` (**Configuration Mode Gesture & Action Preservation**): Gesture contract test asserting that in `ScreenMode.CONFIGURATION`, sensor field editing (`onEditField`), deletion (`onDeleteField`), add row/col, and pick-and-place reordering callbacks remain fully dispatched and interactive in landscape mode (Rule 34).
* `TST-TRK-040.6` (**Targeted & Full Regression Suite Execution**): Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` and verify 100% clean-room test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-040.1` | Compose UI | `SensorGridScreen.kt` / `SensorGridScreen` (Landscape + Map) | `REQ-TRK-040.2` | Planned |
| `TST-TRK-040.2` | Compose UI | `SensorGridScreen.kt` / `SensorGridScreen` (Landscape No Map) | `REQ-TRK-040.4` | Planned |
| `TST-TRK-040.3` | Compose UI | `SensorGridScreen.kt` / Sheet Docking Container | `REQ-TRK-040.3` | Planned |
| `TST-TRK-040.4` | Unit / Logic | `MainActivityWithNavigation.kt`, `TrainingApplication.java` | `REQ-TRK-040.1` | Planned |
| `TST-TRK-040.5` | Contract / UI | `SensorGridScreen.kt` / `GridActions` dispatch | `REQ-TRK-040.5` | Planned |
| `TST-TRK-040.6` | Regression | Full Tracking UI Suite (`ui.tracking.*`) | `REQ-TRK-040.1-5` | Planned |

---

## 4. Independent Peer Review & Verification (ATT-3534)
* **Stage 1 Alignment**: Verified 100% alignment with `subagent_pro`'s Stage 1 analysis (`docs/engineering/analysis/ATT-52_analysis.md`). The responsive dual-pane split cleanly isolates telemetry from map sheets, guaranteeing that telemetry metrics are never occluded during high-effort intervals.
* **Invariant & Governance Safety**: No database schema migrations or service changes required. Portrait mode layout is 100% byte-for-byte preserved. Verified zero requirement governance violations via `verify_requirement_governance.py`.
