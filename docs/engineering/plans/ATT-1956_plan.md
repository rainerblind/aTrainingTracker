# Stage 3: Implementation Plan - ATT-1956: [Aftermath/Graphs] Support Pan/Moving Gesture Across Speed, Heart Rate, and Power Graphs in Zoom Toolbar Pan Mode

**Ticket**: [ATT-1956](https://rainerblind.atlassian.net/browse/ATT-1956)  
**Sub-task**: [ATT-1968](https://rainerblind.atlassian.net/browse/ATT-1968) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*)  
**Test Mapping**: `TST-UI-186` (*Aftermath/Graphs: TelemetryMetricGraph Pan Mode Gesture & Lockstep Synchronization Verification*)  
**Branch**: `feature/ATT-1956`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In ticket ATT-1876 (`REQ-UI-225`), a persistent sticky global zoom toolbar (`GlobalTelemetryZoomToolbar.kt`) was introduced in `MapDetailLayout.kt`, adding an interactive toggle between Scrub Mode and Pan Mode (`isPanMode`). 

In Pan Mode, horizontal dragging currently only pans the visible window when touching `ElevationProfile`. The stacked `TelemetryMetricGraph` instances (Speed/Pace, Heart Rate, Power) located immediately below it continue to scrub telemetry values (`onDistanceSelected`), producing a jarring behavioral inconsistency. 

Under ATT-1956, all stacked graphs will participate in horizontal window panning in lockstep when Pan Mode is active.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*)
* **Test Mapping**: `TST-UI-186` (*Aftermath/Graphs: TelemetryMetricGraph Pan Mode Gesture & Lockstep Synchronization Verification*)
* **Living Documentation**: `docs/requirements.md` (`REQ-UI-232`) and `docs/tests.md` (`TST-UI-186`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All existing unit and visual contract tests in `TelemetryMetricGraphTest`, `TelemetryMetricGraphGestureTest`, and `MapDetailLayoutTest` must pass 100%.
2. **Backward Compatibility**: `TelemetryMetricGraph` default parameters (`isPanMode = false`, `onZoomChanged = null`) guarantee that un-instrumented callers (such as `WorkoutSummary.kt`) retain their exact signature and runtime behavior.
3. **Vertical Scroll Freedom (`REQ-UI-226`)**: Single-pointer vertical swipes ($|\Delta y| > |\Delta x|$ exceeding `touchSlop`) must remain unconsumed by the chart canvas, preserving effortless vertical scrolling of `lowerColumn`.
4. **Lockstep Synchrony**: Panning any stacked chart updates `profileStartDist` in `MapDetailLayout.kt`, moving the visible window across all charts and the map marker simultaneously.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing independent Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TelemetryMetricGraph.kt` (Presentation Layer)
* **Function Signature**:
  ```kotlin
  @Composable
  fun TelemetryMetricGraph(
      pathPoints: List<PathPoint>,
      metricType: TelemetryMetricType,
      currentDistance: Double?,
      onDistanceSelected: (Double?) -> Unit,
      modifier: Modifier = Modifier,
      xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
      bSportType: BSportType = BSportType.UNKNOWN,
      zoomScale: Float = 1.0f,
      startDist: Double = 0.0,
      isPanMode: Boolean = false,
      onZoomChanged: ((Float, Double) -> Unit)? = null,
      hrZoneThresholds: HeartRateZoneThresholds? = null,
      powerZoneThresholds: PowerZoneThresholds? = null
  )
  ```
* **Pointer Input Handler**:
  - Update `pointerInput(totalSpan, isTimeDomain, zoomScale, startDist, isPanMode)`.
  - Maintain `var prevX = down.position.x`.
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
        val localX = (pointer.position.x - startPaddingPx).coerceIn(0f, chartWidthPx)
        val selectedVal = ElevationProfileZoomMath.canvasXToDistance(
            canvasX = localX,
            startDist = startDist,
            visibleDist = visibleSpan,
            canvasWidth = chartWidthPx,
            totalDist = totalSpan
        )
        if (isTimeDomain) {
            val targetTimeSec = selectedVal.toLong()
            val nearest = pathPoints.minByOrNull { abs(it.timeSec - targetTimeSec) }
            onDistanceSelected(nearest?.distance)
        } else {
            onDistanceSelected(selectedVal)
        }
    }
    prevX = pointer.position.x
    ```
  - In gesture completion:
    - If `isDragging`: guard `onDistanceSelected(null)` with `if (!isPanMode)`.
    - If `!isVerticalScrolling`: guard tap selection with `if (!isPanMode)`.

### Component 2: `MapDetailLayout.kt` (Container Layer)
* Wire `isPanMode` and `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` to all three `TelemetryMetricGraph` invocations:
  - Speed / Pace Graph (line ~251)
  - Heart Rate Graph (line ~274)
  - Power Graph (line ~297)

### Component 3: Test Suites
* `TelemetryMetricGraphGestureTest.kt`:
  - Verify parameter signatures (`isPanMode`, `onZoomChanged`).
  - Verify `isPanMode` is in `pointerInput` keys.
  - Verify `applyPan` invocation and `onZoomChanged` dispatch in Pan Mode.
  - Verify `onDistanceSelected(null)` is guarded by `!isPanMode`.
* `MapDetailLayoutTest.kt`:
  - Assert that `isPanMode = isPanMode` occurs $\ge 4$ times in `MapDetailLayout.kt`.
  - Assert that `onZoomChanged` occurs $\ge 4$ times in `MapDetailLayout.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `TelemetryMetricGraph.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* Changes: Add `isPanMode` and `onZoomChanged` parameters; implement delta tracking and `applyPan` window panning in `pointerInput`.

### Step 2: Update `MapDetailLayout.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Changes: Pass `isPanMode = isPanMode` and `onZoomChanged = { z, s -> profileZoomScale = z; profileStartDist = s }` to Speed/Pace, HR, and Power `TelemetryMetricGraph` composables.

### Step 3: Extend Unit & Structural Contract Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphGestureTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
* Changes: Add assertions for pan mode parameter declaration, gesture disambiguation, and multi-graph wiring contracts.

### Step 4: Run Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraph*" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests during construction, verify compilation and contract assertions, followed by clean-room full test suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-1956` ensures any issue can be cleanly abandoned or reset back to `sprint/2026-40.9`.
