# Implementation Plan - ATT-440 Final Refinement: Standardized Period Loading Algorithm

Adopt the robust "Selection-Driven" loading algorithm from Workout Clusters for Period Details to ensure 100% data visibility and technical reliability.

## User Review Required

> [!IMPORTANT]
> - **Guaranteed Data Visibility**: I am refactoring the Period Detail map to utilize a discrete, database-driven loading task (matching the Workout Clusters implementation). This ensures that every workout in your history is faithfully captured and visualized.
> - **Total Consistency**: This shift from an "Always-Reactive" model to a "Selection-Driven" model eliminates race conditions and ensures that the map precisely reflects your training history for the chosen period.
> - **Performance & Stability**: Map data processing (path decoding, track mapping) will be offloaded to a background task with explicit lifecycle management, guaranteeing a responsive UI and zero omissions.

## Proposed Changes

### 1. Repository Layer: Range-Based Data Access
#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- **Implement `getWorkoutsForRange(startS, endS)`**:
    - Perform a direct database query using `workoutSummariesManager.getWorkoutsInRangeCursor`.
    - Map the results into a list of `WorkoutData` objects.
    - **Rationale**: This provides a "Source of Truth" fetch that is independent of the global history loading state.

### 2. UI Logic Layer: Selection-Driven Pipeline
#### [MODIFY] [PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)
- **Define `PeriodMapState`**: (Tracks, HeatmapPaths, ExtremaMarkers, IsLoading).
- **Refactor `showPeriodMap(summary)`**:
    - Cancel any existing selection job.
    - Set `_selectedPeriod`.
    - Update `_mapState` with `isLoading = true`.
    - **Selection Job**:
        - Fetch workouts for the period range directly from the repository.
        - Process data in a background context (`Dispatchers.Default`):
            - Convert `WorkoutData` to `MapTrack`.
            - Decode `mapPolyline` for the heatmap.
            - Pre-calculate specialized extrema markers.
        - Update `_mapState` with the complete analytical picture.

### 3. Map View Layer: State Integration
#### [MODIFY] [InteractivePeriodMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/InteractivePeriodMap.kt)
- **Input Evolution**: Update to take `mapState: PeriodMapState` as a parameter.
- **Blending**: Combine the instant "Anchor" routes (from `summary.polylines`) with the exhaustive "Full Heatmap" (from `mapState.heatmapPaths`).

#### [MODIFY] [PeriodMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt)
- Integrate the `viewModel.mapState` flow to provide a high-fidelity visual experience.

## Verification Plan

### Manual Verification (TST-PERF-008 Refined)
1. Clear app cache or trigger a full history sync.
2. Open a Month or Year period map *immediately*.
3. **Verify** that the 5 anchor routes appear instantly.
4. **Observe** the loading spinner (if history is large).
5. **Verify** that the heatmap eventually populates with 100% of the sessions in that period range.
6. Verify that the session count on the card matches the traces on the map.
