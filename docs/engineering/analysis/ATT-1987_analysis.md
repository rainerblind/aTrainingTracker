# Stage 1 Analysis: ATT-1987 - [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs

**Ticket**: [ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)  
**Sub-task**: [ATT-1990](https://rainerblind.atlassian.net/browse/ATT-1990) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Branch**: `feature/ATT-1987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

During the physical Google Pixel 10 review of ticket ATT-1956 in Sprint 2026-40.9, the human user confirmed that the pan gesture in Pan Mode is wired and operational across Speed, Heart Rate, and Power graphs. However, an acute physical UX defect was noted:
> *"When I touch, I can move it a little bit but not more."*

The visible chart viewport only shifts by a minuscule fraction on the initial touch drag and then completely refuses to move further during the remainder of the swipe, making graph navigation feel broken, restricted, and sluggish.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic investigation of `TelemetryMetricGraph.kt` and `ElevationProfile.kt` revealed two distinct, compounding root causes:

### Root Cause 1: Jetpack Compose `pointerInput` Coroutine Cancellation on State Mutation (Primary Blocker)
In `TelemetryMetricGraph.kt` (lines 407–411):
```kotlin
Canvas(
    modifier = Modifier
        .fillMaxWidth()
        .height(110.dp)
        .pointerInput(totalSpan, isTimeDomain, zoomScale, startDist, isPanMode) { ... }
)
```
And identically in `ElevationProfile.kt` (lines 405–407):
```kotlin
baseCanvasModifier.pointerInput(totalSpan, isTimeDomain, isPanMode, currentZoomScale, currentStartDist) { ... }
```
Notice that `startDist` / `currentStartDist` is declared directly in the **keys of `pointerInput`**.

1. When the athlete touches the screen and drags horizontally:
   - On the very first move event (e.g. $\Delta x = 5\text{px}$), `applyPan` computes `panStart`.
   - `onZoomChanged(zoomScale, panStart)` is invoked, updating `profileStartDist = panStart` in the parent `MapDetailLayout`.
2. This state change recomposes the parent layout, passing the updated `startDist` into `TelemetryMetricGraph` and `ElevationProfile`.
3. In Jetpack Compose, **any change to a key of `Modifier.pointerInput` immediately cancels the running gesture coroutine and restarts the pointerInput block**.
4. Upon restart, `awaitEachGesture` calls `awaitFirstDown(requireUnconsumed = false)`.
5. Under Jetpack Compose gesture mechanics, `awaitFirstDown` **only resolves when a pointer transitions from unpressed to pressed**. Because the athlete's finger is *already down* and continuing to drag, `awaitFirstDown` suspends indefinitely waiting for a new touch down.
6. Consequently, **every subsequent touch event in the entire swipe motion is discarded**. The user experiences a tiny 1-frame movement followed by a total freeze until they lift their finger and touch down again.

### Root Cause 2: Drag Distance to Viewport Scaling Ratio
In `ElevationProfileZoomMath.kt` (lines 46–56):
$$\text{distDelta} = \left(\frac{\text{panDeltaX}}{\text{canvasWidth}}\right) \times \text{visibleDist}$$
While mathematically 1:1 in normalized coordinates, physical mobile touch interaction across high-DPI displays (such as the Pixel 10) benefits from direct finger-following or a calibrated panning multiplier ($1.0\times$–$1.5\times$) so that dragging the canvas feels tactile and responsive rather than heavy or damped.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Eliminate `startDist` and `currentStartDist` (and mutable zoom state) from `pointerInput` parameter keys in both `TelemetryMetricGraph.kt` and `ElevationProfile.kt`.
  2. Implement `rememberUpdatedState` for dynamically changing parameters (`startDist`, `zoomScale`, `onZoomChanged`) and track running start distance within the gesture loop without triggering coroutine cancellations.
  3. Ensure continuous, fluid 60fps/120fps horizontal pan tracking across the entire swipe gesture on all charts (`TelemetryMetricGraph` and `ElevationProfile`).
  4. Ensure strict window clamping within $[0.0, \text{totalDist} - \text{visibleDist}]$ via `ElevationProfileZoomMath.applyPan`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying the underlying database, `samplesTable`, or `TrackPoint` storage.
  2. Altering Scrub Mode behavior (scrubbing continues to track absolute touch position when Pan Mode is disabled).
  3. Modifying two-finger pinch-to-zoom math or reset toolbar actions.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*).
* **Historical Origin & Commit Trace**: Commit `91611fe1` (ATT-1956, Sprint 2026-40.9).
* **Root Reason for Existing Formulation**: In ATT-1956, `startDist` and `zoomScale` were included in `pointerInput` keys under the mistaken assumption that `pointerInput` needed to be refreshed whenever external zoom parameters changed. However, because panning modifies `startDist` continuously during the gesture, keying on `startDist` causes fatal coroutine cancellation mid-gesture.
* **Preservation of Core Invariants**:
  - Horizontal plot paddings (50.dp start / 25.dp end) remain unaltered.
  - Single-finger vertical scroll gesture disambiguation (`REQ-UI-226`) remains 100% functional.
  - Multi-chart lockstep synchronization between `ElevationProfile`, `TelemetryMetricGraph`, and the map route marker remains strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Decouple `pointerInput` from Continuous Drag State**:
   - In `TelemetryMetricGraph.kt`, key `pointerInput` only on stable structural parameters:
     ```kotlin
     .pointerInput(totalSpan, isTimeDomain, isPanMode)
     ```
   - Use `rememberUpdatedState` for `startDist`, `zoomScale`, and `onZoomChanged`.
2. **Local Running Position Tracking in Gesture Loop**:
   - At `down`, initialize `var localStartDist = currentStartDistState`.
   - On each drag delta, compute:
     ```kotlin
     val panStart = ElevationProfileZoomMath.applyPan(
         currentStartDist = localStartDist,
         visibleDist = visibleSpan,
         panDeltaX = dragDeltaX,
         canvasWidth = chartWidthPx,
         totalDist = totalSpan
     )
     localStartDist = panStart
     onZoomChangedState?.invoke(zoomScaleState, panStart)
     ```
   - This allows `onZoomChanged` to broadcast to parent state while the local gesture loop continues smoothly without coroutine cancellation.
3. **Mirror Pattern to `ElevationProfile.kt`**:
   - Apply the identical key decoupling and `rememberUpdatedState` pattern to `ElevationProfile.kt` to ensure two-finger and single-finger pans on the elevation profile also benefit from cancellation-free continuous execution.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Lockstep multi-chart synchronization across all stacked graphs.
  2. Single-finger vertical scroll passthrough (`REQ-UI-226`).
  3. Scrub Mode isolation: when `isPanMode == false`, scrubbing remains continuous.
  4. 100% pass rate on all targeted unit tests and clean-room full suite regression.
  5. Parent ticket Human Decision Gate strictly preserved (`Final Review (Human)`).
* **Risk Rating**: **LOW**. The changes are strictly localized to Compose gesture detection and state reference isolation in the presentation layer.
