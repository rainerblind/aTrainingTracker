# Stage 3: Implementation Plan - ATT-1814: Synchronize horizontal zoom globally across all telemetry graphs

**Ticket**: [ATT-1814](https://rainerblind.atlassian.net/browse/ATT-1814)  
**Sub-task**: [ATT-1856](https://rainerblind.atlassian.net/browse/ATT-1856) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-215` (*Aftermath: Global Synchronized Horizontal Zoom & Panning Architecture Across Stacked Telemetry Graphs*)  
**Test Mapping**: `TST-UI-169` (*Aftermath Global Synchronized Horizontal Zoom & Telemetry Alignment Verification*)  
**Branch**: `feature/ATT-1814`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In the detailed workout inspection view (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`), athletes can zoom and pan along the horizontal axis (distance in meters/miles or time in seconds) on the `ElevationProfile`. However, the stacked continuous telemetry graphs (`TelemetryMetricGraph.kt` displaying Speed/Pace, Heart Rate, and Power) do not observe this horizontal zoom. They remain fixed at 1.0x total span.

This discrepancy causes severe horizontal misalignment across stacked charts and breaks multi-metric scrubbing synchronization:
- An athlete zooming in on a steep climb at kilometer 12 sees the elevation profile zoomed to [11 km .. 13 km], but the Heart Rate and Speed/Pace charts below still show the entire 40 km activity.
- The vertical cursor scrubber line and touch interactions no longer align across the graphs.

To resolve this, we will introduce a global synchronized horizontal zoom architecture that hoists zoom scale and start distance to `MapDetailLayout.kt`, allowing all stacked graphs to scale and pan in lockstep.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-215` (*Aftermath: Global Synchronized Horizontal Zoom & Panning Architecture Across Stacked Telemetry Graphs*)
* **Test Mapping**: `TST-UI-169` (*Aftermath Global Synchronized Horizontal Zoom & Telemetry Alignment Verification*)
  * `[TST-UI-169.1]`: Component API Contract Test (`ElevationProfileLayoutTest.testElevationProfile_declaresHoistedZoomParameters`)
  * `[TST-UI-169.2]`: Mathematical & Interaction Unit Test (`TelemetryMetricGraphTest.testTelemetryMetricGraph_zoomedCoordinateMappingAndScrubbing`)
  * `[TST-UI-169.3]`: Visual & Structural Layout Contract Test (`MapDetailLayoutTest.testMapDetailLayout_wiresGlobalZoomState`)
  * `[TST-UI-169.4]`: Full clean-room regression suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Unhoisted callers of `ElevationProfile` (such as workout list summary cards or simplified inspection views) continue to function identically with internal zoom state if `onZoomChanged == null`.
2. **Padding Invariants**: Horizontal and vertical padding constants (`startPadding = 50.dp`, `endPadding = 25.dp`, `bottomPadding = 24.dp`) remain strictly unified across `ElevationProfile` and `TelemetryMetricGraph`.
3. **Synchronized Multi-Chart Scrubbing**: Shared `selectedDistance` parameter remains synchronously forwarded to all graphs and the map cursor.
4. **Mandatory Programmatic Pre-Check Before Stage 4 Code Modifications**:
   - `python3 tools/jira_util.py check-gate ATT-1856` must exit code 0 (`GATE_PASSED`) before modifying production source files under `app/src/...`.
5. **Human Gate Invariance**: Terminal transition of parent ticket `ATT-1814` is strictly `Final Review (Human)` assigned to `rainer`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `MapDetailLayout.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
- Hoist zoom state alongside `selectedDistance`:
  ```kotlin
  var profileZoomScale by remember(path) { mutableFloatStateOf(1.0f) }
  var profileStartDist by remember(path) { mutableDoubleStateOf(0.0) }
  ```
- Pass `zoomScale`, `startDist`, and callback `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` to `ElevationProfile`.
- Pass `zoomScale = profileZoomScale` and `startDist = profileStartDist` to each `TelemetryMetricGraph` (Speed/Pace, Heart Rate, Power).

### Component 2: `ElevationProfile.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
- Add optional hoisted parameters to `ElevationProfile`:
  ```kotlin
  zoomScale: Float = 1.0f,
  startDist: Double = 0.0,
  onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)? = null,
  ```
- Implement dual-mode state management:
  - If `onZoomChanged != null`: use passed `zoomScale` and `startDist`, and dispatch zoom/pan changes via `onZoomChanged`.
  - If `onZoomChanged == null`: maintain internal mutable state `internalZoomScale` and `internalStartDist`.
- Update zoom buttons (+, -, Reset), pinch gesture, and pan gesture handler to invoke `updateZoom(newZoom, newStartDist)`.
- Forward `zoomScale`, `startDist`, `onZoomChanged` from the encoded string overload `ElevationProfile(encodedAltitudes, encodedDistances, ...)`.

### Component 3: `TelemetryMetricGraph.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
- Add parameters:
  ```kotlin
  zoomScale: Float = 1.0f,
  startDist: Double = 0.0,
  ```
- Compute visible span using `ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, zoomScale)`.
- Use `ElevationProfileZoomMath.distanceToCanvasX` for curve point rendering.
- Update X-axis tick generation:
  - Time domain: use `ElevationProfileZoomMath.calculateAdaptiveTimeStep(visibleSpan)` starting at `ceil(startDist / step) * step`.
  - Distance domain: use `ElevationProfileZoomMath.calculateAdaptiveDistanceStep(visibleSpan, unit)` starting at `ceil(startDist / step) * step`.
- Update scrubbing touch detection (`detectTapGestures` and `detectDragGestures`):
  - Map touch pixel `localX` to distance using `ElevationProfileZoomMath.canvasXToDistance(localX, startDist, visibleSpan, chartWidthPx, totalSpan)`.
- Update scrubbing cursor line & marker dot:
  - Calculate `cursorX` using `ElevationProfileZoomMath.distanceToCanvasX(cursorDistSpan, startDist, visibleSpan, chartWidthPx)`.
  - Only render cursor line and marker dot when `cursorDistSpan` falls within `startDist .. (startDist + visibleSpan)`.

### Component 4: Test Suite Updates
- `ElevationProfileLayoutTest.kt`: Add contract verification for hoisted parameters on `ElevationProfile`.
- `TelemetryMetricGraphTest.kt`: Add unit tests for zoomed coordinate mapping, tick interval selection, and touch conversion across zoom factors.
- `MapDetailLayoutTest.kt`: Add contract tests ensuring `MapDetailLayout` hoists zoom state and passes it to `ElevationProfile` and all `TelemetryMetricGraph` instances.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Command: `python3 tools/jira_util.py check-gate ATT-1856`
* Verify exit code 0 (`GATE_PASSED`) before editing production files.

### Step 2: Implement Hoisting in `ElevationProfile.kt`
* Add `zoomScale: Float = 1.0f`, `startDist: Double = 0.0`, `onZoomChanged: ((Float, Double) -> Unit)? = null` to `ElevationProfile`.
* Wire `updateZoom(newZoom, newStartDist)` to gestures and zoom control buttons.
* Forward parameters in string overload.

### Step 3: Implement Zoomed Rendering in `TelemetryMetricGraph.kt`
* Add `zoomScale: Float = 1.0f`, `startDist: Double = 0.0` to `TelemetryMetricGraph`.
* Refactor curve plotting, X ticks, scrubbing touch detection, and cursor line rendering to use `ElevationProfileZoomMath`.

### Step 4: Wire Hoisted Zoom in `MapDetailLayout.kt`
* Declare `profileZoomScale` and `profileStartDist`.
* Pass state and `onZoomChanged` callback to `ElevationProfile`.
* Forward `zoomScale` and `startDist` to all three telemetry graphs.

### Step 5: Add Unit & Contract Tests
* Update `ElevationProfileLayoutTest.kt`, `TelemetryMetricGraphTest.kt`, and `MapDetailLayoutTest.kt`.
* Run targeted tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted tests in `com.atrainingtracker.trainingtracker.ui.map.*` pass cleanly.
  2. Full clean-room test suite passes (`./gradlew testDebugUnitTest`).
  3. Author Stage 5 walkthrough (`docs/engineering/walkthroughs/ATT-1814_walkthrough.md`).
* **Rollback**:
  - `feature/ATT-1814` is isolated from `sprint/2026-40.7`. If needed, changes can be rolled back via `git checkout sprint/2026-40.7 && git branch -D feature/ATT-1814`.
