# Stage 1 Analysis: ATT-1814 - Synchronize horizontal zoom globally across all telemetry graphs

**Ticket**: [ATT-1814](https://rainerblind.atlassian.net/browse/ATT-1814)  
**Sub-task**: [ATT-1854](https://rainerblind.atlassian.net/browse/ATT-1854) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1814`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

On the detailed workout inspection screen (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`), athletes can zoom into the workout distance/time axis using interactive zoom controls (+, -, Reset, Pan toggle, and pinch-to-zoom gestures) hosted on the `ElevationProfile` composable.

Currently, zooming in `ElevationProfile` is purely local to that individual composable:
- `ElevationProfile.kt` maintains `zoomScale` and `startDist` as internal `remember` state.
- When an athlete zooms in on the Elevation Profile (e.g. 2.5x magnification to inspect a steep climb), the Elevation Profile redraws with a narrow distance window (`startDist .. startDist + visibleSpan`).
- However, all subsequent stacked continuous telemetry graphs (Speed/Pace, Heart Rate, and Power) remain completely unzoomed, rendering the entire 0..100% total distance range.
- This creates severe visual dissonance and misaligns the stacked charts:
  1. The vertical grid lines and distance milestones no longer align across stacked graphs.
  2. Multi-metric scrubbing becomes confusing because moving the cursor on the zoomed Elevation Profile translates to a completely different horizontal position on the unzoomed telemetry graphs.

The goal of ATT-1814 is to synchronize horizontal zoom globally across all stacked metric graphs in `MapDetailLayout.kt` so that every visible graph reflects the exact same horizontal range (`startDist .. startDist + visibleSpan`) and stays pixel-perfectly aligned.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Findings:
1. **Local State Encapsulation in `ElevationProfile.kt`**:
   - In `ElevationProfile.kt` (lines 243–244):
     ```kotlin
     var zoomScale by remember(pathPoints) { mutableFloatStateOf(1.0f) }
     var startDist by remember(pathPoints) { mutableDoubleStateOf(0.0) }
     ```
   - Zoom scale and start distance are encapsulated strictly inside `ElevationProfile`. Neither `MapDetailLayout` nor sibling composables can observe or control this state.

2. **Unparameterized Telemetry Metric Graphs (`TelemetryMetricGraph.kt`)**:
   - `TelemetryMetricGraph` currently assumes that the visible range is always `0.0 .. totalSpan`:
     ```kotlin
     val x = startPaddingPx + ((xSpan / totalSpan) * chartWidthPx).toFloat()
     ```
   - It does not accept `zoomScale` or `startDist` parameters.
   - It computes X-axis ticks assuming the whole span from `0` to `totalSpan`.
   - Scrubbing touch detection (`detectTapGestures` / `detectDragGestures`) computes distance as `(localX / chartWidthPx) * totalSpan`, which does not account for zoomed offsets.
   - Cursor rendering computes `cursorX = startPaddingPx + (cursorDistSpan / totalSpan) * chartWidthPx`, failing to line up with the zoomed elevation curve.

3. **Mathematical Engine Availability (`ElevationProfileZoomMath.kt`)**:
   - `ElevationProfileZoomMath` already provides robust, pure mathematical functions:
     - `calculateVisibleDistance(totalDist, zoomScale)`
     - `distanceToCanvasX(dist, startDist, visibleDist, canvasWidth)`
     - `canvasXToDistance(canvasX, startDist, visibleDist, canvasWidth, totalDist)`
     - `calculateAdaptiveDistanceStep(visibleDist, unit)`
     - `calculateAdaptiveTimeStep(visibleSpan)`
     - `formatTimeTick(sec)`
   - These functions are currently utilized by `ElevationProfile`, but have not yet been wired into `TelemetryMetricGraph`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Hoist horizontal zoom state (`zoomScale`, `startDist`) in `MapDetailLayout.kt` so that zoom changes originating from `ElevationProfile` are propagated globally to all displayed `TelemetryMetricGraph` composables (Speed/Pace, Heart Rate, Power).
  * Parameterize `TelemetryMetricGraph` with `zoomScale: Float = 1.0f` and `startDist: Double = 0.0`.
  * In `TelemetryMetricGraph`:
    - Scale curve X coordinates using `ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)`.
    - Generate adaptive X-axis ticks reflecting `startDist .. startDist + visibleSpan`.
    - Update tap and drag scrubbing detection to use `ElevationProfileZoomMath.canvasXToDistance`.
    - Position scrubbing vertical cursor line and marker dot using `distanceToCanvasX`, clamping/hiding the cursor when `currentDistance` falls outside the visible zoomed window.
  * In `ElevationProfile.kt`, support hoisted zoom state via optional parameters (`zoomScale: Float = 1.0f`, `startDist: Double = 0.0`, `onZoomChanged: ((Float, Double) -> Unit)? = null`) while gracefully falling back to internal state if unhoisted (preserving 100% backward compatibility for all other callers).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not add separate zoom button controls to each individual `TelemetryMetricGraph` card; `ElevationProfile` continues to host the master zoom controls.
  * Do not modify the independent Y-axis dynamic scaling of each metric graph.
  * Do not alter workout list summary cards (`WorkoutSummary.kt`), which do not have zoom controls and display full unzoomed profiles.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-192` (*Elevation Profile: Zoom, Panning, and Elevation Bounds Math*), `REQ-UI-206` (*Aftermath: Continuous Telemetry Metric Graphs*), extending Epic `ATT-111`.
* **Historical Origin & Commit Trace**:
  - `REQ-UI-192` (Sprint 2026-40.4) introduced horizontal zoom and panning exclusively to `ElevationProfile`.
  - `ATT-1740` (Sprint 2026-40.6) introduced `TelemetryMetricGraph` to display continuous curves for HR, Speed, and Power.
* **Root Reason for Existing Formulation**:
  `ElevationProfile` was built prior to the introduction of continuous multi-metric telemetry curves in `ATT-1740`. Consequently, `zoomScale` was encapsulated internally within `ElevationProfile` because no other continuous charts existed on screen at that time.
* **Preservation of Core Invariants**:
  - **Backward Compatibility**: `ElevationProfile` defaults `onZoomChanged = null` and retains internal fallback state for calls in `WorkoutSummary`, `RouteItem`, `SegmentItem`, and `LiveSegmentSheet`.
  - **Identical Geometry & Padding**: Both `ElevationProfile` and `TelemetryMetricGraph` continue to share identical horizontal padding (`start = 50.dp, end = 25.dp`), ensuring that equivalent distances have identical pixel X coordinates across all stacked graphs.
  - **Single-Source Math**: All coordinate mapping and tick step calculations use `ElevationProfileZoomMath`.

---

## 5. Architectural Strategy & High-Level Solution

1. **State Hoisting in `MapDetailLayout.kt`**:
   ```kotlin
   var profileZoomScale by remember(path) { mutableFloatStateOf(1.0f) }
   var profileStartDist by remember(path) { mutableDoubleStateOf(0.0) }
   ```
   - Forward `profileZoomScale`, `profileStartDist`, and `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` to `ElevationProfile`.
   - Forward `zoomScale = profileZoomScale` and `startDist = profileStartDist` to each `TelemetryMetricGraph` (Speed/Pace, Heart Rate, Power).

2. **Hoisting Support in `ElevationProfile.kt`**:
   ```kotlin
   fun ElevationProfile(
       ...
       zoomScale: Float = 1.0f,
       startDist: Double = 0.0,
       onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)? = null
   )
   ```
   - If `onZoomChanged != null`, consume passed-in `zoomScale` and `startDist` and invoke `onZoomChanged(newZoom, newStartDist)` on zoom/pan gestures and button clicks.

3. **Zoom Integration in `TelemetryMetricGraph.kt`**:
   ```kotlin
   fun TelemetryMetricGraph(
       ...
       zoomScale: Float = 1.0f,
       startDist: Double = 0.0
   )
   ```
   - Compute `val visibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, zoomScale)`.
   - Map points via `ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)`.
   - Render X-axis ticks adapting to `visibleSpan` and starting at `startDist`.
   - Handle touch interactions using `ElevationProfileZoomMath.canvasXToDistance(localX, startDist, visibleSpan, chartWidthPx, totalSpan)`.
   - Render cursor at `distanceToCanvasX(cursorDistSpan, startDist, visibleSpan, chartWidthPx)` only when within visible window.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Pixel-perfect horizontal alignment across all stacked graphs (`start = 50.dp, end = 25.dp`).
  2. Cursor position (`selectedDistance`) remains synchronized across all graphs and the map marker.
  3. Other callers of `ElevationProfile` that do not hoist zoom remain 100% unaffected.
  4. 100% unit test pass rate across `./gradlew testDebugUnitTest`.
* **Risk Rating**: **LOW**
  - Reuses thoroughly validated `ElevationProfileZoomMath` formulas; standard Compose state hoisting pattern.
