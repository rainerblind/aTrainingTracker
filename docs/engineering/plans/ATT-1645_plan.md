# Stage 3: Implementation Plan - ATT-1645: Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-1963](https://atrainingtracker.atlassian.net/browse/ATT-1963) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)  
**Test Mapping**: `TST-UI-175` (*Bottom Sheet Peek Baselines, Design Token Centralization & System Navigation Inset Verification*)  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

Bottom sheet peek baselines across the application were standardized into centralized tokens in `BottomSheetDesign.kt` in Sprint `2026-40.8`. However, physical review on Google Pixel 10 (Android 15 edge-to-edge gesture navigation) revealed three concrete calibration defects:
1. In `SegmentOnMapScreen`, `PeekHeightSegment = 156.dp` was too short for the 3-row `SegmentDetails` card, causing Row 3 (altitude icon, elevation gain, min and max altitude) to be obscured behind the system navigation bar.
2. In `RouteOnMapScreen`, `PeekHeightRoute = 112.dp` cleanly frames routes without descriptions, but when a route has a description string, the description row is obscured behind the navigation bar.
3. In `LiveSegmentSheet` (`SensorGridScreen`), `PeekHeightLiveSegment = 140.dp` was taller than the live header footprint, causing the top slice of the underlying elevation profile chart to prematurely peek out above the navigation bar in the resting collapsed state.

This implementation plan defines the exact code modifications and unit test updates to recalibrate these baselines.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)
* **Test Mapping**: `TST-UI-175` (*Bottom Sheet Peek Baselines, Design Token Centralization & System Navigation Inset Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **System Insets Invariant**: Every persistent sheet peek height MUST strictly add `navBarHeight` (`WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`) to ensure the header floats cleanly above the system navigation bar on 3-button and gesture navigation modes.
2. **Maximum Height Boundary Constraint**: The maximum expanded height constraint (`maxSheetHeight = maxHeight - statusBarHeight`) established under `REQ-SET-069` remains strictly unaltered.
3. **Drag Handle Dimensions**: `MinimumDragHandle` layout dimensions (32dp x 3dp, 15dp total vertical footprint) remain strictly unaltered.
4. **Subtask Direct Completion**: Sub-task `ATT-1963` transitions directly to `Erledigt` upon passing Gate 3 review via `freigabe`.
5. **Parent Human Gate Invariance**: Moving parent ticket `ATT-1645` to `Erledigt` remains an inviolable human decision gate reserved exclusively for the user.

---

## 4. Proposed Architectural Changes

### Component 1: `BottomSheetDesign.kt` (Design Tokens)
Update baseline tokens:
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
    val PeekHeightWorkout: Dp = 140.dp
    val PeekHeightRoute: Dp = 112.dp
    val PeekHeightRouteWithDescription: Dp = 152.dp
    val PeekHeightSegment: Dp = 192.dp
    val PeekHeightKnownLocation: Dp = 108.dp
    val PeekHeightLiveSegment: Dp = 126.dp
}
```

### Component 2: `MapScreenWithTrack.kt` (Dynamic Route Description Aware Peek)
In `MapScreenWithTrack.kt`, update `sheetPeekHeight` when evaluating `selectedRouteId`:
```kotlin
sheetPeekHeight = when {
    selectedSegmentId != null -> BottomSheetDesign.PeekHeightSegment + navBarHeight
    selectedRouteId != null -> {
        val routeSummary = allRoutes.find { it.summary.id == selectedRouteId }?.summary
        val basePeek = if (routeSummary?.description.isNullOrEmpty()) {
            BottomSheetDesign.PeekHeightRoute
        } else {
            BottomSheetDesign.PeekHeightRouteWithDescription
        }
        basePeek + navBarHeight
    }
    selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight
    else -> 0.dp
}
```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Tokens in `BottomSheetDesign.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt`
* **Changes**:
  * Update `PeekHeightSegment` from `156.dp` to `192.dp`.
  * Add `PeekHeightRouteWithDescription: Dp = 152.dp`.
  * Update `PeekHeightLiveSegment` from `140.dp` to `126.dp`.

### Step 2: Update Peek Height Calculation in `MapScreenWithTrack.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt`
* **Changes**:
  * In `sheetPeekHeight` branch for `selectedRouteId`, check `routeSummary?.description.isNullOrEmpty()`.
  * Select `BottomSheetDesign.PeekHeightRoute` or `BottomSheetDesign.PeekHeightRouteWithDescription` accordingly.

### Step 3: Update Unit and Contract Tests
* **Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesignTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Changes**:
  * Assert `PeekHeightSegment == 192.dp`.
  * Assert `PeekHeightRouteWithDescription == 152.dp`.
  * Assert `PeekHeightLiveSegment == 126.dp`.
  * Verify `MapScreenWithTrack.kt` references `PeekHeightRouteWithDescription`.

### Step 4: Targeted Test Execution
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.BottomSheet*"`
* **Expected Result**: 100% test pass rate.

---

## 6. Verification & Rollback Strategy

* **Rollback Plan**:
  * Git feature branch isolation: all changes are on `feature/ATT-1645`. If any unexpected regression occurs, `git checkout -- .` restores the pristine state immediately.
* **Gate 3 Verification**:
  * Subtask `ATT-1963` must pass automated Gate 3 review (`tools/review_agent.py audit ATT-1963`) and transition to `Erledigt`.
  * Programmatic pre-check `python3 tools/jira_util.py check-gate ATT-1963` must return code 0 before modifying production code in Stage 4.
