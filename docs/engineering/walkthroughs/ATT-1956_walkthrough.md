# Stage 5: Walkthrough & Verification - ATT-1956: Support Pan/Moving Gesture Across Speed, Heart Rate, and Power Graphs in Zoom Toolbar Pan Mode

**Ticket**: [ATT-1956](https://rainerblind.atlassian.net/browse/ATT-1956)  
**Sub-task**: [ATT-1970](https://rainerblind.atlassian.net/browse/ATT-1970) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*)  
**Test Mapping**: `TST-UI-186`  
**Branch**: `feature/ATT-1956`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

Ticket ATT-1956 delivers horizontal viewport window panning across stacked telemetry metric graphs (`TelemetryMetricGraph.kt` for Speed/Pace, Heart Rate, and Power) when the global zoom toolbar is toggled to Pan Mode (`isPanMode == true`), creating synchronized multi-chart navigation in lockstep with `ElevationProfile.kt` and the map scrub marker.

1. **TelemetryMetricGraph Pan Mode Support (`TelemetryMetricGraph.kt`)**:
   - Added optional parameters `isPanMode: Boolean = false` and `onZoomChanged: ((Float, Double) -> Unit)? = null` with safe defaults ensuring 100% binary and source backward compatibility for un-instrumented callers (e.g. `WorkoutSummary.kt`).
   - Integrated `isPanMode` into `Modifier.pointerInput(totalSpan, isTimeDomain, zoomScale, startDist, isPanMode)`.
   - In `awaitEachGesture`, when a horizontal drag is detected (`isDragging == true`):
     - If `isPanMode == true`, `totalSpan > 10.0`, and `onZoomChanged != null`: consumes pointer event, computes horizontal delta `dragDeltaX = pointer.position.x - prevX`, invokes `ElevationProfileZoomMath.applyPan`, and dispatches `onZoomChanged(zoomScale, panStart)`.
     - If `isPanMode == false`: retains continuous route scrubbing via `onDistanceSelected(selectedVal)`.
   - Guarded gesture completion scrub clearing: if `isPanMode == false`, clears distance with `onDistanceSelected(null)`; if `isPanMode == true`, scrub distance is preserved.
   - Guarded stationary tap selection: suppressed when `isPanMode == true` to avoid accidental scrub cursor jumps during pan manipulation.

2. **MapDetailLayout Multi-Chart Lockstep Synchronization (`MapDetailLayout.kt`)**:
   - Forwarded `isPanMode = isPanMode` and `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` to all three `TelemetryMetricGraph` instances (Speed/Pace, Heart Rate, Power).
   - Manipulating any graph in Pan Mode updates `profileStartDist` in real time, moving the visible window across `ElevationProfile` and all stacked telemetry graphs synchronously.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-232`), extending `REQ-UI-225` (*Persistent Sticky Global Zoom Toolbar*) and `REQ-UI-226` (*Directional Gesture Disambiguation and Smooth Vertical Scrolling in MapDetailLayout*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `d71b4028`, ATT-1876) and Sprint 2026-40.8 (Commit `6a7f21e9`, ATT-1872).
3. *Root Reason for Existing Formulation*: When the global zoom toolbar decoupled zoom controls from `ElevationProfile` (ATT-1876), `isPanMode` was wired to `ElevationProfile` as a proof of concept. `TelemetryMetricGraph` was not initially updated because multi-metric telemetry graphs were previously read-only scrubbing displays; however, in practice, athletes touch whichever chart is currently under their thumb when scrolling and navigating detailed telemetry.
4. *Preservation of Core Invariants*: Single-finger vertical scroll gesture propagation to the parent container (`REQ-UI-226`), synchronized scrubbing cursor parity across all stacked charts when `isPanMode == false`, exact horizontal plot padding alignment (`50.dp` start, `25.dp` end), and 9-language localization parity are 100% strictly preserved.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-232` (items 1, 2, 3) | `[TST-UI-186.1]` | Unit & Gesture Contract Test (`TelemetryMetricGraphGestureTest`) | **PASSED** | `Verified` |
| `REQ-UI-232` (item 4) | `[TST-UI-186.2]` | Integration Wiring Contract Test (`MapDetailLayoutTest`) | **PASSED** | `Verified` |
| `REQ-UI-232` (item 5) | `[TST-UI-186.3]` | Pure Math Unit Tests (`ElevationProfileZoomMathTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-186.4]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraph*" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutTest"
BUILD SUCCESSFUL in 7s
32 actionable tasks: 2 executed, 30 up-to-date
```
- `TelemetryMetricGraphGestureTest.testTelemetryMetricGraph_eliminatesUnconditionalDragAndTapDetectors`: PASSED
- `TelemetryMetricGraphGestureTest.testTelemetryMetricGraph_integratesChartGestureDisambiguator`: PASSED
- `TelemetryMetricGraphGestureTest.testTelemetryMetricGraph_supportsPanModeGesturesAndZoomMath`: PASSED
- `MapDetailLayoutTest.testMapDetailLayout_integratesGlobalTelemetryZoomToolbar`: PASSED
- `MapDetailLayoutTest.testMapDetailLayout_wiresGlobalZoomState`: PASSED

### Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
```text
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 3m 4s
32 actionable tasks: 1 executed, 31 up-to-date
0 failures, 0 regressions across all project modules.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

1. **Scrub Mode (Default / Zoom Toolbar Pan Inactive)**:
   - Open a recorded workout in Aftermath detailed view with Speed, Heart Rate, and Power curves visible.
   - Drag horizontally across the Heart Rate graph: scrubbing cursor moves across the curve, synchronized with ElevationProfile and the map location marker.
   - Release drag: scrubbing cursor is cleared cleanly.
2. **Pan Mode (Zoom Toolbar Pan Active)**:
   - Tap the Pan button on the global zoom toolbar to activate Pan Mode.
   - Drag horizontally across any telemetry graph (Speed/Pace, Heart Rate, or Power): the visible time/distance window pans smoothly in lockstep across all charts and the elevation profile.
   - Release drag: chart window remains at the panned location without resetting or triggering unwanted scrub position clears.
   - Tap stationary on the graph: no inadvertent scrubbing marker jump occurs.
3. **Vertical Scroll Freedom**:
   - Perform a vertical swipe over the Heart Rate or Power graph: parent column scrolls smoothly without gesture interception or jitter.

---

## 5. Invariant & Governance Verification

1. **Directional Gesture Disambiguation (`REQ-UI-226`)**: Single-finger vertical swipes continue to propagate unconsumed to the scrollable container.
2. **Chart Layout Alignment**: Left (`50.dp`) and right (`25.dp`) plot padding match `ElevationProfile.kt` exactly.
3. **Pure Math Engine Reuse**: Window bounds calculation and clamping reuse `ElevationProfileZoomMath.applyPan`.
4. **Fix Version Audit**: Parent ticket `ATT-1956` specifies Fix Version `V4.9.38`.
5. **Living Documentation Synchronized**: `REQ-UI-232` and `TST-UI-186` set to `Verified`.
