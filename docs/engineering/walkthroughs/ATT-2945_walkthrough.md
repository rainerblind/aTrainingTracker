# Stage 5 Verification & Walkthrough: ATT-2945 - Climb cockpit bottom sheet appears on tracking tabs where live climbs are disabled

**Ticket**: [ATT-2945](https://atrainingtracker.atlassian.net/browse/ATT-2945)  
**Sub-task**: [ATT-3008](https://atrainingtracker.atlassian.net/browse/ATT-3008) (`[Test]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2945`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

Ticket `ATT-2945` resolves the unsolicited appearance of the Live ClimbPro and Segment Cockpit `BottomSheetScaffold` on tracking tabs where live climbs/segments are disabled, as well as on inactive background pager tabs:
1. **Active-Tab Gating & State Synchronization (`REQ-UI-327`)**:
   - Threaded `isTabActive: Boolean = true` from `TrackingTabsScreen.kt`'s `HorizontalPager` (`isTabActive = (pagerState.currentPage == page)`) through `TrackingTabGridContent.kt` down to `SensorGridScreen.kt`.
   - Gated cockpit visibility via `val shouldShowBottomSheet = isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING`.
2. **Scaffold State & Interactive Touch Suppression**:
   - Initialized `scaffoldState` with `skipHiddenState = false` and `initialValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden`.
   - Added `LaunchedEffect(shouldShowBottomSheet)` to trigger `scaffoldState.bottomSheetState.partialExpand()` when active and `scaffoldState.bottomSheetState.hide()` when inactive.
   - Guarded sheet properties: `sheetPeekHeight` collapses to `0.dp`, `sheetSwipeEnabled` is set to `false`, and M3 completely offscreen hides the sheet.
   - Sheet content renders an empty composable when `shouldShowBottomSheet` is false, eliminating any residual 1.dp placeholder touch artifacts.
3. **Preservation of System Invariants & WYSIWYG Architecture**:
   - Retained `SensorGridScreen`'s self-contained `BottomSheetScaffold` architecture complying with `REQ-UI-275` and `TrackingTabWysiwygContractTest`.
   - Maintained design tokens `BottomSheetDesign.SheetShape`, `MaterialTheme.colorScheme.surface`, `BottomSheetDesign.SheetShadowElevation`, and `BottomSheetDesign.SheetTonalElevation` in full compliance with `BottomSheetVisualContractTest` and `LiveSegmentSheetLayoutTest`.
   - Multi-scaffold touch interception and ghost gesture traps are completely prevented because inactive pager pages have gestures disabled and sheet states hidden.
   - Full 100% clean-room test pass rate achieved across the unit test suite.

---

## 2. Changes Implemented

### 2.1 UI Presentation Layer
* [SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt):
  - Added `isTabActive: Boolean = true` parameter.
  - Implemented `shouldShowBottomSheet = isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING`.
  - Configured `scaffoldState` with dynamic initial state (`SheetValue.PartiallyExpanded` vs `SheetValue.Hidden`) and `skipHiddenState = false`.
  - Added `LaunchedEffect(shouldShowBottomSheet)` with animated `partialExpand()` and `hide()`.
  - Guarded `sheetPeekHeight` (collapsing to `0.dp`), `sheetSwipeEnabled` (disabled when inactive), and sheet content body (empty composable when suppressed).
  - Preserved standard M3 bottom sheet design tokens (`surface`, `SheetShape`, and elevations).
* [TrackingTabGridContent.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingTabGridContent.kt):
  - Added `isTabActive: Boolean = true` parameter and forwarded it to `SensorGridScreen`.
* [TrackingTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt):
  - In `HorizontalPager`, passed `isTabActive = (pagerState.currentPage == page)` to `TrackingTabGridContent`.

### 2.2 Contract & Unit Tests
* [SensorGridScreenClimbSuppressionTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenClimbSuppressionTest.kt):
  - Verified `isTabActive` parameter presence with default value `true`.
  - Verified `shouldShowBottomSheet` formula evaluates `isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING`.
  - Verified `scaffoldState` uses conditional `initialValue` and `skipHiddenState = false`.
  - Verified `LaunchedEffect(shouldShowBottomSheet)` synchronizes hidden and partially expanded states.
  - Verified `sheetPeekHeight`, `sheetSwipeEnabled`, and visual elevations collapse when inactive.
  - Verified `TrackingTabsScreen.kt` passes `isTabActive` matching `pagerState.currentPage == page`.
  - Verified `TrackingTabGridContent.kt` forwards `isTabActive` down to `SensorGridScreen`.

### 2.3 Living Documentation
* [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): Updated `REQ-UI-327` status to `Verified`.
* [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): Updated `TST-UI-287` status to `Verified`.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit & Contract Tests
Executed targeted unit tests:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenClimbSuppressionTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabWysiwygContractTest"
```
* **`SensorGridScreenClimbSuppressionTest`**: 7/7 tests PASSED.
* **`TrackingTabWysiwygContractTest`**: 18/18 tests PASSED.
* Result: 100% PASS RATE in 13s.

### 3.2 Full Regression Suite
Executed clean-room full test suite:
```bash
./gradlew testDebugUnitTest
```
* **Total Tests Completed**: 2,260+ tests.
* **Failures**: 0.
* **Errors**: 0.
* **Skipped**: 0.
* **Pass Rate**: 100% PASS RATE.

---

## 4. Requirement & Test Specification Traceability

| Requirement ID | Test Case ID | Test Class | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-327` | `TST-UI-287` | `SensorGridScreenClimbSuppressionTest` | Active tab gating, hidden scaffold state, 0dp peek height, swipe disablement | **Verified** |
| `REQ-UI-275` | `TST-UI-235` | `TrackingTabWysiwygContractTest` | SensorGridScreen BottomSheetScaffold hosting and backward-compatible defaults | **Verified** |
| `REQ-UI-295` | `TST-UI-255` | `TrackingTabWysiwygContractTest` | Configuration mode sheetPeekHeight gating and swipe enable contract | **Verified** |

---

## 5. Invariants Maintained

* **Active-Tab Isolation**: Inactive tracking tabs never show the climb/segment bottom sheet peek or intercept horizontal swipe gestures.
* **Configured Tab Support**: Active tracking tabs with live climbs/segments enabled display the sheet at `116.dp` peek height and allow expanding/collapsing.
* **Zero Ghost Touch Targets**: Suppressed sheets render zero-elevation, transparent, empty content with 0.dp peek height and disabled swipes.
* **Zero Regressions**: 100% full-suite unit test pass rate.
