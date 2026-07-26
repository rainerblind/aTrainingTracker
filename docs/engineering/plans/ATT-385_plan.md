# Implementation Plan - ATT-385: Show start and end day for weekly period

Restore the display of start and end dates for weekly summaries in the Periods screen to provide better temporal context for users.

## User Review Required

> [!IMPORTANT]
> - **Visual Continuity**: This change brings back a feature from previous versions, ensuring that the 'Weeks' view is as informative as before.
> - **Localized Ranges**: The date range will follow system locale conventions for date formatting.

## Proposed Changes

### 1. Repository Layer: Date Range Calculation
Fulfills REQ-UI-116 | Test: TST-UI-074

#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- Add a `rangeDateFormatter: DateTimeFormatter` (e.g., using `MMM d`).
- Refactor **`initPeriodFromWorkout`**:
    - Calculate the actual start and end dates of the week based on the provided `start` and `end` timestamps.
    - Format the `periodDateRange` as `"[Start Date] - [End Date]"`.
- Refactor **`aggregateChildrenToParent`**:
    - Apply the same range formatting logic for `PeriodType.WEEK`.

### 2. UI Layer: Card Layout Optimization
Fulfills REQ-UI-116 | Test: TST-UI-074

#### [MODIFY] [PeriodSummaryCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt)
- Ensure the `periodDateRange` is correctly rendered below the `periodLabel` (this is already present in the code but requires the backend data to be populated).

## Verification Plan

### Manual Verification (TST-UI-074)
1. Open the 'Periods' screen.
2. Navigate to the 'Weeks' tab.
3. **Verify** that each week item displays its date range (e.g., "Jul 20 - Jul 26") below the "2026-W30" label.
4. Verify that other period types (Day, Month, Year) remain unaffected or appropriately labeled.
