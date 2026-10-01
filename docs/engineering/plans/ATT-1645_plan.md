# Stage 3: Implementation Plan - ATT-1645: Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-1899](https://atrainingtracker.atlassian.net/browse/ATT-1899) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-221` (*Standardized Bottom Sheet Peek Height Baselines & Information Footprint Framing*)  
**Test Mapping**: `TST-UI-175` (*Bottom Sheet Peek Baselines, Design Token Centralization & System Navigation Inset Verification*)  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

Bottom sheets and persistent scaffolds across the application currently rely on disparate hardcoded numeric literals for their collapsed peek heights (`100.dp`, `120.dp`, `140.dp`, `185.dp`). On physical devices (e.g. Pixel 10), several of these peek baselines lead to degraded user experiences:
1. In `WorkoutClusterHeatmapScreen` and `PeriodMapScreen`, a peek height of `120.dp` cuts right through the Date/Time row of the `WorkoutHeader`.
2. In `RouteOnMapScreen`, `100.dp` tightly pinches the bottom visibility switch against the navigation bar boundary.
3. In `SegmentOnMapScreen`, `185.dp` overshoots the natural 3-row metric card by ~33.5dp, exposing an awkward empty container gap.
4. Sheet peek heights lack centralized design token governance in [BottomSheetDesign.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt).

This task establishes centralized, semantically calibrated peek height tokens in `BottomSheetDesign` and replaces all hardcoded occurrences across the application.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)
* **Test Mapping**: `TST-UI-175` (*Bottom Sheet Peek Baselines, Design Token Centralization & System Navigation Inset Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **System Insets Invariant**: Every persistent sheet peek height MUST strictly add `navBarHeight` (`WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`) to ensure the header floats cleanly above the system navigation bar on 3-button and gesture navigation modes.
2. **Maximum Height Boundary Constraint**: The maximum expanded height constraint (`maxSheetHeight = maxHeight - statusBarHeight`) established under `REQ-SET-069` remains strictly unaltered.
3. **Drag Handle Dimensions**: `MinimumDragHandle` layout dimensions (32dp x 3dp, 15dp total vertical footprint) remain strictly unaltered.
4. **Subtask Direct Completion**: Sub-task `ATT-1899` transitions directly to `Erledigt` upon passing Gate 3 review via `freigabe`.
5. **Parent Human Gate Invariance**: Moving parent ticket `ATT-1645` to `Erledigt` remains an inviolable human decision gate.

---

## 4. Proposed Architectural Changes

### Component 1: `BottomSheetDesign.kt` (Design Tokens)
Introduce 5 canonical, standardized Dp baseline tokens:
```kotlin
object BottomSheetDesign {
    // Existing tokens...
    val SheetCornerRadius: Dp = 20.dp
    val SheetShape: Shape = RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius)
    val SheetShadowElevation: Dp = 8.dp
    val SheetTonalElevation: Dp = 0.dp
    val BorderWidth: Dp = 1.dp
    val DragHandleWidth: Dp = 32.dp
    val DragHandleHeight: Dp = 3.dp

    // --- Standardized Peek Height Baselines (REQ-UI-221, ATT-1645) ---
    /** Calibrated baseline for single workout detail peeks (Heatmap & Period Map) cleanly framing WorkoutHeader. */
    val PeekHeightWorkout: Dp = 140.dp

    /** Calibrated baseline for route detail peeks framing RouteSummaryHeader and visibility switch. */
    val PeekHeightRoute: Dp = 112.dp

    /** Calibrated baseline for segment detail peeks framing SegmentHeader and SegmentDetails. */
    val PeekHeightSegment: Dp = 156.dp

    /** Calibrated baseline for favorite location (Lieblingsort) peeks framing KnownLocationOnMapSheet. */
    val PeekHeightKnownLocation: Dp = 108.dp

    /** Calibrated baseline for active live segment tracking peek framing live delta and target metrics. */
    val PeekHeightLiveSegment: Dp = 140.dp
}
```

### Component 2: Screen Composables (Token Adoption)
* **`MapScreenWithTrack.kt`**:
  ```kotlin
  sheetPeekHeight = when {
      selectedSegmentId != null -> BottomSheetDesign.PeekHeightSegment + navBarHeight
      selectedRouteId != null -> BottomSheetDesign.PeekHeightRoute + navBarHeight
      selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight
      else -> 0.dp
  }
  ```
* **`SensorGridScreen.kt`**:
  ```kotlin
  sheetPeekHeight = if (showLiveSegments && screenMode == ScreenMode.TRACKING) {
      BottomSheetDesign.PeekHeightLiveSegment + navBarHeight
  } else 0.dp
  ```
* **`WorkoutClusterHeatmapScreen.kt` & `PeriodMapScreen.kt`**:
  ```kotlin
  sheetPeekHeight = if (peekedWorkoutDataWithTrack != null && !isEditingFingerprint) {
      BottomSheetDesign.PeekHeightWorkout + navBarHeight
  } else 0.dp
  ```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Extend `BottomSheetDesign.kt` with Peek Baseline Tokens
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt`
* **Changes**: Add `PeekHeightWorkout`, `PeekHeightRoute`, `PeekHeightSegment`, `PeekHeightKnownLocation`, and `PeekHeightLiveSegment`.

### Step 2: Screen-Level Token Adoption
* **File 1**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt`
  * Replace `185.dp` with `BottomSheetDesign.PeekHeightSegment`.
  * Replace `100.dp` with `BottomSheetDesign.PeekHeightRoute` for selected route.
  * Replace `100.dp` with `BottomSheetDesign.PeekHeightKnownLocation` for selected location.
* **File 2**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
  * Replace `140.dp` with `BottomSheetDesign.PeekHeightLiveSegment`.
* **File 3**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt`
  * Replace `120.dp` with `BottomSheetDesign.PeekHeightWorkout`.
* **File 4**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt`
  * Replace `120.dp` with `BottomSheetDesign.PeekHeightWorkout`.

### Step 3: Extend Unit Test Assertions
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesignTest.kt`
* **Changes**: Add `@Test fun testBottomSheetDesign_peekHeightBaselineConstants()` asserting exact Dp values.

### Step 4: Extend Visual Contract Test
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Changes**: Add `@Test fun testScreens_consumeStandardizedPeekHeightTokens()` asserting that all 4 screens reference `BottomSheetDesign.PeekHeight*` and contain zero hardcoded raw literals for peek heights.

### Step 5: Execute Targeted Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.BottomSheet*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit and visual contract tests run first in Stage 4 (~10s). Full regression suite (`./gradlew testDebugUnitTest`) executed in Stage 5. Physical on-device review of Pixel 10 in Ceremony 2.
* **Rollback**: Branch isolation (`feature/ATT-1645`) ensures clean discard via `git checkout sprint/2026-40.8` if needed.
