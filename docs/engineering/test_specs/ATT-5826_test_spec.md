# Stage 2: Requirement & Test Specification - ATT-5826: Fix horizontal tab swipe from 'Map & Graphs' tab

**Ticket**: [ATT-5826](https://rainerblind.atlassian.net/browse/ATT-5826)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10`  
**Requirement Mapping**: `REQ-UI-464` (*WorkoutDetailTabbedContent Horizontal Tab Swipe & Gesture Arbitration*)  
**Test Spec ID**: `TST-UI-464`  
**Branch**: `feature/ATT-5826`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-UI-464)

### 1.1 Problem Statement & Rationale
Athletes on the "Map & Graphs" tab (`tab_map_graphs`) cannot horizontally swipe between tabs because `MapDetailLayout` is wrapped in a blanket touch interceptor, `lastTouchXDp` resets during `MOVE`, and `HorizontalPager` suffers from 1-frame recomposition lag. The system SHALL arbitrate horizontal touch gestures to enable tab swiping from edge gutters (32 dp), dividers, toolbars, headers, and margins while strictly preserving map pan and chart scrubbing within active plot bounds.

### 1.2 Functional & Architectural Requirements
1. **Edge Gutter Lock (`REQ-UI-464.1`)**: Touch X SHALL be latched on initial `ACTION_DOWN` (`awaitFirstDown`). Touches starting within `32.dp` of screen edges SHALL lock `userScrollEnabled = true` until touch release.
2. **Granular Canvas Bounding (`REQ-UI-464.2`)**: Blanket touch interceptors on `MapDetailLayout` SHALL be excised; `isTouchingInteractiveCanvas = true` SHALL apply strictly to the map viewport and active chart plot area (`50.dp .. width - 52.dp`).
3. **Non-Interactive Strip Pass-Through (`REQ-UI-464.3`)**: Touching `SplitPaneDivider`, `GlobalTelemetryZoomToolbar`, chart title headers, spacers, Y-axis (`0..50.dp`), or histogram margin (`52.dp`) SHALL set `isTouchingInteractiveCanvas = false` (enabling tab swipe).
4. **Zero 1-Frame Lag (`REQ-UI-464.4`)**: Gesture arbitration SHALL evaluate synchronously on `ACTION_DOWN` to prevent dropped pager gestures.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Edge Swipe)**: *Given* the athlete is on `tab_map_graphs`, *When* swiping horizontally starting within 32 dp of screen edge, *Then* the pager smoothly switches tabs.
* **Criterion 2 (Non-Interactive Strips)**: *Given* touch on divider, toolbar, header, or axis margins, *When* swiping horizontally, *Then* tab switches smoothly.
* **Criterion 3 (Scrubbing Isolation)**: *Given* touch inside active map canvas or chart plot area away from edge gutters, *When* dragging horizontally, *Then* map panning / telemetry scrubbing executes without page switching.

### 1.4 System Invariants
Map 2D panning, chart scrubbing within plot bounds, vertical scrolling, and tab persistence schemas MUST NOT be degraded.

---

## 2. Test Specification (TST-UI-464)

### Test Case 1: Edge Gutter Initial Down Lock (`TST-UI-464.1`)
* **Scope**: Unit & Contract Test | **Target File**: `app/src/test/.../ui/aftermath/WorkoutDetailTabsTest.kt`
* **Preconditions**: `WorkoutDetailTabConfig` with default `edgeSwipeGutterDp = 32f`.
* **Action**: Evaluate `requiresHorizontalGestureIsolation` with initial down touch at `x <= 32f` and subsequent move to `x > 32f`.
* **Expected Result**: Returns `false` throughout the gesture; tab swipe remains enabled.

### Test Case 2: Granular Canvas & Strip Pass-Through (`TST-UI-464.2`)
* **Scope**: Contract Test | **Target File**: `app/src/test/.../ui/aftermath/WorkoutDetailTabsTest.kt`
* **Preconditions**: `tab_map_graphs` with active map, elevation, and charts.
* **Action**: Evaluate touch on divider, toolbar, headers, and axis margins (`isTouchingInteractiveCanvas = false`).
* **Expected Result**: Returns `false`; horizontal pager swipe enabled.

### Test Case 3: Active Map & Chart Plot Scrubbing Isolation (`TST-UI-464.3`)
* **Scope**: Contract Test | **Target File**: `app/src/test/.../ui/aftermath/WorkoutDetailTabsTest.kt`
* **Preconditions**: `tab_map_graphs`, touch within active plot bounds (`50.dp .. width - 52.dp`).
* **Action**: Evaluate touch with `isTouchingInteractiveCanvas = true`.
* **Expected Result**: Returns `true`; horizontal pager swipe disabled, isolating scrubbing/pan.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-464.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass across all after-math, map, theme audit, and build contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-464.1` | Unit | `WorkoutDetailTabsTest.testEdgeGutterSwipeLock` | `REQ-UI-464.1` | Specified |
| `TST-UI-464.2` | Contract | `WorkoutDetailTabsTest.testNonInteractiveStripPassThrough` | `REQ-UI-464.2`, `REQ-UI-464.3` | Specified |
| `TST-UI-464.3` | Contract | `WorkoutDetailTabsTest.testActivePlotScrubbingIsolation` | `REQ-UI-464.2` | Specified |
| `TST-UI-464.4` | Regression | Tier 1 Targeted Suite | `REQ-PRO-001` | Specified |
