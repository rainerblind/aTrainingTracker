# Stage 3 Implementation Plan: ATT-1647

**Ticket**: [ATT-1647](https://atrainingtracker.atlassian.net/browse/ATT-1647)  
**Sub-task**: [ATT-1676](https://atrainingtracker.atlassian.net/browse/ATT-1676) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1647`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Traceability & Scope Matrix
* **Requirements Traced**: `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Scrubber Text Architecture*)
* **Tests Traced**: `TST-UI-151` (*Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Scrubber Layout Verification*)
* **Scope Definition**:
  1. Add `showZoomControls: Boolean = false` parameter to both `ElevationProfile` composables in `ElevationProfile.kt`.
  2. Conditionally attach `Modifier.pointerInput` only when `showZoomControls == true` to prevent scroll friction and parent card click suppression in list previews.
  3. Conditionally render the zoom controls row (`+`, `-`, Pan/Scrub toggle, reset badge) and the legend info icon button only when `showZoomControls == true`.
  4. Implement non-overlapping vertical architecture:
     - When `showZoomControls == true`: Controls row at `top = 2.dp` (height `24.dp`, occupying `2.dp .. 26.dp`); Canvas `topPadding = 44.dp` (total canvas height = `cachedData.adaptiveHeight + 20.dp` to preserve exact drawable plotting height); Scrubber marker label baseline anchored at `y = -4.dp.toPx()`, positioning the text label in vertical interval `28.dp .. 40.dp`. This guarantees a minimum 2dp clear separation above the text and below the controls row, preventing text collision even at horizontal coordinate `x = 0`.
     - When `showZoomControls == false`: Canvas `topPadding = 16.dp` with height = `cachedData.adaptiveHeight`.
  5. Update `MapDetailLayout.kt` to explicitly pass `showZoomControls = true`.
  6. Author comprehensive unit test suite `ElevationProfileLayoutTest.kt` verifying default parameters, layout values, and call site contracts.

---

## 2. Step-by-Step Atomic Implementation Tasks

### Task 1: Update Parameterization and Layout in `ElevationProfile.kt`
- File: [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt)
- Add `showZoomControls: Boolean = false` to both `ElevationProfile` overloads:
  ```kotlin
  @Composable
  fun ElevationProfile(
      encodedAltitudes: String,
      encodedDistances: String,
      currentDistance: Double? = null,
      minAltitudeOverride: Double? = null,
      maxAltitudeOverride: Double? = null,
      onDistanceSelected: (Double?) -> Unit = {},
      showZoomControls: Boolean = false,
      modifier: Modifier = Modifier
  )
  ```
  and
  ```kotlin
  @Composable
  fun ElevationProfile(
      pathPoints: List<PathPoint>,
      currentDistance: Double?,
      minAltitudeOverride: Double? = null,
      maxAltitudeOverride: Double? = null,
      onDistanceSelected: (Double?) -> Unit = {},
      showZoomControls: Boolean = false,
      modifier: Modifier = Modifier
  )
  ```
- Compute adaptive padding and canvas height:
  ```kotlin
  val topPadding = if (showZoomControls) 44.dp else 16.dp
  val totalCanvasHeight = if (showZoomControls) cachedData.adaptiveHeight + 20.dp else cachedData.adaptiveHeight
  ```
- Conditionally apply `pointerInput` on `Canvas`:
  - When `showZoomControls == true`, attach `pointerInput` gesture handler.
  - When `showZoomControls == false`, omit `pointerInput` entirely.
- Apply `padding(bottom = 24.dp, start = 50.dp, end = 25.dp, top = topPadding)`.
- Update scrubber text baseline anchor to `y = -4.dp.toPx()`:
  ```kotlin
  canvas.nativeCanvas.drawText(
      combinedLabel,
      (markerX - lWidth / 2f).coerceIn(0f, (width - lWidth).coerceAtLeast(0f)),
      -4.dp.toPx(),
      highlightPaint
  )
  ```
- Guard controls row and legend button with `if (showZoomControls)`:
  - Zoom controls row rendered only if `showZoomControls && cachedData.totalDist > 10.0`.
  - Legend info button rendered only if `showZoomControls`.

### Task 2: Pass `showZoomControls = true` in `MapDetailLayout.kt`
- File: [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)
- In line 195, update `ElevationProfile` invocation:
  ```kotlin
  ElevationProfile(
      pathPoints = path,
      currentDistance = selectedDistance,
      minAltitudeOverride = minAltitudeOverride,
      maxAltitudeOverride = maxAltitudeOverride,
      onDistanceSelected = { selectedDistance = it },
      showZoomControls = true,
      modifier = Modifier.fillMaxWidth()
  )
  ```

### Task 3: Author Unit Tests in `ElevationProfileLayoutTest.kt`
- File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt`
- Test cases:
  1. `testElevationProfile_parameterDefaults`: Verify via reflection that `showZoomControls` defaults to `false` in both `ElevationProfile` overloads.
  2. `testElevationProfile_sourceCodeInspection_layoutSeparation`: Verify `ElevationProfile.kt` sets `topPadding = 44.dp` when `showZoomControls == true`, `16.dp` when `false`, and anchors scrubber text at `-4.dp.toPx()`.
  3. `testMapDetailLayout_enablesZoomControls`: Verify `MapDetailLayout.kt` passes `showZoomControls = true`.
  4. `testListPreviewCallers_doNotEnableZoomControls`: Verify `WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`, and `SensorGridScreen.kt` do not pass `showZoomControls = true`.

### Task 4: Targeted Verification & Clean-Room Regression
- Execute targeted unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"
  ```
- Execute full regression suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Invariant & Regression Guards
1. **List Scrolling & Tap Invariant**: Suppressing `pointerInput` when `showZoomControls == false` completely eliminates gesture conflicts in `LazyColumn` and allows instant click pass-through to parent card handlers (`clickable { onMapClick() }`).
2. **Chart Plotting Area Invariant**: Adding 20dp to total canvas height when `topPadding` increases by 20dp preserves the exact drawable chart height `adaptiveHeight - 48.dp`.
3. **Mathematical Zoom Invariant**: `ElevationProfileZoomMath` functions and coordinate transformations are completely untouched and fully preserved.
4. **9-Language Localization Invariant**: Zero changes to string resource names or localization parity.
