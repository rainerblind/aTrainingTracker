# Implementation Plan: Right-Aligned Edge FastScrollbar & Isolated Thumb Touch Target (ATT-861)

## 1. Overview & Architecture
This plan resolves [ATT-861](https://rainerblind.atlassian.net/browse/ATT-861) and implements user feedback requesting the fast scrollbar to be moved flush to the very right of the viewport.

### 1.1 Problem Summary
In [`WorkoutList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt), [`FastScrollbar`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/FastScrollbar.kt) currently sits 18dp–22dp inward from the right display edge due to a combination of:
1. `.padding(end = 4.dp)` in `WorkoutList.kt:123`.
2. Centering a 4dp track and 8dp thumb horizontally inside a 32dp container Box in `FastScrollbar.kt:90-136`.

Crucially, `pointerInput(state) { detectDragGestures { ... } }` is attached to the **entire full-height 32dp container Box**, intercepting and consuming tap and drag events along the rightmost 36dp column of the display. This blocks taps directed at card action buttons (such as the 3-dots export menu and edit button in [`WorkoutHeader.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)) across the entire vertical height of the list, even when the scrollbar thumb is located elsewhere.

### 1.2 Target Solution
1. **Move Flush to the Right Edge**:
   - In `WorkoutList.kt`, remove `.padding(end = 4.dp)` so that `FastScrollbar` aligns flush to `Alignment.CenterEnd`.
   - In `FastScrollbar.kt`, reduce container width (e.g. 16dp), and right-align both track and thumb to `Alignment.CenterEnd` and `Alignment.TopEnd`.
   - Because `LazyColumn` uses `contentPadding(end = 8.dp)`, list cards end at $\text{screenWidth} - 8\text{dp}$. Positioning the scrollbar within the rightmost 0–8dp bounds physically isolates it from card surfaces and button click areas.
2. **Isolate Gesture Detection Strictly to the Draggable Thumb**:
   - Move `pointerInput(state) { detectDragGestures { ... } }` from the outer full-height container Box to the draggable thumb Box.
   - The thumb Box provides an ergonomic touch target (e.g. width 16dp–20dp, height 48dp), while the full track above and below the thumb remains completely click-through.
   - Tapping any card action button outside the active thumb passes directly to the button without interference.
3. **Extract Pure Calculation Logic for Robust Unit Testing**:
   - Extract `calculateScrollProgress(...)` and `calculateTargetIndex(...)` as testable utility functions in `FastScrollbar.kt`.

### 1.3 Traceability
- **Requirement**: [`REQ-UI-139`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L271) (*Right-Aligned Edge FastScrollbar & Isolated Thumb Touch Target*)
- **Test Specification**: [`TST-UI-092`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L306) (*FastScrollbar Edge Alignment & Action Button Non-Interference Verification*)

---

## 2. Proposed Source Code Changes

### Component 1: `WorkoutList.kt` Edge Alignment
#### [MODIFY] [`WorkoutList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt)
- In the `FastScrollbar` invocation (lines 118–125):
  - Remove `.padding(end = 4.dp)`.
  - Maintain `.align(Alignment.CenterEnd)` and vertical insets (`.padding(top = topPadding, bottom = bottomPadding)`).

```kotlin
// Fast Scroll Bar (ATT-303, ATT-861)
FastScrollbar(
    state = scrollState,
    modifier = Modifier
        .align(Alignment.CenterEnd)
        .padding(top = topPadding, bottom = bottomPadding)
)
```

---

### Component 2: `FastScrollbar.kt` Architecture & Isolated Thumb Dragging
#### [MODIFY] [`FastScrollbar.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/FastScrollbar.kt)
1. **Extract Testable Pure Functions**:
   ```kotlin
   fun calculateScrollProgress(firstVisibleIndex: Int, firstVisibleOffset: Int, itemSize: Int, totalItems: Int): Float {
       if (totalItems <= 0 || itemSize <= 0) return 0f
       val progress = (firstVisibleIndex.toFloat() + firstVisibleOffset.toFloat() / itemSize) / totalItems.toFloat()
       return progress.coerceIn(0f, 1f)
   }

   fun calculateTargetIndex(currentProgress: Float, deltaProgress: Float, totalItems: Int): Int {
       if (totalItems <= 0) return 0
       val newProgress = (currentProgress + deltaProgress).coerceIn(0f, 1f)
       return (newProgress * totalItems).toInt().coerceIn(0, totalItems - 1)
   }
   ```
2. **Outer Container Adjustments**:
   - Container width reduced from `32.dp` to `16.dp` (compact margin).
   - Remove `pointerInput(state) { detectDragGestures { ... } }` from the outer Box.
   - Retain `.alpha(alpha)` and `.onGloballyPositioned { trackHeightPx = it.size.height }`.
3. **Track Alignment**:
   - Track `Box`: `width(4.dp)`, `background(trackColor)`, aligned to `Alignment.CenterEnd`.
4. **Draggable Thumb & Gesture Attachment**:
   - Thumb `Box`:
     - Aligned to `Alignment.TopEnd`.
     - Offset derived from `scrollProgress * (trackHeightPx - thumbHeightPx)`.
     - Height: `48.dp`, Width: `16.dp` (touch target) with inner or direct visual shape `width(8.dp)`.
     - Attach `pointerInput(state) { detectDragGestures { ... } }` strictly to this thumb Box:
       ```kotlin
       .pointerInput(state) {
           detectDragGestures(
               onDrag = { change, dragAmount ->
                   change.consume()
                   val totalItemsCount = state.layoutInfo.totalItemsCount
                   if (trackHeightPx > 0 && totalItemsCount > 0) {
                       val deltaProgress = dragAmount.y / trackHeightPx
                       val targetIndex = calculateTargetIndex(scrollProgress, deltaProgress, totalItemsCount)
                       coroutineScope.launch {
                           state.scrollToItem(targetIndex)
                       }
                   }
               }
           )
       }
       ```

---

### Component 3: Unit Tests for FastScrollbar Logic
#### [NEW] [`FastScrollbarTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/FastScrollbarTest.kt)
- Unit tests verifying:
  1. `calculateScrollProgress` with 0 items, single item, boundary offsets, and multi-item progress.
  2. `calculateTargetIndex` with downward drag, upward drag, boundary clamping at index 0 and index $N-1$.
  3. Progress coercing between `0f` and `1f`.

---

## 3. Invariants & System Integrity ("What MUST NOT Change")
1. **Dynamic Alpha Transition**: FastScrollbar's 500ms alpha fade (0.4f idle to 1.0f active scroll) MUST remain intact.
2. **Card Layout & Click Handling**: Padding, elevation, and touch handlers on `WorkoutSummary`, `WorkoutSummaryCompact`, and `WorkoutHeader` MUST remain completely unaltered.
3. **Smooth Coroutine Scrolling**: `state.scrollToItem(targetIndex)` MUST continue to scroll smoothly and responsively without lag or deadlocks.

---

## 4. Verification Plan

### Automated Tests
- Run unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.FastScrollbarTest"
  ```
- Run full regression suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

### On-Device Verification (Pixel 10)
1. Install debug build on device:
   ```bash
   ./gradlew installDebug
   ```
2. Open Workout History:
   - Verify fast scrollbar sits flush against the right edge of the screen.
   - Verify scrollbar thumb and track do not overlap or float awkwardly over workout cards.
3. Verify Action Button Non-Interference:
   - Tap the 3-dots export menu button on various workout cards (at the top, middle, and bottom of the screen): verify the export menu opens immediately every time.
   - Tap the edit button on workout cards: verify `EditWorkoutScreen` opens immediately.
4. Verify Thumb Dragging:
   - Grab the thumb directly and drag vertically: verify list scrolls smoothly to match thumb movement.
