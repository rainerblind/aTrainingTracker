# Stage 2: Requirement & Test Specification - ATT-1814: Synchronize horizontal zoom globally across all telemetry graphs

**Ticket**: [ATT-1814](https://rainerblind.atlassian.net/browse/ATT-1814)  
**Sub-task**: [ATT-1855](https://rainerblind.atlassian.net/browse/ATT-1855) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-215` (*Aftermath: Global Synchronized Horizontal Zoom & Panning Architecture Across Stacked Telemetry Graphs*)  
**Test Mapping**: `TST-UI-169` (*Aftermath Global Synchronized Horizontal Zoom & Telemetry Alignment Verification*)  
**Branch**: `feature/ATT-1814`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-215)

### 1.1 Problem Statement & Rationale
When an athlete zooms in on the distance or time axis in the detailed workout inspection view (`MapDetailLayout.kt`), only the `ElevationProfile` composable currently reflects the zoom factor. The stacked continuous telemetry graphs (Speed/Pace, Heart Rate, Power) remain fixed at 1.0x total span. This causes severe horizontal misalignment across stacked charts and breaks multi-metric scrubbing synchronization. A global synchronized zoom architecture ensures that zooming or panning anywhere on the inspection view scales all stacked metric graphs synchronously to the exact same visible distance/time window.

### 1.2 Functional & Architectural Requirements
1. **Global Zoom State Hoisting in `MapDetailLayout.kt`**:
   - `MapDetailLayout.kt` SHALL maintain hoisted horizontal zoom state:
     - `profileZoomScale: Float` (default 1.0f)
     - `profileStartDist: Double` (default 0.0)
   - When the underlying `path` changes, this state SHALL reset to `1.0f` and `0.0`.
   - `MapDetailLayout.kt` SHALL pass this hoisted state and callback `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` to `ElevationProfile`, and forward `zoomScale = profileZoomScale` and `startDist = profileStartDist` to every displayed `TelemetryMetricGraph` (Speed/Pace, Heart Rate, Power).

2. **Hoisting Support in `ElevationProfile.kt`**:
   - `ElevationProfile.kt` SHALL declare optional parameters:
     - `zoomScale: Float = 1.0f`
     - `startDist: Double = 0.0`
     - `onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)? = null`
   - When `onZoomChanged != null`, `ElevationProfile` SHALL consume the passed `zoomScale` and `startDist` and invoke `onZoomChanged(newZoom, newStart)` on zoom button taps (+, -, Reset), pinch-to-zoom gestures, and pan gestures.
   - When `onZoomChanged == null`, `ElevationProfile` SHALL fall back to internal `remember` state, preserving 100% backward compatibility for all unhoisted callers.

3. **Synchronized Rendering & Scrubbing in `TelemetryMetricGraph.kt`**:
   - `TelemetryMetricGraph.kt` SHALL accept `zoomScale: Float = 1.0f` and `startDist: Double = 0.0`.
   - `visibleSpan` SHALL be computed via `ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, zoomScale)`.
   - Point X coordinates SHALL be mapped via `startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)`.
   - X-axis ticks along the bottom axis SHALL reflect `[startDist .. startDist + visibleSpan]` using `calculateAdaptiveDistanceStep(visibleSpan, unit)` or `calculateAdaptiveTimeStep(visibleSpan)`.
   - Touch scrubbing gestures (`detectTapGestures`, `detectDragGestures`) SHALL map touch coordinates to distance using `ElevationProfileZoomMath.canvasXToDistance(localX, startDist, visibleSpan, chartWidthPx, totalSpan)`.
   - Scrubbing cursor rendering SHALL place the dashed vertical cursor line and marker dot using `distanceToCanvasX(cursorDistSpan, startDist, visibleSpan, chartWidthPx)`, and SHALL omit/suppress rendering when `cursorDistSpan` falls outside `[startDist .. startDist + visibleSpan]`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Synchronized Magnification)**:
  * *Given* an athlete viewing a workout with Elevation, Speed, and Heart Rate graphs in Aftermath (`TrackOnMapScreen`),
  * *When* the athlete taps the `+` zoom button or pinches to zoom on the Elevation Profile (e.g. 2.0x zoom),
  * *Then* the Elevation Profile, Speed/Pace graph, and Heart Rate graph SHALL simultaneously zoom into the exact same distance range (`startDist .. startDist + visibleSpan`), with identical tick milestones along their X-axes.
* **Criterion 2 (Synchronized Panning)**:
  * *Given* all graphs zoomed in to 3.0x,
  * *When* the athlete enables Pan mode and drags horizontally on the Elevation Profile,
  * *Then* all displayed telemetry graphs SHALL scroll horizontally in perfect lockstep.
* **Criterion 3 (Scrubbing Alignment Across Zoomed Graphs)**:
  * *Given* all graphs zoomed in to 2.5x,
  * *When* the athlete drags to scrub on the Speed/Pace graph or the Elevation Profile,
  * *Then* the vertical cursor line and instantaneous metric dots SHALL appear at the exact same horizontal canvas coordinate across all stacked graphs.
* **Criterion 4 (Zoom Reset)**:
  * *Given* zoomed graphs,
  * *When* the athlete taps the Reset (1.0x) button,
  * *Then* all stacked graphs SHALL smoothly return to 1.0x full-span view.

### 1.4 System Invariants
1. Horizontal padding invariants (`start = 50.dp, end = 25.dp, bottom = 24.dp`) must be preserved across all graphs.
2. Single-thread database and mathematical isolation must remain 100% intact.
3. Unhoisted callers of `ElevationProfile` (workout lists, route inspection) continue functioning without regressions.
4. Clean-room test suite maintains 100% pass rate.

---

## 2. Test Specification (TST-UI-169)

### Test Case 1: `testElevationProfile_declaresHoistedZoomParameters` (`[TST-UI-169.1]`)
* **Scope**: Component API Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt`
* **Preconditions**: `ElevationProfile.kt` exists.
* **Action**: Verify `ElevationProfile` signature supports `zoomScale: Float`, `startDist: Double`, and `onZoomChanged`.
* **Expected Result**: Assertions pass.

### Test Case 2: `testTelemetryMetricGraph_zoomedCoordinateMappingAndScrubbing` (`[TST-UI-169.2]`)
* **Scope**: Mathematical & Interaction Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* **Preconditions**: `TelemetryMetricGraph.kt` and `ElevationProfileZoomMath.kt` exist.
* **Action**: Verify `distanceToCanvasX` and `canvasXToDistance` calculations for 1.0x, 2.0x, and 5.0x zoom with non-zero `startDist`.
* **Expected Result**: X coordinates map strictly within `0f..canvasWidth` and round-trip faithfully.

### Test Case 3: `testMapDetailLayout_wiresGlobalZoomState` (`[TST-UI-169.3]`)
* **Scope**: Visual & Structural Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
* **Preconditions**: `MapDetailLayout.kt` exists.
* **Action**: Verify `MapDetailLayout.kt` declares hoisted zoom state and passes `zoomScale` and `startDist` to `ElevationProfile` and all `TelemetryMetricGraph` invocations.
* **Expected Result**: Contract assertions pass.

### Test Case 4: Clean-Room Regression Suite (`[TST-UI-169.4]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-169.1]` | Contract | `ElevationProfileLayoutTest.testElevationProfile_declaresHoistedZoomParameters` | `REQ-UI-215` | Specified |
| `[TST-UI-169.2]` | Unit | `TelemetryMetricGraphTest.testTelemetryMetricGraph_zoomedCoordinateMappingAndScrubbing` | `REQ-UI-215` | Specified |
| `[TST-UI-169.3]` | Contract | `MapDetailLayoutTest.testMapDetailLayout_wiresGlobalZoomState` | `REQ-UI-215` | Specified |
| `[TST-UI-169.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
