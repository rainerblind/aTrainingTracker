# Implementation Plan - ATT-359: Workout Cluster Detail Optimization

Implement high-performance batch metadata lookups and asynchronous spatial preparation to ensure sub-1-second loading times for Workout Cluster detail screens.

## User Review Required

> [!IMPORTANT]
> - **Batch Fetching**: Transitions from O(N) sequential database queries to O(1) vectorized queries for family metadata.
> - **Thread Isolation**: All expensive CPU tasks (polyline decoding, simplification) are moved off the main thread to prevent UI freezing.
> - **UI Feedback**: Introduces a subtle loading state to confirm the app is processing high-fidelity paths.

## Proposed Changes

### 1. Data Layer: O(1) Batch Queries
Fulfills REQ-PER-001 | Test: TST-PERF-002

#### [MODIFY] [WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
- Implement `getExtremaForWorkouts(Collection<Long> workoutIds)`: Fetch all relevant metrics for a batch in one query.
- Add `ExtremaRecord` DTO for clean data transfer.

#### [MODIFY] [StravaUploadDbHelper.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/db/StravaUploadDbHelper.java)
- Implement `getStravaActivityDataForWorkouts(Collection<String> fileNames)`: Vectorized lookup of Strava JSON metadata.

### 2. Domain & Mapping Optimization
Fulfills REQ-PER-001 | Test: TST-PERF-002

#### [MODIFY] [WorkoutDataMapper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt)
- Introduce `BatchMetadata` data class.
- Overload `fromCursor` to accept pre-fetched metadata, eliminating internal `getExtremaValue` and `getStravaActivityData` calls inside the mapping loop.

#### [MODIFY] [WorkoutClusterRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt)
- Implement `loadWorkoutsWithBatchMetadata`: The core orchestration logic for optimized family loading.

### 3. UI Performance: Background Preparation
Fulfills REQ-PER-001 | Test: TST-PERF-002

#### [MODIFY] [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
- Introduce `ClusterMapState` to encapsulate all ready-to-render map objects.
- Refactor `selectCluster`: Perform all coordinate decoding, path simplification, and marker construction in `Dispatchers.Default`.

#### [MODIFY] [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)
- Observe `mapState` instead of raw workouts.
- Implement a `CircularProgressIndicator` overlay during the background loading phase.

## Verification Plan

### Automated Tests
- **NumericalEncodingUtilsTest**: Ensure that optimized batch decoding preserves data integrity.

### Manual Verification (TST-PERF-002)
1. Open a Workout Cluster family with > 30 recordings.
2. Verify display completes in < 1 second.
3. Verify zero main-thread jank during navigation.
