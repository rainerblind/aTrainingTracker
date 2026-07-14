# Implementation Plan: Unclustered Workout Access (SCRUM-229)

## 1. Problem Statement
When a Workout Cluster is deleted, or when a workout fails to match any existing cluster, the workout becomes "orphaned" from the clustering system. There is currently no way within the Favorite Tracks UI to see these workouts or manually re-assign them to a cluster.

## 2. Requirement Mapping
| Requirement ID | Component | Test ID | Description |
|:---|:---|:---|:---|
| **REQ-SET-040** | `WorkoutClustersTabsScreen`, `WorkoutClusterRepository` | **TST-SET-029** | Provide a dedicated view for workouts without a cluster. |

## 3. Impact Analysis
*   **Component: UI Layer**:
    *   Adds a 5th tab to the track management interface.
    *   Integrates `WorkoutSummaryCompact` for high-fidelity historical display.
*   **Data Integrity**: 
    *   Read-only view of unclustered workouts.
    *   Re-assignment uses the established `assignClusterToWorkout` logic.

## 4. Proposed Changes

### `WorkoutClusterRepository.kt`
*   Add `getUnclusteredWorkouts(): List<WorkoutData>`: Queries `WorkoutSummaries` where `clusterId = -1`, ordered by start time.

### `WorkoutClustersViewModel.kt`
*   Add a StateFlow `unclusteredWorkouts`.
*   Update `refresh()` to populate this flow.

### `WorkoutClustersList.kt`
*   Implement `UnclusteredWorkoutsList` component:
    *   Uses `LazyColumn` with `WorkoutSummaryCompact` items.
    *   Displays `ic_history` icon for the empty state.

### `WorkoutClustersTabsScreen.kt`
*   Add a 5th tab labeled "Offen" (DE) / "Unclustered" (EN).
*   Handle the index of the new tab to render `UnclusteredWorkoutsList`.
*   Pass an `onWorkoutClick` callback that triggers the cluster selection dialog.

### `WorkoutClustersFragment.kt`
*   Increase pager count to 5.
*   Initialize and manage `unclusteredListState`.
*   Implement the `onWorkoutClick` lambda to show the `WorkoutClusterSelectionDialog`.

## 5. Verification Plan
### Manual Verification (TST-SET-029)
1.  **Orphan a Workout**: Delete a cluster containing 1 workout.
2.  **Navigation**: Open **Favorite Tracks**.
3.  **Observation**: 
    *   Navigate to the **Offen** tab.
    *   Verify the orphaned workout is visible.
4.  **Re-assignment**:
    *   Tap the workout.
    *   Select a cluster from the candidate list.
    *   Verify the workout disappears from "Offen" and appears in the target cluster's heatmap.
