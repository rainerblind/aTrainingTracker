# Stage 2: Requirement & Test Specification - ATT-1987: [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs

**Ticket**: [ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)  
**Sub-task**: [ATT-1991](https://rainerblind.atlassian.net/browse/ATT-1991) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Requirement Mapping**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*)  
**Test Spec ID**: `TST-UI-190`  
**Branch**: `feature/ATT-1987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-232 Refinement)

### 1.1 Problem Statement & Rationale
During physical on-device review of ATT-1956 on Pixel 10 hardware, user testing revealed that while horizontal panning in Pan Mode is wired across all stacked graphs, touching and dragging on the canvas moves the viewport only a tiny fraction on frame 1 and then completely stops moving for the remainder of the swipe (*"When I touch, I can move it a little bit but not more"*). Forensic analysis revealed that `pointerInput` declared mutable running parameters (`startDist`, `currentStartDist`, `zoomScale`) in its keys, causing Jetpack Compose to cancel the gesture coroutine on the very first move event. When restarted mid-swipe, `awaitFirstDown` hangs waiting for a new touch down event, discarding all subsequent move events.

### 1.2 Functional & Architectural Requirements
The system SHALL refine `REQ-UI-232` to guarantee continuous, cancellation-free horizontal panning across all stacked telemetry metric graphs (`TelemetryMetricGraph.kt` for Speed/Pace, Heart Rate, and Power) and `ElevationProfile.kt` in Pan Mode (`isPanMode == true`):

1. **Cancellation-Free PointerInput Key Scoping**:
   - In `TelemetryMetricGraph.kt` and `ElevationProfile.kt`, the canvas `pointerInput` SHALL observe ONLY stable configuration parameters:
     ```kotlin
     .pointerInput(totalSpan, isTimeDomain, isPanMode)
     ```
   - Running mutable parameters (`startDist`, `currentStartDist`, `zoomScale`, `currentZoomScale`) and callbacks (`onZoomChanged`, `updateZoom`) SHALL NOT be declared in `pointerInput` parameter keys, preventing mid-gesture coroutine cancellation.

2. **Dynamic State Ingestion via `rememberUpdatedState`**:
   - Dynamic parameters subject to external mutation (`startDist`, `zoomScale`, `onZoomChanged`) SHALL be captured using Compose `rememberUpdatedState`:
     ```kotlin
     val currentStartDistState by rememberUpdatedState(startDist)
     val currentZoomScaleState by rememberUpdatedState(zoomScale)
     val onZoomChangedState by rememberUpdatedState(onZoomChanged)
     ```

3. **Continuous Running Position Tracking in Gesture Loop**:
   - Inside `awaitEachGesture`, upon detecting `down`:
     ```kotlin
     var localStartDist = currentStartDistState
     ```
   - Upon each dominant horizontal drag event:
     - The handler SHALL consume the pointer event (`pointer.consume()`).
     - The handler SHALL calculate pointer horizontal delta `dragDeltaX = pointer.position.x - prevX`.
     - The handler SHALL invoke `ElevationProfileZoomMath.applyPan(currentStartDist = localStartDist, visibleDist = visibleSpan, panDeltaX = dragDeltaX, canvasWidth = chartWidthPx, totalDist = totalSpan)`.
     - The handler SHALL update `localStartDist = panStart`.
     - The handler SHALL invoke `onZoomChangedState?.invoke(currentZoomScaleState, panStart)`.
     - `prevX` SHALL update to `pointer.position.x`.
   - This ensures continuous, uninterrupted 60fps/120fps pan tracking throughout the entire physical swipe motion.

4. **Preservation of Core Invariants**:
   - Single-finger vertical scroll gesture propagation (`REQ-UI-226`) remains fully unconsumed.
   - Exact horizontal plot margins (`50.dp` start, `25.dp` end) match `ElevationProfile.kt`.
   - Strict window clamping within `[0.0, totalDist - visibleDist]` remains enforced by `applyPan`.
   - When `isPanMode == false`, continuous route scrubbing behavior is strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-232`), extending `REQ-UI-225` (*Persistent Sticky Global Zoom Toolbar*) and `REQ-UI-226` (*Directional Gesture Disambiguation and Smooth Vertical Scrolling in MapDetailLayout*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*). Refined in ATT-1987.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `d71b4028`, ATT-1876), Sprint 2026-40.9 (Commit `91611fe1`, ATT-1956), and Sprint 2026-40.10 (`ATT-1987`).
3. *Root Reason for Existing Formulation*: In ATT-1956, `startDist` and `zoomScale` were included in `pointerInput` keys under the mistaken assumption that `pointerInput` needed to be refreshed whenever external zoom parameters changed. However, because panning modifies `startDist` continuously during the gesture, keying on `startDist` causes fatal coroutine cancellation mid-gesture. Decoupling pointerInput keys and capturing dynamic states via `rememberUpdatedState` restores fluid, uninterrupted panning.
4. *Preservation of Core Invariants*: Single-finger vertical scroll gesture propagation to the parent container (`REQ-UI-226`), synchronized scrubbing cursor parity across all stacked charts when `isPanMode == false`, exact horizontal plot padding alignment (`50.dp` start, `25.dp` end), and 9-language localization parity are 100% strictly preserved.

### Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Continuous Pan Motion Without Mid-Swipe Freezing)**:
  * *Given* an athlete viewing a workout in `MapDetailLayout` with `isPanMode == true`,
  * *When* dragging horizontally across `TelemetryMetricGraph` (Speed/Pace, Heart Rate, or Power) or `ElevationProfile`,
  * *Then* the chart SHALL consume the gesture continuously without coroutine cancellation, calculate `panStart` via `applyPan`, and invoke `onZoomChanged`, smoothly panning the viewport across the entire swipe in lockstep with `ElevationProfile` and the map marker.
* **Criterion 2 (Scrub Mode Preserved)**:
  * *Given* an athlete viewing a workout with `isPanMode == false`,
  * *When* dragging horizontally across `TelemetryMetricGraph`,
  * *Then* the chart SHALL continue to scrub the cursor position via `onDistanceSelected`.
* **Criterion 3 (Vertical Scrolling Propagation)**:
  * *Given* a single-finger vertical swipe over any `TelemetryMetricGraph`,
  * *Then* the parent `lowerColumn` SHALL scroll vertically without hindrance.

---

## 2. Test Specification (TST-UI-190)

### 2.1 Structural & Gesture Contract Tests (`TelemetryMetricGraphGestureTest.kt`)
1. **PointerInput Keys Decoupling Contract**:
   - Assert `TelemetryMetricGraph.kt` does NOT declare `startDist` or `zoomScale` in `pointerInput(...)` keys.
   - Assert `TelemetryMetricGraph.kt` declares only `(totalSpan, isTimeDomain, isPanMode)`.
   - Assert `TelemetryMetricGraph.kt` references dynamic parameters via `rememberUpdatedState`.
   - Assert `ElevationProfile.kt` does NOT declare `currentStartDist` or `currentZoomScale` in `pointerInput(...)` keys.
   - Assert `ElevationProfile.kt` declares only `(totalSpan, isTimeDomain, isPanMode)`.
2. **Local Running Distance Accumulation Contract**:
   - Assert `localStartDist` tracks running start position across drag events without resetting to initial down position.

### 2.2 Clean-Room Full Suite Regression Execution
- Run `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate.

---

## 3. Traceability Matrix

| Requirement | Test Spec | Verification Method | Target Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-232` (PointerInput Decoupling) | `TST-UI-190.1` | Structural Contract Test (`TelemetryMetricGraphGestureTest`) | `Approved` |
| `REQ-UI-232` (Continuous Pan Accumulation) | `TST-UI-190.2` | Unit / Logic Test (`TelemetryMetricGraphGestureTest`) | `Approved` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-190.3` | Clean-Room Full Suite (`./gradlew testDebugUnitTest`) | `Approved` |
