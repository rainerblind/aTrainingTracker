# Stage 5 Verification & Walkthrough: ATT-1645

## 1. Ticket Information
- **Parent Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645) - `Map: Dynamic bottom sheet height depending on title`
- **Subtask**: [ATT-2056](https://atrainingtracker.atlassian.net/browse/ATT-2056) - `[Test] Verification, Clean-Room Regression & Release Verification (Map: Dynamic bottom sheet height depending on title)`
- **Fix Version**: `V4.9.38`
- **Target Branch**: `sprint/2026-40.12`
- **Feature Branch**: `feature/ATT-1645`

---

## 2. Executive Summary of Changes
Addressed the bottom sheet resting offset alignment in [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt):
1. **Dynamic Peek Re-anchoring via `LaunchedEffect`**:
   - In Material 3 Jetpack Compose `BottomSheetScaffold`, dynamic updates to `sheetPeekHeight` after the initial layout pass do not automatically re-anchor the resting position of an already partially expanded sheet.
   - Introduced a `LaunchedEffect(measuredSegmentHeaderHeight, measuredRouteHeaderHeight)` block in `MapScreenWithTrack.kt` that detects when `scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded` and invokes `scaffoldState.bottomSheetState.partialExpand()`.
   - This ensures the bottom boundary line of the white header card (`MinimumDragHandle() + Surface(header)`) sits flush with the top edge of the system navigation bar (`WindowInsets.navigationBars`), satisfying the user requirement: zero pixels of the green map peeking out above the bar and zero pixels of the white header occluded.
2. **Defensive Test Suite Stabilization**:
   - In [TrackerServiceAsyncInitTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceAsyncInitTest.kt), eliminated a race condition between background worker execution and main test thread assertion by adding a `CountDownLatch(1)` in `TestableTrackerService.performStopSelf()`.

---

## 3. Test & Verification Results

### A. Targeted Unit Test Suite
- Test Files:
  - [BottomSheetDesignTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/design/BottomSheetDesignTest.kt): Verifies all canonical `BottomSheetDesign` peek height constants.
  - [BottomSheetVisualContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/design/BottomSheetVisualContractTest.kt): Verifies dynamic self-measurement forwarding contracts across `MapDetailLayout`, `SegmentOnMapScreen`, `RouteOnMapScreen`, and `MapScreenWithTrack`.
- Result: 100% tests passed.

### B. Clean-Room Regression Test Suite
- Command: `./gradlew testDebugUnitTest`
- Result: Full test suite executed with zero regressions and 100% pass rate.

---

## 4. Traceability & Living Documentation
- **Requirements**:
  - `REQ-UI-221`: UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing.
  - Status in `docs/requirements.md`: **Verified**
- **Test Specifications**:
  - `TST-UI-175`: Bottom Sheet Peek Baselines, Design Token Centralization, Dynamic Self-Measurement & System Navigation Inset Verification.
  - Status in `docs/tests.md`: **Verified**
