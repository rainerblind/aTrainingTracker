# ATT-3176 Walkthrough: Default Live Segment and Climb Bottom Sheets to Fully Expanded State During Tracking

## Overview
- **Issue**: [ATT-3176](https://atrainingtracker.atlassian.net/browse/ATT-3176)
- **Requirement**: `REQ-UI-343` (amending `REQ-UI-327`)
- **Test Specification**: `TST-UI-303`
- **Sprint**: `2026-41.9`
- **Status**: Verified

## Problem Addressed
During active tracking workouts, when an athlete approached or entered a live segment (`LiveSegmentSheet`) or climb (`LiveClimbSheet`), `SensorGridScreen` previously initialized and expanded the bottom sheet into `SheetValue.PartiallyExpanded` (peek height 126dp + insets).
In this peeked state, only the top header card was visible. The graphical elevation profile curve (`ClimbProfileCanvas`), instantaneous gradient visualization, and real-time pacing HUD telemetry remained occluded until the athlete manually swiped up.
Touching or swiping a screen while riding on the road or running introduces safety hazards and unnecessary friction. The bottom sheet must default to the fully expanded state (`SheetValue.Expanded`) when popping up.

## Architectural Changes & Solution

1. **Default Expanded BottomSheet State (`SensorGridScreen.kt`)**:
   - Updated `initialSheetValue`:
     ```kotlin
     val initialSheetValue = if (shouldShowBottomSheet) SheetValue.Expanded else SheetValue.Hidden
     ```
   - In `LaunchedEffect(shouldShowBottomSheet)`:
     ```kotlin
     if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
         scaffoldState.bottomSheetState.expand()
     }
     ```
   - This smoothly and reactively animates the bottom sheet directly into `SheetValue.Expanded`, presenting the full elevation profile, gradient HUD, and pacing details immediately upon segment/climb activation.

2. **Interactive Manual Swipe Down Retention**:
   - Preserved `sheetSwipeEnabled = (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING && isTabActive`.
   - Athletes retain complete control to swipe the sheet down to peek height (`SheetValue.PartiallyExpanded`) or dismiss it at any time if they prefer to view underlying sensor tiles.

3. **Strict Tab Isolation & Artifact Suppression**:
   - When switching tabs in `HorizontalPager` or when no segment/climb is active (`!shouldShowBottomSheet`), `SensorGridScreen` forces `SheetValue.Hidden`, sets `sheetPeekHeight = 0.dp`, container color to `Transparent`, and shadow elevation to `0.dp`, completely preventing peek artifacts or cross-tab state bleeding.

4. **Design Guidelines Alignment**:
   - Fully aligned with Section 5.8 (*Live Tracking Bottom Sheets: Segments & Climbs*) of `docs/design_guidelines.md`.

5. **Scope Bounding (Rule 26 / ATT-1250)**:
   - Changes are strictly bounded to `SensorGridScreen.kt` and `SensorGridScreenClimbSuppressionTest.kt`.
   - Zero modifications to sensor drivers, database schemas, or Android manifests.

## Verification & Test Results
- **Targeted Unit Tests (`SensorGridScreenClimbSuppressionTest.kt`)**:
  - `testSensorGridScreen_acceptsIsTabActiveParameterWithDefaultTrue`: Passed.
  - `testSensorGridScreen_shouldShowBottomSheet_incorporatesTabActiveAndScreenMode`: Passed.
  - `testSensorGridScreen_scaffoldState_initializesWithConditionalHiddenState`: Passed (validates `SheetValue.Expanded`).
  - `testSensorGridScreen_launchedEffect_drivesSheetStateHideAndExpand`: Passed (validates `scaffoldState.bottomSheetState.expand()`).
  - `testSensorGridScreen_suppressesPeekHeightAndSwipeGesturesWhenInactive`: Passed.
  - `testTrackingScreenState_defaultsShowLiveClimbsToTrueForBackwardCompatibility`: Passed.
  - `testTrackingTabsScreen_passesIsTabActiveDerivedFromCurrentPage`: Passed.
  - Result: **7 / 7 unit tests passed cleanly**.
- **Clean-Room Regression**:
  - Full project test suite (`./gradlew testDebugUnitTest`) passed cleanly with zero regressions.

## ASPICE Traceability
- **Requirement**: `REQ-UI-343` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-UI-303` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3176_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3176_analysis.md`
