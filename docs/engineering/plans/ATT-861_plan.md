# Implementation Plan: Right-Aligned Edge FastScrollbar, Isolated Thumb Touch Target & Continuous Drag Accumulation (ATT-861)

* **Parent Ticket**: [ATT-861](https://rainerblind.atlassian.net/browse/ATT-861) (*[Bug] FastScrollbar full-height touch overlay intercepts clicks on right-aligned list item actions*)
* **Active Sub-Task**: [ATT-879](https://rainerblind.atlassian.net/browse/ATT-879) (*[Impl-Plan] FastScrollbar full-height touch overlay intercepts clicks on right-aligned list item actions*)
* **Target Version**: `V4.9.36`
* **Requirement**: [`REQ-UI-139`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L271) (*Right-Aligned Edge FastScrollbar, Isolated Thumb Touch Target & Continuous Drag Accumulation*)
* **Test Specification**: [`TST-UI-092`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L306) (*FastScrollbar Edge Alignment, Continuous Drag Accumulation & Action Non-Interference Verification*)
* **Branch**: `bugfix/ATT-861`

---

## 1. Overview & Architecture

This implementation plan resolves [ATT-861](https://rainerblind.atlassian.net/browse/ATT-861) and addresses user testing feedback from Iteration 1, where the scrollbar could not move the list under normal finger dragging.

### 1.1 Problem Summary & Root Causes
1. **Truncation & Loss of Drag Displacement**: In Iteration 1, `detectDragGestures` computed `deltaProgress = dragAmount.y / trackHeightPx` per 16ms frame (~2–10px) and calculated `(scrollProgress + deltaProgress) * totalItems`. Because `deltaProgress * totalItems` ($0.0025 \times 20 = 0.05$) is smaller than 1.0, `.toInt()` truncated the fraction to 0 on every single frame. Displacement was discarded continuously, leaving the list stuck at the current item.
2. **Frozen Visual Thumb**: The thumb position was derived purely from `scrollProgress` in `LazyListState`. Because the list never moved, the thumb stayed locked in place, producing an unresponsive UI feel.
3. **Narrow Hit Box (16dp)**: At 420dpi, 16dp is ~42px wide at the extreme display bezel, making it difficult for human thumbs (~40dp wide) to grab.
4. **Touch Overlay Interception (Original Bug)**: Prior to ATT-861, `pointerInput` on the outer full-height 32dp container intercepted clicks destined for list item cards (such as the 3-dots export menu and edit button).

### 1.2 Target Solution Architecture
1. **Continuous Drag Progress Accumulator**:
   - Introduce `isDragging: Boolean` and continuous `dragProgress: Float` ($0.0 \dots 1.0$).
   - On `onDragStart`: set `isDragging = true` and initialize `dragProgress = scrollProgress`.
   - On `onDrag`: continuously accumulate displacement:
     $$\text{dragProgress} = \left(\text{dragProgress} + \frac{\text{dragAmount.y}}{\text{maxOffsetPx}}\right)\!.coerceIn(0f, 1f)$$
     $$\text{targetIndex} = \left(\text{dragProgress} \times (\text{totalItems} - 1)\right)\!.roundToInt()$$
     `coroutineScope.launch { state.scrollToItem(targetIndex) }`
   - On `onDragEnd` / `onDragCancel`: set `isDragging = false`.
2. **Zero-Latency Visual Feedback**:
   - Drive the visual thumb offset using `effectiveProgress = if (isDragging) dragProgress else scrollProgress`.
   - The thumb tracks the user's finger with 1:1 precision and zero lag.
3. **Ergonomic Thumb Hit Box & Flush Edge Alignment**:
   - Set thumb touch hit box to **`28.dp`** width (`Alignment.TopEnd`), containing an 8dp visual pill (`Alignment.CenterEnd`).
   - `pointerInput` is attached strictly to the 48dp thumb Box. The remaining 95% of the track has zero gesture consumption, guaranteeing full click-through accessibility for card action buttons.
   - FastScrollbar in `WorkoutList.kt` maintains 0dp end padding, flush against the viewport edge.

---

## 2. Proposed Source Code Changes

### Component 1: `FastScrollbar.kt` Drag Accumulation & Touch Ergonomics
#### [MODIFY] [`FastScrollbar.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/FastScrollbar.kt)

1. **State-Driven Drag Tracking**:
   ```kotlin
   var isDragging by remember { mutableStateOf(false) }
   var dragProgress by remember { mutableFloatStateOf(0f) }

   LaunchedEffect(scrollProgress, isDragging) {
       if (!isDragging) {
           dragProgress = scrollProgress
       }
   }
   ```

2. **Thumb Offset & Touch Dimensions**:
   ```kotlin
   val effectiveProgress = if (isDragging) dragProgress else scrollProgress
   val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

   Box(
       modifier = Modifier
           .offset {
               IntOffset(0, (effectiveProgress * maxOffsetPx).roundToInt().coerceIn(0, maxOffsetPx.roundToInt()))
           }
           .height(thumbHeightDp)
           .width(28.dp) // Generous hit box for effortless thumb grabbing
           .align(Alignment.TopEnd)
           .pointerInput(state) {
               detectDragGestures(
                   onDragStart = {
                       isDragging = true
                       dragProgress = scrollProgress
                   },
                   onDragEnd = { isDragging = false },
                   onDragCancel = { isDragging = false },
                   onDrag = { change, dragAmount ->
                       change.consume()
                       val totalItemsCount = state.layoutInfo.totalItemsCount
                       val travelDistancePx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                       if (totalItemsCount > 0) {
                           dragProgress = calculateAccumulatedProgress(dragProgress, dragAmount.y, travelDistancePx)
                           val targetIndex = calculateTargetIndexFromProgress(dragProgress, totalItemsCount)
                           coroutineScope.launch {
                               state.scrollToItem(targetIndex)
                           }
                       }
                   }
               )
           }
   ) {
       // Visual Thumb Pill (8dp width, aligned flush to right edge)
       Box(
           modifier = Modifier
               .fillMaxHeight()
               .width(8.dp)
               .clip(CircleShape)
               .background(thumbColor)
               .align(Alignment.CenterEnd)
       )
   }
   ```

3. **Pure Math Helper Functions**:
   ```kotlin
   fun calculateAccumulatedProgress(
       currentProgress: Float,
       dragDeltaY: Float,
       trackLengthPx: Float
   ): Float {
       if (trackLengthPx <= 0f) return currentProgress.coerceIn(0f, 1f)
       return (currentProgress + dragDeltaY / trackLengthPx).coerceIn(0f, 1f)
   }

   fun calculateTargetIndexFromProgress(
       progress: Float,
       totalItems: Int
   ): Int {
       if (totalItems <= 0) return 0
       return (progress.coerceIn(0f, 1f) * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
   }
   ```

---

### Component 2: Automated Unit Tests
#### [MODIFY] [`FastScrollbarTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/FastScrollbarTest.kt)

1. Test small delta accumulation:
   * Simulate 60 frames of 5px deltas over a 2000px track; verify `dragProgress` advances from 0.0 to 0.15 and increments `targetIndex` smoothly.
2. Test progress-to-index mapping:
   * Progress 0.0 maps to index 0.
   * Progress 1.0 maps to index `totalItems - 1`.
   * Mid-range progress maps proportionally with rounding.
3. Test boundary clamping:
   * Negative deltas clamp at 0.0 / index 0.
   * Overflow deltas clamp at 1.0 / index `totalItems - 1`.
   * Edge cases: `totalItems = 0`, `totalItems = 1`, `trackLengthPx <= 0`.

---

## 3. Verification Plan

### Automated Tests
1. Unit test suite for fast scrollbar math:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.FastScrollbarTest"
   ```
2. Clean-room regression suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### On-Device Hardware Verification (Google Pixel 10)
1. Deploy build: `./gradlew installDebug`.
2. Verify list action accessibility:
   * Tap 3-dots export menu at `(1001, 2212)` -> popup menu opens immediately.
   * Tap edit button at `(900, 2212)` -> editor opens immediately.
3. Verify human finger scroll responsiveness:
   * Perform a slow, normal finger drag on the 28dp thumb hit box (~5px per frame).
   * Confirm thumb moves with zero visual lag.
   * Confirm `LazyColumn` scrolls smoothly from first item down through all items.
4. Capture screenshots and document test evidence in walkthrough.
