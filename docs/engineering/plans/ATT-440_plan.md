# Implementation Plan - ATT-440: Reactive Period Detail Loading

Improve Workout Periods by ensuring all sessions are correctly and reactively associated when viewing period details (Map or List), even during background loading.

## User Review Required

> [!IMPORTANT]
> - **Live Map Updates**: The period map (heatmap) will now automatically "grow" as workouts are loaded from the database in the background. You no longer have to wait for the entire history to load before seeing a complete period map.
> - **Accurate List Filtering**: I am verifying that the workout list launched from a period header uses a robust time-range filter that matches the period's database-defined boundaries.

## Proposed Changes

### 1. UI Logic Layer: Reactive Filtering Pipeline
Fulfills REQ-PER-007 | Test: TST-PERF-008

#### [MODIFY] [PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)
- **State Transformation**:
    - Remove the one-time `viewModelScope.launch` inside `showPeriodMap`.
    - Introduce a reactive `Flow` pipeline that combines `workoutRepo.allWorkouts` and `_selectedPeriod`.
    - Automatically update the map's `workoutIdToPathMap` as new workouts enter the period's temporal range.
- **Optimization**: Use a `SharedFlow` or similar to prevent redundant track simplification when the same workout is part of multiple overlapping periods.

### 2. Repository Layer: Consistent Boundaries
#### [CHECK] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- Verify that `startTimestampS` and `endTimestampS` correctly cover the full range of sessions for all period types (Day, Week, Month, Year).
- Ensure `endTimestampS` is always set to the last second of the period (e.g., 23:59:59) to ensure inclusive filtering in the UI list.

## Verification Plan

### Manual Verification (TST-PERF-008)
1. Open the 'Periods' screen.
2. Tap the map icon for a period *immediately* before the background loading completes.
3. **Verify** that the heatmap on the map grows visibly as more sessions are processed.
4. **Verify** that clicking a period header results in a workout list that exactly matches the stats shown on the period card.
