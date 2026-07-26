# Implementation Plan - ATT-350: Sport-Type Aware Clustering

Enhance the Workout Cluster Engine to optionally take the `BSportType` into account during similarity calculation. This prevents different activities (e.g., Running vs. Cycling) on the same route from being merged into the same cluster unless explicitly desired.

## User Review Required

> [!IMPORTANT]
> - **Similarity Penalty**: When enabled, if the workout's sport type doesn't match the cluster's majority sport, a significant penalty (**+2.0**) is added to the similarity score. This effectively isolates different sports into separate clusters even if they share the exact same path.
> - **User Control**: A new toggle will be added to the Cluster Tuning screen to allow users to disable this behavior if they prefer purely spatial clustering.
> - **Default Behavior**: Enabled by default to maintain high-quality automated categorization.

## Proposed Changes

### 1. Core Logic: Sport-Aware Engine
Fulfills REQ-SET-043 (Updated) | Test: TST-SET-034

#### [MODIFY] [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- **Refactor `calculateSimilarity`**:
    - Accept `workoutSportType: BSportType`.
    - If `TrainingApplication.useSportTypeForClustering()` is true AND `workoutSportType != cluster.bSportType`:
        - Add `2.0` to the total score.
- **Update `suggestCluster` & `scoreClusters`**:
    - Propagate `BSportType` from the caller.

### 2. UI: Tuning Toggle
Fulfills REQ-SET-010 (Updated) | Test: TST-SET-034

#### [MODIFY] [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
- Add `useSportTypeForClustering: Boolean` observable state.
- Update `recalculateClusters()` to persist this preference.

#### [MODIFY] [ClusterTuningScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningScreen.kt)
- Add a `Switch` in the tuning content labeled "Take Sport Type into account".

### 3. Foundation: Preferences
#### [MODIFY] [TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)
- Add `SP_CLUSTER_USE_SPORT_TYPE` constant and static accessor.

## Verification Plan

### Manual Verification (TST-SET-034)
1. Identify a route recorded as both "Run" and "Bike".
2. **Verify** that with the toggle ON, they form/join separate clusters.
3. **Verify** that with the toggle OFF and recalculation, they merge into a single cluster (purely spatial matching).
