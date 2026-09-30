# Stage 1 Problem Domain & Root Cause Analysis: ATT-1647

**Ticket**: [ATT-1647](https://atrainingtracker.atlassian.net/browse/ATT-1647)  
**Sub-task**: [ATT-1674](https://atrainingtracker.atlassian.net/browse/ATT-1674) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1647`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & User Feedback

During the Sprint Review of `2026-40.4` following the delivery of ticket `ATT-527` (*Elevation Graph: Support zooming* / `REQ-UI-192`), the user identified two visual and interaction defects in the elevation profile component ([ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt)):

1. **Unwanted Zoom Controls in List Item Previews**:
   - In compact list items across the app—specifically Workout Summary cards in history ([WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)), Route cards ([RouteItem.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt)), and Segment cards ([SegmentItem.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/SegmentItem.kt))—the full suite of interactive zoom controls (`+`, `-`, Pan/Scrub mode toggle, and zoom scale reset badge) is overlaid directly on top of the miniature chart.
   - These preview cards are meant strictly for passive scanning and list navigation. Tapping on a preview card is designed to open the full detailed map inspection screen (`onMapClick`). Having interactive zoom buttons on list cards clutters the UI, intercepts click events, and creates gesture ambiguity while scrolling the `LazyColumn`.
   - **Requirement**: Interactive zoom and pan controls must strictly be confined to full/detailed views (e.g. `MapDetailLayout.kt` for Workout Details, Route Inspection, and Segment Inspection) and suppressed in compact list item previews.

2. **Scrubber Text & Control Button Collision / Overlap**:
   - In detailed inspection views where the interactive scrubber and zoom controls are active, dragging the scrubber marker toward the left edge of the chart (near the start of the activity) causes the text label (`distanceFormatter | altitudeFormatter`) to collide and overlap directly with the overlay control buttons (`+`, `-`, Pan).
   - This occurs because both the control buttons (rendered in an overlay `Row` at `top = 2.dp`) and the scrubber text label (drawn at canvas coordinate `y = -15f`, which maps into the `top = 24.dp` padding of the canvas) occupy the exact same vertical band between `8.dp` and `26.dp`.
   - **Requirement**: Adjust vertical anchoring, margins, or positioning so that the scrubber text and overlay control buttons never overlap under any zoom level or scrubber position.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 Control Buttons Overlaid on List Cards
- In ticket `ATT-527`, the zoom controls row was embedded directly into `ElevationProfile` with only a route distance guard:
  ```kotlin
  if (cachedData.totalDist > 10.0) {
      Row(
          modifier = Modifier
              .align(Alignment.TopStart)
              .padding(start = 50.dp, top = 2.dp),
          ...
      ) {
          IconButton(...) { /* Zoom In */ }
          IconButton(...) { /* Zoom Out */ }
          IconButton(...) { /* Toggle Pan/Scrub */ }
          if (zoomScale > 1.01f) { Surface(...) { /* Reset */ } }
      }
  }
  ```
- Because neither `ElevationProfile` composable overload accepted a parameter to control the visibility of zoom affordances, every single call site inherited the zoom buttons by default whenever `totalDist > 10m`.
- Furthermore, the `pointerInput` block on the `Canvas` unconditionally intercepts multi-touch and drag gestures, creating friction with `LazyColumn` scrolling when an athlete's finger brushes over an elevation profile in a list.

### 2.2 Root Cause of the Scrubber Text Collision
- In `ElevationProfile.kt`:
  - The outer container is a `Box(modifier = modifier.fillMaxWidth())`.
  - The `Canvas` applies padding: `.padding(bottom = 24.dp, start = 50.dp, end = 25.dp, top = 24.dp)`.
  - In Canvas coordinates, `(x=0, y=0)` is located at `x = 50.dp` and `y = 24.dp` from the top-left of the outer `Box`.
  - The zoom controls row is placed at `Alignment.TopStart` with `.padding(start = 50.dp, top = 2.dp)` and button size `24.dp`. It spans vertically from `2.dp` to `26.dp` from the top of the `Box`.
  - When the user scrubs, the scrubber text is rendered via:
    ```kotlin
    canvas.nativeCanvas.drawText(
        combinedLabel,
        (markerX - lWidth / 2f).coerceIn(0f, (width - lWidth).coerceAtLeast(0f)),
        -15f,
        highlightPaint
    )
    ```
  - In Android's native text painting, `-15f` px is the baseline. With `textSize = 32f` (~10.7sp), the text spans from `-47f` to `-15f` px relative to `y=0`.
  - In outer `Box` space (with `top = 24.dp` padding), this places the text at `24.dp - 15.6 dp ≈ 8.4 dp` to `19.0 dp`.
  - Because `8.4 dp .. 19.0 dp` falls directly inside the `2.dp .. 26.0 dp` footprint of the zoom controls row, any scrubber position with `markerX` in `[0f .. controlsWidthPx]` renders the text directly over the `+`, `-`, and Pan buttons.

---

## 3. Chesterton's Fence & Requirement Archaeology

1. **Origin of `REQ-UI-192` (`ATT-527`)**:
   - `REQ-UI-192` introduced pinch-to-zoom, horizontal pan, centroid scaling, and accessible buttons to prevent short climbs from being unreadable on long activities.
   - The zoom mathematics ([ElevationProfileZoomMath.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMath.kt)) and core viewport mechanics are robust and fully verified by unit tests ([ElevationProfileZoomMathTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTest.kt)).
2. **Intent of the Original Layout**:
   - Overlaying the zoom controls directly inside the chart's top margin was chosen to minimize overall vertical footprint. However, the author did not account for the scrubber text which also shared that top margin.
   - Similarly, omitting a visibility flag was an oversight that caused preview cards to inadvertently display full inspection controls.
3. **Preservation of Invariants**:
   - Viewport clamping ($Z \in [1.0f .. 10.0f]$, $D_{\text{start}} \in [0.0 .. D_{\text{total}} - W_{\text{vis}}]$) must remain intact.
   - Mathematical precision of distance-to-canvas coordinate translation must remain intact.
   - Full detailed views (`MapDetailLayout.kt`) must continue to provide full interactive zoom and scrub functionality.

---

## 4. Scope Bounding & Proposed Architecture

### 4.1 Parameterized Zoom Controls Visibility (`showZoomControls: Boolean = false`)
- Add `showZoomControls: Boolean = false` to both `ElevationProfile` composable overloads:
  - Defaulting to `false` ensures that list preview cards (`WorkoutSummary`, `RouteItem`, `SegmentItem`) and ambient displays (`SensorGridScreen`) remain clean, compact, and free from intrusive overlay buttons without touching every caller.
  - Detail inspection layouts (`MapDetailLayout.kt`) will explicitly pass `showZoomControls = true`.
- When `showZoomControls == false`:
  - The zoom controls row (`+`, `-`, pan toggle, reset badge) and legend button are completely suppressed.
  - The `Canvas` does NOT attach the gesture `pointerInput` modifier, allowing list scrolling and card `.clickable { onMapClick() }` to operate without gesture contention.
  - The canvas uses compact top padding (`top = 16.dp`).

### 4.2 Non-Overlapping Vertical Layout Architecture
- In detail inspection views where `showZoomControls == true`:
  - Constrain the controls row height explicitly (`24.dp`) at `top = 2.dp` (occupying vertical interval `2.dp .. 26.dp`).
  - Increase the `Canvas` top padding to `44.dp` (with `canvasHeight = cachedData.adaptiveHeight + 20.dp` to preserve exact drawable plotting height).
  - Anchor the scrubber text baseline at `y = -4.dp.toPx()`.
  - In outer container space:
    - Controls row: `2.dp .. 26.dp`
    - Scrubber text: `28.dp .. 40.dp`
    - Elevation curves: `44.dp .. height`
  - This guarantees a minimum 2dp clear separation between the bottom edge of the control buttons and the top edge of the scrubber text across all horizontal coordinates, eliminating overlap unconditionally.

---

## 5. Verification Strategy
1. **Unit Tests**:
   - Verify `ElevationProfile` composable parameter defaults (`showZoomControls = false`).
   - Create `ElevationProfileLayoutTest.kt` verifying that list items do not render zoom controls and that vertical padding bounds maintain separation.
2. **Clean-Room Regression**:
   - Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`).
