# Implementation Plan - ATT-440 Refinement: Periods UI Layout & Graph Exposure

Address the issue where bar graphs are missing and the layout is inconsistent in the Workout Periods tabbed screen.

## User Review Required

> [!IMPORTANT]
> - **Visible Graphs**: I identified that the bar graphs were being hidden behind the top header. I am refactoring the layout to ensure they are always visible and correctly positioned below the tabs.
> - **Smooth Transitions**: The graphs and migration cards will now move in perfect synchronization with the collapsing header, providing a professional and responsive experience.
> - **Clean List**: I am simplifying the padding logic to ensure that the workout periods are perfectly aligned and not obscured by other UI elements.

## Proposed Changes

### 1. UI Layer: Header-Content Alignment
Fulfills REQ-UI-104 | Test: TST-UI-076

#### [MODIFY] [PeriodsTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt)
- **Top Padding**: Apply a reactive top padding to the content `Column` inside the `HorizontalPager`.
  ```kotlin
  modifier = Modifier.fillMaxSize().padding(top = with(density) { (appBarMaxHeightPx + connection.appBarOffset).toDp() })
  ```
- **Redundancy Removal**: Remove the complex manual padding from the `migrationStatus` Surface and the `top` content padding from `PeriodList`.
- **Spacing**: Ensure consistent vertical arrangement between `migrationStatus`, `PeriodBarGraph`, and `PeriodList`.

### 2. UI Layer: List Refinement
#### [MODIFY] [PeriodList.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodList.kt)
- **Simplify `contentPadding`**: Remove the `headerHeightPx + appBarOffsetPx` calculation. Replace with a standard `top = 8.dp` since the entire list is now pushed down by its parent `Column`.

## Verification Plan

### Manual Verification (TST-UI-076)
1. Open the 'Periods' screen.
2. **Verify** that the Bar Graph is immediately visible below the Tabs.
3. Scroll the list. **Verify** that the Bar Graph moves up with the list as the header collapses.
4. Verify that during migration, the `migrationStatus` card is also visible and correctly positioned.
