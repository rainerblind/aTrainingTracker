# Implementation Plan: Limit Workout, Route & Segment Detail Bottom Scaffold to Status Bar (ATT-832)

* **Parent Ticket**: [ATT-832](https://atrainingtracker.atlassian.net/browse/ATT-832) ([Verbesserung] Workout Detail bottom scaffold: whitespace at the very top must have the height of the top info bar)
* **Sub-Task**: [ATT-846](https://atrainingtracker.atlassian.net/browse/ATT-846) ([Impl-Plan] Workout Detail bottom scaffold: whitespace at the very top must have the height of the top info bar)
* **Requirement**: `REQ-SET-069` (*Workout, Route & Segment Detail Bottom Sheet Scaffold Status Bar Boundary Constraint*)
* **Test Specification**: `TST-SET-058` (*Workout, Route & Segment Bottom Scaffold Status Bar Boundary & Peek Verification*)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-832`

---

## 1. Context & Architectural Motivation

In the Workout, Route, and Segment detail screens, a bottom sheet scaffold (`BottomSheetScaffold`) is used to present detail information (track telemetry, elevation profiles, splits, and route/segment summaries) over an interactive background map.

In the initial implementation attempt, an attempt was made to increase the container height of `MinimumDragHandle` to match `WindowInsets.statusBars`. On real physical devices, this led to two major issues:
1. The drag handle container was excessively tall (~36–48 dp), causing an oversized, awkward whitespace block around the pill.
2. The extra top padding inside the bottom sheet pushed the sheet content downwards, displacing the peeked content and cutting off critical workout metrics in the initial popup state (`sheetPeekHeight`).

### Architectural Solution:
Following user redirection and modern Android UX standards (e.g. Google Maps), the bottom popup height must be constrained to the bottom edge of the Android system status bar (`WindowInsets.statusBars`):
- Instead of altering the internal padding or size of `MinimumDragHandle`, the top boundary of `BottomSheetScaffold` is aligned with the bottom of the status bar using `Modifier.statusBarsPadding()`.
- In Compose Material 3 `BottomSheetScaffold`, the `Expanded` anchor is calculated as:
  $$\text{anchor}_{\text{Expanded}} = \max(\text{layoutHeight} - \text{sheetHeight},\, 0\text{f})$$
  Because the scaffold itself is padded by `statusBarsPadding()`, its coordinate space starts at $Y = \text{statusBarHeight}$ on the screen. The sheet's maximum upward movement stops at $0\text{f}$ relative to the scaffold, preventing it from ever overlapping the Android status bar icons (clock, battery, notifications).
- The `sheetPeekHeight` is anchored to the bottom edge of the scaffold (which reaches the bottom edge / navigation bar of the display). Thus, the initial popup position and content visibility remain 100% unaltered.
- `MinimumDragHandle` retains its original compact height (~24 dp) and minimal vertical padding (4 dp).

---

## 2. Impact Analysis & Proposed Code Changes

### 2.1 Component: Period Map Screen (`PeriodMapScreen.kt`)
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt)
* **Changes**:
  1. Add `modifier = Modifier.statusBarsPadding()` to `BottomSheetScaffold` at line 213.
  2. Remove redundant `.statusBarsPadding()` from the stats header `Surface` at line 236, as the parent scaffold is now inset from the status bar.

### 2.2 Component: Map Fragment with Track (`MapFragmentWithTrack.kt`)
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrack.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrack.kt)
* **Changes**:
  1. Add `modifier = Modifier.statusBarsPadding()` to `BottomSheetScaffold` at line 112.
  2. The segment and route peeked sheets (`SegmentOnMapScreen` and `RouteOnMapScreen`) continue to use `useStatusBarsPadding = false`. When fully expanded, the scaffold stops at the status bar bottom edge.

### 2.3 Component: Workout Cluster Heatmap Screen (`WorkoutClusterHeatmapScreen.kt`)
* **File**: [`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)
* **Changes**:
  1. Add `modifier = Modifier.statusBarsPadding()` to `BottomSheetScaffold` at line 333.
  2. The peeked workout sheet (`TrackOnMapScreen`) continues to use `useStatusBarsPadding = false`. When expanded, it stops cleanly below the status bar.

---

## 3. System Invariants ("What MUST NOT Change")

1. **`MinimumDragHandle` Layout & Size**: `MinimumDragHandle` in `BottomSheetUtils.kt` must remain at its standard compact dimensions (32 dp pill width, 4 dp height, 4 dp vertical padding) without status bar height overrides.
2. **Initial Popup (Peek) Height**:
   - `PeriodMapScreen`: `120.dp + navBarHeight`
   - `MapFragmentWithTrack` (Segment): `185.dp + navBarHeight`
   - `MapFragmentWithTrack` (Route): `100.dp + navBarHeight`
   - `WorkoutClusterHeatmapScreen`: `120.dp + navBarHeight`
   All peeked content must remain unclipped and completely visible.
3. **Map Rendering & Gestures**: Map panning, zooming, marker taps, and snapshot generation must remain fully functional.
4. **Data Entities & ViewModels**: No changes to Room/SQLite schemas, ViewModels, or repository interfaces.

---

## 4. Verification & Testing Strategy (`TST-SET-058`)

1. **Static / Structural Verification**:
   - Verify that `BottomSheetScaffold` in all 3 screens specifies `modifier = Modifier.statusBarsPadding()`.
   - Verify `MinimumDragHandle` retains default compact styling.
2. **Initial Popup Verification**:
   - Verify that tapping a workout in `PeriodMapScreen` opens the peek without cutting off title, sport icon, date, or elevation profile.
   - Verify segment and route peeks in `MapFragmentWithTrack` render cleanly without excessive whitespace.
3. **Full Expansion Verification**:
   - Expand the bottom sheet in all 3 screens to `SheetValue.Expanded`: verify the top edge of the sheet aligns with the bottom of the status bar and does not obscure the clock, battery, or notification icons.
4. **Clean-Room Regression Testing**:
   - Run `./gradlew testDebugUnitTest` to guarantee 100% test pass rate with zero regressions.
