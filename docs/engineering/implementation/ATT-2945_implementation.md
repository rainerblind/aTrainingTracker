# Stage 4 Implementation: ATT-2945 - Climb cockpit bottom sheet appears on tracking tabs where live climbs are disabled

**Ticket**: [ATT-2945](https://atrainingtracker.atlassian.net/browse/ATT-2945)  
**Sub-task**: [ATT-3007](https://atrainingtracker.atlassian.net/browse/ATT-3007) (`[Implementation]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2945`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Summary of Code Changes

1. **`SensorGridScreen.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   - Added parameter `isTabActive: Boolean = true` to `SensorGridScreen` with default `true` for standalone testing and preview compatibility.
   - Defined `val shouldShowBottomSheet = isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING`.
   - Initialized `scaffoldState` with `initialValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden` and `skipHiddenState = false`.
   - Added `LaunchedEffect(shouldShowBottomSheet)` that explicitly calls `scaffoldState.bottomSheetState.hide()` when `!shouldShowBottomSheet` (if not hidden), and `partialExpand()` when `shouldShowBottomSheet` (if hidden).
   - Configured `BottomSheetScaffold`:
     - `sheetPeekHeight = if (shouldShowBottomSheet && screenMode == ScreenMode.TRACKING) BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp`
     - `sheetSwipeEnabled = (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING && isTabActive`
     - `sheetShadowElevation = if (shouldShowBottomSheet) BottomSheetDesign.SheetShadowElevation else 0.dp`
     - `sheetTonalElevation = if (shouldShowBottomSheet) BottomSheetDesign.SheetTonalElevation else 0.dp`
     - `sheetContainerColor = if (shouldShowBottomSheet) MaterialTheme.colorScheme.surface else Color.Transparent`
     - `sheetContent`: When `shouldShowBottomSheet`, renders `LiveSegmentSheet` or `LiveClimbSheet`. When `!shouldShowBottomSheet`, renders an empty composable block (eliminating the 1.dp placeholder box and shadow artifact).

2. **`TrackingTabGridContent.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   - Added `isTabActive: Boolean = true` parameter to `TrackingTabGridContent`.
   - Forwarded `isTabActive = isTabActive` to `SensorGridScreen`.

3. **`TrackingTabsScreen.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs`)**:
   - In `HorizontalPager`, passed `isTabActive = (pagerState.currentPage == page)` to `TrackingTabGridContent`, isolating bottom sheet activation strictly to the active visible tab.

4. **`SensorGridScreenClimbSuppressionTest.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
   - Added architectural contract tests verifying:
     - `SensorGridScreen` accepts `isTabActive: Boolean = true`.
     - `shouldShowBottomSheet` gating expression.
     - `scaffoldState` conditional initial value (`SheetValue.Hidden` vs `SheetValue.PartiallyExpanded`).
     - `LaunchedEffect` driving `hide()` and `partialExpand()`.
     - `sheetShadowElevation`, `sheetTonalElevation`, and `sheetContainerColor` suppression to `0.dp` / `Color.Transparent` when inactive.
     - `TrackingScreenState.showLiveClimbs` defaults to `true` for backward compatibility (`REQ-UI-275`).
     - `TrackingTabsScreen` passes `isTabActive = pagerState.currentPage == page`.

---

## 2. Targeted Verification Results

Executed targeted unit tests:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenClimbSuppressionTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabWysiwygContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingMapIsolationContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelGridTest"
```
* **`SensorGridScreenClimbSuppressionTest`**: 7/7 tests PASSED (`TST-UI-287`).
* **`TrackingTabWysiwygContractTest`**: 10/10 tests PASSED (`TST-UI-235`, `TST-UI-255`).
* **`TrackingMapIsolationContractTest`**: 3/3 tests PASSED (`TST-UI-286`).
* **`TrackingViewModelGridTest`**: 5/5 tests PASSED.
* Result: BUILD SUCCESSFUL in 13s (25/25 tests passed, 0 failures, 100% pass rate).
