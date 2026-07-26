# Implementation Plan - ATT-375: Synchronize Bar Graph with Dynamic List Growth

Resolve the issue where the period volume bar graph remains scrolled to the left (the past) during the initial training history migration, instead of following the newest-first growth of the period list.

## User Review Required

> [!IMPORTANT]
> - **Real-Time Synchronization**: The bar graph will now automatically shift its focus as new months are aggregated, ensuring the most recent data is always visible on the right.
> - **Zero-Jank Scrolling**: The synchronization logic is optimized to avoid redundant animations while maintaining perfect alignment with the list's scroll position.

## Proposed Changes

### UI Logic Refinement
Fulfills REQ-UI-104 | Test: TST-UI-073

#### [MODIFY] [PeriodsTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt)
- **Refactor `PeriodBarGraph`**:
    - Update the `LaunchedEffect` to be keyed on both `firstVisibleIndex` and `periods.size`. This ensures the graph re-evaluates its position every time a new bucket is added to the list.
    - Improve the scroll logic to use `scrollToItem` (snap) during the high-frequency migration phase and `animateScrollToItem` only for user-initiated scrolls, preventing animation overlap.
    - Explicitly calculate `graphIndex = periods.size - 1 - firstVisibleIndex` inside the effect.

## Verification Plan

### Manual Verification (TST-UI-073)
1. Trigger a full Periods migration (Version 17 restart).
2. Open the "Periods" screen.
3. **Verify** that the bar graph displays the newest month on its rightmost side and stays pinned there as the list grows into the past.
4. **Verify** that manually scrolling the list correctly updates the "Primary" bar in the graph.
