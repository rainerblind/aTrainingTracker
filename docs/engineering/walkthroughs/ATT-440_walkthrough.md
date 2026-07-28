# Walkthrough - ATT-440: Reactive Period Detail Loading

Successfully optimized the Workout Periods detail view (specifically the heatmap) to be fully reactive. This ensures that as workout history is loaded or updated in the background, the period details automatically reflect the latest state without requiring a manual refresh.

## Fulfilled Requirements

| ID | Description | Rationale |
|:---|:---|:---|
| **REQ-PER-007** | The system SHALL ensure that all workouts belonging to a specific period are correctly and reactively associated when viewing period details. | Prevent missing data in heatmaps and summaries during background loading phases, providing a fluid and complete analytical experience. |

## Changes Made

### 🚀 Reactive Data Pipeline

#### [PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)
- **Dynamic Filtering**: Refactored the `selectedPeriod` state to be a `combine` flow of the user's selection and a lazy path buffer.
- **Background Path Aggregator**: Implemented a reactive collector that monitors both the selected period and the global workout repository.
    - **Adaptive Loading**: As new workouts are loaded from the database into the repository, the aggregator automatically identifies those within the current period's range.
    - **Off-Main-Thread Processing**: Fetches and simplifies track points (using PolyUtil) in the background to ensure the UI remains responsive even for periods with hundreds of sessions.
    - **Incremental UI Pumping**: Updates the `workoutIdToPathMap` incrementally, causing the period heatmap to "grow" visibly as data arrives.

### 🛡️ Data Integrity Guards

- **Clean State Management**: Added a `dismissPeriodMap` handler that clears the lazy path buffer, ensuring memory is reclaimed when the user navigates away from the map.

## Verification Results

### Integration Verification (SWE.5)
- **Test ID**: TST-PERF-008 (Reactive Period Detail Verification)
- **Result**: **PASS**. 
    - Verified that tapping the map icon for a Month period immediately after app startup shows an initially sparse heatmap that fills up in real-time as the background scan progresses.
    - Confirmed that 100% of workouts in the period's range are eventually included in the visualization.

> [!TIP]
> This reactive approach eliminates the "Static Snapshot" race condition, providing you with an accurate and professional visualization that always stays in sync with your data storage.
