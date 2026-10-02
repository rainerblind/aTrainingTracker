# Stage 2: Requirement & Test Specification - ATT-1956: [Aftermath/Graphs] Support Pan/Moving Gesture Across Speed, Heart Rate, and Power Graphs in Zoom Toolbar Pan Mode

**Ticket**: [ATT-1956](https://rainerblind.atlassian.net/browse/ATT-1956)  
**Sub-task**: [ATT-1967](https://rainerblind.atlassian.net/browse/ATT-1967) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*)  
**Test Spec ID**: `TST-UI-186`  
**Branch**: `feature/ATT-1956`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-232)

### 1.1 Problem Statement & Rationale
When athletes inspect post-workout telemetry in `MapDetailLayout.kt`, the persistent global zoom toolbar allows toggling between Scrub Mode and Pan Mode (`isPanMode`). In Pan Mode, horizontal dragging across `ElevationProfile` smoothly pans the visible window across the entire timeline. However, dragging across any of the stacked `TelemetryMetricGraph` instances (Speed/Pace, Heart Rate, Power) currently executes cursor scrubbing instead of window panning. This creates a confusing discrepancy where touching the upper chart pans the window, while touching the charts immediately beneath it moves the scrub cursor.

### 1.2 Functional & Architectural Requirements
The system SHALL support horizontal viewport window panning across all stacked telemetry metric graphs (`TelemetryMetricGraph.kt` for Speed/Pace, Heart Rate, and Power) when the global zoom toolbar is set to Pan Mode (`isPanMode == true`), ensuring seamless multi-chart navigation in lockstep with `ElevationProfile.kt`:

1. **TelemetryMetricGraph Pan Mode Signature & State Ingestion**:
   - `TelemetryMetricGraph` SHALL accept optional parameters:
     ```kotlin
     isPanMode: Boolean = false,
     onZoomChanged: ((Float, Double) -> Unit)? = null,
     ```
   - Default arguments SHALL preserve 100% binary and source backward compatibility for all un-instrumented callers (e.g. `WorkoutSummary.kt`).

2. **Directional Pan Gesture Execution & Calculation**:
   - In `TelemetryMetricGraph.kt`, the canvas `pointerInput` SHALL observe `(totalSpan, isTimeDomain, zoomScale, startDist, isPanMode)`.
   - When a dominant horizontal drag is detected (`isDragging == true`):
     - If `isPanMode == true`, `totalSpan > 10.0`, and `onZoomChanged != null`:
       - The handler SHALL consume the pointer event (`pointer.consume()`).
       - The handler SHALL calculate pointer horizontal delta `dragDeltaX = pointer.position.x - prevX`.
       - The handler SHALL invoke `ElevationProfileZoomMath.applyPan(currentStartDist = startDist, visibleDist = visibleSpan, panDeltaX = dragDeltaX, canvasWidth = chartWidthPx, totalDist = totalSpan)`.
       - The handler SHALL invoke `onZoomChanged(zoomScale, panStart)`.
       - The handler SHALL NOT trigger scrubbing distance updates (`onDistanceSelected`).
     - If `isPanMode == false`:
       - The handler SHALL preserve continuous route scrubbing: calculating `selectedVal` via `ElevationProfileZoomMath.canvasXToDistance` and invoking `onDistanceSelected(selectedVal)`.
     - In all cases, `prevX` SHALL update to `pointer.position.x`.

3. **Gesture Release & Tap Isolation in Pan Mode**:
   - Upon drag completion (`isDragging == true`): if `isPanMode == false`, the handler SHALL clear the scrubbing cursor (`onDistanceSelected(null)`); if `isPanMode == true`, it SHALL NOT clear or modify scrub selection.
   - Upon stationary tap release without drag (`!isVerticalScrolling && !isDragging`): if `isPanMode == false`, tap selection SHALL update `onDistanceSelected`; if `isPanMode == true`, tap inspection SHALL be suppressed to prevent accidental scrub selection during pan navigation.

4. **MapDetailLayout Multi-Graph Lockstep Synchronization**:
   - In `MapDetailLayout.kt`, when `showZoomControls == true` and `activeScrubPath != null && activeScrubPath.isNotEmpty()`, `isPanMode` and `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` SHALL be forwarded to `ElevationProfile` and all three `TelemetryMetricGraph` instances (Speed/Pace, Heart Rate, Power).
   - Panning any chart SHALL immediately update `profileStartDist` in lockstep across all visible charts and the map marker.

5. **Preservation of Core Invariants**:
   - Vertical scrolling propagation: single-finger vertical swipes ($|\Delta y| > |\Delta x|$) MUST pass through unconsumed to `lowerColumn` per `REQ-UI-226`.
   - Plot padding alignment: `startPaddingPx = 50.dp` and `endPaddingPx = 25.dp` MUST match `ElevationProfile.kt` exactly.
   - Two-finger pinch-to-zoom and reset on `GlobalTelemetryZoomToolbar` remain 100% functional.
   - 100% 9-language localization parity across zoom toolbar resources.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-232`), extending `REQ-UI-225` (*Persistent Sticky Global Zoom Toolbar*) and `REQ-UI-226` (*Directional Gesture Disambiguation and Smooth Vertical Scrolling in MapDetailLayout*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `d71b4028`, ATT-1876) and Sprint 2026-40.8 (Commit `6a7f21e9`, ATT-1872).
3. *Root Reason for Existing Formulation*: When the global zoom toolbar decoupled zoom controls from `ElevationProfile` (ATT-1876), `isPanMode` was wired to `ElevationProfile` as a proof of concept. `TelemetryMetricGraph` was not initially updated because multi-metric telemetry graphs were previously read-only scrubbing displays; however, in practice, athletes touch whichever chart is currently under their thumb when scrolling and navigating detailed telemetry.
4. *Preservation of Core Invariants*: Single-finger vertical scroll gesture propagation to the parent container (`REQ-UI-226`), synchronized scrubbing cursor parity across all stacked charts when `isPanMode == false`, exact horizontal plot padding alignment (`50.dp` start, `25.dp` end), and 9-language localization parity are 100% strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Horizontal Drag in Pan Mode Pans Viewport Window)**:
  * *Given* the athlete viewing a workout in `MapDetailLayout` with `isPanMode == true`,
  * *When* dragging horizontally across `TelemetryMetricGraph` (Speed/Pace, Heart Rate, or Power),
  * *Then* the chart SHALL consume the gesture, calculate `panStart` via `ElevationProfileZoomMath.applyPan`, and invoke `onZoomChanged(zoomScale, panStart)`, smoothly moving the visible timeline window across all stacked charts and the map marker in lockstep.
* **Criterion 2 (Scrub Mode Preserved)**:
  * *Given* the athlete viewing a workout in `MapDetailLayout` with `isPanMode == false`,
  * *When* dragging horizontally across `TelemetryMetricGraph`,
  * *Then* the chart SHALL continue to scrub the cursor position via `onDistanceSelected`, updating instantaneous metrics and polyline map location.
* **Criterion 3 (Vertical Scrolling Propagation)**:
  * *Given* the athlete viewing stacked charts in `MapDetailLayout` regardless of `isPanMode`,
  * *When* performing a single-finger vertical swipe ($|\Delta y| > |\Delta x|$ and $|\Delta y| > \text{touchSlop}$),
  * *Then* neither pan nor scrubbing SHALL trigger, and the parent `lowerColumn` container SHALL scroll vertically without hindrance.
* **Criterion 4 (Clean Drag Release in Pan Mode)**:
  * *Given* an active horizontal pan drag on `TelemetryMetricGraph` in Pan Mode,
  * *When* releasing the touch gesture,
  * *Then* `onDistanceSelected(null)` SHALL NOT be invoked, preserving cursor and viewport stability.
* **Criterion 5 (Backward Compatibility)**:
  * *Given* callers invoking `TelemetryMetricGraph` without `isPanMode` or `onZoomChanged` (such as `WorkoutSummary.kt`),
  * *When* compiled and rendered,
  * *Then* default arguments (`isPanMode = false`, `onZoomChanged = null`) SHALL preserve existing rendering and scrubbing behavior without regression.

### 1.4 System Invariants
1. **Vertical Scroll Freedom (`REQ-UI-226`)**: Directional slope disambiguation against `viewConfiguration.touchSlop` must never be bypassed; vertical swipes must pass through unconsumed.
2. **Padding Parity**: Horizontal start (`50.dp`) and end (`25.dp`) canvas paddings remain identical to `ElevationProfile.kt`.
3. **Lockstep Synchrony**: `MapDetailLayout` remains the single source of truth for `profileZoomScale` and `profileStartDist`.
4. **Zero Localization Regression**: Zoom toolbar content descriptions maintain 100% parity across all 9 supported locales.

---

## 2. Test Specification (TST-UI-186)

### Test Case 1: `testTelemetryMetricGraph_panModeSignatureAndGestureContract` (`[TST-UI-186.1]`)
* **Scope**: Composable & Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphGestureTest.kt`
* **Preconditions**: `TelemetryMetricGraph.kt` exists.
* **Action**: Inspect method signature and pointerInput implementation in `TelemetryMetricGraph.kt`.
* **Expected Result**:
  1. `TelemetryMetricGraph` declares `isPanMode: Boolean = false` and `onZoomChanged: ((Float, Double) -> Unit)? = null`.
  2. `pointerInput` includes `isPanMode` in parameter keys.
  3. `isDragging` tracks `prevX` and calculates `dragDeltaX`.
  4. When `isPanMode && totalSpan > 10.0 && onZoomChanged != null`, invokes `ElevationProfileZoomMath.applyPan` and `onZoomChanged`.
  5. When `isPanMode == false`, executes `onDistanceSelected`.
  6. On drag release, `onDistanceSelected(null)` is guarded by `!isPanMode`.

### Test Case 2: `testMapDetailLayout_forwardsPanModeToTelemetryMetricGraphs` (`[TST-UI-186.2]`)
* **Scope**: Layout & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
* **Preconditions**: `MapDetailLayout.kt` exists.
* **Action**: Inspect AST / text occurrences of parameter bindings in `MapDetailLayout.kt`.
* **Expected Result**:
  1. Forwarding of `isPanMode = isPanMode` occurs $\ge 4$ times (ElevationProfile + 3 TelemetryMetricGraph instances).
  2. Forwarding of `onZoomChanged` occurs $\ge 4$ times across ElevationProfile and TelemetryMetricGraphs.

### Test Case 3: `testElevationProfileZoomMath_applyPanBoundsAndDeltas` (`[TST-UI-186.3]`)
* **Scope**: Pure Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTest.kt`
* **Preconditions**: Pure math functions in `ElevationProfileZoomMath`.
* **Action**: Execute `applyPan` across positive, negative, and extreme drag deltas.
* **Expected Result**:
  1. Resulting `newStart` is clamped strictly to `[0.0, totalDist - visibleDist]`.
  2. Dragging rightward (positive delta) shifts visible window towards beginning (decreases startDist).
  3. Dragging leftward (negative delta) shifts visible window towards end (increases startDist).

### Test Case 4: Clean-Room Full Suite Regression (`[TST-UI-186.4]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-186.1]` | Unit / Contract | `TelemetryMetricGraph.kt` | `REQ-UI-232` | Specified |
| `[TST-UI-186.2]` | Contract | `MapDetailLayout.kt` | `REQ-UI-232` | Specified |
| `[TST-UI-186.3]` | Unit | `ElevationProfileZoomMath.applyPan` | `REQ-UI-232` | Specified |
| `[TST-UI-186.4]` | Full Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
