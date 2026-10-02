# Stage 1: Problem Domain & Root Cause Analysis - ATT-1872: Resolve Gesture Conflict to Enable Smooth Vertical Scrolling of Graphs in MapDetailLayout

**Ticket**: [ATT-1872](https://rainerblind.atlassian.net/browse/ATT-1872)  
**Sub-task**: [ATT-1922](https://rainerblind.atlassian.net/browse/ATT-1922) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Investigator)  
**Date**: 2026-10-02  

---

## 1. Problem Domain & Forensic Investigation

### 1.1 The Reported Defect
During on-device testing and verification on physical devices (e.g., Pixel 10), scrolling through the lower analytics section of `MapDetailLayout` is almost impossible. Athletes attempting to scroll down to view Heart Rate, Speed/Pace, Power graphs, lap splits, or workout summaries find their swipe gestures trapped and frozen by the charts. The only way to scroll is to precisely locate narrow spacer gaps between chart cards.

### 1.2 Anatomy of the Lower Viewport
In `MapDetailLayout.kt`, the lower viewport is wrapped in:
```kotlin
lowerColumn(
    Modifier
        .weight(1f - splitFraction)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
)
```
Inside `lowerColumn`, four consecutive chart composables are rendered:
1. `ElevationProfile` (adaptive height: 140–180 dp)
2. `TelemetryMetricGraph` (Speed / Pace: ~140 dp with heading)
3. `TelemetryMetricGraph` (Heart Rate: ~140 dp with heading)
4. `TelemetryMetricGraph` (Power: ~140 dp with heading)

The stacked height of these charts exceeds 500 dp, occupying virtually 100% of the visible scrollable viewport.

### 1.3 Root Cause Analysis

#### Root Cause 1: `ElevationProfile.kt` Direction-Agnostic Drag Threshold & Unconditional Consumption
In `ElevationProfile.kt` (lines 448–460):
```kotlin
} else if (pressed.size == 1 && !isTransforming) {
    val pointer = pressed[0]
    val diffX = pointer.position.x - down.position.x
    val diffY = pointer.position.y - down.position.y
    if (!isDragging && (diffX * diffX + diffY * diffY > 64f)) {
        isDragging = true
    }

    if (isDragging) {
        val dragDeltaX = pointer.position.x - prevCentroid.x
        pointer.consume()
        ...
```
- **The Defect**: `diffX * diffX + diffY * diffY > 64f` triggers whenever the pointer moves more than $\sqrt{64} = 8\text{ px}$ in **any direction**.
- If an athlete swipes vertically ($\Delta y = 20\text{ px}, \Delta x = 1\text{ px}$), `isDragging` immediately becomes `true`.
- On every event thereafter, `pointer.consume()` is called unconditionally.
- In Jetpack Compose gesture dispatch, parent containers (`Modifier.verticalScroll`) evaluate `change.isConsumed`. Because `pointer.consume()` was invoked by the child, the parent vertical scroll gesture is starved of unconsumed delta and fails to initiate scrolling.

#### Root Cause 2: `TelemetryMetricGraph.kt` Direction-Agnostic `detectDragGestures`
In `TelemetryMetricGraph.kt` (lines 353–373):
```kotlin
detectDragGestures(
    onDrag = { change, _ ->
        change.consume()
        ...
```
- **The Defect**: `detectDragGestures` from `androidx.compose.foundation.gestures` detects drag after touch slop in **any direction** (both horizontal and vertical).
- When a vertical swipe occurs over any of the 3 telemetry graphs, `detectDragGestures` consumes the pointer event immediately upon exceeding touch slop.
- Parent `Modifier.verticalScroll` is starved, completely locking vertical scrolling.
- Furthermore, `TelemetryMetricGraph` also registers a separate `detectTapGestures(onPress = { ... })` block that eagerly selects data points on initial touch down before swipe intention is even established.

---

## 2. Chesterton's Fence & Requirement Archaeology

1. **Why was `diffX * diffX + diffY * diffY > 64f` introduced?**
   - Introduced in `ElevationProfile.kt` to distinguish a stationary tap (which selects an inspection point or double-taps to reset zoom) from a drag (which continuously scrubs the elevation profile).
   - *Intention*: Separate tap from drag.
   - *Flaw*: It failed to differentiate between horizontal dragging (scrubbing/panning) and vertical dragging (viewport scrolling).

2. **Why was `detectDragGestures` used in `TelemetryMetricGraph.kt`?**
   - Introduced in Sprint 2026-40.5 (`ATT-1740`) as a quick implementation of touch scrubbing.
   - At the time, continuous graphs were displayed below `ElevationProfile`, but comprehensive on-device scrolling verification across multiple stacked graphs was deferred.

3. **Core Invariants That Must Be Preserved**:
   - **Horizontal Scrubbing Parity**: Dragging horizontally must continue to update the scrubbing cursor synchronously across all charts and update the map marker.
   - **Pinch-to-Zoom Parity**: 2-finger pinch gestures on `ElevationProfile` must continue to zoom and pan.
   - **Tap Inspection Parity**: Tapping without dragging must continue to select inspection points or double-tap reset.
   - **No Regressions in List Previews**: Compact list previews (`RouteItem`, `SegmentItem`, `WorkoutSummary`) must continue to scroll effortlessly.

---

## 3. Scope Bounding & Proposed Architecture

### 3.1 Directional Gesture Disambiguation Strategy
To resolve the conflict while strictly preserving horizontal scrubbing and tap inspection, we adopt a directional slope disambiguation model:

```text
                                [Pointer Down]
                                       │
                         [Calculate Δx, Δy from down]
                                       │
                      ┌────────────────┴────────────────┐
                      ▼                                 ▼
             √(Δx² + Δy²) < touchSlop          √(Δx² + Δy²) ≥ touchSlop
                      │                                 │
             [Pending Intent]                           │
             (Do not consume)              ┌────────────┴────────────┐
                      │                    ▼                         ▼
                      │              |Δy| > |Δx|                |Δx| ≥ |Δy|
                      │           (Vertical Movement)       (Horizontal Movement)
                      │                    │                         │
                      │          [Vertical Scroll Wins]     [Horizontal Scrub Wins]
                      │          • Do NOT consume event     • Consume event (change.consume())
                      │          • Yield to verticalScroll  • Update scrub / pan
                      │          • Cancel tap inspection    • Lock out vertical scroll
                      │
                      ▼
               [Pointer Up]
                      │
         ┌────────────┴────────────┐
         ▼                         ▼
  Never exceeded slop         Was Vertical Drag
   (Intentional Tap)          (Ignore Tap Selection)
         │
   [Select Point]
```

### 3.2 Pure Mathematical & Logic Helper: `ChartGestureDisambiguator`
To avoid code duplication and enable 100% unit-testable gesture direction logic:
- Encapsulate gesture classification into a pure utility:
  - `isDominantHorizontal(diffX: Float, diffY: Float, touchSlop: Float): Boolean`
  - `isDominantVertical(diffX: Float, diffY: Float, touchSlop: Float): Boolean`
- In `ElevationProfile.kt`:
  - When `pressed.size == 1`:
    - Calculate `abs(diffX)` and `abs(diffY)` relative to initial down position.
    - If `abs(diffY) > abs(diffX)` and `abs(diffY) > touchSlop`: mark `isVerticalScrolling = true`, do not consume, do not scrub, allow parent to scroll.
    - If `abs(diffX) >= abs(diffY)` and `abs(diffX) > touchSlop`: mark `isDragging = true`, consume pointer change, execute horizontal scrubbing or pan.
    - If pointer released without exceeding slop: perform tap inspection. If `isVerticalScrolling` was true, suppress tap inspection.
- In `TelemetryMetricGraph.kt`:
  - Replace `detectDragGestures` with `detectHorizontalDragGestures` or a matching `awaitEachGesture` loop with directional disambiguation.
  - Ensure vertical drags are never consumed, allowing parent `verticalScroll` to smoothly scroll.

---

## 4. Verification & Gate 1 Criteria

- [x] Forensic investigation identifies the exact lines consuming vertical drag events in `ElevationProfile.kt` and `TelemetryMetricGraph.kt`.
- [x] Chesterton's fence established: preserving pinch-to-zoom, tap inspection, and synchronized multi-metric scrubbing.
- [x] Clear directional disambiguation strategy formulated ($|\Delta x| \ge |\Delta y|$ vs $|\Delta y| > |\Delta x|$ relative to `touchSlop`).
- [x] Subtask `ATT-1922` audited and passed Gate 1.
