# Implementation Plan - ATT-392: Inform user about creation of WorkoutCluster DB

Implement a detailed progress notification for the Workout Cluster migration and recalculation process, following the project's 'Migration Status' design pattern.

## User Review Required

> [!IMPORTANT]
> - **Visual Consistency**: The progress card will use the same tiered, multi-row layout established for the Periods sync.
> - **Phase Demarcation**: The process will be explicitly divided into Phase 1 (Processing Routes) and Phase 2 (Processing Workouts) for maximum transparency.

## Proposed Changes

### 1. Core Logic: Progress-Aware Engine
Fulfills REQ-PER-006 | Test: TST-PERF-007

#### [NEW] [ClusterMigrationListener.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/ClusterMigrationListener.kt)
- Define `ClusterMigrationListener` interface to report multi-phase progress.

#### [MODIFY] [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- Refactor `migrateHistory(context: Context)` to accept an optional `ClusterMigrationListener`.
- During route processing: Report **Phase 1** progress.
- During workout processing: Report **Phase 1 (Complete)** and **Phase 2** (X of Y) progress.

### 2. Repository & ViewModel: Status Propagation
Fulfills REQ-PER-006 | Test: TST-PERF-007

#### [MODIFY] [WorkoutClusterRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt)
- Implement a bridge between `ClusterMigrationListener` and the repository's `_migrationStatus` Flow.
- Ensure `_migrationStatus` is cleared upon completion.

#### [MODIFY] [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
- Update `recalculateClusters()` to utilize the repository's progress-aware migration method instead of calling the engine directly.

### 3. Localization
Fulfills REQ-UI-106

#### [MODIFY] `strings.xml` (and translations)
- Add keys:
    - `cluster_migration_processing_routes`: "Processing routes…"
    - `cluster_migration_processing_workouts`: "Processing workout %1$d of %2$d…"

## Verification Plan

### Manual Verification (TST-PERF-007)
1. Navigate to 'My Locations' -> 'Tuning'.
2. Tap 'Recalculate All Clusters'.
3. **Verify** that the progress card appears immediately with 'Phase 1: Processing routes…'.
4. **Verify** that it transitions to 'Phase 2: Processing workout X of Y…' while keeping Phase 1 visible as completed.
5. **Verify** that the card disappears once recalculation is finished and the list refreshes.
