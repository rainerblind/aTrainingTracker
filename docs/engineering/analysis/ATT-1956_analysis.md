# Stage 1 Analysis: ATT-1956 - [Aftermath/Graphs] Support Pan/Moving Gesture Across Speed, Heart Rate, and Power Graphs in Zoom Toolbar Pan Mode

**Ticket**: [ATT-1956](https://rainerblind.atlassian.net/browse/ATT-1956)  
**Sub-task**: [ATT-1966](https://rainerblind.atlassian.net/browse/ATT-1966) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Branch**: `feature/ATT-1956`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

In sprint ticket ATT-1876 (`REQ-UI-225`), the persistent sticky global zoom toolbar (`GlobalTelemetryZoomToolbar.kt`) was introduced directly between the map viewport and the scrollable telemetry graph container in `MapDetailLayout.kt`. This toolbar added an interactive mode toggle between **Scrub Mode** (`Icons.Default.TouchApp`) and **Pan Mode** (`Icons.Default.PanTool`), managing a hoisted `isPanMode` boolean state.

However, during pan mode, horizontal dragging currently only pans the visible viewport window when the touch gesture occurs directly over the `ElevationProfile` graph. Because zoom controls are now global across all stacked graphs in `MapDetailLayout.kt` (Elevation Profile, Speed/Pace, Heart Rate, and Power), users naturally expect that dragging across *any* of the stacked telemetry metric graphs in Pan Mode will also pan the visible window in lockstep.

Currently, touching and dragging horizontally on `TelemetryMetricGraph` (Speed/Pace, HR, Power) while in Pan Mode continues to scrub the cursor line (`onDistanceSelected`), producing a jarring behavioral inconsistency where touching the top chart pans the window, but touching any chart immediately below it moves the scrub cursor.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic code inspection of `TelemetryMetricGraph.kt` and `MapDetailLayout.kt` reveals the architectural cause:

1. **Missing Parameters in `TelemetryMetricGraph.kt`**:
   - `TelemetryMetricGraph` currently accepts `zoomScale: Float = 1.0f` and `startDist: Double = 0.0`, but does not declare `isPanMode: Boolean = false` or `onZoomChanged: ((Float, Double) -> Unit)? = null` (or `onPanWindow`).
   - Consequently, `TelemetryMetricGraph` is unaware of whether the parent container is in Scrub Mode or Pan Mode.

2. **Pointer Input Gesture Loop in `TelemetryMetricGraph.kt`**:
   - In `TelemetryMetricGraph.kt` (lines 408–484), the `Canvas` modifier declares:
     ```kotlin
     .pointerInput(totalSpan, isTimeDomain, zoomScale, startDist) {
     ```
   - When a horizontal drag is detected (`isDragging == true`), it unconditionally maps the touch position to a distance/time value and executes scrubbing:
     ```kotlin
     val localX = (pointer.position.x - startPaddingPx).coerceIn(0f, chartWidthPx)
     val selectedVal = ElevationProfileZoomMath.canvasXToDistance(...)
     onDistanceSelected(...)
     ```
   - It does not track drag deltas (`dragDeltaX = pointer.position.x - prevX`) and does not invoke `ElevationProfileZoomMath.applyPan`.
   - On gesture release (`isDragging == true`), it unconditionally clears the scrubbing selection: `onDistanceSelected(null)`.

3. **Incomplete Wiring in `MapDetailLayout.kt`**:
   - In `MapDetailLayout.kt` (lines 230–236), `ElevationProfile` is passed both `isPanMode = isPanMode` and `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }`.
   - In contrast, the three downstream `TelemetryMetricGraph` invocations (Speed/Pace at line 251, HR at line 274, Power at line 297) only receive `zoomScale = profileZoomScale` and `startDist = profileStartDist`. They are not provided `isPanMode` or `onZoomChanged`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Expand `TelemetryMetricGraph` signature to accept optional `isPanMode: Boolean = false` and `onZoomChanged: ((Float, Double) -> Unit)? = null`, maintaining 100% binary and source backward compatibility for all existing callers (e.g. `WorkoutSummary.kt`).
  2. Update `pointerInput` in `TelemetryMetricGraph` to observe `isPanMode`:
     - When `isPanMode == true`, `totalSpan > 10.0`, and `onZoomChanged != null`: calculate `panStart = ElevationProfileZoomMath.applyPan(...)` from horizontal pointer movement deltas and invoke `onZoomChanged(zoomScale, panStart)`.
     - When `isPanMode == false`: preserve existing directional scrubbing and tap selection behavior (`onDistanceSelected`).
     - On drag release in Pan Mode, do not trigger scrub clearance.
  3. Update `MapDetailLayout.kt` to forward `isPanMode = isPanMode` and `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` to all three `TelemetryMetricGraph` composables (Speed/Pace, Heart Rate, Power).
  4. Author unit and visual contract tests in `TelemetryMetricGraphGestureTest.kt` and `MapDetailLayoutTest.kt` verifying parameter adoption, lockstep window panning, and gesture isolation.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Do not alter `GlobalTelemetryZoomToolbar.kt` styling, button layouts, or zoom calculation math.
  2. Do not modify `ElevationProfile.kt` gesture handling; its pan implementation remains the authoritative reference.
  3. Do not modify `WorkoutSummary.kt` where zoom/pan controls are not rendered.
  4. Do not alter `ChartGestureDisambiguator` slope thresholds ($|\Delta y| > |\Delta x|$). Vertical scrolling propagation to `lowerColumn` remains strictly preserved per `REQ-UI-226`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-232`), extending `REQ-UI-225` (*Persistent Sticky Global Zoom Toolbar*) and `REQ-UI-226` (*Directional Gesture Disambiguation and Smooth Vertical Scrolling in MapDetailLayout*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `d71b4028`, ATT-1876) and Sprint 2026-40.8 (Commit `6a7f21e9`, ATT-1872).
3. *Root Reason for Existing Formulation*: When the global zoom toolbar decoupled zoom controls from `ElevationProfile` (ATT-1876), `isPanMode` was wired to `ElevationProfile` as a proof of concept. `TelemetryMetricGraph` was not initially updated because multi-metric telemetry graphs were previously read-only scrubbing displays; however, in practice, athletes touch whichever chart is currently under their thumb when scrolling and navigating detailed telemetry.
4. *Preservation of Core Invariants*:
   - Single-finger vertical scroll gesture propagation to the parent container (`REQ-UI-226`) remains 100% intact.
   - Synchronized scrubbing cursor parity across all stacked charts when `isPanMode == false` remains strictly preserved.
   - Exact horizontal plot padding alignment (`50.dp` start, `25.dp` end) remains intact.
   - 9-language localization parity across all zoom toolbar content descriptions remains intact.

---

## 5. Architectural Strategy & High-Level Solution

### Component Changes:

1. **`TelemetryMetricGraph.kt`**:
   - Add parameters:
     ```kotlin
     isPanMode: Boolean = false,
     onZoomChanged: ((Float, Double) -> Unit)? = null,
     ```
   - Update `pointerInput`:
     ```kotlin
     .pointerInput(totalSpan, isTimeDomain, zoomScale, startDist, isPanMode) {
     ```
   - In the gesture loop:
     - Introduce `var prevX = down.position.x`
     - In `isDragging`:
       ```kotlin
       val dragDeltaX = pointer.position.x - prevX
       pointer.consume()
       if (isPanMode && totalSpan > 10.0 && onZoomChanged != null) {
           val panStart = ElevationProfileZoomMath.applyPan(
               currentStartDist = startDist,
               visibleDist = visibleSpan,
               panDeltaX = dragDeltaX,
               canvasWidth = chartWidthPx,
               totalDist = totalSpan
           )
           onZoomChanged(zoomScale, panStart)
       } else {
           // Existing scrubbing code ...
       }
       prevX = pointer.position.x
       ```
     - In gesture completion:
       ```kotlin
       if (isDragging) {
           if (!isPanMode) {
               onDistanceSelected(null)
           }
       } else if (!isVerticalScrolling) {
           if (!isPanMode) {
               // Tap selection ...
           }
       }
       ```

2. **`MapDetailLayout.kt`**:
   - Forward `isPanMode = isPanMode` and `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` to all three `TelemetryMetricGraph` instances (Speed/Pace, HR, Power).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Regression**: All existing unit tests in `TelemetryMetricGraphTest`, `TelemetryMetricGraphGestureTest`, and `MapDetailLayoutTest` must pass 100%.
  2. **Backward Compatibility**: Default parameter values (`isPanMode = false`, `onZoomChanged = null`) ensure callers without zoom/pan controls (such as `WorkoutSummary.kt`) compile and execute without changes.
  3. **Vertical Scroll Freedom**: Vertical swipes ($|\Delta y| > |\Delta x|$) must never be consumed, ensuring smooth scrolling of the parent `lowerColumn` remains completely unaffected.
  4. **Lockstep Synchrony**: Panning on any telemetry graph modifies `profileZoomScale` and `profileStartDist` in `MapDetailLayout`, immediately updating all stacked graphs and the map marker in unified lockstep.
* **Risk Rating**: **LOW**
  - High cohesion, low coupling. Uses existing tested math (`ElevationProfileZoomMath.applyPan`) and established callback patterns from `ElevationProfile`.
