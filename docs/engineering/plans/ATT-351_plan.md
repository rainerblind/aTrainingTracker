# Implementation Plan - ATT-351: Adaptive Header Calculation

Implement a dynamic header height calculation system that adapts to varied Android status bar insets (e.g., Pixel notches), replacing fragile hardcoded constants.

## User Review Required

> [!IMPORTANT]
> - **Dynamic Sizing**: The system will move from a fixed 135dp header height to a dynamic calculation: `Actual StatusBar Height + 96dp Content`.
> - **Global Refactoring**: This change affects almost all top-level tabbed screens (Workouts, Periods, Clusters, Segments, Routes, My Sensors, etc.) to ensure a perfectly consistent UI across all Android hardware.
> - **Zero Overlap**: This fix guarantees that the top of your lists will never be hidden behind the header, regardless of your phone's notch or status bar size.

## Proposed Changes

### 1. UI Layer: Dynamic Header Refactoring
Fulfills REQ-UI-117 | Test: TST-BUG-006

#### [MODIFY] Multiple Files (WorkoutTabsScreen.kt, PeriodsTabsScreen.kt, WorkoutClustersTabsScreen.kt, etc.)
- **Adaptive Calculation**:
  ```kotlin
  val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val contentHeight = 96.dp // Standard for Title + Tabs
  val appBarMaxHeightPx = with(density) { (statusBarHeight + contentHeight).roundToPx() }
  ```
- **Consistent Propagation**:
  - Use the new `appBarMaxHeightPx` for the `CollapsingAppBarNestedScrollConnection`.
  - Pass the exact value to the list's `headerHeightPx` to ensure perfect framing.

### 2. Standardized Layout Constants
#### [NEW] [LayoutConstants.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/LayoutConstants.kt)
- Define `val HEADER_CONTENT_HEIGHT = 96.dp` to centralize the "Inner" header size.

## Verification Plan

### Manual Verification (TST-BUG-006)
1. Open the app on a device or emulator with a large status bar (e.g., Pixel).
2. Navigate through all tabbed management screens.
3. **Verify** that the first item in each list is fully visible and not overlapped by the header.
4. **Verify** that there is no large empty gap between the header and the list.
