# Stage 3: Implementation Plan - ATT-1872: Resolve Gesture Conflict to Enable Smooth Vertical Scrolling of Graphs in MapDetailLayout

**Ticket**: [ATT-1872](https://rainerblind.atlassian.net/browse/ATT-1872)  
**Sub-task**: [ATT-1924](https://rainerblind.atlassian.net/browse/ATT-1924) (`[Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-226` (*Aftermath/Map: Directional Gesture Disambiguation and Smooth Vertical Scrolling in MapDetailLayout*)  
**Test Mapping**: `TST-UI-180`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-10-02  

---

## 1. Technical Architecture & Component Design

The objective is to eliminate gesture locking across all charts rendered in the scrollable lower viewport of `MapDetailLayout.kt`, ensuring vertical swipes scroll the viewport smoothly while horizontal drags scrub the data and 2-finger pinches scale the viewport.

### 1.1 Architecture Topology
```text
MapDetailLayout.kt
  ├── SplitPaneDivider.kt (Vertical dragging & reset)
  ├── GlobalTelemetryZoomToolbar.kt (Stationary pinned toolbar)
  └── lowerColumn (Modifier.verticalScroll(rememberScrollState()))
        ├── ElevationProfile.kt
        │     └── awaitEachGesture:
        │           ├── Multi-touch (>=2 pointers): Pinch zoom/pan (consumed)
        │           ├── Vertical single-finger drag (|Δy| > |Δx| > touchSlop): UNCONSUMED (scrolls page)
        │           ├── Horizontal single-finger drag (|Δx| >= |Δy| > touchSlop): CONSUMED (scrubs/pans chart)
        │           └── Sub-slop tap: selects point / double-tap reset
        ├── TelemetryMetricGraph.kt (Speed/Pace)
        │     └── awaitEachGesture:
        │           ├── Vertical single-finger drag (|Δy| > |Δx| > touchSlop): UNCONSUMED (scrolls page)
        │           ├── Horizontal single-finger drag (|Δx| >= |Δy| > touchSlop): CONSUMED (scrubs chart)
        │           └── Sub-slop tap: selects point
        ├── TelemetryMetricGraph.kt (Heart Rate)
        └── TelemetryMetricGraph.kt (Power)
```

---

## 2. Atomic Implementation Steps

### Step 1: Implement Pure Helper `ChartGestureDisambiguator.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ChartGestureDisambiguator.kt`
- Encapsulate mathematical calculations:
  - `fun isDominantVertical(diffX: Float, diffY: Float, touchSlop: Float): Boolean`:
    Returns `abs(diffY) > abs(diffX) && abs(diffY) > touchSlop`.
  - `fun isDominantHorizontal(diffX: Float, diffY: Float, touchSlop: Float): Boolean`:
    Returns `abs(diffX) >= abs(diffY) && abs(diffX) > touchSlop`.
  - `fun isSubSlop(diffX: Float, diffY: Float, touchSlop: Float): Boolean`:
    Returns `abs(diffX) <= touchSlop && abs(diffY) <= touchSlop`.

### Step 2: Refactor Pointer Input in `ElevationProfile.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
- Query `val touchSlop = viewConfiguration.touchSlop`.
- In single-pointer evaluation (`pressed.size == 1 && !isTransforming`):
  - Track `var isVerticalScrolling = false`.
  - If neither `isDragging` nor `isVerticalScrolling`:
    - Evaluate `ChartGestureDisambiguator.isDominantVertical(diffX, diffY, touchSlop)`:
      - If true, mark `isVerticalScrolling = true`. Do NOT consume pointer.
    - Evaluate `ChartGestureDisambiguator.isDominantHorizontal(diffX, diffY, touchSlop)`:
      - If true, mark `isDragging = true`.
  - If `isVerticalScrolling`:
    - Do NOT consume pointer (`pointer.consume()` is omitted).
    - Do NOT update scrubbing distance or pan offset.
    - If `pointer.isConsumed` becomes true (claimed by parent `verticalScroll`), break or yield cleanly.
  - If `isDragging`:
    - Consume pointer change (`pointer.consume()`).
    - Execute horizontal scrubbing or window panning.
  - On gesture release:
    - If `isDragging`: reset `onDistanceSelected(null)` (if `!isPanMode`).
    - Else if `isVerticalScrolling`: suppress tap inspection (do not select random points on swipe release).
    - Else: execute tap inspection or double-tap reset.

### Step 3: Refactor Pointer Input in `TelemetryMetricGraph.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
- Replace existing separate `detectTapGestures` + `detectDragGestures` with a single unified `awaitEachGesture` loop using `ChartGestureDisambiguator`:
  - Query `val touchSlop = viewConfiguration.touchSlop`.
  - Track `isDragging = false`, `isVerticalScrolling = false`.
  - In loop:
    - If `isDominantVertical(diffX, diffY, touchSlop)`: `isVerticalScrolling = true`. Do NOT consume pointer.
    - If `isDominantHorizontal(diffX, diffY, touchSlop)`: `isDragging = true`. Consume pointer (`pointer.consume()`). Update `onDistanceSelected`.
  - On release:
    - If `isDragging`: reset `onDistanceSelected(null)`.
    - Else if `!isVerticalScrolling`: select point at tap position.

### Step 4: Implement Pure Unit & Contract Tests
- `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ChartGestureDisambiguatorTest.kt`
- `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGestureTest.kt`
- `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphGestureTest.kt`

### Step 5: Test Execution & Clean-Room Regression
- Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "*ChartGestureDisambiguatorTest*" --tests "*ElevationProfileGestureTest*" --tests "*TelemetryMetricGraphGestureTest*"
  ```
- Run full regression suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Invariant Protection & Verification

1. **Horizontal Scrubbing Parity**: Scrubbing cursor line appears at identical horizontal position across all stacked charts.
2. **Pinch-to-zoom on graph surface**: Multi-pointer gestures on `ElevationProfile.kt` continue zooming/panning smoothly.
3. **SplitPane Dragging & Reset**: `SplitPaneDivider` vertical dragging and double-tap reset remain intact.
4. **Clean-Room Regression**: Full test suite `./gradlew testDebugUnitTest` must pass with 0 failures.
