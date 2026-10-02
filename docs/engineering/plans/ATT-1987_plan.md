# Stage 3: Implementation Plan - ATT-1987: [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs

**Ticket**: [ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)  
**Sub-task**: [ATT-1992](https://rainerblind.atlassian.net/browse/ATT-1992) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1895](https://rainerblind.atlassian.net/browse/ATT-1895) (*Phase 3: Visual & UX Polish (2026 Q4)*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Requirement Mapping**: `REQ-UI-232`  
**Test Mapping**: `TST-UI-190`  
**Branch**: `feature/ATT-1987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

During on-device physical testing on Pixel 10 hardware, user reported:
> *"When I touch, I can move it a little bit but not more."*

Forensic investigation revealed the root cause:
In `TelemetryMetricGraph.kt` (lines 407–411) and `ElevationProfile.kt` (lines 405–406), `pointerInput` declared continuously mutable running parameters (`startDist`, `currentStartDist`, `zoomScale`, `currentZoomScale`) directly as keys:
```kotlin
.pointerInput(totalSpan, isTimeDomain, zoomScale, startDist, isPanMode)
```
and
```kotlin
baseCanvasModifier.pointerInput(totalSpan, isTimeDomain, isPanMode, currentZoomScale, currentStartDist)
```

In Jetpack Compose, modifying any key of `Modifier.pointerInput` immediately cancels the active gesture coroutine and relaunches it. On the very first drag event (frame 1), `applyPan` invoked `onZoomChanged`, which mutated `profileStartDist` in `MapDetailLayout`. This recomposed the graph with an updated `startDist`.
The key change cancelled the running coroutine and restarted `awaitEachGesture`. Upon restart:
1. `isDragging` was reset to `false`.
2. `prevX` was re-initialized to the finger's current position mid-swipe.
3. The gesture was forced to re-disambiguate horizontal vs. vertical travel past `viewConfiguration.touchSlop`, or if `awaitFirstDown` waited for an unconsumed down event, the remaining move events of the swipe were discarded.

This resulted in sluggish, stuttering travel ("a little bit but not more") instead of continuous 60/120fps fluid panning.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-232` (*Interactive Pan & Zoom Telemetry Synchronization*)
  - Decouple mutable zoom parameters (`startDist`, `zoomScale`) from `pointerInput` coroutine lifecycle keys.
  - Maintain smooth continuous panning across entire horizontal swipe distance without coroutine restarts.
* **Test Mapping**: `TST-UI-190` (*Interactive Pan & Zoom Telemetry Verification*)
  - Verify that `pointerInput` in both `TelemetryMetricGraph.kt` and `ElevationProfile.kt` is keyed exclusively on structural domain keys (`totalSpan`, `isTimeDomain`, `isPanMode`).
  - Verify continuous accumulated drag tracking via `localStartDist` initialized from `rememberUpdatedState`.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites (scrubbing, time domain selection, zone rendering, pinch-to-zoom on elevation profile) continue to pass cleanly.
2. **Gesture Disambiguation Invariance**: Vertical scrolling disambiguation (`ChartGestureDisambiguator`) remains intact, yielding unconsumed vertical touch events to the parent scroll container.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.
5. **Programmatic Pre-Check Gate**: Gate 3 verification via `tools/jira_util.py check-gate ATT-1992` must pass before any production code edits.

---

## 4. Proposed Architectural Changes

### Component 1: `TelemetryMetricGraph.kt`
* **Current State**: Keyed on `pointerInput(totalSpan, isTimeDomain, zoomScale, startDist, isPanMode)`.
* **Target Architecture**:
  1. Capture mutable states using `rememberUpdatedState`:
     ```kotlin
     val currentStartDistState by rememberUpdatedState(startDist)
     val currentZoomScaleState by rememberUpdatedState(zoomScale)
     val currentOnZoomChangedState by rememberUpdatedState(onZoomChanged)
     val currentOnDistanceSelectedState by rememberUpdatedState(onDistanceSelected)
     ```
  2. Stabilize `pointerInput` keys:
     ```kotlin
     Modifier.pointerInput(totalSpan, isTimeDomain, isPanMode)
     ```
  3. Inside `awaitEachGesture`:
     - Initialize `var localStartDist = currentStartDistState` at touch down.
     - On each drag event:
       ```kotlin
       val panStart = ElevationProfileZoomMath.applyPan(
           currentStartDist = localStartDist,
           panDeltaX = dragDeltaX,
           visibleDist = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, currentZoomScaleState),
           canvasWidth = chartWidthPx,
           totalDist = totalSpan
       )
       localStartDist = panStart
       currentOnZoomChangedState?.invoke(currentZoomScaleState, panStart)
       ```
     - For scrub mode (`!isPanMode`), evaluate using `currentStartDistState`.

### Component 2: `ElevationProfile.kt`
* **Current State**: Keyed on `pointerInput(totalSpan, isTimeDomain, isPanMode, currentZoomScale, currentStartDist)`.
* **Target Architecture**:
  1. Capture mutable states using `rememberUpdatedState`:
     ```kotlin
     val currentStartDistState by rememberUpdatedState(currentStartDist)
     val currentZoomScaleState by rememberUpdatedState(currentZoomScale)
     val currentUpdateZoomState by rememberUpdatedState(updateZoom)
     val currentOnSelectedPointState by rememberUpdatedState(onSelectedPointChanged)
     ```
  2. Stabilize `pointerInput` keys:
     ```kotlin
     baseCanvasModifier.pointerInput(totalSpan, isTimeDomain, isPanMode)
     ```
  3. Inside `awaitEachGesture`:
     - Initialize `var localStartDist = currentStartDistState` and `var localZoom = currentZoomScaleState` at touch down.
     - On multi-touch pinch-to-zoom:
       Accumulate `localZoom = newZoom`, `localStartDist = panStart`, invoke `currentUpdateZoomState(newZoom, panStart)`.
     - On 1-finger pan:
       ```kotlin
       val panStart = ElevationProfileZoomMath.applyPan(
           currentStartDist = localStartDist,
           visibleDist = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, currentZoomScaleState),
           panDeltaX = dragDeltaX,
           canvasWidth = chartWidthPx,
           totalDist = totalSpan
       )
       localStartDist = panStart
       currentUpdateZoomState(currentZoomScaleState, panStart)
       ```
     - For scrub mode (`!isPanMode`), evaluate using `currentStartDistState`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 status of `ATT-1992` via `python3 tools/jira_util.py check-gate ATT-1992`.

### Step 2: Refactor Gesture Handling in `TelemetryMetricGraph.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* Changes:
  - Add `rememberUpdatedState` declarations for `startDist`, `zoomScale`, `onZoomChanged`, and `onDistanceSelected`.
  - Strip `startDist` and `zoomScale` from `pointerInput` keys.
  - Track `localStartDist` across drag events in `awaitEachGesture`.

### Step 3: Refactor Gesture Handling in `ElevationProfile.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* Changes:
  - Add `rememberUpdatedState` declarations for `currentStartDist`, `currentZoomScale`, `updateZoom`, and `onSelectedPointChanged`.
  - Strip `currentStartDist` and `currentZoomScale` from `pointerInput` keys.
  - Track `localStartDist` and `localZoom` across gesture events in `awaitEachGesture`.

### Step 4: Update Contract and Gesture Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphGestureTest.kt`
* Changes:
  - Update `testTelemetryMetricGraph_supportsPanModeGesturesAndZoomMath` to assert decoupled `pointerInput` keys:
    `pointerInput(totalSpan, isTimeDomain, isPanMode)`.
  - Add test verifying `rememberUpdatedState` integration and `localStartDist` accumulation.

### Step 5: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction (`TelemetryMetricGraphGestureTest`, `ElevationProfileGestureTest`, `MapDetailLayoutTest`), followed by clean-room suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-1987` allows full revert via `git reset --hard origin/sprint/2026-40.10` without affecting the integration branch.
