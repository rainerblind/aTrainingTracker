# Implementation Plan: Update Unclustered Workouts UI & Navigation (ATT-277)

## 1. Requirement Traceability
| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-056** | Harmonize unclustered workout layout and implement direct map navigation with cluster assignment option. | `WorkoutClusterComponents.kt`, `WorkoutClustersFragment.kt`, `TrackOnMapScreen.kt` | `TST-SET-040` |

## 2. Proposed Changes

### `WorkoutClusterComponents.kt`
- **New Composable**: `UnclusteredWorkoutItem`
    - Mirror the layout of `ClusterItem` (Sober Identity Row + Metadata Block + Mini-map).
    - **Identity Row**: Use `bSportType` icon and `workoutName`.
    - **Metadata Block**: Show `Distance`, `Sport Name`, and `Equipment`.
    - **Mini-map**: Render the workout's `mapPolyline` and its signature markers (Start, End, Apex).
    - Handle long-press for potential context menu (e.g., delete workout).

### `WorkoutClustersList.kt`
- Update `UnclusteredWorkoutsList` to use `UnclusteredWorkoutItem` instead of `WorkoutSummaryCompact`.

### `WorkoutClustersFragment.kt`
- Add state: `selectedUnclusteredWorkout: WorkoutData?`.
- Update `onWorkoutClick` callback in `WorkoutClustersTabsScreen` to set `selectedUnclusteredWorkout = it`.
- Update the `when` block to handle `selectedUnclusteredWorkout != null`:
    - Display `TrackOnMapScreen`.
    - Pass `headerActions` containing a "Move to Cluster" `IconButton`.
    - Handle "Move to Cluster" by showing the `WorkoutClusterSelectionDialog`.

### `TrackOnMapScreen.kt`
- (Audit) Ensure it correctly displays the `headerActions`. (Already verified in code read).

### `WorkoutClustersViewModel.kt`
- **Updated**: `moveWorkout(workout, newClusterId)`
    - Removed dependency on `_selectedCluster.value` for determining the current cluster ID; now uses `workout.clusterId` directly.
    - Added immediate refresh of both `_clusterWorkouts` and `_unclusteredWorkouts` after a move operation to ensure UI consistency.

## 3. Impact Analysis
- **UI Parity**: Significantly improves the consistency of the "My Locations" area.
- **Workflow**: Provides a better flow for auditing and assigning unclustered recordings.
- **State Integrity**: Ensures that the unclustered list is always up-to-date after manual reassignment.

## 4. Verification Plan (TST-SET-040)
1. Open the "My Locations" screen and navigate to the "Unclustered" tab.
2. Verify that workout items now feature a mini-map preview, matching the "Clusters" tab style.
3. Tap on a workout.
4. **Expected Result**: The app navigates to the detailed map view of that workout.
5. Tap the "Move to Cluster" icon in the top header.
6. **Expected Result**: The cluster selection dialog appears.
7. Select a cluster and confirm.
8. **Expected Result**: The workout is assigned and the view returns to the unclustered list (with the item removed).
