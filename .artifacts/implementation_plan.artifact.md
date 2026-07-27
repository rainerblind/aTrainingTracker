# Implementation Plan - ATT-440 Final Refinement: Exhaustive Period Synchronization

Address the issue where workouts are missing from period maps by implementing an exhaustive enrichment strategy in the repository and fixing the race conditions in the reactive data pipeline.

## User Review Required

> [!IMPORTANT]
> - **Total Data Visibility**: Every workout in your history will now be correctly included in your period summaries. I am removing the restriction that limited the background loading to only a few "anchor" sessions.
> - **Zero Race Conditions**: I am refactoring the background engine to ensure that fast-loading batches of data no longer "interrupt" each other, guaranteeing that every path is processed to completion.
> - **Instant Heatmaps**: The full-screen period maps will now load much faster by utilizing the pre-simplified polylines already present in the repository, rather than re-querying the heavy raw samples database.

## Proposed Changes

### 1. Repository Layer: Global Polyline Mapping
Fulfills REQ-PER-007 | Test: TST-PERF-008

#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- **Refactor `enrich`**:
    - Populate `workoutIdToPolylineMap` with **ALL** workouts in the period that have spatial data.
    - Keep the `polylines` list limited to anchors for instant rendering in the list view.
    - This provides a high-performance "directory" of all paths that the ViewModel can decode reactively.

### 2. UI Logic Layer: Fast Reactive Aggregation
#### [MODIFY] [PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)
- **Safe Pipeline**: Use a `flatMapLatest` pattern combined with a non-cancelling loop to aggregate paths.
- **Data Source Shift**: Decode paths directly from the `summary.workoutIdToPolylineMap`. This eliminates the heavy database I/O previously used for the "remainder" loading.
- **Completion Guarantee**: Ensure the buffer is strictly cumulative for the active period, preventing data loss during progressive history loads.

## Verification Plan

### Manual Verification (TST-PERF-008 Refined)
1. Clear app cache or trigger a full history sync.
2. Open a Year period map *immediately*.
3. **Verify** that the 5 anchor routes appear instantly.
4. **Observe** the heatmap. It should grow steadily until the number of sessions on the map matches the count shown on the period card.
5. Cross-reference the workout count on the card with the number of visible traces on the map to ensure zero omissions.
