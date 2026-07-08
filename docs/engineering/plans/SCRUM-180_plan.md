# Implementation Plan: Manual Workout Cluster Reassignment (SCRUM-180)

## 1. Problem Statement
Automated clustering may sometimes misclassify a workout due to GPS noise or edge-case routes. Users need a way to manually correct these assignments by choosing from a list of spatially similar clusters.

## 2. Requirement Traceability
| Requirement ID | Component | Description | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-017** | UI/Logic | Manual Workout Reassignment between clusters. | TST-SET-009 |

## 3. Impact Analysis
* **Mathematical Core**: Reassigning a workout requires updating the moving average centroids of both the source and target clusters.
* **UX**: Map interaction must be precise enough to distinguish between overlapping tracks in a cluster.
* **Database**: Updates both the `RouteClusters` (metadata/centroids) and `WorkoutSummaries` (association) tables.

## 4. Proposed Changes
### `RouteClusterEngine.kt`
* Expose `getClusterScores(...)` to provide a sorted list of all clusters and their similarity to a specific workout shape.
* Implement `moveWorkoutToCluster(...)`:
    1. Removes workout influence from source cluster (centroid formula reversal).
    2. Adds workout influence to target cluster.
    3. Deletes source cluster if it becomes empty.
    4. Updates `clusterId` in `WorkoutSummaries`.

### `FrequentPathsViewModel.kt`
* Add `moveWorkout(workout: WorkoutData, newClusterId: Long)` to orchestrate the repository and engine updates.
* Expose scoring logic to the UI.

### `FrequentPathHeatmapScreen.kt`
* Add `onPathClick` listener to cluster polylines.
* Implement `MoveWorkoutClusterDialog`:
    * Shows target cluster name and its similarity score.
    * Allows selection via RadioButtons.
    * Triggers the move operation.

## 5. Verification Criteria (TST-SET-009)
1. Open a "Frequent Path" heatmap.
2. Tap on a workout track.
3. Observe the "Move Workout" dialog appearing with candidates and scores.
4. Select a different cluster and tap "Save".
5. Verify the workout disappears from the current heatmap.
6. Navigate to the target cluster and verify the workout is now part of its heatmap.
